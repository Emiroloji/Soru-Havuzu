package app.soruhavuzu.moderation

import app.soruhavuzu.ConflictException
import app.soruhavuzu.ForbiddenException
import app.soruhavuzu.NotFoundException
import app.soruhavuzu.domain.CalibrationEngine
import app.soruhavuzu.users.ReputationLedger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.postgresql.util.PSQLException
import java.sql.Connection
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

enum class ReportReason(val priority: Boolean = false) {
    WRONG_KEY, COPYRIGHT_OSYM(priority = true), DUPLICATE, UNCLEAR, INAPPROPRIATE, OTHER
}

data class ReportInfo(
    val reason: String,
    val note: String,
    val weight: Int,
    val reporterNickname: String,
    val createdAt: Instant
)

/** Moderatörün gördüğü tam soru: doğru cevap, çözüm, şık dağılımı ve açık şikayetler dahil. */
data class ModerationItem(
    val id: UUID,
    val status: String,
    val topicName: String,
    val uploaderNickname: String,
    val versionNumber: Int,
    val bodyText: String,
    val imagePath: String?,
    val options: List<String>,
    val correctOptionIndex: Int,
    val solutionText: String,
    val uploaderRating: Int,
    val difficultyBucket: Int,
    val firstAttemptCount: Int,
    val correctFirstCount: Int,
    val optionSelectionCounts: List<Int>,
    val keySuspect: Boolean,
    val keySuspectReason: String?,
    val weightedReportScore: Int,
    val reports: List<ReportInfo>,
    val createdAt: Instant
)

data class VersionEdit(
    val bodyText: String,
    val options: List<String>,
    val correctOptionIndex: Int,
    val solutionText: String,
    val contentHash: String,
    val editorNote: String
)

data class VersionResult(val versionNumber: Int, val calibrationReset: Boolean)

class ModerationStore(private val dataSource: DataSource) {

    /**
     * Şikayet kaydeder. Ağırlıklı skor eşiği aşarsa ya da telif şikayetiyse
     * yayındaki soru FLAGGED olur ve havuzdan çekilir. Dönen değer: soru işaretlendi mi.
     */
    suspend fun report(reporterId: UUID, questionId: UUID, reason: ReportReason, note: String, weight: Int): Boolean =
        transaction { conn ->
            val (status, score) = lockQuestion(conn, questionId) { it.getString("status") to it.getInt("weighted_report_score") }
            if (status != "ACTIVE" && status != "FLAGGED") throw NotFoundException("Soru bulunamadı")

            try {
                conn.prepareStatement(
                    "INSERT INTO reports (question_id, reporter_id, reason, weight, note) VALUES (?, ?, ?, ?, ?)"
                ).use { st ->
                    st.setObject(1, questionId)
                    st.setObject(2, reporterId)
                    st.setString(3, reason.name)
                    st.setInt(4, weight)
                    st.setString(5, note)
                    st.executeUpdate()
                }
            } catch (e: PSQLException) {
                if (e.serverErrorMessage?.constraint == "reports_open_uq") {
                    throw ConflictException("Bu soru için zaten açık bir şikayetin var")
                }
                throw e
            }

            val newScore = score + weight
            val flag = status == "ACTIVE" && (reason.priority || newScore >= FLAG_THRESHOLD)
            conn.prepareStatement(
                "UPDATE questions SET weighted_report_score = ?, status = ?, updated_at = now() WHERE id = ?"
            ).use { st ->
                st.setInt(1, newScore)
                st.setString(2, if (flag) "FLAGGED" else status)
                st.setObject(3, questionId)
                st.executeUpdate()
            }
            flag || status == "FLAGGED"
        }

