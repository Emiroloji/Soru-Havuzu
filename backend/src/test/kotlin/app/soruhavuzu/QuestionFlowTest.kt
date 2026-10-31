package app.soruhavuzu

import app.soruhavuzu.auth.AuthResponse
import app.soruhavuzu.auth.RegisterRequest
import app.soruhavuzu.questions.AttemptRequest
import app.soruhavuzu.questions.AttemptResponse
import app.soruhavuzu.questions.CatalogExamDto
import app.soruhavuzu.questions.NextQuestionResponse
import app.soruhavuzu.questions.PostgresQuestionStore
import app.soruhavuzu.questions.QuestionDto
import app.soruhavuzu.users.PostgresUserStore
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
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Uçtan uca soru çözme akışı, gerçek Postgres ve V3'teki başlangıç sorularıyla.
 * Her test kendi kullanıcısını açar; soruların zorluğu testler arasında değişebileceği
 * için kesin değerler yerine yönler kontrol edilir.
 */
@Testcontainers(disabledWithoutDocker = true)
class QuestionFlowTest {

    private val dataSource = TestDatabase.dataSource

    private fun ApplicationTestBuilder.setup(): HttpClient {
        application {
            module(testServices(users = PostgresUserStore(dataSource), questions = PostgresQuestionStore(dataSource)))
        }
        return jsonClient()
    }

