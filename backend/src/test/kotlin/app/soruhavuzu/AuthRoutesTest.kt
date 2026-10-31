package app.soruhavuzu

import app.soruhavuzu.auth.AuthResponse
import app.soruhavuzu.auth.LoginRequest
import app.soruhavuzu.auth.RegisterRequest
import app.soruhavuzu.auth.UserDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthRoutesTest {

    private val validRegister = RegisterRequest(
        email = "Ali@Ornek.com",
        password = "guclu-sifre-123",
        nickname = "ali_kpss",
        birthYear = 1998,
        targetExamId = "exam_kpss"
    )

    private suspend fun HttpClient.register(req: RegisterRequest): HttpResponse = post("/auth/register") {
        contentType(ContentType.Application.Json)
        setBody(req)
    }

    private suspend fun HttpClient.login(email: String, password: String): HttpResponse = post("/auth/login") {
        contentType(ContentType.Application.Json)
        setBody(LoginRequest(email, password))
    }

    @Test
    fun `kayit token ve kullanici doner, me ayni kullaniciyi verir`() = testApplication {
        application { module(testServices()) }
        val client = jsonClient()

        val response = client.register(validRegister)
        assertEquals(HttpStatusCode.Created, response.status)
        val auth = response.body<AuthResponse>()
        assertEquals("ali@ornek.com", auth.user.email)
        assertEquals("USER", auth.user.role)
        assertEquals(0, auth.user.reputation)

        val me = client.get("/me") { bearerAuth(auth.token) }
        assertEquals(HttpStatusCode.OK, me.status)
        assertEquals(auth.user.id, me.body<UserDto>().id)
    }

    @Test
    fun `ayni e-posta veya takma adla ikinci kayit 409 doner`() = testApplication {
        application { module(testServices()) }
        val client = jsonClient()
        client.register(validRegister)

        val sameEmail = client.register(validRegister.copy(email = "ALI@ornek.com", nickname = "baska"))
        assertEquals(HttpStatusCode.Conflict, sameEmail.status)

        val sameNick = client.register(validRegister.copy(email = "veli@ornek.com"))
        assertEquals(HttpStatusCode.Conflict, sameNick.status)
    }

    @Test
    fun `gecersiz kayit bilgileri 400 doner`() = testApplication {
        application { module(testServices()) }
        val client = jsonClient()

        val invalids = listOf(
            validRegister.copy(email = "eposta-degil"),
            validRegister.copy(password = "kisa"),
            validRegister.copy(password = "ş".repeat(40)), // 80 bayt, bcrypt sınırını aşar
            validRegister.copy(nickname = "a"),
            validRegister.copy(nickname = "boşluk var"),
            validRegister.copy(birthYear = 1800),
            validRegister.copy(targetExamId = "exam_yok")
        )
        for (req in invalids) {
            assertEquals(HttpStatusCode.BadRequest, client.register(req).status, "beklenen 400: $req")
        }
    }

    @Test
    fun `bozuk json 400 doner`() = testApplication {
        application { module(testServices()) }

        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("{\"email\":")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `dogru sifreyle giris olur, yanlis sifre ve olmayan e-posta ayni hatayi verir`() = testApplication {
        application { module(testServices()) }
        val client = jsonClient()
        client.register(validRegister)

        val ok = client.login("ali@ornek.com", validRegister.password)
        assertEquals(HttpStatusCode.OK, ok.status)
        assertEquals("ali_kpss", ok.body<AuthResponse>().user.nickname)

        val wrongPassword = client.login("ali@ornek.com", "yanlis-sifre")
        val unknownEmail = client.login("yok@ornek.com", validRegister.password)
        assertEquals(HttpStatusCode.Unauthorized, wrongPassword.status)
        assertEquals(HttpStatusCode.Unauthorized, unknownEmail.status)
        assertEquals(wrongPassword.bodyAsText(), unknownEmail.bodyAsText())
    }

    @Test
    fun `token olmadan veya bozuk tokenla me 401 doner`() = testApplication {
        application { module(testServices()) }

        assertEquals(HttpStatusCode.Unauthorized, client.get("/me").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/me") { bearerAuth("bozuk.token.degeri") }.status)
    }

    @Test
    fun `baska anahtarla imzalanmis token kabul edilmez`() = testApplication {
        application { module(testServices()) }
        val foreignToken = app.soruhavuzu.auth.TokenService(
            JwtConfig(secret = "baska-bir-sunucunun-gizli-anahtari-32-karakter")
        ).issue(java.util.UUID.randomUUID())

        assertEquals(HttpStatusCode.Unauthorized, client.get("/me") { bearerAuth(foreignToken) }.status)
    }

    @Test
    fun `auth uclari dakikalik sinirdan sonra 429 doner`() = testApplication {
        application { module(testServices(authRequestsPerMinute = 3)) }
        val client = jsonClient()

        repeat(3) { client.login("ali@ornek.com", "fark-etmez") }
        val blocked = client.login("ali@ornek.com", "fark-etmez")

        assertEquals(HttpStatusCode.TooManyRequests, blocked.status)
        assertTrue(client.get("/health").status == HttpStatusCode.OK, "sınır yalnızca /auth için geçerli olmalı")
    }
}