    /** İnceleme bekleyen, şikayetle işaretlenmiş ve anahtar şüphesi taşıyan sorular; telif şikayetleri en üstte. */
    suspend fun queue(): List<ModerationItem> = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            val items = conn.prepareStatement(
                """
                SELECT q.id, q.status, t.name AS topic_name, u.nickname AS uploader_nickname, v.version_number,
                       v.body_text, v.image_path, v.options, v.correct_option_index, v.solution_text,
                       v.option_selection_counts, q.uploader_rating, q.difficulty_bucket, q.first_attempt_count,
                       q.correct_first_count, q.key_suspect, q.key_suspect_reason, q.weighted_report_score, q.created_at,
                       EXISTS (SELECT 1 FROM reports r WHERE r.question_id = q.id AND r.status = 'OPEN'
                               AND r.reason = 'COPYRIGHT_OSYM') AS has_copyright_report
                FROM questions q
                JOIN question_versions v ON v.id = q.current_version_id
                JOIN topics t ON t.id = q.topic_id
                JOIN users u ON u.id = q.uploader_id
                WHERE q.status IN ('IN_REVIEW', 'FLAGGED') OR (q.status = 'ACTIVE' AND q.key_suspect)
                ORDER BY has_copyright_report DESC,
                         CASE q.status WHEN 'FLAGGED' THEN 0 WHEN 'ACTIVE' THEN 1 ELSE 2 END,
                         q.created_at
                LIMIT 100
                """.trimIndent()
            ).use { st -> st.executeQuery().use { rs -> generateSequence { if (rs.next()) rs.toItem() else null }.toList() } }

