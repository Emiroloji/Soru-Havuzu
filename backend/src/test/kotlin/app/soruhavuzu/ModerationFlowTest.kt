package app.soruhavuzu

import app.soruhavuzu.auth.AuthResponse
import app.soruhavuzu.auth.RegisterRequest
import app.soruhavuzu.moderation.ModerationItemDto
import app.soruhavuzu.moderation.ModerationStore
import app.soruhavuzu.moderation.NewVersionRequest
import app.soruhavuzu.moderation.NewVersionResponse
import app.soruhavuzu.moderation.RemoveRequest
import app.soruhavuzu.moderation.ReportRequest
import app.soruhavuzu.moderation.ReportResponse
import app.soruhavuzu.questions.AttemptRequest
import app.soruhavuzu.questions.ImageStorage
import app.soruhavuzu.questions.PostgresQuestionStore
import app.soruhavuzu.questions.UploadQuestionRequest
import app.soruhavuzu.questions.UploadQuestionResponse
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
import kotlinx.serialization.json.Json
import org.testcontainers.junit.jupiter.Testcontainers
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.UUID
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Testcontainers(disabledWithoutDocker = true)
class ModerationFlowTest {

    private val dataSource = TestDatabase.dataSource
    private val imageDir = Files.createTempDirectory("moderation-test")

    private class TestUser(val token: String, val id: UUID)

    private fun ApplicationTestBuilder.setup(reportsPerHour: Int = 1000): HttpClient {
        application {
            module(
                testServices(
                    users = PostgresUserStore(dataSource),
                    questions = PostgresQuestionStore(dataSource),
                    images = ImageStorage(imageDir),
                    moderation = ModerationStore(dataSource),
                    reportsPerHour = reportsPerHour
                )
            )
        }
        return jsonClient()
    }

