package app.soruhavuzu

import app.soruhavuzu.auth.AUTH_JWT
import app.soruhavuzu.auth.AUTH_RATE_LIMIT
import app.soruhavuzu.auth.AuthUser
import app.soruhavuzu.auth.PasswordHasher
import app.soruhavuzu.auth.TokenService
import app.soruhavuzu.auth.authRoutes
import app.soruhavuzu.questions.PostgresQuestionStore
import app.soruhavuzu.questions.QuestionStore
import app.soruhavuzu.questions.questionRoutes
import app.soruhavuzu.users.DuplicateUserException
import app.soruhavuzu.users.PostgresUserStore
import app.soruhavuzu.users.UserStore
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.time.Duration.Companion.minutes

private val log = LoggerFactory.getLogger("app.soruhavuzu")

fun main() {
    val config = AppConfig.fromEnv()
    val dataSource = Database.connect(config.database)
    Database.migrate(dataSource)

    val services = Services(
        healthCheck = DatabaseHealthCheck(dataSource),
        users = PostgresUserStore(dataSource),
        questions = PostgresQuestionStore(dataSource),
        hasher = PasswordHasher(),
        tokens = TokenService(config.jwt)
    )

    embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
        monitor.subscribe(ApplicationStopped) { dataSource.close() }
        module(services)
    }.start(wait = true)
}

class Services(
    val healthCheck: HealthCheck,
    val users: UserStore,
    val questions: QuestionStore,
    val hasher: PasswordHasher,
    val tokens: TokenService,
    val authRequestsPerMinute: Int = 10
)

@Serializable
data class HealthResponse(val status: String, val database: String)

@Serializable
data class ErrorResponse(val error: String)

fun Application.module(services: Services) {
    install(ContentNegotiation) { json() }
    install(CallLogging)
    // Dokploy'un Traefik'i gerçek istemci IP'sini X-Forwarded-For'un sonuna ekler
    install(XForwardedHeaders) { useLastProxy() }
    install(RateLimit) {
        register(AUTH_RATE_LIMIT) {
            rateLimiter(limit = services.authRequestsPerMinute, refillPeriod = 1.minutes)
            requestKey { call -> call.request.origin.remoteHost }
        }
    }
    install(Authentication) {
        jwt(AUTH_JWT) {
            verifier(services.tokens.verifier)
            validate { credential ->
                credential.payload.subject
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?.let { AuthUser(it) }
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Oturum geçersiz veya süresi dolmuş"))
            }
        }
    }
    install(StatusPages) {
        exception<ValidationException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.message ?: "Geçersiz istek"))
        }
        exception<NotFoundException> { call, cause ->
            call.respond(HttpStatusCode.NotFound, ErrorResponse(cause.message ?: "Bulunamadı"))
        }
        exception<ForbiddenException> { call, cause ->
            call.respond(HttpStatusCode.Forbidden, ErrorResponse(cause.message ?: "Bu işlem için yetkin yok"))
        }
        exception<BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("İstek gövdesi eksik veya hatalı"))
        }
        exception<DuplicateUserException> { call, cause ->
            val message = when (cause.field) {
                "email" -> "Bu e-posta adresiyle zaten bir hesap var"
                else -> "Bu takma ad başka biri tarafından kullanılıyor"
            }
            call.respond(HttpStatusCode.Conflict, ErrorResponse(message))
        }
        exception<Throwable> { call, cause ->
            log.error("Beklenmeyen hata", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Sunucu hatası"))
        }
    }

    routing {
        get("/health") {
            val dbUp = services.healthCheck.isDatabaseUp()
            call.respond(
                if (dbUp) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable,
                HealthResponse(status = if (dbUp) "ok" else "degraded", database = if (dbUp) "up" else "down")
            )
        }
        authRoutes(services.users, services.hasher, services.tokens)
        questionRoutes(services.questions)
    }
}
