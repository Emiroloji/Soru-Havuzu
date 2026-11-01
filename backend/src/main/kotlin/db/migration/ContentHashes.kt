package db.migration

import app.soruhavuzu.questions.ContentRules
import java.sql.Connection
import java.util.UUID

/** Tüm soruların kopya özetini güncel versiyonlarının kök ve şıklarından, güncel kurala göre yeniden hesaplar. */
internal fun recomputeContentHashes(conn: Connection) {
    val questions = conn.prepareStatement(
        "SELECT q.id, v.body_text, v.options FROM questions q JOIN question_versions v ON v.id = q.current_version_id"
    ).use { st ->
        st.executeQuery().use { rs ->
            generateSequence {
                if (!rs.next()) return@generateSequence null
                @Suppress("UNCHECKED_CAST")
                val options = (rs.getArray(3).array as Array<String>).toList()
                rs.getObject(1, UUID::class.java) to ContentRules.contentHash(rs.getString(2), options)
            }.toList()
        }
    }
    conn.prepareStatement("UPDATE questions SET content_hash = ? WHERE id = ?").use { st ->
        for ((id, hash) in questions) {
            st.setString(1, hash)
            st.setObject(2, id)
            st.addBatch()
        }
        st.executeBatch()
    }
}
