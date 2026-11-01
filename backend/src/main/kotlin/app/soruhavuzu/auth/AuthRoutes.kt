package app.soruhavuzu.auth

import app.soruhavuzu.ErrorResponse
import app.soruhavuzu.domain.CalibrationEngine
import app.soruhavuzu.validate
import app.soruhavuzu.users.NewUser
import app.soruhavuzu.users.User
import app.soruhavuzu.users.UserStore
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import java.time.Year

const val AUTH_JWT = "auth-jwt"
val AUTH_RATE_LIMIT = RateLimitName("auth")

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val nickname: String,
    val birthYear: Int? = null,
    val targetExamId: String? = null
)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class UserDto(
    val id: String,
    val email: String,
    val nickname: String,
    val role: String,
    val reputation: Int,
    val theta: Double,
    /** θ'nın 1..10 ölçeğindeki karşılığı; uygulamada gösterilen seviye. */
    val level: Double,
    val birthYear: Int?,
    val targetExamId: String?
)

@Serializable
data class AuthResponse(val token: String, val user: UserDto)

@Serializable
data class ReputationEventDto(val delta: Int, val reason: String, val createdAt: String)

fun User.toDto() = UserDto(
    id.toString(), email, nickname, role, reputation, theta, CalibrationEngine.thetaToLevel(theta), birthYear, targetExamId
)

private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
private val NICKNAME_REGEX = Regex("^[\\p{L}0-9_.]{3,24}$")

fun Route.authRoutes(users: UserStore, hasher: PasswordHasher, tokens: TokenService) {
    rateLimit(AUTH_RATE_LIMIT) {
        route("/auth") {
            post("/register") {
                val req = call.receive<RegisterRequest>()
                val email = req.email.trim().lowercase()
                val nickname = req.nickname.trim()

                validate(email.length <= 254 && EMAIL_REGEX.matches(email)) { "Geçerli bir e-posta adresi girin" }
                // bcrypt yalnızca ilk 72 baytı kullanır; Türkçe karakterler 2 bayt sayılır
                validate(req.password.length >= 8 && req.password.toByteArray().size <= 72) {
                    "Şifre en az 8 karakter olmalı ve çok uzun olmamalı"
                }
                validate(NICKNAME_REGEX.matches(nickname)) {
                    "Takma ad 3-24 karakter olmalı; harf, rakam, alt çizgi ve nokta kullanılabilir"
                }
                req.birthYear?.let { year ->
                    validate(year in 1940..(Year.now().value - 10)) { "Geçerli bir doğum yılı girin" }
                }
                req.targetExamId?.let { examId ->
                    validate(users.examExists(examId)) { "Hedef sınav bulunamadı" }
                }

                val user = users.create(
                    NewUser(
                        email = email,
                        passwordHash = hasher.hash(req.password),
                        nickname = nickname,
                        birthYear = req.birthYear,
                        targetExamId = req.targetExamId
                    )
                )
                call.respond(HttpStatusCode.Created, AuthResponse(tokens.issue(user.id), user.toDto()))
            }

            post("/login") {
                val req = call.receive<LoginRequest>()
                val found = users.findByEmailWithPassword(req.email.trim())
                if (found == null) hasher.burnTime(req.password)
                val user = found?.takeIf { (_, hash) -> hasher.verify(req.password, hash) }?.first
                if (user == null) {
                    // Hangi bilginin yanlış olduğunu söylemiyoruz: kayıtlı e-postalar tahmin edilemesin
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponse("E-posta veya şifre hatalı"))
                    return@post
                }
                call.respond(AuthResponse(tokens.issue(user.id), user.toDto()))
            }
        }
    }

    authenticate(AUTH_JWT) {
        get("/me/reputation") {
            val auth = call.principal<AuthUser>()!!
            call.respond(users.reputationEvents(auth.id).map { ReputationEventDto(it.delta, it.reason, it.createdAt.toString()) })
        }

        get("/me") {
            val auth = call.principal<AuthUser>()!!
            val user = users.findById(auth.id)
            if (user == null) {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Kullanıcı bulunamadı"))
            } else {
                call.respond(user.toDto())
            }
        }
    }
}
