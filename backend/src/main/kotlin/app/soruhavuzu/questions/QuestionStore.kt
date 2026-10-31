package app.soruhavuzu.questions

import app.soruhavuzu.ForbiddenException
import app.soruhavuzu.NotFoundException
import app.soruhavuzu.domain.CalibrationEngine
import app.soruhavuzu.users.Permissions
import app.soruhavuzu.validate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.postgresql.util.PSQLException
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.util.UUID
import javax.sql.DataSource

enum class SessionType { PRACTICE, CHALLENGE, REVIEW }

data class QuestionFilter(
    val examId: String? = null,
    val subjectId: String? = null,
    val topicId: String? = null
)

/** Çözülmek üzere gösterilen soru. Doğru cevap ve çözüm bilerek yok. */
data class QuestionForSolving(
    val id: UUID,
    val examId: String,
    val subjectId: String,
    val subjectName: String,
    val topicId: String,
    val topicName: String,
    val bodyText: String,
    val imagePath: String?,
    val options: List<String>,
    val difficultyBucket: Int,
    val firstAttemptCount: Int,
    val alreadySolved: Boolean
)

data class PickResult(
    val question: QuestionForSolving?,
    /** Seçilen aralıkta soru kalmadıysa önerilen yeni aralık. */
    val suggestedRange: IntRange?
)

data class AttemptResult(
    val isCorrect: Boolean,
    val correctOptionIndex: Int,
    val solutionText: String,
    val wasFirstAttempt: Boolean,
    val expectedProbability: Double,
    val oldBucket: Int,
    val newBucket: Int,
    val oldTheta: Double,
    val newTheta: Double
)

data class CatalogTopic(val id: String, val name: String, val activeQuestionCount: Int)
data class CatalogSubject(val id: String, val name: String, val topics: List<CatalogTopic>)
data class CatalogExam(val id: String, val code: String, val title: String, val subjects: List<CatalogSubject>)

interface QuestionStore {
    suspend fun catalog(): List<CatalogExam>
    suspend fun findForSolving(questionId: UUID, userId: UUID): QuestionForSolving?
    suspend fun pickPractice(userId: UUID, filter: QuestionFilter, buckets: IntRange, includeSolved: Boolean): PickResult
    suspend fun pickChallenge(userId: UUID, filter: QuestionFilter): QuestionForSolving?
    suspend fun submitAttempt(
        userId: UUID,
        questionId: UUID,
        selectedOptionIndex: Int,
        responseTimeMs: Long,
        sessionType: SessionType
    ): AttemptResult

    suspend fun topicExists(topicId: String): Boolean
    suspend fun createQuestion(question: NewQuestion, maxUploadsPerHour: Int): CreateQuestionResult
    suspend fun questionsUploadedBy(userId: UUID): List<UploadedQuestion>
}

data class NewQuestion(
    val uploaderId: UUID,
    val topicId: String,
    val bodyText: String,
    val options: List<String>,
    val correctOptionIndex: Int,
    val uploaderRating: Int,
    val solutionText: String,
    val contentHash: String,
    val imagePath: String?,
    val imagePhash: String?
)

sealed interface CreateQuestionResult {
    data class Created(val id: UUID, val status: String, val difficultyBucket: Int) : CreateQuestionResult
    data class Duplicate(val existingId: UUID) : CreateQuestionResult
    data object RateLimited : CreateQuestionResult
}

data class UploadedQuestion(
    val id: UUID,
    val topicName: String,
    val bodyPreview: String,
    val status: String,
    val uploaderRating: Int,
    val difficultyBucket: Int,
    val firstAttemptCount: Int,
    val correctFirstCount: Int,
    val keySuspect: Boolean,
    val createdAt: java.time.Instant
)

class PostgresQuestionStore(private val dataSource: DataSource) : QuestionStore {

