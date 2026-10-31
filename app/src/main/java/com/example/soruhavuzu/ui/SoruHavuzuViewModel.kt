package com.example.soruhavuzu.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.soruhavuzu.data.AttemptEntity
import com.example.soruhavuzu.data.AttemptEvaluationResult
import com.example.soruhavuzu.data.DockerClientFactory
import com.example.soruhavuzu.data.QuestionStatus
import com.example.soruhavuzu.data.QuestionWithDetails
import com.example.soruhavuzu.data.ReportEntity
import com.example.soruhavuzu.data.ReportReason
import com.example.soruhavuzu.data.ReputationEventEntity
import com.example.soruhavuzu.data.SessionType
import com.example.soruhavuzu.data.SoruHavuzuRepository
import com.example.soruhavuzu.data.TopicEntity
import com.example.soruhavuzu.data.TopicMasteryEntity
import com.example.soruhavuzu.data.UserProfileEntity
import com.example.soruhavuzu.data.UserRole
import com.example.soruhavuzu.domain.CalibrationEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.abs

enum class MainTab {
    HOME_POOL,
    UPLOAD,
    SAVED_REVIEW,
    MODERATION,
    PROFILE
}

data class ActiveSessionState(
    val sessionId: String,
    val sessionType: SessionType,
    val filterMinBucket: Int,
    val filterMaxBucket: Int,
    val filterTopicId: String?,
    val currentQuestion: QuestionWithDetails?,
    val selectedOptionIndex: Int?,
    val evaluationResult: AttemptEvaluationResult?,
    val questionShownAtMs: Long,
    val solvedInSession: Int,
    val correctInSession: Int,
    val consecutiveCorrects: Int,
    val consecutiveWrongs: Int,
    val sessionStartTheta: Double,
    val seenQuestionIdsInSession: Set<String>,
    val emptyPoolSuggestionRange: Pair<Int, Int>?,
    val isSessionFinished: Boolean
)

data class SoruHavuzuUiState(
    val currentTab: MainTab = MainTab.HOME_POOL,
    val userProfile: UserProfileEntity? = null,
    val topics: List<TopicEntity> = emptyList(),
    val allQuestions: List<QuestionWithDetails> = emptyList(),
    val attempts: List<AttemptEntity> = emptyList(),
    val topicMasteries: List<TopicMasteryEntity> = emptyList(),
    val reputationEvents: List<ReputationEventEntity> = emptyList(),
    val reports: List<ReportEntity> = emptyList(),
    val selectedMinBucket: Int = 7,
    val selectedMaxBucket: Int = 7,
    val selectedTopicId: String? = null,
    val includeSolvedInPractice: Boolean = false,
    val activeSession: ActiveSessionState? = null,
    val statusBannerMessage: String? = null,
    val dockerConnectionStatus: String? = null,
    val exportedJsonData: String? = null
) {
    val activeQuestions: List<QuestionWithDetails>
        get() = allQuestions.filter { it.question.status == QuestionStatus.ACTIVE.name }

    val bucketCounts: Map<Int, Int>
        get() {
            val filteredByTopic = activeQuestions.filter {
                selectedTopicId == null || it.topic.id == selectedTopicId
            }
            return (1..10).associateWith { b ->
                filteredByTopic.count { it.question.difficultyBucket == b }
            }
        }

    val matchingPracticeQuestionsCount: Int
        get() {
            val uid = userProfile?.id ?: "active_user"
            return activeQuestions.count { q ->
                val inBucket = q.question.difficultyBucket in selectedMinBucket..selectedMaxBucket
                val inTopic = selectedTopicId == null || q.topic.id == selectedTopicId
                val notOwnUpload = q.question.uploaderId != uid
                val solvedFilter = includeSolvedInPractice || !q.hasUserSolvedBefore
                inBucket && inTopic && notOwnUpload && solvedFilter
            }
        }

    val moderationQueueQuestions: List<QuestionWithDetails>
        get() = allQuestions.filter {
            it.question.status == QuestionStatus.IN_REVIEW.name ||
                it.question.status == QuestionStatus.FLAGGED.name ||
                it.question.keySuspect
        }

    val myUploadedQuestions: List<QuestionWithDetails>
        get() {
            val uid = userProfile?.id ?: "active_user"
            return allQuestions.filter { it.question.uploaderId == uid }
        }

    val wrongNoteQuestions: List<QuestionWithDetails>
        get() = allQuestions.filter { it.isWrongNote && it.question.status == QuestionStatus.ACTIVE.name }

    val favoriteQuestions: List<QuestionWithDetails>
        get() = allQuestions.filter { it.isFavorite && it.question.status == QuestionStatus.ACTIVE.name }
}

