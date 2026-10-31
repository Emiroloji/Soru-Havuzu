package db.migration

import app.soruhavuzu.questions.ContentRules
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import java.util.UUID

/**
 * Başlangıç sorularının yer tutucu ("seed-...") özetlerini gerçek içerik özetiyle değiştirir ve
 * kopya soruyu veritabanı seviyesinde engelleyen tekil indeksi kurar. Özet hesabı Kotlin'de
 * (ContentRules) yapıldığı için bu migration SQL değil.
 */
@Suppress("ClassName", "unused")
class V4__IcerikHashleri : BaseJavaMigration() {

    override fun migrate(context: Context) {
        val conn = context.connection
        val bodies = conn.prepareStatement(
            "SELECT q.id, v.body_text FROM questions q JOIN question_versions v ON v.id = q.current_version_id"
        ).use { st ->
            st.executeQuery().use { rs ->
                generateSequence { if (rs.next()) rs.getObject(1, UUID::class.java) to rs.getString(2) else null }.toList()
            }
        }
        conn.prepareStatement("UPDATE questions SET content_hash = ? WHERE id = ?").use { st ->
            for ((id, body) in bodies) {
                st.setString(1, ContentRules.contentHash(body))
                st.setObject(2, id)
                st.addBatch()
            }
            st.executeBatch()
        }
        conn.createStatement().use { st ->
            st.execute("DROP INDEX questions_content_hash_idx")
            st.execute("CREATE UNIQUE INDEX questions_content_hash_uq ON questions (content_hash) WHERE status <> 'REMOVED'")
        }
    }
}