    override suspend fun catalog(): List<CatalogExam> = withContext(Dispatchers.IO) {
        data class Row(
            val examId: String, val examCode: String, val examTitle: String,
            val subjectId: String, val subjectName: String,
            val topicId: String, val topicName: String, val count: Int
        )
        val rows = dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                SELECT e.id AS exam_id, e.code, e.title, s.id AS subject_id, s.name AS subject_name,
                       t.id AS topic_id, t.name AS topic_name,
                       (SELECT count(*) FROM questions q WHERE q.topic_id = t.id AND q.status = 'ACTIVE') AS cnt
                FROM exams e
                JOIN subjects s ON s.exam_id = e.id
                JOIN topics t ON t.subject_id = s.id
                ORDER BY e.order_index, s.order_index, t.order_index
                """.trimIndent()
            ).use { st ->
                st.executeQuery().use { rs ->
                    generateSequence { if (rs.next()) rs else null }.map {
                        Row(
                            it.getString("exam_id"), it.getString("code"), it.getString("title"),
                            it.getString("subject_id"), it.getString("subject_name"),
                            it.getString("topic_id"), it.getString("topic_name"), it.getInt("cnt")
                        )
                    }.toList()
                }
            }
        }
        rows.groupBy { it.examId }.values.map { examRows ->
            val e = examRows.first()
            CatalogExam(e.examId, e.examCode, e.examTitle, examRows.groupBy { it.subjectId }.values.map { subjectRows ->
                val s = subjectRows.first()
                CatalogSubject(s.subjectId, s.subjectName, subjectRows.map { CatalogTopic(it.topicId, it.topicName, it.count) })
            })
        }
    }

    override suspend fun findForSolving(questionId: UUID, userId: UUID): QuestionForSolving? = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("$SOLVING_SELECT WHERE q.id = ? AND q.status = 'ACTIVE'").use { st ->
                st.setObject(1, userId)
                st.setObject(2, questionId)
                st.executeQuery().use { rs -> if (rs.next()) rs.toQuestionForSolving() else null }
            }
        }
    }

    override suspend fun pickPractice(
        userId: UUID,
        filter: QuestionFilter,
        buckets: IntRange,
        includeSolved: Boolean
    ): PickResult = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            fun pick(range: IntRange): QuestionForSolving? {
                val where = PickConditions(userId, filter).apply {
                    add("q.difficulty_bucket BETWEEN ? AND ?", range.first, range.last)
                    if (!includeSolved) add(NOT_SOLVED, userId)
                }
                return conn.prepareStatement("$SOLVING_SELECT WHERE ${where.sql} ORDER BY random() LIMIT 1").use { st ->
                    st.setObject(1, userId)
                    where.bind(st, startIndex = 2)
                    st.executeQuery().use { rs -> if (rs.next()) rs.toQuestionForSolving() else null }
                }
            }

            pick(buckets)?.let { return@use PickResult(it, null) }

            // "4'te çözmediğin soru kalmadı, 3-5 aralığından devam edelim mi?"
            val widened = (buckets.first - 1).coerceAtLeast(1)..(buckets.last + 1).coerceAtMost(10)
            val suggestion = listOf(widened, 1..10)
                .distinct()
                .filter { it != buckets }
                .firstOrNull { pick(it) != null }
            PickResult(null, suggestion)
        }
    }

    override suspend fun pickChallenge(userId: UUID, filter: QuestionFilter): QuestionForSolving? = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            val theta = conn.prepareStatement("SELECT theta FROM users WHERE id = ?").use { st ->
                st.setObject(1, userId)
                st.executeQuery().use { rs -> if (rs.next()) rs.getDouble(1) else null }
            } ?: throw NotFoundException("Kullanıcı bulunamadı")

            val (corrects, wrongs) = recentStreak(conn, userId)
            val targetLogit = CalibrationEngine.targetLogitForProbability(
                theta, CalibrationEngine.challengeTargetProbability(corrects, wrongs)
            )

            // Hedefe en yakın 5 sorudan rastgele biri; çözülmemişler önce gelir
            val where = PickConditions(userId, filter)
            conn.prepareStatement(
                """
                SELECT * FROM (
                    $SOLVING_SELECT WHERE ${where.sql}
                    ORDER BY already_solved, abs(q.difficulty_logit - ?)
                    LIMIT 5
                ) best ORDER BY random() LIMIT 1
                """.trimIndent()
            ).use { st ->
                st.setObject(1, userId)
                val next = where.bind(st, startIndex = 2)
                st.setDouble(next, targetLogit)
                st.executeQuery().use { rs -> if (rs.next()) rs.toQuestionForSolving() else null }
            }
        }
    }

    override suspend fun submitAttempt(
        userId: UUID,
        questionId: UUID,
        selectedOptionIndex: Int,
        responseTimeMs: Long,
        sessionType: SessionType
    ): AttemptResult = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                val result = submitInTransaction(conn, userId, questionId, selectedOptionIndex, responseTimeMs, sessionType)
                conn.commit()
                result
            } catch (e: Throwable) {
                conn.rollback()
                throw e
            } finally {
                conn.autoCommit = true
            }
        }
    }

    override suspend fun topicExists(topicId: String): Boolean = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT 1 FROM topics WHERE id = ?").use { st ->
                st.setString(1, topicId)
                st.executeQuery().use { it.next() }
            }
        }
    }

    override suspend fun createQuestion(question: NewQuestion, maxUploadsPerHour: Int): CreateQuestionResult =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                conn.autoCommit = false
                try {
                    createInTransaction(conn, question, maxUploadsPerHour).also { result ->
                        if (result is CreateQuestionResult.Created) conn.commit() else conn.rollback()
                    }
                } catch (e: PSQLException) {
                    conn.rollback()
                    // Aynı içerik aynı anda başka biri tarafından yüklendiyse tekil indeks yakalar
                    if (e.sqlState == UNIQUE_VIOLATION && e.serverErrorMessage?.constraint == "questions_content_hash_uq") {
                        findActiveByHash(conn, question.contentHash)?.let { CreateQuestionResult.Duplicate(it) } ?: throw e
                    } else {
                        throw e
                    }
                } catch (e: Throwable) {
                    conn.rollback()
                    throw e
                } finally {
                    conn.autoCommit = true
                }
            }
        }

    private fun createInTransaction(conn: Connection, question: NewQuestion, maxUploadsPerHour: Int): CreateQuestionResult {
        // Yükleyeni kilitlemek saatlik sınırın eşzamanlı isteklerle aşılmasını engeller
        val uploader = conn.prepareStatement("SELECT role, reputation, uploader_bias FROM users WHERE id = ? FOR UPDATE").use { st ->
            st.setObject(1, question.uploaderId)
            st.executeQuery().use { rs ->
                if (!rs.next()) throw NotFoundException("Kullanıcı bulunamadı")
                Triple(rs.getString("role"), rs.getInt("reputation"), rs.getDouble("uploader_bias"))
            }
        }
        val (role, reputation, bias) = uploader

        val lastHour = conn.prepareStatement(
            "SELECT count(*) FROM questions WHERE uploader_id = ? AND created_at > now() - interval '1 hour'"
        ).use { st ->
            st.setObject(1, question.uploaderId)
            st.executeQuery().use { rs -> rs.next(); rs.getInt(1) }
        }
        if (lastHour >= maxUploadsPerHour) return CreateQuestionResult.RateLimited

        findActiveByHash(conn, question.contentHash)?.let { return CreateQuestionResult.Duplicate(it) }

        val status = if (Permissions.canPublishDirectly(role, reputation)) "ACTIVE" else "IN_REVIEW"
        val logit = CalibrationEngine.initialLogit(question.uploaderRating, bias)
        val bucket = CalibrationEngine.logitToBucket(logit)
        val questionId = UUID.randomUUID()
        val versionId = UUID.randomUUID()

        // current_version_id'nin yabancı anahtarı transaction sonunda kontrol edilir (DEFERRABLE)
        conn.prepareStatement(
            """
            INSERT INTO questions (id, uploader_id, topic_id, current_version_id, status, uploader_rating,
                                   difficulty_logit, difficulty_bucket, content_hash, image_phash)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { st ->
            st.setObject(1, questionId)
            st.setObject(2, question.uploaderId)
            st.setString(3, question.topicId)
            st.setObject(4, versionId)
            st.setString(5, status)
            st.setInt(6, question.uploaderRating)
            st.setDouble(7, logit)
            st.setInt(8, bucket)
            st.setString(9, question.contentHash)
            st.setString(10, question.imagePhash)
            st.executeUpdate()
        }
        conn.prepareStatement(
            """
            INSERT INTO question_versions (id, question_id, version_number, body_text, image_path, options,
                                           correct_option_index, option_selection_counts, solution_text, editor_note, created_by)
            VALUES (?, ?, 1, ?, ?, ?, ?, ?, ?, 'İlk yükleme', ?)
            """.trimIndent()
        ).use { st ->
            st.setObject(1, versionId)
            st.setObject(2, questionId)
            st.setString(3, question.bodyText)
            st.setString(4, question.imagePath)
            st.setArray(5, conn.createArrayOf("text", question.options.toTypedArray()))
            st.setInt(6, question.correctOptionIndex)
            st.setArray(7, conn.createArrayOf("integer", Array(question.options.size) { 0 }))
            st.setString(8, question.solutionText)
            st.setObject(9, question.uploaderId)
            st.executeUpdate()
        }

        if (status == "ACTIVE") {
            addReputation(conn, question.uploaderId, PUBLISH_REPUTATION, "Sorun doğrudan yayına alındı", questionId)
        }
        return CreateQuestionResult.Created(questionId, status, bucket)
    }

