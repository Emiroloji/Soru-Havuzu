package app.soruhavuzu

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("app.soruhavuzu")

fun main() {
    val config = AppConfig.fromEnv()
    val dataSource = Database.connect(config.database)
    Database.migrate(dataSource)

    embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
        monitor.subscribe(ApplicationStopped) { dataSource.close() }
        module(healthCheck = DatabaseHealthCheck(dataSource))
    }.start(wait = true)
}

@Serializable
data class HealthResponse(val status: String, val database: String)

@Serializable
data class ErrorResponse(val error: String)

fun Application.module(healthCheck: HealthCheck) {
    install(ContentNegotiation) { json() }
    install(CallLogging)
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            log.error("Beklenmeyen hata", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Sunucu hatası"))
        }
    }

    routing {
        get("/health") {
            val dbUp = healthCheck.isDatabaseUp()
            call.respond(
                if (dbUp) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable,
                HealthResponse(status = if (dbUp) "ok" else "degraded", database = if (dbUp) "up" else "down")
            )
        }
    }
}
