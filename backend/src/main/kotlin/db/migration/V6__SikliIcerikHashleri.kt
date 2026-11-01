package db.migration

import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context

/**
 * Kopya özeti artık kök + şıklardan hesaplanıyor (ContentRules.contentHash). Daha önce yalnızca
 * kökten hesaplanmış özetleri yeni kurala göre günceller; aksi hâlde aynı soru tekrar yüklenebilirdi.
 */
@Suppress("ClassName", "unused")
class V6__SikliIcerikHashleri : BaseJavaMigration() {
    override fun migrate(context: Context) = recomputeContentHashes(context.connection)
}