    private suspend fun HttpClient.newUser(): String {
        val tag = UUID.randomUUID().toString().take(8)
        return post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("u$tag@ornek.com", "guclu-sifre-123", "u_$tag"))
        }.body<AuthResponse>().token
    }

    private suspend fun HttpClient.next(token: String, query: String): NextQuestionResponse =
        get("/questions/next?$query") { bearerAuth(token) }.body()

    private suspend fun HttpClient.answer(token: String, questionId: String, option: Int): HttpResponse =
        post("/questions/$questionId/attempts") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(AttemptRequest(selectedOptionIndex = option, responseTimeMs = 12_000))
        }

    private fun correctIndexOf(questionId: String): Int = dataSource.connection.use { conn ->
        conn.prepareStatement(
            "SELECT v.correct_option_index FROM questions q JOIN question_versions v ON v.id = q.current_version_id WHERE q.id = ?"
        ).use { st ->
            st.setObject(1, UUID.fromString(questionId))
            st.executeQuery().use { rs -> rs.next(); rs.getInt(1) }
        }
    }

    @Test
    fun `katalog KPSS derslerini ve TYT soru sayilarini doner`() = testApplication {
        val client = setup()

        val catalog = client.get("/catalog").body<List<CatalogExamDto>>()

        val kpss = catalog.first { it.code == "KPSS" }
        assertEquals(6, kpss.subjects.size)
        val tytQuestions = catalog.first { it.code == "TYT" }.subjects.flatMap { it.topics }.sumOf { it.activeQuestionCount }
        assertEquals(12, tytQuestions)
    }

    @Test
    fun `istenen zorlukta soru gelir ve dogru cevap icinde yoktur`() = testApplication {
        val client = setup()
        val token = client.newUser()

        val raw = client.get("/questions/next?examId=exam_tyt&minBucket=6&maxBucket=8") { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, raw.status)
        assertFalse(raw.bodyAsText().contains("correct"), "doğru cevap istemciye gitmemeli")

        val question = assertNotNull(raw.body<NextQuestionResponse>().question)
        assertTrue(question.difficultyBucket in 6..8)
        assertFalse(question.alreadySolved)
        assertTrue(question.options.size in 2..5)
    }

    @Test
    fun `ilk deneme seviyeyi ve zorlugu gunceller, tekrar deneme degistirmez`() = testApplication {
        val client = setup()
        val token = client.newUser()
        val question = assertNotNull(client.next(token, "examId=exam_tyt&minBucket=5&maxBucket=8").question)
        val correct = correctIndexOf(question.id)

        val first = client.answer(token, question.id, correct).body<AttemptResponse>()
        assertTrue(first.isCorrect)
        assertTrue(first.wasFirstAttempt)
        assertEquals(correct, first.correctOptionIndex)
        assertTrue(first.solutionText.isNotBlank())
        assertTrue(first.newLevel > first.oldLevel, "doğru cevap seviyeyi artırmalı")

        val again = client.answer(token, question.id, (correct + 1) % question.options.size).body<AttemptResponse>()
        assertFalse(again.isCorrect)
        assertFalse(again.wasFirstAttempt)
        assertEquals(again.oldLevel, again.newLevel, "tekrar deneme seviyeyi değiştirmemeli")
        assertEquals(again.oldDifficultyBucket, again.newDifficultyBucket)

        val solved = client.get("/questions/${question.id}") { bearerAuth(token) }.body<QuestionDto>()
        assertTrue(solved.alreadySolved)
    }

    @Test
    fun `yanlis cevap yanlislarim listesine ve konu seviyesine yazilir`() = testApplication {
        val client = setup()
        val token = client.newUser()
        val userId = UUID.fromString(client.get("/me") { bearerAuth(token) }.body<app.soruhavuzu.auth.UserDto>().id)
        // Kolay soru: yanlış cevabın seviyeyi görünür biçimde düşürmesi için (zor soruda düşüş 0.1'in altında kalır)
        val question = assertNotNull(client.next(token, "examId=exam_tyt&maxBucket=3").question)
        val wrong = (correctIndexOf(question.id) + 1) % question.options.size

        val result = client.answer(token, question.id, wrong).body<AttemptResponse>()
        assertFalse(result.isCorrect)
        assertTrue(result.newLevel < result.oldLevel)

        val (wrongNote, solvedInTopic) = dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                SELECT b.is_wrong_note, m.solved_count
                FROM bookmarks b JOIN topic_mastery m ON m.user_id = b.user_id AND m.topic_id = ?
                WHERE b.question_id = ? AND b.user_id = ?
                """.trimIndent()
            ).use { st ->
                st.setString(1, question.topicId)
                st.setObject(2, UUID.fromString(question.id))
                st.setObject(3, userId)
                st.executeQuery().use { rs -> rs.next(); rs.getBoolean(1) to rs.getInt(2) }
            }
        }
        assertTrue(wrongNote)
        assertEquals(1, solvedInTopic)
    }

    @Test
    fun `butun sorular cozulunce soru ve oneri gelmez, includeSolved ile gelir`() = testApplication {
        val client = setup()
        val token = client.newUser()

        // TYT'deki bütün soruları çöz
        while (true) {
            val q = client.next(token, "examId=exam_tyt").question ?: break
            client.answer(token, q.id, 0)
        }

        val empty = client.next(token, "examId=exam_tyt&bucket=4")
        assertNull(empty.question)
        assertNull(empty.suggestedRange, "hiç çözülmemiş soru kalmadıysa öneri olmamalı")

        val withSolved = client.next(token, "examId=exam_tyt&includeSolved=true")
        assertNotNull(withSolved.question)
    }

    @Test
    fun `secilen kova bossa komsu aralik onerilir`() = testApplication {
        val client = setup()
        val token = client.newUser()

        // KPSS'de henüz soru yok, TYT'de var: konu filtresiz TYT, olmayan bir kova seçimi
        val bucketCounts = dataSource.connection.use { conn ->
            conn.createStatement().use { st ->
                st.executeQuery(
                    """
                    SELECT q.difficulty_bucket FROM questions q JOIN topics t ON t.id = q.topic_id
                    JOIN subjects s ON s.id = t.subject_id WHERE q.status = 'ACTIVE' AND s.exam_id = 'exam_tyt'
                    """.trimIndent()
                ).use { rs ->
                    generateSequence { if (rs.next()) rs.getInt(1) else null }.toSet()
                }
            }
        }
        val emptyBucket = (1..10).firstOrNull { it !in bucketCounts && (it - 1 in bucketCounts || it + 1 in bucketCounts) }
            ?: return@testApplication // tüm kovalar dolu: bu senaryo bu veriyle kurulamıyor

        val result = client.next(token, "examId=exam_tyt&bucket=$emptyBucket")
        assertNull(result.question)
        val range = assertNotNull(result.suggestedRange)
        assertEquals((emptyBucket - 1).coerceAtLeast(1), range.min)
        assertEquals((emptyBucket + 1).coerceAtMost(10), range.max)
    }

    @Test
    fun `beni zorla modu soru dondurur`() = testApplication {
        val client = setup()
        val token = client.newUser()

        assertNotNull(client.next(token, "mode=CHALLENGE&examId=exam_tyt").question)
    }

    @Test
    fun `ayni soruya ayni anda gelen iki cevaptan yalnizca biri ilk deneme sayilir`() = testApplication {
        val client = setup()
        val token = client.newUser()
        val question = assertNotNull(client.next(token, "examId=exam_tyt").question)

        val results = coroutineScope {
            (1..4).map { async { client.answer(token, question.id, 0).body<AttemptResponse>() } }.awaitAll()
        }

        assertEquals(1, results.count { it.wasFirstAttempt })
    }

    @Test
    fun `hatali istekler dogru durum kodlarini doner`() = testApplication {
        val client = setup()
        val token = client.newUser()
        val question = assertNotNull(client.next(token, "examId=exam_tyt").question)

        assertEquals(HttpStatusCode.BadRequest, client.answer(token, question.id, 9).status)
        assertEquals(HttpStatusCode.NotFound, client.answer(token, UUID.randomUUID().toString(), 0).status)
        assertEquals(HttpStatusCode.NotFound, client.answer(token, "uuid-degil", 0).status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/questions/next?bucket=11") { bearerAuth(token) }.status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/questions/next?mode=BASKA") { bearerAuth(token) }.status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/questions/next").status)
    }

    @Test
    fun `kullanici kendi yukledigi soruyu cozemez ve kendi sorusu ona gelmez`() = testApplication {
        val client = setup()
        val token = client.newUser()
        val me = client.get("/me") { bearerAuth(token) }.body<app.soruhavuzu.auth.UserDto>()

        val ownQuestionId = UUID.randomUUID()
        dataSource.connection.use { conn ->
            conn.autoCommit = false
            conn.prepareStatement(
                """
                INSERT INTO questions (id, uploader_id, topic_id, status, uploader_rating, difficulty_logit, difficulty_bucket, content_hash)
                VALUES (?, ?, 'kpss_gun_genel', 'ACTIVE', 5, 0.0, 6, 'test');
                """.trimIndent()
            ).use { st -> st.setObject(1, ownQuestionId); st.setObject(2, UUID.fromString(me.id)); st.executeUpdate() }
            conn.prepareStatement(
                """
                INSERT INTO question_versions (id, question_id, version_number, body_text, options, correct_option_index,
                                               option_selection_counts, solution_text, created_by)
                VALUES (?, ?, 1, 'Test sorusu', ARRAY['a','b'], 0, ARRAY[0,0], 'çözüm', ?)
                """.trimIndent()
            ).use { st ->
                val versionId = UUID.randomUUID()
                st.setObject(1, versionId); st.setObject(2, ownQuestionId); st.setObject(3, UUID.fromString(me.id)); st.executeUpdate()
                conn.prepareStatement("UPDATE questions SET current_version_id = ? WHERE id = ?").use { up ->
                    up.setObject(1, versionId); up.setObject(2, ownQuestionId); up.executeUpdate()
                }
            }
            conn.commit()
        }

        assertEquals(HttpStatusCode.Forbidden, client.answer(token, ownQuestionId.toString(), 0).status)
        assertNull(client.next(token, "topicId=kpss_gun_genel").question)
    }
}
