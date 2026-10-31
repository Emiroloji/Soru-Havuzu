package app.soruhavuzu.users

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.postgresql.util.PSQLException
import java.sql.ResultSet
import java.util.UUID
import javax.sql.DataSource

data class User(
    val id: UUID,
    val email: String,
    val nickname: String,
    val role: String,
    val reputation: Int,
    val theta: Double,
    val birthYear: Int?,
    val targetExamId: String?
)

data class NewUser(
    val email: String,
    val passwordHash: String,
    val nickname: String,
    val birthYear: Int?,
    val targetExamId: String?
)

class DuplicateUserException(val field: String) : RuntimeException("$field zaten kayıtlı")

interface UserStore {
    /** E-posta veya takma ad başkasında varsa [DuplicateUserException] fırlatır. */
    suspend fun create(newUser: NewUser): User
    suspend fun findById(id: UUID): User?
    /** Giriş için kullanıcıyı şifre hash'iyle birlikte döner. */
    suspend fun findByEmailWithPassword(email: String): Pair<User, String>?
    suspend fun examExists(examId: String): Boolean
}

class PostgresUserStore(private val dataSource: DataSource) : UserStore {

    override suspend fun create(newUser: NewUser): User = withContext(Dispatchers.IO) {
        try {
            dataSource.connection.use { conn ->
                conn.prepareStatement(
                    """
                    INSERT INTO users (email, password_hash, nickname, birth_year, target_exam_id)
                    VALUES (?, ?, ?, ?, ?)
                    RETURNING $USER_COLUMNS
                    """.trimIndent()
                ).use { st ->
                    st.setString(1, newUser.email)
                    st.setString(2, newUser.passwordHash)
                    st.setString(3, newUser.nickname)
                    st.setObject(4, newUser.birthYear)
                    st.setString(5, newUser.targetExamId)
                    st.executeQuery().use { rs ->
                        rs.next()
                        rs.toUser()
                    }
                }
            }
        } catch (e: PSQLException) {
            if (e.sqlState == UNIQUE_VIOLATION) {
                when (e.serverErrorMessage?.constraint) {
                    "users_email_uq" -> throw DuplicateUserException("email")
                    "users_nickname_uq" -> throw DuplicateUserException("nickname")
                }
            }
            throw e
        }
    }

    override suspend fun findById(id: UUID): User? = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT $USER_COLUMNS FROM users WHERE id = ?").use { st ->
                st.setObject(1, id)
                st.executeQuery().use { rs -> if (rs.next()) rs.toUser() else null }
            }
        }
    }

    override suspend fun findByEmailWithPassword(email: String): Pair<User, String>? = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT $USER_COLUMNS, password_hash FROM users WHERE lower(email) = lower(?)").use { st ->
                st.setString(1, email)
                st.executeQuery().use { rs ->
                    if (rs.next()) rs.toUser() to rs.getString("password_hash") else null
                }
            }
        }
    }

    override suspend fun examExists(examId: String): Boolean = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT 1 FROM exams WHERE id = ?").use { st ->
                st.setString(1, examId)
                st.executeQuery().use { it.next() }
            }
        }
    }

    private fun ResultSet.toUser() = User(
        id = getObject("id", UUID::class.java),
        email = getString("email"),
        nickname = getString("nickname"),
        role = getString("role"),
        reputation = getInt("reputation"),
        theta = getDouble("theta"),
        birthYear = getObject("birth_year") as Int?,
        targetExamId = getString("target_exam_id")
    )

    private companion object {
        const val USER_COLUMNS = "id, email, nickname, role, reputation, theta, birth_year, target_exam_id"
        const val UNIQUE_VIOLATION = "23505"
    }
}
