package app.soruhavuzu

import java.net.URI

data class DatabaseConfig(
    val jdbcUrl: String,
    val user: String,
    val password: String,
    val maxPoolSize: Int
)

data class AppConfig(
    val port: Int,
    val uploadDir: String,
    val database: DatabaseConfig
) {
    companion object {
        fun fromEnv(env: Map<String, String> = System.getenv()): AppConfig {
            fun required(key: String) = env[key]?.takeIf { it.isNotBlank() }
                ?: error("$key ortam değişkeni tanımlı değil")

            return AppConfig(
                port = env["PORT"]?.toIntOrNull() ?: 8080,
                uploadDir = env["UPLOAD_DIR"] ?: "/data/uploads",
                database = parseDatabaseUrl(
                    url = required("DATABASE_URL"),
                    user = env["DATABASE_USER"],
                    password = env["DATABASE_PASSWORD"],
                    maxPoolSize = env["DATABASE_POOL_SIZE"]?.toIntOrNull() ?: 5
                )
            )
        }
    }
}

/**
 * Hem JDBC biçimini (jdbc:postgresql://host:5432/db) hem de Dokploy'un verdiği
 * "Internal Connection URL" biçimini (postgresql://user:pass@host:5432/db) kabul eder.
 * Ayrı verilen DATABASE_USER / DATABASE_PASSWORD, URL içindekilerin önüne geçer.
 */
fun parseDatabaseUrl(url: String, user: String?, password: String?, maxPoolSize: Int): DatabaseConfig {
    if (url.startsWith("jdbc:")) {
        return DatabaseConfig(
            jdbcUrl = url,
            user = user ?: error("JDBC URL kullanılırken DATABASE_USER gerekli"),
            password = password ?: error("JDBC URL kullanılırken DATABASE_PASSWORD gerekli"),
            maxPoolSize = maxPoolSize
        )
    }

    val uri = URI(url)
    require(uri.scheme == "postgres" || uri.scheme == "postgresql") {
        "DATABASE_URL postgresql:// veya jdbc:postgresql:// ile başlamalı"
    }
    val userInfo = uri.rawUserInfo?.split(":", limit = 2).orEmpty().map { decode(it) }
    val port = if (uri.port == -1) 5432 else uri.port
    val query = uri.rawQuery?.let { "?$it" } ?: ""

    return DatabaseConfig(
        jdbcUrl = "jdbc:postgresql://${uri.host}:$port${uri.rawPath}$query",
        user = user ?: userInfo.getOrNull(0) ?: error("DATABASE_URL içinde kullanıcı adı yok"),
        password = password ?: userInfo.getOrNull(1) ?: error("DATABASE_URL içinde şifre yok"),
        maxPoolSize = maxPoolSize
    )
}

private fun decode(value: String): String = java.net.URLDecoder.decode(value, Charsets.UTF_8)
