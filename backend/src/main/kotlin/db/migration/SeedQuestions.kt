package db.migration

import app.soruhavuzu.domain.CalibrationEngine
import app.soruhavuzu.questions.ContentRules
import java.sql.Connection
import java.util.UUID

/** Migration'larla eklenen başlangıç sorusu. [correct] doğru şıkkın sırası (0'dan başlar). */
internal data class Seed(
    val topicId: String,
    val rating: Int,
    val body: String,
    val options: List<String>,
    val correct: Int,
    val solution: String
)

/** V3'te oluşturulan, giriş yapılamayan sistem kullanıcısı; başlangıç sorularının sahibi. */
internal val SYSTEM_USER: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")

/**
 * Başlangıç sorularını sistem kullanıcısı adına yayında ekler. Zorluk, yazarın 1-10 tahmininden
 * başlar ve çözümlerle kalibre olur. Aynı soru (kök + şıklar) havuzda zaten varsa atlanır;
 * böylece migration hiçbir veritabanında kopya kısıtına takılıp kırılmaz.
 */
internal fun insertSeedQuestions(conn: Connection, seeds: List<Seed>) {
    for (seed in seeds) {
        val hash = ContentRules.contentHash(seed.body, seed.options)
        val exists = conn.prepareStatement(
            "SELECT 1 FROM questions WHERE content_hash = ? AND status <> 'REMOVED'"
        ).use { st ->
            st.setString(1, hash)
            st.executeQuery().use { it.next() }
        }
        if (exists) continue

        val questionId = UUID.randomUUID()
        val versionId = UUID.randomUUID()
        val logit = CalibrationEngine.initialLogit(seed.rating, uploaderBias = 0.0)
        conn.prepareStatement(
            """
            INSERT INTO questions (id, uploader_id, topic_id, current_version_id, status, uploader_rating,
                                   difficulty_logit, difficulty_bucket, content_hash)
            VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?, ?, ?)
            """.trimIndent()
        ).use { st ->
            st.setObject(1, questionId)
            st.setObject(2, SYSTEM_USER)
            st.setString(3, seed.topicId)
            st.setObject(4, versionId)
            st.setInt(5, seed.rating)
            st.setDouble(6, logit)
            st.setInt(7, CalibrationEngine.logitToBucket(logit))
            st.setString(8, hash)
            st.executeUpdate()
        }
        conn.prepareStatement(
            """
            INSERT INTO question_versions (id, question_id, version_number, body_text, options, correct_option_index,
                                           option_selection_counts, solution_text, editor_note, created_by)
            VALUES (?, ?, 1, ?, ?, ?, ?, ?, 'Başlangıç sorusu', ?)
            """.trimIndent()
        ).use { st ->
            st.setObject(1, versionId)
            st.setObject(2, questionId)
            st.setString(3, seed.body)
            st.setArray(4, conn.createArrayOf("text", seed.options.toTypedArray()))
            st.setInt(5, seed.correct)
            st.setArray(6, conn.createArrayOf("integer", Array(seed.options.size) { 0 }))
            st.setString(7, seed.solution)
            st.setObject(8, SYSTEM_USER)
            st.executeUpdate()
        }
    }
}