    private suspend fun HttpClient.user(role: String = "USER", reputation: Int = 0): TestUser {
        val tag = UUID.randomUUID().toString().take(8)
        val auth = post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("m$tag@ornek.com", "guclu-sifre-123", "m_$tag"))
        }.body<AuthResponse>()
        val id = UUID.fromString(auth.user.id)
        sql("UPDATE users SET role = ?, reputation = ? WHERE id = ?", role, reputation, id)
        return TestUser(auth.token, id)
    }

    private fun sql(statement: String, vararg params: Any) = dataSource.connection.use { conn ->
        conn.prepareStatement(statement).use { st ->
            params.forEachIndexed { i, p -> st.setObject(i + 1, p) }
            st.execute()
        }
    }

    private fun <T> query(statement: String, vararg params: Any, read: (java.sql.ResultSet) -> T): T =
        dataSource.connection.use { conn ->
            conn.prepareStatement(statement).use { st ->
                params.forEachIndexed { i, p -> st.setObject(i + 1, p) }
                st.executeQuery().use { rs -> rs.next(); read(rs) }
            }
        }

    private fun reputationOf(user: TestUser) = query("SELECT reputation FROM users WHERE id = ?", user.id) { it.getInt(1) }

    private suspend fun HttpClient.upload(uploader: TestUser, withImage: Boolean = false): String {
        val req = UploadQuestionRequest(
            topicId = "kpss_cog_iklim",
            bodyText = "Moderasyon testi ${UUID.randomUUID()}: Türkiye'de en fazla yağış alan bölge hangisidir?",
            options = listOf("Karadeniz", "İç Anadolu", "Güneydoğu Anadolu"),
            correctOptionIndex = 0,
            uploaderRating = 3,
            solutionText = "Karadeniz Bölgesi, nemli hava kütlelerinin dağlara çarpmasıyla en çok yağışı alır.",
            originalityConfirmed = true
        )
        val image = if (withImage) {
            ByteArrayOutputStream().also { ImageIO.write(BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB), "png", it) }.toByteArray()
        } else null
        return submitFormWithBinaryData("/questions", formData {
            append("question", Json.encodeToString(UploadQuestionRequest.serializer(), req), Headers.build {
                append(HttpHeaders.ContentType, "application/json")
            })
            if (image != null) append("image", image, Headers.build {
                append(HttpHeaders.ContentType, "image/png")
                append(HttpHeaders.ContentDisposition, "filename=\"a.png\"")
            })
        }) { bearerAuth(uploader.token) }.body<UploadQuestionResponse>().id
    }

    private suspend fun HttpClient.report(user: TestUser, questionId: String, reason: String = "WRONG_KEY"): HttpResponse =
        post("/questions/$questionId/reports") {
            bearerAuth(user.token)
            contentType(ContentType.Application.Json)
            setBody(ReportRequest(reason, "test notu"))
        }

    private suspend fun HttpClient.moderate(mod: TestUser, path: String, body: Any? = null): HttpResponse =
        post("/moderation/questions/$path") {
            bearerAuth(mod.token)
            if (body != null) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }

    private suspend fun HttpClient.queue(mod: TestUser) =
        get("/moderation/queue") { bearerAuth(mod.token) }.body<List<ModerationItemDto>>()

    @Test
    fun `kuyrugu yalnizca moderatorler ve 1000 itibarlilar gorur`() = testApplication {
        val client = setup()

        assertEquals(HttpStatusCode.Forbidden, client.get("/moderation/queue") { bearerAuth(client.user(reputation = 999).token) }.status)
        assertEquals(HttpStatusCode.OK, client.get("/moderation/queue") { bearerAuth(client.user(reputation = 1000).token) }.status)
        assertEquals(HttpStatusCode.OK, client.get("/moderation/queue") { bearerAuth(client.user(role = "MODERATOR").token) }.status)
    }

    @Test
    fun `incelemedeki soru onaylaninca havuza girer ve yukleyen itibar kazanir`() = testApplication {
        val client = setup()
        val uploader = client.user()
        val mod = client.user(role = "MODERATOR")
        val solver = client.user()
        val questionId = client.upload(uploader)

        val item = client.queue(mod).single { it.id == questionId }
        assertEquals("IN_REVIEW", item.status)
        assertEquals(0, item.correctOptionIndex, "moderatör doğru cevabı görmeli")
        assertEquals(HttpStatusCode.NotFound, client.get("/questions/$questionId") { bearerAuth(solver.token) }.status)

        assertEquals(HttpStatusCode.OK, client.moderate(mod, "$questionId/approve").status)

        assertEquals(HttpStatusCode.OK, client.get("/questions/$questionId") { bearerAuth(solver.token) }.status)
        assertEquals(5, reputationOf(uploader))
        val history = client.get("/me/reputation") { bearerAuth(uploader.token) }.body<List<app.soruhavuzu.auth.ReputationEventDto>>()
        assertEquals(5, history.single().delta)
        assertFalse(client.queue(mod).any { it.id == questionId })
    }

    @Test
    fun `uc sikayet soruyu havuzdan ceker, ayni kisi iki kez sikayet edemez`() = testApplication {
        val client = setup()
        val question = client.upload(client.user(reputation = 60))
        val (a, b, c) = List(3) { client.user() }

        assertFalse(client.report(a, question).body<ReportResponse>().flagged)
        assertEquals(HttpStatusCode.Conflict, client.report(a, question).status)
        assertFalse(client.report(b, question).body<ReportResponse>().flagged)
        assertTrue(client.report(c, question).body<ReportResponse>().flagged)

        assertEquals(HttpStatusCode.NotFound, client.get("/questions/$question") { bearerAuth(a.token) }.status)
        val item = client.queue(client.user(role = "MODERATOR")).single { it.id == question }
        assertEquals("FLAGGED", item.status)
        assertEquals(3, item.reports.size)
    }

    @Test
    fun `dogrulayici sikayeti iki kat sayilir, telif sikayeti tek basina yeter`() = testApplication {
        val client = setup()
        val uploader = client.user(reputation = 60)

        val q1 = client.upload(uploader)
        assertFalse(client.report(client.user(reputation = 200), q1).body<ReportResponse>().flagged)
        assertTrue(client.report(client.user(), q1).body<ReportResponse>().flagged, "2 + 1 = 3 eşiği aşmalı")

        val q2 = client.upload(uploader)
        assertTrue(client.report(client.user(), q2, reason = "COPYRIGHT_OSYM").body<ReportResponse>().flagged)
        assertEquals(q2, client.queue(client.user(role = "MODERATOR")).first().id, "telif şikayeti kuyruğun başında olmalı")

        assertEquals(HttpStatusCode.BadRequest, client.report(client.user(), q2, reason = "SACMA").status)
    }

    @Test
    fun `kaldirma yukleyeni cezalandirir, sikayetcileri odullendirir ve gorseli siler`() = testApplication {
        val client = setup()
        val uploader = client.user(reputation = 60)
        val question = client.upload(uploader, withImage = true)
        val reporter = client.user()
        client.report(reporter, question)
        val mod = client.user(role = "MODERATOR")
        val imageFile = query("SELECT image_path FROM question_versions WHERE question_id = ?", UUID.fromString(question)) { it.getString(1) }
        assertTrue(Files.exists(imageDir.resolve(imageFile)))

        assertEquals(HttpStatusCode.OK, client.moderate(mod, "$question/remove", RemoveRequest("Bilgi hatası")).status)

        assertEquals(60 + 5 - 15, reputationOf(uploader))
        assertEquals(2, reputationOf(reporter))
        assertFalse(Files.exists(imageDir.resolve(imageFile)), "kaldırılan sorunun görseli silinmeli")
        assertEquals("RESOLVED_REMOVED", query("SELECT status FROM reports WHERE question_id = ?", UUID.fromString(question)) { it.getString(1) })
        assertEquals(HttpStatusCode.NotFound, client.moderate(mod, "$question/remove", RemoveRequest("tekrar")).status)
    }

    @Test
    fun `anahtar duzeltilince kalibrasyon sifirlanir, sadece metin duzeltilince korunur`() = testApplication {
        val client = setup()
        val question = client.upload(client.user(reputation = 60))
        val qid = UUID.fromString(question)
        val solver = client.user()
        client.post("/questions/$question/attempts") {
            bearerAuth(solver.token); contentType(ContentType.Application.Json); setBody(AttemptRequest(1))
        }
        client.report(solver, question)
        val mod = client.user(role = "MODERATOR")

        val typoFix = client.moderate(
            mod, "$question/versions",
            NewVersionRequest(
                bodyText = "Düzeltilmiş metin ${UUID.randomUUID()}: Türkiye'de en fazla yağış alan bölge hangisidir?",
                options = listOf("Karadeniz", "İç Anadolu", "Güneydoğu Anadolu"),
                correctOptionIndex = 0,
                solutionText = "Karadeniz Bölgesi, nemli hava kütlelerinin dağlara çarpmasıyla en çok yağışı alır.",
                editorNote = "Yazım düzeltmesi"
            )
        ).body<NewVersionResponse>()
        assertEquals(2, typoFix.versionNumber)
        assertFalse(typoFix.calibrationReset)
        assertEquals(1, query("SELECT first_attempt_count FROM questions WHERE id = ?", qid) { it.getInt(1) })
        assertEquals(2, reputationOf(solver), "haklı şikayet +2 itibar")

        sql("UPDATE questions SET key_suspect = true, difficulty_logit = 2.5, difficulty_bucket = 10 WHERE id = ?", qid)
        val keyFix = client.moderate(
            mod, "$question/versions",
            NewVersionRequest(
                bodyText = "Anahtarı düzeltilmiş ${UUID.randomUUID()}: Türkiye'de en fazla yağış alan bölge hangisidir?",
                options = listOf("İç Anadolu", "Karadeniz", "Güneydoğu Anadolu"),
                correctOptionIndex = 1,
                solutionText = "Karadeniz Bölgesi, nemli hava kütlelerinin dağlara çarpmasıyla en çok yağışı alır.",
                editorNote = "Şık sırası ve anahtar düzeltildi"
            )
        ).body<NewVersionResponse>()
        assertTrue(keyFix.calibrationReset)

        val (firstCount, bucket, suspect) = query(
            "SELECT first_attempt_count, difficulty_bucket, key_suspect FROM questions WHERE id = ?", qid
        ) { Triple(it.getInt(1), it.getInt(2), it.getBoolean(3)) }
        assertEquals(0, firstCount)
        assertEquals(3, bucket, "zorluk yükleyenin 3 puanından yeniden başlamalı")
        assertFalse(suspect)
        val counts = query(
            "SELECT v.option_selection_counts FROM questions q JOIN question_versions v ON v.id = q.current_version_id WHERE q.id = ?", qid
        ) { (it.getArray(1).array as Array<*>).toList() }
        assertEquals(listOf(0, 0, 0), counts, "yeni versiyonun şık sayıları sıfırdan başlamalı")
    }

    @Test
    fun `anahtar duzeltilince eski anahtarla cozenlerin seviyesi geri verilir, ikinci duzeltmede tekrar verilmez`() = testApplication {
        val client = setup()
        val question = client.upload(client.user(reputation = 60)) // anahtar: 0 (Karadeniz)
        val solver = client.user()
        fun theta() = query("SELECT theta FROM users WHERE id = ?", solver.id) { it.getDouble(1) }

        // Çözen 1. şıkkı seçiyor; eski anahtara göre yanlış sayılıp seviyesi düşüyor
        client.post("/questions/$question/attempts") {
            bearerAuth(solver.token); contentType(ContentType.Application.Json); setBody(AttemptRequest(1))
        }
        assertTrue(theta() < 0.0)
        val mod = client.user(role = "MODERATOR")

        fun fix(correct: Int) = NewVersionRequest(
            bodyText = "Anahtar testi ${UUID.randomUUID()}: Türkiye'de en fazla yağış alan bölge hangisidir?",
            options = listOf("Karadeniz", "İç Anadolu", "Güneydoğu Anadolu"),
            correctOptionIndex = correct,
            solutionText = "Karadeniz Bölgesi, nemli hava kütlelerinin dağlara çarpmasıyla en çok yağışı alır.",
            editorNote = "Anahtar düzeltildi"
        )
        val first = client.moderate(mod, "$question/versions", fix(correct = 1)).body<NewVersionResponse>()
        assertEquals(1, first.correctedUsers)
        assertEquals(0.0, theta(), 1e-9)
        val stats = client.get("/me/stats") { bearerAuth(solver.token) }.body<app.soruhavuzu.questions.UserStatsDto>()
        assertEquals(0, stats.solvedCount, "geçersiz deneme istatistikte sayılmamalı")
        val topics = client.get("/me/topics") { bearerAuth(solver.token) }.body<List<app.soruhavuzu.questions.TopicMasteryDto>>()
        assertEquals(0, topics.single().solvedCount)

        val second = client.moderate(mod, "$question/versions", fix(correct = 2)).body<NewVersionResponse>()
        assertEquals(0, second.correctedUsers, "aynı deneme iki kez geri alınmamalı")
        assertEquals(0.0, theta(), 1e-9)
    }

    @Test
    fun `saatlik sikayet siniri asilinca 429 doner`() = testApplication {
        val client = setup(reportsPerHour = 2)
        val uploader = client.user(reputation = 60)
        val reporter = client.user()
        val questions = List(3) { client.upload(uploader) }

        assertEquals(HttpStatusCode.Created, client.report(reporter, questions[0]).status)
        assertEquals(HttpStatusCode.Created, client.report(reporter, questions[1]).status)
        assertEquals(HttpStatusCode.TooManyRequests, client.report(reporter, questions[2]).status)
    }

    @Test
    fun `moderator kendi sorusunu onaylayamaz ve kaldiramaz`() = testApplication {
        val client = setup()
        val mod = client.user(role = "MODERATOR")
        sql("UPDATE users SET role = 'USER' WHERE id = ?", mod.id)
        val question = client.upload(mod) // USER iken yükledi, incelemeye düştü
        sql("UPDATE users SET role = 'MODERATOR' WHERE id = ?", mod.id)

        assertEquals(HttpStatusCode.Forbidden, client.moderate(mod, "$question/approve").status)
        assertEquals(HttpStatusCode.Forbidden, client.moderate(mod, "$question/remove", RemoveRequest("deneme")).status)
    }
}