private data class LocalControlState(
    val currentTab: MainTab = MainTab.HOME_POOL,
    val selectedMinBucket: Int = 7,
    val selectedMaxBucket: Int = 7,
    val selectedTopicId: String? = null,
    val includeSolvedInPractice: Boolean = false,
    val activeSession: ActiveSessionState? = null,
    val statusBannerMessage: String? = null,
    val dockerConnectionStatus: String? = null,
    val exportedJsonData: String? = null
)

class SoruHavuzuViewModel(
    private val repository: SoruHavuzuRepository
) : ViewModel() {

    private val controlState = MutableStateFlow(LocalControlState())

    init {
        viewModelScope.launch {
            repository.ensureSeedDataInitialized()
        }
    }

    private val dataBundleFlow = combine(
        repository.userProfileFlow,
        repository.topicsFlow,
        repository.questionsWithDetailsFlow,
        repository.userAttemptsFlow,
        repository.topicMasteryFlow
    ) { profile, topics, questions, attempts, masteries ->
        DataBundle(profile, topics, questions, attempts, masteries)
    }

    val uiState: StateFlow<SoruHavuzuUiState> = combine(
        dataBundleFlow,
        repository.reputationEventsFlow,
        repository.reportsFlow,
        controlState
    ) { bundle, repEvents, reports, ctrl ->
        // Refresh currentQuestion inside activeSession if needed
        val refreshedSession = ctrl.activeSession?.let { sess ->
            val updatedCurr = sess.currentQuestion?.let { curr ->
                bundle.questions.find { it.question.id == curr.question.id } ?: curr
            }
            sess.copy(currentQuestion = updatedCurr)
        }

        SoruHavuzuUiState(
            currentTab = ctrl.currentTab,
            userProfile = bundle.profile,
            topics = bundle.topics,
            allQuestions = bundle.questions,
            attempts = bundle.attempts,
            topicMasteries = bundle.masteries,
            reputationEvents = repEvents,
            reports = reports,
            selectedMinBucket = ctrl.selectedMinBucket,
            selectedMaxBucket = ctrl.selectedMaxBucket,
            selectedTopicId = ctrl.selectedTopicId,
            includeSolvedInPractice = ctrl.includeSolvedInPractice,
            activeSession = refreshedSession,
            statusBannerMessage = ctrl.statusBannerMessage,
            dockerConnectionStatus = ctrl.dockerConnectionStatus,
            exportedJsonData = ctrl.exportedJsonData
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SoruHavuzuUiState()
    )

    private data class DataBundle(
        val profile: UserProfileEntity?,
        val topics: List<TopicEntity>,
        val questions: List<QuestionWithDetails>,
        val attempts: List<AttemptEntity>,
        val masteries: List<TopicMasteryEntity>
    )

    fun selectTab(tab: MainTab) {
        controlState.update { it.copy(currentTab = tab, statusBannerMessage = null) }
    }

    fun selectSingleDifficultyBucket(bucket: Int) {
        val b = bucket.coerceIn(1, 10)
        controlState.update { it.copy(selectedMinBucket = b, selectedMaxBucket = b) }
    }

    fun selectDifficultyRange(minBucket: Int, maxBucket: Int) {
        val low = minOf(minBucket, maxBucket).coerceIn(1, 10)
        val high = maxOf(minBucket, maxBucket).coerceIn(1, 10)
        controlState.update { it.copy(selectedMinBucket = low, selectedMaxBucket = high) }
    }

    fun selectTopic(topicId: String?) {
        controlState.update { it.copy(selectedTopicId = topicId) }
    }

    fun toggleIncludeSolved(include: Boolean) {
        controlState.update { it.copy(includeSolvedInPractice = include) }
    }

    fun dismissBanner() {
        controlState.update { it.copy(statusBannerMessage = null) }
    }

    /**
     * Zorluk seçerek (PRACTICE), Beni Zorla (CHALLENGE) veya Yanlışlarım/Favoriler (REVIEW)
     * oturumunu başlatır.
     */
    fun startSession(
        sessionType: SessionType,
        minBucketOverride: Int? = null,
        maxBucketOverride: Int? = null,
        topicIdOverride: String? = uiState.value.selectedTopicId
    ) {
        val state = uiState.value
        val minB = minBucketOverride ?: state.selectedMinBucket
        val maxB = maxBucketOverride ?: state.selectedMaxBucket
        val userTheta = state.userProfile?.globalTheta ?: 0.0
        val sessionId = "sess_${UUID.randomUUID().toString().take(8)}"

        val pickResult = pickNextQuestionForSession(
            allQuestions = state.activeQuestions,
            userId = state.userProfile?.id ?: "active_user",
            sessionType = sessionType,
            minBucket = minB,
            maxBucket = maxB,
            topicId = topicIdOverride,
            includeSolved = state.includeSolvedInPractice,
            seenIds = emptySet(),
            userTheta = userTheta,
            consecutiveCorrects = 0,
            consecutiveWrongs = 0
        )

        controlState.update {
            it.copy(
                activeSession = ActiveSessionState(
                    sessionId = sessionId,
                    sessionType = sessionType,
                    filterMinBucket = minB,
                    filterMaxBucket = maxB,
                    filterTopicId = topicIdOverride,
                    currentQuestion = pickResult.question,
                    selectedOptionIndex = null,
                    evaluationResult = null,
                    questionShownAtMs = System.currentTimeMillis(),
                    solvedInSession = 0,
                    correctInSession = 0,
                    consecutiveCorrects = 0,
                    consecutiveWrongs = 0,
                    sessionStartTheta = userTheta,
                    seenQuestionIdsInSession = pickResult.question?.let { q -> setOf(q.question.id) } ?: emptySet(),
                    emptyPoolSuggestionRange = pickResult.neighborSuggestion,
                    isSessionFinished = false
                )
            )
        }
    }

    fun expandSessionToSuggestedRange(minBucket: Int, maxBucket: Int) {
        val currentSession = controlState.value.activeSession ?: return
        val state = uiState.value
        val pickResult = pickNextQuestionForSession(
            allQuestions = state.activeQuestions,
            userId = state.userProfile?.id ?: "active_user",
            sessionType = currentSession.sessionType,
            minBucket = minBucket,
            maxBucket = maxBucket,
            topicId = currentSession.filterTopicId,
            includeSolved = true,
            seenIds = currentSession.seenQuestionIdsInSession,
            userTheta = state.userProfile?.globalTheta ?: 0.0,
            consecutiveCorrects = currentSession.consecutiveCorrects,
            consecutiveWrongs = currentSession.consecutiveWrongs
        )

        controlState.update {
            it.copy(
                selectedMinBucket = minBucket,
                selectedMaxBucket = maxBucket,
                activeSession = currentSession.copy(
                    filterMinBucket = minBucket,
                    filterMaxBucket = maxBucket,
                    currentQuestion = pickResult.question,
                    selectedOptionIndex = null,
                    evaluationResult = null,
                    questionShownAtMs = System.currentTimeMillis(),
                    seenQuestionIdsInSession = pickResult.question?.let { q ->
                        currentSession.seenQuestionIdsInSession + q.question.id
                    } ?: currentSession.seenQuestionIdsInSession,
                    emptyPoolSuggestionRange = pickResult.neighborSuggestion
                )
            )
        }
    }

    fun selectOptionInSession(optionIndex: Int) {
        val sess = controlState.value.activeSession ?: return
        if (sess.evaluationResult != null) return // Zaten cevaplandı
        controlState.update {
            it.copy(activeSession = sess.copy(selectedOptionIndex = optionIndex))
        }
    }

    fun submitCurrentAnswer() {
        val sess = controlState.value.activeSession ?: return
        val question = sess.currentQuestion ?: return
        val selectedIdx = sess.selectedOptionIndex ?: return
        if (sess.evaluationResult != null) return

        val elapsedMs = (System.currentTimeMillis() - sess.questionShownAtMs).coerceAtLeast(500L)

        viewModelScope.launch {
            val eval = repository.submitAttempt(
                questionWithDetails = question,
                selectedOptionIndex = selectedIdx,
                sessionId = sess.sessionId,
                sessionType = sess.sessionType,
                responseTimeMs = elapsedMs,
                consecutiveCorrects = sess.consecutiveCorrects,
                consecutiveWrongs = sess.consecutiveWrongs
            )

            controlState.update { ctrl ->
                val active = ctrl.activeSession ?: return@update ctrl
                ctrl.copy(
                    activeSession = active.copy(
                        evaluationResult = eval,
                        solvedInSession = active.solvedInSession + 1,
                        correctInSession = active.correctInSession + (if (eval.isCorrect) 1 else 0),
                        consecutiveCorrects = if (eval.isCorrect) active.consecutiveCorrects + 1 else 0,
                        consecutiveWrongs = if (!eval.isCorrect) active.consecutiveWrongs + 1 else 0
                    )
                )
            }
        }
    }

    fun proceedToNextQuestion() {
        val sess = controlState.value.activeSession ?: return
        val state = uiState.value
        val userTheta = state.userProfile?.globalTheta ?: 0.0

        val pickResult = pickNextQuestionForSession(
            allQuestions = state.activeQuestions,
            userId = state.userProfile?.id ?: "active_user",
            sessionType = sess.sessionType,
            minBucket = sess.filterMinBucket,
            maxBucket = sess.filterMaxBucket,
            topicId = sess.filterTopicId,
            includeSolved = state.includeSolvedInPractice,
            seenIds = sess.seenQuestionIdsInSession,
            userTheta = userTheta,
            consecutiveCorrects = sess.consecutiveCorrects,
            consecutiveWrongs = sess.consecutiveWrongs
        )

        if (pickResult.question == null && pickResult.neighborSuggestion == null) {
            controlState.update {
                it.copy(activeSession = sess.copy(isSessionFinished = true, currentQuestion = null))
            }
        } else {
            controlState.update {
                it.copy(
                    activeSession = sess.copy(
                        currentQuestion = pickResult.question,
                        selectedOptionIndex = null,
                        evaluationResult = null,
                        questionShownAtMs = System.currentTimeMillis(),
                        seenQuestionIdsInSession = pickResult.question?.let { q ->
                            sess.seenQuestionIdsInSession + q.question.id
                        } ?: sess.seenQuestionIdsInSession,
                        emptyPoolSuggestionRange = pickResult.neighborSuggestion
                    )
                )
            }
        }
    }

    fun finishSessionSummary() {
        controlState.update {
            val sess = it.activeSession ?: return@update it
            it.copy(activeSession = sess.copy(isSessionFinished = true))
        }
    }

    fun closeSession() {
        controlState.update { it.copy(activeSession = null) }
    }

    private data class QuestionPickResult(
        val question: QuestionWithDetails?,
        val neighborSuggestion: Pair<Int, Int>?
    )

    private fun pickNextQuestionForSession(
        allQuestions: List<QuestionWithDetails>,
        userId: String,
        sessionType: SessionType,
        minBucket: Int,
        maxBucket: Int,
        topicId: String?,
        includeSolved: Boolean,
        seenIds: Set<String>,
        userTheta: Double,
        consecutiveCorrects: Int,
        consecutiveWrongs: Int
    ): QuestionPickResult {
        // Kullanıcı kendi yüklediği soruyu çözme havuzunda görmez (proje.md §7.1)
        val basePool = allQuestions.filter { q ->
            q.question.uploaderId != userId && q.question.id !in seenIds
        }

        return when (sessionType) {
            SessionType.REVIEW, SessionType.OFFLINE_REVIEW -> {
                val reviewCandidates = basePool.filter { it.isWrongNote || it.isFavorite }
                QuestionPickResult(reviewCandidates.firstOrNull(), null)
            }

            SessionType.CHALLENGE -> {
                val topicFiltered = basePool.filter { topicId == null || it.topic.id == topicId }
                val unsolved = topicFiltered.filter { !it.hasUserSolvedBefore }.ifEmpty { topicFiltered }
                val targetProb = CalibrationEngine.computeChallengeTargetProbability(
                    consecutiveCorrects = consecutiveCorrects,
                    consecutiveWrongs = consecutiveWrongs
                )
                val targetLogit = CalibrationEngine.targetLogitForProbability(userTheta, targetProb)
                val bestMatch = unsolved.minByOrNull { abs(it.question.difficultyLogit - targetLogit) }
                QuestionPickResult(bestMatch, null)
            }

            SessionType.PRACTICE -> {
                val topicFiltered = basePool.filter { topicId == null || it.topic.id == topicId }
                val exactBucketMatches = topicFiltered.filter { q ->
                    q.question.difficultyBucket in minBucket..maxBucket &&
                        (includeSolved || !q.hasUserSolvedBefore)
                }
                if (exactBucketMatches.isNotEmpty()) {
                    return QuestionPickResult(exactBucketMatches.first(), null)
                }

                // Seçilen kovada soru kalmadıysa komşu aralığı öner ("7'de soru kalmadı, 6-8 aralığından devam edelim mi?")
                val expandedMin = (minBucket - 1).coerceAtLeast(1)
                val expandedMax = (maxBucket + 1).coerceAtMost(10)
                val neighborMatches = topicFiltered.filter { q ->
                    q.question.difficultyBucket in expandedMin..expandedMax
                }
                if (neighborMatches.isNotEmpty() && (expandedMin != minBucket || expandedMax != maxBucket)) {
                    QuestionPickResult(null, expandedMin to expandedMax)
                } else if (topicFiltered.isNotEmpty()) {
                    QuestionPickResult(null, 1 to 10)
                } else {
                    QuestionPickResult(null, null)
                }
            }
        }
    }

    fun toggleFavorite(questionId: String) {
        viewModelScope.launch {
            repository.toggleFavorite(questionId)
        }
    }

    fun removeFromWrongNotes(questionId: String) {
        viewModelScope.launch {
            repository.removeFromWrongNotes(questionId)
        }
    }

    suspend fun processPickedImage(uri: Uri): Pair<String, String>? {
        return repository.sanitizeAndStoreQuestionImage(uri)
    }

    fun submitNewQuestion(
        topicId: String,
        bodyText: String,
        imageUri: String?,
        imagePhash: String?,
        options: List<String>,
        correctOptionIndex: Int,
        uploaderRating: Int,
        solutionText: String,
        originalityConfirmed: Boolean,
        onSuccessResetForm: () -> Unit
    ) {
        viewModelScope.launch {
            when (val res = repository.uploadQuestion(
                topicId = topicId,
                bodyText = bodyText,
                imageUri = imageUri,
                imagePhash = imagePhash,
                options = options,
                correctOptionIndex = correctOptionIndex,
                uploaderRating = uploaderRating,
                solutionText = solutionText,
                originalityConfirmed = originalityConfirmed
            )) {
                is SoruHavuzuRepository.UploadOutcome.Success -> {
                    onSuccessResetForm()
                    val msg = if (res.publishedDirectly) {
                        "Sorunuz (#${res.question.id}) başlangıç zorluğu ${res.question.uploaderRating}/10 ile doğrudan YAYINA alındı! (+5 İtibar)"
                    } else {
                        "Sorunuz (#${res.question.id}) otomatik kontrollerden geçti ve İNCELEME KUYRUĞUNA eklendi. (İtibar ≥ 50 olduğunda doğrudan yayınlanır)"
                    }
                    controlState.update { it.copy(statusBannerMessage = msg) }
                }

                is SoruHavuzuRepository.UploadOutcome.Error -> {
                    controlState.update { it.copy(statusBannerMessage = res.message) }
                }
            }
        }
    }

    fun reportQuestion(questionId: String, reason: ReportReason, note: String) {
        viewModelScope.launch {
            val flagged = repository.reportQuestion(questionId, reason, note)
            val msg = if (flagged) {
                "Şikayetiniz kaydedildi. Eşik aşıldığı / öncelikli olduğu için soru FLAGGED durumuna alınıp havuzdan çekildi."
            } else {
                "Şikayetiniz kaydedildi ve moderasyon incelemesine iletildi."
            }
            controlState.update { it.copy(statusBannerMessage = msg) }
        }
    }

    fun approveQuestionInModeration(questionId: String) {
        viewModelScope.launch {
            repository.approveQuestionInQueue(questionId)
            controlState.update {
                it.copy(statusBannerMessage = "Soru (#$questionId) onaylandı ve aktif soru havuzuna eklendi.")
            }
        }
    }

    fun removeQuestionInModeration(questionId: String, reasonNote: String) {
        viewModelScope.launch {
            repository.removeQuestionFromPool(questionId, reasonNote)
            controlState.update {
                it.copy(statusBannerMessage = "Soru (#$questionId) havuzdan kaldırıldı (REMOVED).")
            }
        }
    }

    fun createNewQuestionVersion(
        questionId: String,
        newBodyText: String,
        newOptions: List<String>,
        newCorrectIndex: Int,
        newSolutionText: String,
        editorNote: String
    ) {
        viewModelScope.launch {
            repository.createNewVersionAndApprove(
                questionId = questionId,
                newBodyText = newBodyText,
                newOptions = newOptions,
                newCorrectIndex = newCorrectIndex,
                newSolutionText = newSolutionText,
                editorNote = editorNote
            )
            controlState.update {
                it.copy(statusBannerMessage = "Yeni versiyon oluşturuldu, cevap anahtarı güncellendi ve soru YAYINA alındı! (+10 İtibar)")
            }
        }
    }

    fun completeOnboarding(
        nickname: String,
        email: String,
        authProvider: String,
        targetExam: String,
        birthYear: Int,
        initialSelfLevel: Int
    ) {
        viewModelScope.launch {
            repository.completeOnboarding(
                nickname = nickname,
                email = email,
                authProvider = authProvider,
                targetExam = targetExam,
                birthYear = birthYear,
                initialSelfLevel = initialSelfLevel
            )
            controlState.update {
                it.copy(
                    selectedMinBucket = initialSelfLevel.coerceIn(1, 10),
                    selectedMaxBucket = initialSelfLevel.coerceIn(1, 10),
                    statusBannerMessage = "Hoş geldin $nickname! Hedef sınavın ($targetExam) ve başlangıç seviyen ($initialSelfLevel/10) kaydedildi."
                )
            }
        }
    }

    fun updateUserSettings(
        nickname: String,
        targetExam: String,
        birthYear: Int,
        role: UserRole,
        reputation: Int,
        dockerUrl: String,
        syncDocker: Boolean
    ) {
        viewModelScope.launch {
            repository.updateUserProfileSettings(
                nickname = nickname,
                targetExam = targetExam,
                birthYear = birthYear,
                role = role,
                reputation = reputation,
                dockerUrl = dockerUrl,
                syncDocker = syncDocker
            )
            controlState.update {
                it.copy(statusBannerMessage = "Profil, yetki seviyesi ve tek servis (Docker) ayarları güncellendi.")
            }
        }
    }

    fun testDockerConnection(url: String) {
        viewModelScope.launch {
            controlState.update { it.copy(dockerConnectionStatus = "Bağlantı test ediliyor ($url)...") }
            val result = runCatching {
                DockerClientFactory.create(url).checkHealth()
            }
            result.fold(
                onSuccess = { health ->
                    controlState.update {
                        it.copy(dockerConnectionStatus = "Bağlantı Başarılı: ${health.service} (${health.activeQuestions} aktif soru)")
                    }
                },
                onFailure = {
                    controlState.update {
                        it.copy(
                            dockerConnectionStatus = "Yerel Çevrimdışı Mod Aktif: Docker sunucusuna ($url) şu an erişilemedi, tüm işlemler cihaz içi Room veritabanında kayıpsız çalışıyor."
                        )
                    }
                }
            )
        }
    }

    fun exportUserDataJson() {
        val state = uiState.value
        val profile = state.userProfile ?: return
        val json = """
{
  "exportDate": "2026-10-31",
  "kvkkCompliance": "SoruHavuzu Kişisel Veri Dışa Aktarımı",
  "user": {
    "nickname": "${profile.nickname}",
    "targetExam": "${profile.targetExam}",
    "birthYear": ${profile.birthYear},
    "under18Protected": ${profile.isUnder18},
    "role": "${profile.role}",
    "reputation": ${profile.reputation},
    "globalTheta": ${"%.3f".format(profile.globalTheta)},
    "uploaderBias": ${"%.3f".format(profile.uploaderBias)}
  },
  "stats": {
    "totalAttempts": ${state.attempts.size},
    "uploadedQuestions": ${state.myUploadedQuestions.size},
    "savedFavorites": ${state.favoriteQuestions.size},
    "wrongNotesCount": ${state.wrongNoteQuestions.size}
  }
}
        """.trimIndent()
        controlState.update { it.copy(exportedJsonData = json) }
    }

    fun dismissExportedJsonDialog() {
        controlState.update { it.copy(exportedJsonData = null) }
    }

    fun resetAccountData() {
        viewModelScope.launch {
            repository.resetUserProgressOrDeleteAccount()
            controlState.update {
                it.copy(statusBannerMessage = "KVKK kapsamında çözüm geçmişiniz ve kişisel ilerleme verileriniz sıfırlandı.")
            }
        }
    }

    class Factory(private val repository: SoruHavuzuRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SoruHavuzuViewModel(repository) as T
        }
    }
}
