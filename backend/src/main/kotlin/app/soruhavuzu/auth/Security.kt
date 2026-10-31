package app.soruhavuzu.auth

import app.soruhavuzu.JwtConfig
import at.favre.lib.crypto.bcrypt.BCrypt
import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import java.time.Clock
import java.time.Duration
import java.util.UUID

class PasswordHasher(private val cost: Int = 12) {

    fun hash(password: String): String =
        BCrypt.withDefaults().hashToString(cost, password.toCharArray())

    /** Geçersiz hash (ör. giriş yapamayan sistem kullanıcısının "!" değeri) her zaman false döner. */
    fun verify(password: String, hash: String): Boolean = runCatching {
        BCrypt.verifyer().verify(password.toCharArray(), hash).verified
    }.getOrDefault(false)

    /**
     * Kayıtlı olmayan e-postayla girişte de aynı süre harcansın diye sahte bir hash'e karşı doğrular;
     * böylece yanıt süresinden hangi e-postaların kayıtlı olduğu anlaşılamaz.
     */
    fun burnTime(password: String) {
        verify(password, dummyHash)
    }

    private val dummyHash by lazy { hash("sahte-sifre-zamanlama-icin") }
}

class TokenService(private val config: JwtConfig, private val clock: Clock = Clock.systemUTC()) {

    private val algorithm = Algorithm.HMAC256(config.secret)

    val verifier: JWTVerifier = JWT.require(algorithm).withIssuer(config.issuer).build()

    fun issue(userId: UUID): String {
        val now = clock.instant()
        return JWT.create()
            .withIssuer(config.issuer)
            .withSubject(userId.toString())
            .withIssuedAt(now)
            .withExpiresAt(now.plus(Duration.ofDays(config.validityDays)))
            .sign(algorithm)
    }
}

/** Doğrulanmış isteğin sahibi. Rol/itibar token'a yazılmaz, gerektiğinde veritabanından okunur. */
data class AuthUser(val id: UUID)