    override suspend fun questionsUploadedBy(userId: UUID): List<UploadedQuestion> = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                SELECT q.id, t.name AS topic_name, left(v.body_text, 120) AS preview, q.status, q.uploader_rating,
                       q.difficulty_bucket, q.first_attempt_count, q.correct_first_count, q.key_suspect, q.created_at
                FROM questions q
                JOIN question_versions v ON v.id = q.current_version_id
                JOIN topics t ON t.id = q.topic_id
                WHERE q.uploader_id = ?
                ORDER BY q.created_at DESC
                LIMIT 200
                """.trimIndent()
            ).use { st ->
                st.setObject(1, userId)
                st.executeQuery().use { rs ->
                    generateSequence { if (rs.next()) rs else null }.map {
                        UploadedQuestion(
                            id = it.getObject("id", UUID::class.java),
                            topicName = it.getString("topic_name"),
                            bodyPreview = it.getString("preview"),
                            status = it.getString("status"),
                            uploaderRating = it.getInt("uploader_rating"),
                            difficultyBucket = it.getInt("difficulty_bucket"),
                            firstAttemptCount = it.getInt("first_attempt_count"),
                            correctFirstCount = it.getInt("correct_first_count"),
                            keySuspect = it.getBoolean("key_suspect"),
                            createdAt = it.getTimestamp("created_at").toInstant()
                        )
                    }.toList()
                }
            }
        }
    }

    private fun findActiveByHash(conn: Connection, contentHash: String): UUID? =
        conn.prepareStatement("SELECT id FROM questions WHERE content_hash = ? AND status <> 'REMOVED'").use { st ->
            st.setString(1, contentHash)
            st.executeQuery().use { rs -> if (rs.next()) rs.getObject(1, UUID::class.java) else null }
        }

    private fun addReputation(conn: Connection, userId: UUID, delta: Int, reason: String, questionId: UUID?) {
        conn.prepareStatement("UPDATE users SET reputation = greatest(0, reputation + ?) WHERE id = ?").use { st ->
            st.setInt(1, delta)
            st.setObject(2, userId)
            st.executeUpdate()
        }
        conn.prepareStatement(
            "INSERT INTO reputation_events (user_id, delta, reason, related_question_id) VALUES (?, ?, ?, ?)"
        ).use { st ->
            st.setObject(1, userId)
            st.setInt(2, delta)
            st.setString(3, reason)
            st.setObject(4, questionId)
            st.executeUpdate()
        }
    }

    private fun submitInTransaction(
        conn: Connection,
        userId: UUID,
        questionId: UUID,
        selectedOptionIndex: Int,
        responseTimeMs: Long,
        sessionType: SessionType
    ): AttemptResult {
        // Soru satırını kilitlemek aynı soruya gelen eşzamanlı cevapları sıraya sokar:
        // ikisi birden "ilk deneme" sayılamaz, sayaçlar kaybolmaz.
        val q = conn.prepareStatement(
            """
            SELECT uploader_id, status, topic_id, uploader_rating, difficulty_logit, difficulty_bucket,
                   first_attempt_count, correct_first_count, current_version_id
            FROM questions WHERE id = ? FOR UPDATE
            """.trimIndent()
        ).use { st ->
            st.setObject(1, questionId)
            st.executeQuery().use { rs ->
                if (!rs.next() || rs.getString("status") != "ACTIVE") throw NotFoundException("Soru bulunamadı")
                LockedQuestion(
                    uploaderId = rs.getObject("uploader_id", UUID::class.java),
                    topicId = rs.getString("topic_id"),
                    uploaderRating = rs.getInt("uploader_rating"),
                    logit = rs.getDouble("difficulty_logit"),
                    bucket = rs.getInt("difficulty_bucket"),
                    firstAttemptCount = rs.getInt("first_attempt_count"),
                    correctFirstCount = rs.getInt("correct_first_count"),
                    versionId = rs.getObject("current_version_id", UUID::class.java)
                )
            }
        }
        if (q.uploaderId == userId) throw ForbiddenException("Kendi yüklediğin soruyu çözemezsin")

        val v = conn.prepareStatement(
            "SELECT options, correct_option_index, option_selection_counts, solution_text FROM question_versions WHERE id = ?"
        ).use { st ->
            st.setObject(1, q.versionId)
            st.executeQuery().use { rs ->
                rs.next()
                @Suppress("UNCHECKED_CAST")
                Version(
                    optionCount = (rs.getArray("options").array as Array<String>).size,
                    correctIndex = rs.getInt("correct_option_index"),
                    counts = (rs.getArray("option_selection_counts").array as Array<Int>).toList(),
                    solutionText = rs.getString("solution_text")
                )
            }
        }
        validate(selectedOptionIndex in 0 until v.optionCount) { "Geçersiz şık" }

        // Çözen ve yükleyen birlikte, hep aynı sırayla kilitlenir (kilitlenme/deadlock olmasın)
        val users = conn.prepareStatement(
            "SELECT id, theta, uploader_bias FROM users WHERE id IN (?, ?) ORDER BY id FOR UPDATE"
        ).use { st ->
            st.setObject(1, userId)
            st.setObject(2, q.uploaderId)
            st.executeQuery().use { rs ->
                generateSequence { if (rs.next()) rs else null }
                    .associate { it.getObject("id", UUID::class.java) to (it.getDouble("theta") to it.getDouble("uploader_bias")) }
            }
        }
        val solverTheta = users[userId]?.first ?: throw NotFoundException("Kullanıcı bulunamadı")

        val isFirst = conn.prepareStatement(
            "SELECT NOT EXISTS (SELECT 1 FROM attempts WHERE user_id = ? AND question_id = ? AND is_first)"
        ).use { st ->
            st.setObject(1, userId)
            st.setObject(2, questionId)
            st.executeQuery().use { rs -> rs.next(); rs.getBoolean(1) }
        }

        val isCorrect = selectedOptionIndex == v.correctIndex
        var newTheta = solverTheta
        var newLogit = q.logit
        var newBucket = q.bucket
        val expected = CalibrationEngine.expectedSuccessProbability(solverTheta, q.logit)

        if (isFirst) {
            val step = CalibrationEngine.evaluateFirstAttempt(
                CalibrationEngine.AttemptInput(solverTheta, q.logit, q.firstAttemptCount, q.correctFirstCount, isCorrect)
            )
            newTheta = step.newTheta
            newLogit = step.newQuestionLogit
            newBucket = CalibrationEngine.logitToBucket(newLogit)
            val newFirstCount = q.firstAttemptCount + 1

            val counts = v.counts.toMutableList().also { it[selectedOptionIndex] += 1 }
            val suspectReason = CalibrationEngine.detectKeySuspect(counts, v.correctIndex)

            conn.prepareStatement(
                """
                UPDATE questions SET
                    difficulty_logit = ?, difficulty_bucket = ?,
                    attempt_count = attempt_count + 1,
                    first_attempt_count = first_attempt_count + 1,
                    correct_first_count = correct_first_count + ?,
                    key_suspect = key_suspect OR ?,
                    key_suspect_reason = COALESCE(?, key_suspect_reason),
                    updated_at = now()
                WHERE id = ?
                """.trimIndent()
            ).use { st ->
                st.setDouble(1, newLogit)
                st.setInt(2, newBucket)
                st.setInt(3, if (isCorrect) 1 else 0)
                st.setBoolean(4, suspectReason != null)
                st.setString(5, suspectReason)
                st.setObject(6, questionId)
                st.executeUpdate()
            }
            conn.prepareStatement("UPDATE question_versions SET option_selection_counts = ? WHERE id = ?").use { st ->
                st.setArray(1, conn.createArrayOf("integer", counts.toTypedArray()))
                st.setObject(2, q.versionId)
                st.executeUpdate()
            }
            conn.prepareStatement("UPDATE users SET theta = ? WHERE id = ?").use { st ->
                st.setDouble(1, newTheta)
                st.setObject(2, userId)
                st.executeUpdate()
            }
            updateTopicMastery(conn, userId, q, solverTheta, isCorrect)

            if (CalibrationEngine.shouldUpdateUploaderBias(newFirstCount)) {
                val currentBias = users[q.uploaderId]?.second ?: 0.0
                conn.prepareStatement("UPDATE users SET uploader_bias = ? WHERE id = ?").use { st ->
                    st.setDouble(1, CalibrationEngine.updatedUploaderBias(currentBias, q.uploaderRating, newLogit))
                    st.setObject(2, q.uploaderId)
                    st.executeUpdate()
                }
            }
        } else {
            conn.prepareStatement("UPDATE questions SET attempt_count = attempt_count + 1 WHERE id = ?").use { st ->
                st.setObject(1, questionId)
                st.executeUpdate()
            }
        }

        if (!isCorrect) {
            // Yanlış yapılan soru "Yanlışlarım" listesine düşer
            conn.prepareStatement(
                """
                INSERT INTO bookmarks (user_id, question_id, is_wrong_note) VALUES (?, ?, true)
                ON CONFLICT (user_id, question_id) DO UPDATE SET is_wrong_note = true, updated_at = now()
                """.trimIndent()
            ).use { st ->
                st.setObject(1, userId)
                st.setObject(2, questionId)
                st.executeUpdate()
            }
        }

        conn.prepareStatement(
            """
            INSERT INTO attempts (user_id, question_id, version_id, session_type, selected_option_index, is_correct,
                                  is_first, response_time_ms, user_theta_before, user_theta_after,
                                  question_logit_before, question_logit_after)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { st ->
            st.setObject(1, userId)
            st.setObject(2, questionId)
            st.setObject(3, q.versionId)
            st.setString(4, sessionType.name)
            st.setInt(5, selectedOptionIndex)
            st.setBoolean(6, isCorrect)
            st.setBoolean(7, isFirst)
            st.setLong(8, responseTimeMs.coerceIn(0, MAX_RESPONSE_TIME_MS))
            st.setDouble(9, solverTheta)
            st.setDouble(10, newTheta)
            st.setDouble(11, q.logit)
            st.setDouble(12, newLogit)
            st.executeUpdate()
        }

