package app.soruhavuzu.questions

import app.soruhavuzu.NotFoundException
import app.soruhavuzu.ValidationException
import app.soruhavuzu.auth.AUTH_JWT
import app.soruhavuzu.auth.AuthUser
import app.soruhavuzu.domain.CalibrationEngine
import app.soruhavuzu.validate
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import java.util.UUID
import kotlin.math.roundToInt

@Serializable
data class CatalogTopicDto(val id: String, val name: String, val activeQuestionCount: Int)

@Serializable
data class CatalogSubjectDto(val id: String, val name: String, val topics: List<CatalogTopicDto>)

@Serializable
data class CatalogExamDto(val id: String, val code: String, val title: String, val subjects: List<CatalogSubjectDto>)

@Serializable
data class QuestionDto(
    val id: String,
    val examId: String,
    val subjectId: String,
    val subjectName: String,
    val topicId: String,
    val topicName: String,
    val bodyText: String,
    /** Sunucu köküne göre görsel adresi, ör. "/images/q_....jpg". */
    val imageUrl: String?,
    val options: List<String>,
    val difficultyBucket: Int,
    val firstAttemptCount: Int,
    val alreadySolved: Boolean
)

@Serializable
data class BucketRangeDto(val min: Int, val max: Int)

@Serializable
data class NextQuestionResponse(
    val question: QuestionDto?,
    /** Soru bulunamadıysa denenebilecek daha geniş zorluk aralığı. */
    val suggestedRange: BucketRangeDto? = null
)

@Serializable
data class AttemptRequest(
    val selectedOptionIndex: Int,
    val responseTimeMs: Long = 0,
    val sessionType: String = SessionType.PRACTICE.name
)

@Serializable
data class AttemptResponse(
    val isCorrect: Boolean,
    val correctOptionIndex: Int,
    val solutionText: String,
    val wasFirstAttempt: Boolean,
    val expectedProbabilityPercent: Int,
    val oldDifficultyBucket: Int,
    val newDifficultyBucket: Int,
    /** Kullanıcının 1..10 ölçeğindeki seviyesi (θ'dan çevrilir). */
    val oldLevel: Double,
    val newLevel: Double
)

fun QuestionForSolving.toDto() = QuestionDto(
    id.toString(), examId, subjectId, subjectName, topicId, topicName,
    bodyText, imagePath?.let { "/images/$it" }, options, difficultyBucket, firstAttemptCount, alreadySolved
)

fun Route.questionRoutes(questions: QuestionStore) {
    get("/catalog") {
        call.respond(questions.catalog().map { e ->
            CatalogExamDto(e.id, e.code, e.title, e.subjects.map { s ->
                CatalogSubjectDto(s.id, s.name, s.topics.map { CatalogTopicDto(it.id, it.name, it.activeQuestionCount) })
            })
        })
    }

    authenticate(AUTH_JWT) {
        route("/questions") {
            /**
             * Sıradaki soru. Örnek: /questions/next?subjectId=kpss_tarih&bucket=4
             * mode=PRACTICE (varsayılan): bucket ya da minBucket/maxBucket aralığından rastgele, çözülmemiş soru.
             * mode=CHALLENGE: seviyene göre ~%60 başarı ihtimalli soru.
             */
            get("/next") {
                val userId = call.userId()
                val params = call.request.queryParameters
                val filter = QuestionFilter(
                    examId = params["examId"],
                    subjectId = params["subjectId"],
                    topicId = params["topicId"]
                )
                val mode = params["mode"]?.uppercase() ?: SessionType.PRACTICE.name

                when (mode) {
                    SessionType.PRACTICE.name -> {
                        val bucket = params.int("bucket")
                        val min = bucket ?: params.int("minBucket") ?: 1
                        val max = bucket ?: params.int("maxBucket") ?: 10
                        validate(min in 1..10 && max in 1..10 && min <= max) { "Zorluk 1 ile 10 arasında olmalı" }

                        val result = questions.pickPractice(userId, filter, min..max, params["includeSolved"] == "true")
                        call.respond(
                            NextQuestionResponse(
                                question = result.question?.toDto(),
                                suggestedRange = result.suggestedRange?.let { BucketRangeDto(it.first, it.last) }
                            )
                        )
                    }
                    SessionType.CHALLENGE.name ->
                        call.respond(NextQuestionResponse(questions.pickChallenge(userId, filter)?.toDto()))
                    else -> throw ValidationException("mode PRACTICE veya CHALLENGE olmalı")
                }
            }

            get("/{id}") {
                val question = questions.findForSolving(call.questionId(), call.userId())
                    ?: throw NotFoundException("Soru bulunamadı")
                call.respond(question.toDto())
            }

            post("/{id}/attempts") {
                val req = call.receive<AttemptRequest>()
                val sessionType = SessionType.entries.firstOrNull { it.name == req.sessionType.uppercase() }
                    ?: throw ValidationException("sessionType PRACTICE, CHALLENGE veya REVIEW olmalı")

                val result = questions.submitAttempt(
                    userId = call.userId(),
                    questionId = call.questionId(),
                    selectedOptionIndex = req.selectedOptionIndex,
                    responseTimeMs = req.responseTimeMs,
                    sessionType = sessionType
                )
                call.respond(
                    AttemptResponse(
                        isCorrect = result.isCorrect,
                        correctOptionIndex = result.correctOptionIndex,
                        solutionText = result.solutionText,
                        wasFirstAttempt = result.wasFirstAttempt,
                        expectedProbabilityPercent = (result.expectedProbability * 100).roundToInt(),
                        oldDifficultyBucket = result.oldBucket,
                        newDifficultyBucket = result.newBucket,
                        oldLevel = CalibrationEngine.thetaToLevel(result.oldTheta),
                        newLevel = CalibrationEngine.thetaToLevel(result.newTheta)
                    )
                )
            }
        }
    }
}

private fun RoutingCall.userId(): UUID = principal<AuthUser>()!!.id

private fun RoutingCall.questionId(): UUID =
    parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        ?: throw NotFoundException("Soru bulunamadı")

private fun io.ktor.http.Parameters.int(name: String): Int? =
    this[name]?.let { it.toIntOrNull() ?: throw ValidationException("$name sayı olmalı") }
