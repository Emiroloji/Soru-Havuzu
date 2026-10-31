package app.soruhavuzu

import app.soruhavuzu.auth.PasswordHasher
import app.soruhavuzu.auth.TokenService
import app.soruhavuzu.users.DuplicateUserException
import app.soruhavuzu.users.NewUser
import app.soruhavuzu.users.User
import app.soruhavuzu.users.UserStore
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import java.util.UUID

val testJwtConfig = JwtConfig(secret = "test-secret-en-az-otuz-iki-karakter-uzunlugunda")

fun testServices(
    healthCheck: HealthCheck = HealthCheck { true },
    users: UserStore = InMemoryUserStore(),
    authRequestsPerMinute: Int = 1000
) = Services(
    healthCheck = healthCheck,
    users = users,
    // Testlerde düşük maliyet: bcrypt 12 her hash'te ~250 ms sürer
    hasher = PasswordHasher(cost = 4),
    tokens = TokenService(testJwtConfig),
    authRequestsPerMinute = authRequestsPerMinute
)

fun ApplicationTestBuilder.jsonClient(): HttpClient = createClient {
    install(ContentNegotiation) { json() }
}

class InMemoryUserStore(private val examIds: Set<String> = setOf("exam_kpss")) : UserStore {
    private val users = mutableListOf<Pair<User, String>>()

    override suspend fun create(newUser: NewUser): User {
        if (users.any { it.first.email.equals(newUser.email, ignoreCase = true) }) throw DuplicateUserException("email")
        if (users.any { it.first.nickname.equals(newUser.nickname, ignoreCase = true) }) throw DuplicateUserException("nickname")
        val user = User(
            id = UUID.randomUUID(),
            email = newUser.email,
            nickname = newUser.nickname,
            role = "USER",
            reputation = 0,
            theta = 0.0,
            birthYear = newUser.birthYear,
            targetExamId = newUser.targetExamId
        )
        users += user to newUser.passwordHash
        return user
    }

    override suspend fun findById(id: UUID): User? = users.firstOrNull { it.first.id == id }?.first

    override suspend fun findByEmailWithPassword(email: String): Pair<User, String>? =
        users.firstOrNull { it.first.email.equals(email, ignoreCase = true) }

    override suspend fun examExists(examId: String): Boolean = examId in examIds
}