            if (items.isEmpty()) return@use items
            val reports = openReports(conn, items.map { it.id })
            items.map { it.copy(reports = reports[it.id].orEmpty()) }
        }
    }

    /** Soruyu yayına alır, açık şikayetleri reddedilmiş sayar. İncelemeden geçen soru yükleyene +5 itibar verir. */
    suspend fun approve(moderatorId: UUID, questionId: UUID) = transaction { conn ->
        val (status, uploaderId) = lockQuestion(conn, questionId) {
            it.getString("status") to it.getObject("uploader_id", UUID::class.java)
        }
        if (status == "REMOVED") throw NotFoundException("Soru bulunamadı")
        if (uploaderId == moderatorId) throw ForbiddenException("Kendi sorunu onaylayamazsın")

        conn.prepareStatement(
            """
            UPDATE questions SET status = 'ACTIVE', key_suspect = false, key_suspect_reason = NULL,
                                 weighted_report_score = 0, updated_at = now()
            WHERE id = ?
            """.trimIndent()
        ).use { st -> st.setObject(1, questionId); st.executeUpdate() }
        resolveOpenReports(conn, questionId, moderatorId, "DISMISSED")

        if (status == "IN_REVIEW") {
            ReputationLedger.add(conn, uploaderId, ReputationLedger.QUESTION_PUBLISHED, "Sorun incelemeden geçip yayına alındı", questionId)
        }
    }

    /**
     * Soruyu kaldırır. Yayınlanmış bir soruysa yükleyen itibar kaybeder; haklı çıkan şikayetçiler itibar kazanır.
     * Dönen liste: artık hiçbir yerde gösterilmeyecek görsel dosyaları (çağıran diskten siler).
     */
    suspend fun remove(moderatorId: UUID, questionId: UUID, reason: String): List<String> = transaction { conn ->
        val (status, uploaderId) = lockQuestion(conn, questionId) {
            it.getString("status") to it.getObject("uploader_id", UUID::class.java)
        }
        if (status == "REMOVED") throw NotFoundException("Soru bulunamadı")
        if (uploaderId == moderatorId) throw ForbiddenException("Kendi sorunu moderasyonla kaldıramazsın")

        conn.prepareStatement("UPDATE questions SET status = 'REMOVED', key_suspect = false, updated_at = now() WHERE id = ?")
            .use { st -> st.setObject(1, questionId); st.executeUpdate() }
        val reporters = resolveOpenReports(conn, questionId, moderatorId, "RESOLVED_REMOVED")

        if (status != "IN_REVIEW") {
            ReputationLedger.add(conn, uploaderId, ReputationLedger.QUESTION_REMOVED, "Sorun kaldırıldı: $reason", questionId)
        }
        reporters.forEach {
            ReputationLedger.add(conn, it, ReputationLedger.VALID_REPORT, "Şikayetin haklı bulundu, soru kaldırıldı", questionId)
        }

        conn.prepareStatement("SELECT DISTINCT image_path FROM question_versions WHERE question_id = ? AND image_path IS NOT NULL")
            .use { st ->
                st.setObject(1, questionId)
                st.executeQuery().use { rs -> generateSequence { if (rs.next()) rs.getString(1) else null }.toList() }
            }
    }

    /**
     * Soruyu yeni versiyonla düzeltip yayına alır. Şıklar veya doğru cevap değiştiyse eski çözümler
     * artık bu soruyu temsil etmez: zorluk, yükleyenin puanından yeniden başlatılır ve sayaçlar sıfırlanır.
     * Görsel önceki versiyondan taşınır.
     */
    suspend fun createVersion(moderatorId: UUID, questionId: UUID, edit: VersionEdit): VersionResult = transaction { conn ->
        data class Current(
            val status: String, val uploaderId: UUID, val rating: Int, val versionNumber: Int,
            val imagePath: String?, val options: List<String>, val correctIndex: Int
        )
        val current = lockQuestion(conn, questionId) { rs ->
            conn.prepareStatement(
                "SELECT version_number, image_path, options, correct_option_index FROM question_versions WHERE id = ?"
            ).use { st ->
                st.setObject(1, rs.getObject("current_version_id", UUID::class.java))
                st.executeQuery().use { v ->
                    v.next()
                    @Suppress("UNCHECKED_CAST")
                    Current(
                        status = rs.getString("status"),
                        uploaderId = rs.getObject("uploader_id", UUID::class.java),
                        rating = rs.getInt("uploader_rating"),
                        versionNumber = v.getInt("version_number"),
                        imagePath = v.getString("image_path"),
                        options = (v.getArray("options").array as Array<String>).toList(),
                        correctIndex = v.getInt("correct_option_index")
                    )
                }
            }
        }
        if (current.status == "REMOVED") throw NotFoundException("Soru bulunamadı")
        if (current.uploaderId == moderatorId) throw ForbiddenException("Kendi soruna moderatör olarak versiyon açamazsın")

        val answerChanged = edit.options != current.options || edit.correctOptionIndex != current.correctIndex
        val versionId = UUID.randomUUID()
        val versionNumber = current.versionNumber + 1

        conn.prepareStatement(
            """
            INSERT INTO question_versions (id, question_id, version_number, body_text, image_path, options,
                                           correct_option_index, option_selection_counts, solution_text, editor_note, created_by)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { st ->
            st.setObject(1, versionId)
            st.setObject(2, questionId)
            st.setInt(3, versionNumber)
            st.setString(4, edit.bodyText)
            st.setString(5, current.imagePath)
            st.setArray(6, conn.createArrayOf("text", edit.options.toTypedArray()))
            st.setInt(7, edit.correctOptionIndex)
            st.setArray(8, conn.createArrayOf("integer", Array(edit.options.size) { 0 }))
            st.setString(9, edit.solutionText)
            st.setString(10, edit.editorNote)
            st.setObject(11, moderatorId)
            st.executeUpdate()
        }

        val resetLogit = CalibrationEngine.initialLogit(current.rating, uploaderBias = 0.0)
        try {
            conn.prepareStatement(
                """
                UPDATE questions SET
                    current_version_id = ?, status = 'ACTIVE', content_hash = ?,
                    key_suspect = false, key_suspect_reason = NULL, weighted_report_score = 0,
                    difficulty_logit = CASE WHEN ? THEN ? ELSE difficulty_logit END,
                    difficulty_bucket = CASE WHEN ? THEN ? ELSE difficulty_bucket END,
                    first_attempt_count = CASE WHEN ? THEN 0 ELSE first_attempt_count END,
                    correct_first_count = CASE WHEN ? THEN 0 ELSE correct_first_count END,
                    updated_at = now()
                WHERE id = ?
                """.trimIndent()
            ).use { st ->
                st.setObject(1, versionId)
                st.setString(2, edit.contentHash)
                st.setBoolean(3, answerChanged); st.setDouble(4, resetLogit)
                st.setBoolean(5, answerChanged); st.setInt(6, CalibrationEngine.logitToBucket(resetLogit))
                st.setBoolean(7, answerChanged)
                st.setBoolean(8, answerChanged)
                st.setObject(9, questionId)
                st.executeUpdate()
            }
        } catch (e: PSQLException) {
            if (e.serverErrorMessage?.constraint == "questions_content_hash_uq") {
                throw ConflictException("Bu metinle havuzda başka bir soru var")
            }
            throw e
        }

        val reporters = resolveOpenReports(conn, questionId, moderatorId, "RESOLVED_FIXED")
        reporters.forEach {
            ReputationLedger.add(conn, it, ReputationLedger.VALID_REPORT, "Şikayetin haklı bulundu, soru düzeltildi", questionId)
        }
        if (current.status == "IN_REVIEW") {
            ReputationLedger.add(conn, current.uploaderId, ReputationLedger.QUESTION_PUBLISHED, "Sorun düzeltilerek yayına alındı", questionId)
        }
        VersionResult(versionNumber, answerChanged)
    }

    /** Açık şikayetleri kapatır ve şikayet edenlerin id'lerini döner. */
    private fun resolveOpenReports(conn: Connection, questionId: UUID, moderatorId: UUID, status: String): List<UUID> =
        conn.prepareStatement(
            """
            UPDATE reports SET status = ?, resolved_by = ?, resolved_at = now()
            WHERE question_id = ? AND status = 'OPEN'
            RETURNING reporter_id
            """.trimIndent()
        ).use { st ->
            st.setString(1, status)
            st.setObject(2, moderatorId)
            st.setObject(3, questionId)
            st.executeQuery().use { rs -> generateSequence { if (rs.next()) rs.getObject(1, UUID::class.java) else null }.toList() }
        }

    private fun openReports(conn: Connection, questionIds: List<UUID>): Map<UUID, List<ReportInfo>> =
        conn.prepareStatement(
            """
            SELECT r.question_id, r.reason, r.note, r.weight, u.nickname, r.created_at
            FROM reports r JOIN users u ON u.id = r.reporter_id
            WHERE r.status = 'OPEN' AND r.question_id = ANY (?)
            ORDER BY r.created_at
            """.trimIndent()
        ).use { st ->
            st.setArray(1, conn.createArrayOf("uuid", questionIds.toTypedArray()))
            st.executeQuery().use { rs ->
                generateSequence { if (rs.next()) rs else null }.map {
                    it.getObject("question_id", UUID::class.java) to ReportInfo(
                        reason = it.getString("reason"),
                        note = it.getString("note"),
                        weight = it.getInt("weight"),
                        reporterNickname = it.getString("nickname"),
                        createdAt = it.getTimestamp("created_at").toInstant()
                    )
                }.toList()
            }
        }.groupBy({ it.first }, { it.second })

    private fun <T> lockQuestion(conn: Connection, questionId: UUID, read: (ResultSet) -> T): T =
        conn.prepareStatement(
            "SELECT status, uploader_id, uploader_rating, current_version_id, weighted_report_score FROM questions WHERE id = ? FOR UPDATE"
        ).use { st ->
            st.setObject(1, questionId)
            st.executeQuery().use { rs -> if (rs.next()) read(rs) else throw NotFoundException("Soru bulunamadı") }
        }

    private suspend fun <T> transaction(block: (Connection) -> T): T = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                block(conn).also { conn.commit() }
            } catch (e: Throwable) {
                conn.rollback()
                throw e
            } finally {
                conn.autoCommit = true
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun ResultSet.toItem() = ModerationItem(
        id = getObject("id", UUID::class.java),
        status = getString("status"),
        topicName = getString("topic_name"),
        uploaderNickname = getString("uploader_nickname"),
        versionNumber = getInt("version_number"),
        bodyText = getString("body_text"),
        imagePath = getString("image_path"),
        options = (getArray("options").array as Array<String>).toList(),
        correctOptionIndex = getInt("correct_option_index"),
        solutionText = getString("solution_text"),
        uploaderRating = getInt("uploader_rating"),
        difficultyBucket = getInt("difficulty_bucket"),
        firstAttemptCount = getInt("first_attempt_count"),
        correctFirstCount = getInt("correct_first_count"),
        optionSelectionCounts = (getArray("option_selection_counts").array as Array<Int>).toList(),
        keySuspect = getBoolean("key_suspect"),
        keySuspectReason = getString("key_suspect_reason"),
        weightedReportScore = getInt("weighted_report_score"),
        reports = emptyList(),
        createdAt = getTimestamp("created_at").toInstant()
    )

    private companion object {
        /** Bu ağırlığa ulaşan şikayetler soruyu havuzdan çeker (ör. 3 normal kullanıcı ya da 1 doğrulayıcı + 1 kullanıcı). */
        const val FLAG_THRESHOLD = 3
    }
}
