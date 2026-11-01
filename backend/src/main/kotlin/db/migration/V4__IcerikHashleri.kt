package db.migration

import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context

/**
 * Başlangıç sorularının yer tutucu ("seed-...") özetlerini gerçek içerik özetiyle değiştirir ve
 * kopya soruyu veritabanı seviyesinde engelleyen tekil indeksi kurar. Özet hesabı Kotlin'de
 * (ContentRules) yapıldığı için bu migration SQL değil.
 */
@Suppress("ClassName", "unused")
class V4__IcerikHashleri : BaseJavaMigration() {

    override fun migrate(context: Context) {
        val conn = context.connection
        recomputeContentHashes(conn)
        conn.createStatement().use { st ->
            st.execute("DROP INDEX questions_content_hash_idx")
            st.execute("CREATE UNIQUE INDEX questions_content_hash_uq ON questions (content_hash) WHERE status <> 'REMOVED'")
        }
    }
}
