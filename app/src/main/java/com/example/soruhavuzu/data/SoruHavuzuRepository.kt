package com.example.soruhavuzu.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.soruhavuzu.domain.CalibrationEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.abs

class SoruHavuzuRepository(
    private val dao: SoruHavuzuDao,
    private val appContext: Context
) {
    private val activeUserId = "active_user"

    // Rate-limit tracking (in-memory per session + persisted checks)
    private val recentUploadTimestamps = mutableListOf<Long>()

    val examsFlow: Flow<List<ExamEntity>> = dao.observeExams()
    val topicsFlow: Flow<List<TopicEntity>> = dao.observeTopics()
    val userProfileFlow: Flow<UserProfileEntity?> = dao.observeUserProfile()
    val userAttemptsFlow: Flow<List<AttemptEntity>> = dao.observeUserAttempts(activeUserId)
    val topicMasteryFlow: Flow<List<TopicMasteryEntity>> = dao.observeTopicMastery(activeUserId)
    val reputationEventsFlow: Flow<List<ReputationEventEntity>> = dao.observeReputationEvents(activeUserId)
    val reportsFlow: Flow<List<ReportEntity>> = dao.observeReports()

    val questionsWithDetailsFlow: Flow<List<QuestionWithDetails>> = combine(
        dao.observeAllQuestions(),
        dao.observeAllVersions(),
        dao.observeTopics(),
        dao.observeBookmarks(activeUserId),
        dao.observeUserAttempts(activeUserId)
    ) { questions, versions, topics, bookmarks, attempts ->
        val versionMap = versions.associateBy { it.id }
        val topicMap = topics.associateBy { it.id }
        val bookmarkMap = bookmarks.associateBy { it.questionId }
        val solvedQuestionIds = attempts.map { it.questionId }.toSet()

        questions.mapNotNull { q ->
            val ver = versionMap[q.currentVersionId] ?: return@mapNotNull null
            val top = topicMap[q.topicId] ?: TopicEntity(q.topicId, "exam_tyt", "TYT Matematik", "Genel", 99)
            val bm = bookmarkMap[q.id]
            QuestionWithDetails(
                question = q,
                version = ver,
                topic = top,
                isFavorite = bm?.isFavorite == true,
                isWrongNote = bm?.isWrongNote == true,
                hasUserSolvedBefore = solvedQuestionIds.contains(q.id)
            )
        }
    }

    suspend fun ensureSeedDataInitialized() = withContext(Dispatchers.IO) {
        val existingProfile = dao.getUserProfile()
        if (existingProfile == null) {
            val defaultProfile = UserProfileEntity(
                id = activeUserId,
                nickname = "MatematikGezgini",
                email = "ogrenci@soruhavuzu.app",
                targetExam = "TYT",
                birthYear = 2007,
                role = UserRole.USER.name,
                reputation = 65, // Başlangıçta güvenilir yükleyen eşiğini (50) deneyimleyebilir
                globalTheta = 0.25,
                uploaderBias = 0.0,
                uploaderRatedCount = 2,
                onboardingCompleted = true,
                dockerServerUrl = "http://10.0.2.2:8080",
                syncWithDocker = false
            )
            dao.insertUserProfile(defaultProfile)
            dao.insertReputationEvent(
                ReputationEventEntity(
                    userId = activeUserId,
                    delta = 50,
                    reason = "SoruHavuzu Erken Katılımcı & Özgün İçerik Rozeti",
                    relatedQuestionId = null,
                    createdAt = System.currentTimeMillis() - 86_400_000L
                )
            )
            dao.insertReputationEvent(
                ReputationEventEntity(
                    userId = activeUserId,
                    delta = 15,
                    reason = "3 Doğru Çözüm Serisi & Kalibrasyon Katkısı",
                    relatedQuestionId = "q_tyt_05",
                    createdAt = System.currentTimeMillis() - 36_000_000L
                )
            )
        }

        val existingTopics = dao.getAllTopics()
        if (existingTopics.isEmpty()) {
            dao.insertExams(SeedData.exams)
            dao.insertTopics(SeedData.topics)
            val (seedQuestions, seedVersions) = SeedData.buildSeedEntities()
            dao.insertQuestions(seedQuestions)
            dao.insertVersions(seedVersions)

            // Örnek bir şikayet kaydı ekle (Moderasyon ekranında görünmesi için)
            dao.insertReport(
                ReportEntity(
                    questionId = "q_tyt_flagged_1",
                    reporterId = "user_verifier_01",
                    reporterName = "Dogrulayici_Elif",
                    reason = ReportReason.UNCLEAR.name,
                    weight = 2,
                    note = "Soru kökünde aracın dönüş hızı eksik bırakılmış, çözüme göre 90 km/s eklenmeli.",
                    status = "OPEN",
                    createdAt = System.currentTimeMillis() - 7200_000L
                )
            )
        }
    }

    /**
     * Bir kullanıcının soruya verdiği cevabı değerlendirir, ilk denemeyse (isFirst)
     * hem kullanıcının theta değerini hem sorunun difficultyLogit ve difficultyBucket değerini günceller.
     */
    suspend fun submitAttempt(
        questionWithDetails: QuestionWithDetails,
        selectedOptionIndex: Int,
        sessionId: String,
        sessionType: SessionType,
        responseTimeMs: Long,
        consecutiveCorrects: Int,
        consecutiveWrongs: Int
    ): AttemptEvaluationResult = withContext(Dispatchers.IO) {
        val profile = dao.getUserProfile() ?: error("Kullanıcı profili bulunamadı")
        val q = questionWithDetails.question
        val v = questionWithDetails.version

        val priorAttempts = dao.countUserAttemptsForQuestion(activeUserId, q.id)
        val isFirstAttempt = (priorAttempts == 0)
        val isCorrect = (selectedOptionIndex == v.correctOptionIndex)

        val step = CalibrationEngine.evaluateAttempt(
            userTheta = profile.globalTheta,
            question = q,
            selectedOptionIndex = selectedOptionIndex,
            correctOptionIndex = v.correctOptionIndex,
            optionCount = questionWithDetails.options.size,
            isFirstAttempt = isFirstAttempt
        )

        val newFirstCount = if (isFirstAttempt) q.firstAttemptCount + 1 else q.firstAttemptCount
        val newCorrectFirst = if (isFirstAttempt && isCorrect) q.correctFirstCount + 1 else q.correctFirstCount
        val newBucket = CalibrationEngine.logitToBucket(step.newQuestionLogit)

        val updatedQuestion = q.copy(
            attemptCount = q.attemptCount + 1,
            firstAttemptCount = newFirstCount,
            correctFirstCount = newCorrectFirst,
            difficultyLogit = step.newQuestionLogit,
            difficultyBucket = newBucket,
            optionSelectionCountsCsv = step.updatedOptionCountsCsv,
            keySuspect = step.isKeySuspect,
            keySuspectReason = step.keySuspectExplanation
        )
        dao.updateQuestion(updatedQuestion)

        // Kullanıcı global theta ve konu bazlı theta güncellemesi
        if (isFirstAttempt) {
            dao.insertUserProfile(profile.copy(globalTheta = step.newUserTheta))
            val existingMastery = dao.getTopicMastery(activeUserId, q.topicId)
            val currentTopicTheta = existingMastery?.theta ?: profile.globalTheta
            val topicStep = CalibrationEngine.evaluateAttempt(
                userTheta = currentTopicTheta,
                question = q,
                selectedOptionIndex = selectedOptionIndex,
                correctOptionIndex = v.correctOptionIndex,
                optionCount = questionWithDetails.options.size,
                isFirstAttempt = true
            )
            dao.insertTopicMastery(
                TopicMasteryEntity(
                    userId = activeUserId,
                    topicId = q.topicId,
                    theta = topicStep.newUserTheta,
                    solvedCount = (existingMastery?.solvedCount ?: 0) + 1,
                    correctCount = (existingMastery?.correctCount ?: 0) + (if (isCorrect) 1 else 0)
                )
            )
        }

        // Yanlış yapıldıysa otomatik olarak Yanlışlarım (offline okunabilir tekrar) listesine ekle
        val existingBookmark = dao.getBookmark(activeUserId, q.id)
        if (!isCorrect) {
            dao.insertBookmark(
                BookmarkEntity(
                    userId = activeUserId,
                    questionId = q.id,
                    isFavorite = existingBookmark?.isFavorite ?: false,
                    isWrongNote = true,
                    updatedAt = System.currentTimeMillis()
                )
            )
        } else if (sessionType == SessionType.REVIEW && existingBookmark?.isWrongNote == true) {
            // Tekrar modunda doğru çözüldüyse yanlışlarım işaretini kaldırabilir veya güncel tutar
            dao.insertBookmark(existingBookmark.copy(updatedAt = System.currentTimeMillis()))
        }

        val now = System.currentTimeMillis()
        val attemptEntity = AttemptEntity(
            userId = activeUserId,
            questionId = q.id,
            versionId = v.id,
            sessionId = sessionId,
            sessionType = sessionType.name,
            selectedOptionIndex = selectedOptionIndex,
            isCorrect = isCorrect,
            isFirst = isFirstAttempt,
            responseTimeMs = responseTimeMs,
            userThetaBefore = profile.globalTheta,
            userThetaAfter = step.newUserTheta,
            questionLogitBefore = q.difficultyLogit,
            questionLogitAfter = step.newQuestionLogit,
            createdAt = now
        )
        val attemptId = dao.insertAttempt(attemptEntity)

        // Docker tek servis senkronizasyonu açıksa arka planda gönder
        if (profile.syncWithDocker && profile.dockerServerUrl.isNotBlank()) {
            runCatching {
                DockerClientFactory.create(profile.dockerServerUrl).pushAttempt(
                    DockerAttemptPayload(
                        userId = activeUserId,
                        questionId = q.id,
                        versionId = v.id,
                        selectedOptionIndex = selectedOptionIndex,
                        isFirst = isFirstAttempt,
                        responseTimeMs = responseTimeMs
                    )
                )
            }
        }

        val updatedStreakCorrects = if (isCorrect) consecutiveCorrects + 1 else 0
        val updatedStreakWrongs = if (!isCorrect) consecutiveWrongs + 1 else 0
        val nextChallengeProb = (CalibrationEngine.computeChallengeTargetProbability(
            updatedStreakCorrects,
            updatedStreakWrongs
        ) * 100).toInt()

        AttemptEvaluationResult(
            attempt = attemptEntity.copy(id = attemptId),
            isCorrect = isCorrect,
            correctOptionIndex = v.correctOptionIndex,
            solutionText = v.solutionText,
            oldDifficultyBucket = q.difficultyBucket,
            newDifficultyBucket = newBucket,
            oldLogit = q.difficultyLogit,
            newLogit = step.newQuestionLogit,
            oldUserTheta = profile.globalTheta,
            newUserTheta = step.newUserTheta,
            expectedProbabilityPercent = (step.expectedProbability * 100).toInt(),
            wasFirstAttempt = isFirstAttempt,
            newChallengeTargetProbabilityPercent = nextChallengeProb
        )
    }

    /**
     * Görselden EXIF verilerini (konum, cihaz bilgisi vb.) tamamen temizleyerek
     * uygulama içi güvenli yerel depolamaya kaydeder ve pHash benzeri görsel imzası üretir.
     */
    suspend fun sanitizeAndStoreQuestionImage(sourceUri: Uri): Pair<String, String>? = withContext(Dispatchers.IO) {
        runCatching {
            val inputStream = appContext.contentResolver.openInputStream(sourceUri) ?: return@runCatching null
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (bitmap == null) return@runCatching null

            // 8x8 ortalama parlaklık tabanlı görsel pHash hesapla (Duplicate görsel tespiti için)
            val tiny = Bitmap.createScaledBitmap(bitmap, 8, 8, true)
            var sumLum = 0L
            val lums = IntArray(64)
            for (y in 0 until 8) {
                for (x in 0 until 8) {
                    val pixel = tiny.getPixel(x, y)
                    val r = (pixel shr 16) and 0xFF
                    val g = (pixel shr 8) and 0xFF
                    val b = pixel and 0xFF
                    val lum = (r * 3 + g * 4 + b) / 8
                    lums[y * 8 + x] = lum
                    sumLum += lum
                }
            }
            val avg = (sumLum / 64).toInt()
            val pHashBits = lums.joinToString("") { if (it >= avg) "1" else "0" }
            val pHashHex = pHashBits.chunked(4).joinToString("") { it.toInt(2).toString(16) }

            // Yeni bir JPEG dosyasına yeniden kodlayarak tüm EXIF (GPS, kamera modeli vb.) мета verilerini sıfırla
            val uploadsDir = File(appContext.filesDir, "sanitized_uploads").apply { mkdirs() }
            val outFile = File(uploadsDir, "q_img_${System.currentTimeMillis()}.jpg")
            FileOutputStream(outFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
            }
            Uri.fromFile(outFile).toString() to pHashHex
        }.getOrNull()
    }

    sealed class UploadOutcome {
        data class Success(val question: QuestionEntity, val publishedDirectly: Boolean) : UploadOutcome()
        data class Error(val message: String) : UploadOutcome()
    }

    suspend fun uploadQuestion(
        topicId: String,
        bodyText: String,
        imageUri: String?,
        imagePhash: String?,
        options: List<String>,
        correctOptionIndex: Int,
        uploaderRating: Int,
        solutionText: String,
        originalityConfirmed: Boolean
    ): UploadOutcome = withContext(Dispatchers.IO) {
        if (!originalityConfirmed) {
            return@withContext UploadOutcome.Error("Devam etmek için sorunun ÖSYM veya telifli bir yayına ait olmadığını beyan etmelisiniz.")
        }

        // Rate Limit kontrolü (1 dakikada en fazla 4 soru)
        val now = System.currentTimeMillis()
        recentUploadTimestamps.removeAll { now - it > 60_000L }
        if (recentUploadTimestamps.size >= 4) {
            return@withContext UploadOutcome.Error("Hız Sınırı (Rate Limit): Çok hızlı soru yüklüyorsunuz. Lütfen 1 dakika bekleyin.")
        }

        val cleanOptions = options.map { it.trim() }.filter { it.isNotEmpty() }
        if (cleanOptions.size < 2 || cleanOptions.size > 5) {
            return@withContext UploadOutcome.Error("En az 2, en fazla 5 şık girmelisiniz.")
        }
        if (correctOptionIndex !in cleanOptions.indices) {
            return@withContext UploadOutcome.Error("Geçerli bir doğru şık seçmelisiniz.")
        }

        // Telif / ÖSYM ve kalite kontrolü
        val qualityCheck = CalibrationEngine.checkCopyrightAndQuality(bodyText, solutionText)
        if (qualityCheck.isBlockedCopyright) {
            return@withContext UploadOutcome.Error(qualityCheck.message ?: "İçerik kalite kontrolünden geçemedi.")
        }

        // Duplicate (Kopya) metin hash kontrolü
        val contentHash = CalibrationEngine.computeQuestionContentHash(bodyText)
        val existingDuplicate = dao.findDuplicateByContentHash(contentHash)
        if (existingDuplicate != null) {
            return@withContext UploadOutcome.Error("Mükerrer Soru Tespiti (Duplicate Hash): Bu sorunun aynısı veya çok benzeri zaten havuzda mevcut (#${existingDuplicate.id}).")
        }

        val profile = dao.getUserProfile() ?: return@withContext UploadOutcome.Error("Kullanıcı profili bulunamadı.")
        val initialLogit = CalibrationEngine.initialLogitFromUploaderRating(uploaderRating, profile.uploaderBias)
        val initialBucket = CalibrationEngine.logitToBucket(initialLogit)

        // İtibar kapısı: itibar >= 50 veya Editör/Moderatör ise doğrudan ACTIVE, değilse IN_REVIEW
        val targetStatus = if (profile.canDirectPublish) QuestionStatus.ACTIVE else QuestionStatus.IN_REVIEW
        val qId = "q_usr_${UUID.randomUUID().toString().take(6)}"
        val vId = "${qId}_v1"
        val zeroCounts = List(cleanOptions.size) { 0 }.joinToString(",")

        val questionEntity = QuestionEntity(
            id = qId,
            uploaderId = profile.id,
            uploaderName = profile.nickname,
            uploaderRole = profile.role,
            topicId = topicId,
            currentVersionId = vId,
            status = targetStatus.name,
            uploaderRating = uploaderRating.coerceIn(1, 10),
            difficultyLogit = initialLogit,
            difficultyBucket = initialBucket,
            attemptCount = 0,
            firstAttemptCount = 0,
            correctFirstCount = 0,
            keySuspect = false,
            keySuspectReason = null,
            weightedReportScore = 0,
            contentHash = contentHash,
            imagePhash = imagePhash,
            optionSelectionCountsCsv = zeroCounts,
            createdAt = now
        )

        val versionEntity = QuestionVersionEntity(
            id = vId,
            questionId = qId,
            versionNumber = 1,
            bodyText = bodyText.trim(),
            imageUri = imageUri,
            optionsDelimited = cleanOptions.joinToString("|||"),
            correctOptionIndex = correctOptionIndex,
            solutionText = solutionText.trim(),
            editorNote = "İlk yükleme (v1)",
            createdAt = now
        )

        dao.insertQuestion(questionEntity)
        dao.insertVersion(versionEntity)
        recentUploadTimestamps.add(now)

        // Doğrudan yayına çıktıysa +5 itibar puanı deftere işlenir
        if (targetStatus == QuestionStatus.ACTIVE) {
            val newRep = profile.reputation + 5
            dao.insertUserProfile(
                profile.copy(
                    reputation = newRep,
                    uploaderRatedCount = profile.uploaderRatedCount + 1
                )
            )
            dao.insertReputationEvent(
                ReputationEventEntity(
                    userId = profile.id,
                    delta = 5,
                    reason = "Yeni özgün soru doğrudan yayına alındı (#$qId)",
                    relatedQuestionId = qId,
                    createdAt = now
                )
            )
        }

        if (profile.syncWithDocker && profile.dockerServerUrl.isNotBlank()) {
            runCatching {
                DockerClientFactory.create(profile.dockerServerUrl).pushQuestion(
                    DockerQuestionUploadPayload(
                        topicId = topicId,
                        uploaderRating = uploaderRating,
                        bodyText = bodyText,
                        options = cleanOptions,
                        correctOptionIndex = correctOptionIndex,
                        solutionText = solutionText
                    )
                )
            }
        }

        UploadOutcome.Success(questionEntity, targetStatus == QuestionStatus.ACTIVE)
    }

    suspend fun toggleFavorite(questionId: String) = withContext(Dispatchers.IO) {
        val current = dao.getBookmark(activeUserId, questionId)
        val updated = BookmarkEntity(
            userId = activeUserId,
            questionId = questionId,
            isFavorite = !(current?.isFavorite ?: false),
            isWrongNote = current?.isWrongNote ?: false,
            updatedAt = System.currentTimeMillis()
        )
        dao.insertBookmark(updated)
    }

    suspend fun removeFromWrongNotes(questionId: String) = withContext(Dispatchers.IO) {
        val current = dao.getBookmark(activeUserId, questionId) ?: return@withContext
        dao.insertBookmark(current.copy(isWrongNote = false, updatedAt = System.currentTimeMillis()))
    }

    /**
     * Şikayet gönderimi: Doğrulayıcıların (itibar >= 200) şikayeti 2 kat ağırlıklıdır.
     * Telif şikayeti veya ağırlıklı skor >= 3 olduğunda soru otomatik FLAGGED durumuna geçip havuzdan çekilir.
     */
    suspend fun reportQuestion(
        questionId: String,
        reason: ReportReason,
        note: String
    ): Boolean = withContext(Dispatchers.IO) {
        val profile = dao.getUserProfile() ?: return@withContext false
        val question = dao.getQuestionById(questionId) ?: return@withContext false
        val weight = if (profile.isVerifier) 2 else 1

        dao.insertReport(
            ReportEntity(
                questionId = questionId,
                reporterId = profile.id,
                reporterName = profile.nickname,
                reason = reason.name,
                weight = weight,
                note = note.ifBlank { reason.labelTr },
                status = "OPEN",
                createdAt = System.currentTimeMillis()
            )
        )

        val newScore = question.weightedReportScore + weight
        val shouldFlag = reason.isPriorityCopyright || newScore >= 3
        val updatedStatus = if (shouldFlag) QuestionStatus.FLAGGED.name else question.status

        dao.updateQuestion(
            question.copy(
                weightedReportScore = newScore,
                status = updatedStatus
            )
        )
        shouldFlag
    }

    /**
     * Moderatör veya Doğrulayıcı aksiyonu:
     * Sorunun cevap anahtarını veya metnini yeni bir QuestionVersion oluşturarak düzeltir ve ACTIVE yapar.
     */
    suspend fun createNewVersionAndApprove(
        questionId: String,
        newBodyText: String,
        newOptions: List<String>,
        newCorrectIndex: Int,
        newSolutionText: String,
        editorNote: String
    ) = withContext(Dispatchers.IO) {
        val question = dao.getQuestionById(questionId) ?: return@withContext
        val existingVersions = dao.getVersionsForQuestion(questionId)
        val nextVerNum = (existingVersions.maxOfOrNull { it.versionNumber } ?: 1) + 1
        val newVerId = "${questionId}_v$nextVerNum"
        val now = System.currentTimeMillis()

        val newVersion = QuestionVersionEntity(
            id = newVerId,
            questionId = questionId,
            versionNumber = nextVerNum,
            bodyText = newBodyText.trim(),
            imageUri = existingVersions.firstOrNull()?.imageUri,
            optionsDelimited = newOptions.joinToString("|||"),
            correctOptionIndex = newCorrectIndex,
            solutionText = newSolutionText.trim(),
            editorNote = editorNote,
            createdAt = now
        )
        dao.insertVersion(newVersion)
        dao.updateQuestion(
            question.copy(
                currentVersionId = newVerId,
                status = QuestionStatus.ACTIVE.name,
                keySuspect = false,
                keySuspectReason = null,
                weightedReportScore = 0
            )
        )

        val profile = dao.getUserProfile()
        if (profile != null) {
            dao.insertUserProfile(profile.copy(reputation = profile.reputation + 10))
            dao.insertReputationEvent(
                ReputationEventEntity(
                    userId = profile.id,
                    delta = 10,
                    reason = "Soru incelendi ve v$nextVerNum versiyonuyla yayına alındı (#$questionId)",
                    relatedQuestionId = questionId,
                    createdAt = now
                )
            )
        }
    }

    suspend fun approveQuestionInQueue(questionId: String) = withContext(Dispatchers.IO) {
        val question = dao.getQuestionById(questionId) ?: return@withContext
        dao.updateQuestion(
            question.copy(
                status = QuestionStatus.ACTIVE.name,
                weightedReportScore = 0
            )
        )
    }

    suspend fun removeQuestionFromPool(questionId: String, reasonNote: String) = withContext(Dispatchers.IO) {
        val question = dao.getQuestionById(questionId) ?: return@withContext
        dao.updateQuestion(
            question.copy(
                status = QuestionStatus.REMOVED.name,
                keySuspect = false
            )
        )
        val profile = dao.getUserProfile()
        if (profile != null && question.uploaderId == profile.id) {
            dao.insertUserProfile(profile.copy(reputation = (profile.reputation - 15).coerceAtLeast(0)))
            dao.insertReputationEvent(
                ReputationEventEntity(
                    userId = profile.id,
                    delta = -15,
                    reason = "Kaldırılan soru yaptırımı: $reasonNote (#$questionId)",
                    relatedQuestionId = questionId,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun completeOnboarding(
        nickname: String,
        email: String,
        authProvider: String,
        targetExam: String,
        birthYear: Int,
        initialSelfLevel: Int
    ) = withContext(Dispatchers.IO) {
        val current = dao.getUserProfile()
        val initialTheta = CalibrationEngine.initialLogitFromUploaderRating(initialSelfLevel, 0.0)
        if (current != null) {
            dao.insertUserProfile(
                current.copy(
                    nickname = nickname,
                    email = "$email ($authProvider)",
                    targetExam = targetExam,
                    birthYear = birthYear,
                    globalTheta = initialTheta,
                    onboardingCompleted = true
                )
            )
        }
    }

    suspend fun updateUserProfileSettings(
        nickname: String,
        targetExam: String,
        birthYear: Int,
        role: UserRole,
        reputation: Int,
        dockerUrl: String,
        syncDocker: Boolean
    ) = withContext(Dispatchers.IO) {
        val current = dao.getUserProfile() ?: return@withContext
        dao.insertUserProfile(
            current.copy(
                nickname = nickname.trim().ifBlank { current.nickname },
                targetExam = targetExam,
                birthYear = birthYear,
                role = role.name,
                reputation = reputation,
                dockerServerUrl = dockerUrl.trim(),
                syncWithDocker = syncDocker
            )
        )
    }

    suspend fun resetUserProgressOrDeleteAccount() = withContext(Dispatchers.IO) {
        dao.clearUserAttempts(activeUserId)
        dao.clearUserBookmarks(activeUserId)
        dao.clearUserTopicMastery(activeUserId)
        dao.clearUserReputationEvents(activeUserId)
        val current = dao.getUserProfile()
        if (current != null) {
            dao.insertUserProfile(
                current.copy(
                    globalTheta = 0.0,
                    reputation = 50,
                    uploaderBias = 0.0
                )
            )
        }
    }
}
