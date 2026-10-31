package app.soruhavuzu

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import javax.sql.DataSource

object Database {

    fun connect(config: DatabaseConfig): HikariDataSource = HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = config.jdbcUrl
            username = config.user
            password = config.password
            maximumPoolSize = config.maxPoolSize
            poolName = "soruhavuzu"
        }
    )

    /** src/main/resources/db/migration altındaki SQL dosyalarını sırayla uygular. */
    fun migrate(dataSource: DataSource) {
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate()
    }
}

fun interface HealthCheck {
    fun isDatabaseUp(): Boolean
}

class DatabaseHealthCheck(private val dataSource: DataSource) : HealthCheck {
    override fun isDatabaseUp(): Boolean = runCatching {
        dataSource.connection.use { it.isValid(2) }
    }.getOrDefault(false)
}
