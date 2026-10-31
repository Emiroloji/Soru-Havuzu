package app.soruhavuzu

import app.soruhavuzu.auth.AuthResponse
import app.soruhavuzu.auth.RegisterRequest
import app.soruhavuzu.questions.ImageStorage
import app.soruhavuzu.questions.PostgresQuestionStore
import app.soruhavuzu.questions.QuestionDto
import app.soruhavuzu.questions.UploadQuestionRequest
import app.soruhavuzu.questions.UploadQuestionResponse
import app.soruhavuzu.questions.UploadedQuestionDto
import app.soruhavuzu.users.PostgresUserStore
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.testcontainers.junit.jupiter.Testcontainers
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.UUID
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Testcontainers(disabledWithoutDocker = true)
class QuestionUploadTest {

    private val dataSource = TestDatabase.dataSource
    private val imageDir = Files.createTempDirectory("upload-test")

    private fun ApplicationTestBuilder.setup(uploadsPerHour: Int = 1000): HttpClient {
        application {
            module(
                testServices(
                    users = PostgresUserStore(dataSource),
                    questions = PostgresQuestionStore(dataSource),
                    images = ImageStorage(imageDir),
                    uploadsPerHour = uploadsPerHour
                )
            )
        }
        return jsonClient()
    }

    private suspend fun HttpClient.newUser(reputation: Int = 0): Pair<String, UUID> {
        val tag = UUID.randomUUID().toString().take(8)
        val auth = post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("y$tag@ornek.com", "guclu-sifre-123", "y_$tag"))
        }.body<AuthResponse>()
        val id = UUID.fromString(auth.user.id)
        if (reputation > 0) {
            dataSource.connection.use { conn ->
                conn.prepareStatement("UPDATE users SET reputation = ? WHERE id = ?").use { st ->
                    st.setInt(1, reputation); st.setObject(2, id); st.executeUpdate()
                }
            }
        }
        return auth.token to id
    }

    private fun request(body: String = "Benzersiz soru ${UUID.randomUUID()}: Lale Devri hangi padişah döneminde yaşanmıştır?") =
        UploadQuestionRequest(
            topicId = "kpss_tar_osmanli",
            bodyText = body,
            options = listOf("III. Ahmet", "II. Mahmut", "IV. Murat", "I. Abdülhamit"),
            correctOptionIndex = 0,
            uploaderRating = 4,
            solutionText = "Lale Devri (1718-1730) III. Ahmet döneminde, Nevşehirli Damat İbrahim Paşa sadrazamken yaşanmıştır.",
            originalityConfirmed = true
        )

    private suspend fun HttpClient.upload(token: String, req: UploadQuestionRequest, image: ByteArray? = null): HttpResponse =
        submitFormWithBinaryData(
            url = "/questions",
            formData = formData {
                append("question", Json.encodeToString(req), Headers.build {
                    append(HttpHeaders.ContentType, "application/json")
                })
                if (image != null) {
                    append("image", image, Headers.build {
                        append(HttpHeaders.ContentType, "image/png")
                        append(HttpHeaders.ContentDisposition, "filename=\"soru.png\"")
                    })
                }
            }
        ) { bearerAuth(token) }

    private fun png(): ByteArray {
        val img = BufferedImage(120, 80, BufferedImage.TYPE_INT_RGB)
        return ByteArrayOutputStream().also { ImageIO.write(img, "png", it) }.toByteArray()
    }

    @Test
    fun `yeni kullanicinin sorusu incelemeye duser ve havuzda gorunmez`() = testApplication {
        val client = setup()
        val (uploader, _) = client.newUser()
        val (solver, _) = client.newUser()

        val response = client.upload(uploader, request())
        assertEquals(HttpStatusCode.Created, response.status)
        val created = response.body<UploadQuestionResponse>()
        assertEquals("IN_REVIEW", created.status)

        assertEquals(HttpStatusCode.NotFound, client.get("/questions/${created.id}") { bearerAuth(solver) }.status)
        val mine = client.get("/me/questions") { bearerAuth(uploader) }.body<List<UploadedQuestionDto>>()
        assertEquals(created.id, mine.single().id)
        assertNull(mine.single().accuracyPercent)
    }

    @Test
    fun `itibari yeterli kullanicinin sorusu gorseliyle birlikte hemen yayina girer`() = testApplication {
        val client = setup()
        val (uploader, uploaderId) = client.newUser(reputation = 60)
        val (solver, _) = client.newUser()

        val created = client.upload(uploader, request(), image = png()).body<UploadQuestionResponse>()
        assertEquals("ACTIVE", created.status)
        assertEquals(4, created.difficultyBucket, "4 puanlık soru 4. kovadan başlamalı")

        val question = client.get("/questions/${created.id}") { bearerAuth(solver) }.body<QuestionDto>()
        val imageUrl = assertNotNull(question.imageUrl)
        val image = client.get(imageUrl)
        assertEquals(HttpStatusCode.OK, image.status)
        assertEquals(ContentType.Image.JPEG, image.contentType()?.withoutParameters())

        val reputation = dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT reputation FROM users WHERE id = ?").use { st ->
                st.setObject(1, uploaderId); st.executeQuery().use { it.next(); it.getInt(1) }
            }
        }
        assertEquals(65, reputation, "doğrudan yayın +5 itibar vermeli")
    }

    @Test
    fun `ayni soru bicim farkiyla tekrar yuklenemez`() = testApplication {
        val client = setup()
        val (uploader, _) = client.newUser()
        val (other, _) = client.newUser()
        val body = "Kopya testi ${UUID.randomUUID()}: Tanzimat Fermanı hangi yıl ilan edilmiştir?"

        assertEquals(HttpStatusCode.Created, client.upload(uploader, request(body)).status)
        val duplicate = client.upload(other, request("  " + body.uppercase().replace(" ", "   ") + "  "))

        assertEquals(HttpStatusCode.Conflict, duplicate.status)
    }

    @Test
    fun `hatali yuklemeler reddedilir ve gorsel diskte kalmaz`() = testApplication {
        val client = setup()
        val (token, _) = client.newUser()
        val filesBefore = Files.list(imageDir).count()

        assertEquals(HttpStatusCode.BadRequest, client.upload(token, request().copy(originalityConfirmed = false), png()).status)
        assertEquals(HttpStatusCode.BadRequest, client.upload(token, request().copy(topicId = "konu_yok"), png()).status)
        assertEquals(HttpStatusCode.BadRequest, client.upload(token, request().copy(solutionText = "ÖSYM 2022 sorusu, cevap A")).status)
        assertEquals(HttpStatusCode.BadRequest, client.upload(token, request(), image = "resim değil".toByteArray()).status)

        val noJson = client.submitFormWithBinaryData("/questions", formData { append("question", "{bozuk") }) { bearerAuth(token) }
        assertEquals(HttpStatusCode.BadRequest, noJson.status)
        val notMultipart = client.post("/questions") {
            bearerAuth(token); contentType(ContentType.Application.Json); setBody(request())
        }
        assertEquals(HttpStatusCode.BadRequest, notMultipart.status)

        assertEquals(filesBefore, Files.list(imageDir).count(), "reddedilen yüklemenin görseli silinmeli")
        assertEquals(HttpStatusCode.Unauthorized, client.post("/questions").status)
    }

    @Test
    fun `saatlik yukleme siniri asilinca 429 doner`() = testApplication {
        val client = setup(uploadsPerHour = 2)
        val (token, _) = client.newUser()

        repeat(2) { assertEquals(HttpStatusCode.Created, client.upload(token, request()).status) }
        assertEquals(HttpStatusCode.TooManyRequests, client.upload(token, request()).status)
    }

    @Test
    fun `abartili puan veren yukleyenin yeni sorusu daha dusuk zorlukla baslar`() = testApplication {
        val client = setup()
        val (token, userId) = client.newUser()
        dataSource.connection.use { conn ->
            conn.prepareStatement("UPDATE users SET uploader_bias = 1.5 WHERE id = ?").use { st ->
                st.setObject(1, userId); st.executeUpdate()
            }
        }

        val created = client.upload(token, request().copy(uploaderRating = 8)).body<UploadQuestionResponse>()

        assertTrue(created.difficultyBucket < 8, "yanlılık 8 puanı aşağı çekmeli, gelen: ${created.difficultyBucket}")
    }

    @Test
    fun `baslangic sorularinin ozetleri gercek icerikten hesaplanmis`() = testApplication {
        val client = setup()
        val (token, _) = client.newUser(reputation = 60)
        val seedBody = dataSource.connection.use { conn ->
            conn.createStatement().use { st ->
                st.executeQuery(
                    "SELECT v.body_text FROM questions q JOIN question_versions v ON v.id = q.current_version_id WHERE q.topic_id = 'top_sayilar' LIMIT 1"
                ).use { it.next(); it.getString(1) }
            }
        }

        val response = client.upload(token, request(seedBody).copy(topicId = "top_sayilar"))

        assertEquals(HttpStatusCode.Conflict, response.status)
    }
}
