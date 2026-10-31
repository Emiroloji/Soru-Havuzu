package app.soruhavuzu

import app.soruhavuzu.auth.PasswordHasher
import app.soruhavuzu.auth.TokenService
import app.soruhavuzu.moderation.ModerationStore
import app.soruhavuzu.questions.ImageStorage
import app.soruhavuzu.questions.NewQuestion
import app.soruhavuzu.questions.QuestionFilter
import app.soruhavuzu.questions.QuestionStore
import app.soruhavuzu.questions.SessionType
import com.zaxxer.hikari.HikariDataSource
import org.postgresql.ds.PGSimpleDataSource
import org.testcontainers.postgresql.PostgreSQLContainer
import java.nio.file.Files
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
    questions: QuestionStore = UnusedQuestionStore,
    images: ImageStorage = ImageStorage(Files.createTempDirectory("soruhavuzu-test-images")),
    // Bağlantı ayarı olmayan veri kaynağı: moderasyon uçlarını kullanmayan testlerde hiç açılmaz
    moderation: ModerationStore = ModerationStore(PGSimpleDataSource()),
    authRequestsPerMinute: Int = 1000,
    uploadsPerHour: Int = 1000
) = Services(
    healthCheck = healthCheck,
    users = users,
    questions = questions,
    images = images,
    moderation = moderation,
    uploadsPerHour = uploadsPerHour,
    // Testlerde düşük maliyet: bcrypt 12 her hash'te ~250 ms sürer
    hasher = PasswordHasher(cost = 4),
    tokens = TokenService(testJwtConfig),
    authRequestsPerMinute = authRequestsPerMinute
)

fun ApplicationTestBuilder.jsonClient(): HttpClient = createClient {
    install(ContentNegotiation) { json() }
}

/** Soru uçlarını kullanmayan testler için; çağrılırsa test hatalı demektir. */
object UnusedQuestionStore : QuestionStore {
    private fun unused(): Nothing = error("Bu testte soru deposu kullanılmamalı")
    override suspend fun catalog() = unused()
    override suspend fun findForSolving(questionId: UUID, userId: UUID) = unused()
    override suspend fun pickPractice(userId: UUID, filter: QuestionFilter, buckets: IntRange, includeSolved: Boolean) = unused()
    override suspend fun pickChallenge(userId: UUID, filter: QuestionFilter) = unused()
    override suspend fun submitAttempt(
        userId: UUID, questionId: UUID, selectedOptionIndex: Int, responseTimeMs: Long, sessionType: SessionType
    ) = unused()
    override suspend fun topicExists(topicId: String) = unused()
    override suspend fun createQuestion(question: NewQuestion, maxUploadsPerHour: Int) = unused()
    override suspend fun questionsUploadedBy(userId: UUID) = unused()
}

/** Testcontainers ile tüm test sınıflarının paylaştığı tek Postgres; migration'lar bir kez çalışır. */
object TestDatabase {
    private val container by lazy { PostgreSQLContainer("postgres:17").also { it.start() } }

    val dataSource: HikariDataSource by lazy {
        Database.connect(DatabaseConfig(container.jdbcUrl, container.username, container.password, 8))
            .also { Database.migrate(it) }
    }
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