        return AttemptResult(
            isCorrect = isCorrect,
            correctOptionIndex = v.correctIndex,
            solutionText = v.solutionText,
            wasFirstAttempt = isFirst,
            expectedProbability = expected,
            oldBucket = q.bucket,
            newBucket = newBucket,
            oldTheta = solverTheta,
            newTheta = newTheta
        )
    }

    private fun updateTopicMastery(conn: Connection, userId: UUID, q: LockedQuestion, globalTheta: Double, isCorrect: Boolean) {
        val topicTheta = conn.prepareStatement(
            "SELECT theta FROM topic_mastery WHERE user_id = ? AND topic_id = ?"
        ).use { st ->
            st.setObject(1, userId)
            st.setString(2, q.topicId)
            st.executeQuery().use { rs -> if (rs.next()) rs.getDouble(1) else globalTheta }
        }
        val newTopicTheta = CalibrationEngine.evaluateFirstAttempt(
            CalibrationEngine.AttemptInput(topicTheta, q.logit, q.firstAttemptCount, q.correctFirstCount, isCorrect)
        ).newTheta

        conn.prepareStatement(
            """
            INSERT INTO topic_mastery (user_id, topic_id, theta, solved_count, correct_count) VALUES (?, ?, ?, 1, ?)
            ON CONFLICT (user_id, topic_id) DO UPDATE SET
                theta = EXCLUDED.theta,
                solved_count = topic_mastery.solved_count + 1,
                correct_count = topic_mastery.correct_count + EXCLUDED.correct_count
            """.trimIndent()
        ).use { st ->
            st.setObject(1, userId)
            st.setString(2, q.topicId)
            st.setDouble(3, newTopicTheta)
            st.setInt(4, if (isCorrect) 1 else 0)
            st.executeUpdate()
        }
    }

    /** Son ilk denemelerdeki üst üste doğru / yanlış serisi ("Beni Zorla" hedefini ayarlar). */
    private fun recentStreak(conn: Connection, userId: UUID): Pair<Int, Int> {
        val recent = conn.prepareStatement(
            "SELECT is_correct FROM attempts WHERE user_id = ? AND is_first ORDER BY created_at DESC, id DESC LIMIT 3"
        ).use { st ->
            st.setObject(1, userId)
            st.executeQuery().use { rs -> generateSequence { if (rs.next()) rs.getBoolean(1) else null }.toList() }
        }
        val corrects = recent.takeWhile { it }.size
        val wrongs = recent.takeWhile { !it }.size
        return corrects to wrongs
    }

    private fun ResultSet.toQuestionForSolving(): QuestionForSolving {
        @Suppress("UNCHECKED_CAST")
        val options = (getArray("options").array as Array<String>).toList()
        return QuestionForSolving(
            id = getObject("id", UUID::class.java),
            examId = getString("exam_id"),
            subjectId = getString("subject_id"),
            subjectName = getString("subject_name"),
            topicId = getString("topic_id"),
            topicName = getString("topic_name"),
            bodyText = getString("body_text"),
            imagePath = getString("image_path"),
            options = options,
            difficultyBucket = getInt("difficulty_bucket"),
            firstAttemptCount = getInt("first_attempt_count"),
            alreadySolved = getBoolean("already_solved")
        )
    }

    /** Soru seçme sorgularının ortak WHERE koşulları; sırayla parametre bağlamayı kolaylaştırır. */
    private class PickConditions(userId: UUID, filter: QuestionFilter) {
        private val clauses = mutableListOf<String>()
        private val params = mutableListOf<Any>()

        init {
            add("q.status = 'ACTIVE'")
            add("q.uploader_id <> ?", userId)
            filter.examId?.let { add("s.exam_id = ?", it) }
            filter.subjectId?.let { add("t.subject_id = ?", it) }
            filter.topicId?.let { add("q.topic_id = ?", it) }
        }

        fun add(clause: String, vararg values: Any) {
            clauses += clause
            params.addAll(values)
        }

        val sql: String get() = clauses.joinToString(" AND ")

        /** Parametreleri bağlar ve sıradaki boş indeksi döner. */
        fun bind(st: PreparedStatement, startIndex: Int): Int {
            params.forEachIndexed { i, value -> st.setObject(startIndex + i, value) }
            return startIndex + params.size
        }
    }

    private data class LockedQuestion(
        val uploaderId: UUID,
        val topicId: String,
        val uploaderRating: Int,
        val logit: Double,
        val bucket: Int,
        val firstAttemptCount: Int,
        val correctFirstCount: Int,
        val versionId: UUID
    )

    private data class Version(val optionCount: Int, val correctIndex: Int, val counts: List<Int>, val solutionText: String)

    private companion object {
        const val MAX_RESPONSE_TIME_MS = 3_600_000L
        const val UNIQUE_VIOLATION = "23505"
        const val PUBLISH_REPUTATION = 5
        const val NOT_SOLVED = "NOT EXISTS (SELECT 1 FROM attempts a WHERE a.user_id = ? AND a.question_id = q.id)"

        /** İlk parametre: already_solved hesabı için kullanıcı id'si. */
        val SOLVING_SELECT = """
            SELECT q.id, s.exam_id, t.subject_id, s.name AS subject_name, q.topic_id, t.name AS topic_name,
                   v.body_text, v.image_path, v.options, q.difficulty_bucket, q.difficulty_logit, q.first_attempt_count,
                   EXISTS (SELECT 1 FROM attempts a WHERE a.user_id = ? AND a.question_id = q.id) AS already_solved
            FROM questions q
            JOIN question_versions v ON v.id = q.current_version_id
            JOIN topics t ON t.id = q.topic_id
            JOIN subjects s ON s.id = t.subject_id
        """.trimIndent()
    }
}
