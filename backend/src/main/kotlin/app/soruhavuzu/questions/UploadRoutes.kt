package app.soruhavuzu.questions

import app.soruhavuzu.ErrorResponse
import app.soruhavuzu.NotFoundException
import app.soruhavuzu.ValidationException
import app.soruhavuzu.auth.AUTH_JWT
import app.soruhavuzu.auth.AuthUser
import app.soruhavuzu.validate
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

@Serializable
data class UploadQuestionRequest(
    val topicId: String,
    val bodyText: String,
    val options: List<String>,
    val correctOptionIndex: Int,
    /** Yükleyenin 1..10 zorluk tahmini; sadece başlangıç değeri, çözümlerle kalibre edilir. */
    val uploaderRating: Int,
    val solutionText: String,
    val originalityConfirmed: Boolean = false
)

@Serializable
data class UploadQuestionResponse(
    val id: String,
    /** ACTIVE: hemen yayında. IN_REVIEW: inceleme kuyruğunda. */
    val status: String,
    val difficultyBucket: Int,
    val message: String
)

@Serializable
data class UploadedQuestionDto(
    val id: String,
    val topicName: String,
    val bodyPreview: String,
    val status: String,
    val uploaderRating: Int,
    val difficultyBucket: Int,
    val firstAttemptCount: Int,
    val accuracyPercent: Int?,
    val keySuspect: Boolean,
    val createdAt: String
)

private val json = Json { ignoreUnknownKeys = true }

/**
 * POST /questions: multipart istek.
 *  - "question" alanı: UploadQuestionRequest JSON'u
 *  - "image" alanı (isteğe bağlı): JPEG veya PNG, en fazla 5 MB
 */
fun Route.uploadRoutes(questions: QuestionStore, images: ImageStorage, maxUploadsPerHour: Int) {
    get("/images/{name}") {
        val file = call.parameters["name"]?.let { images.resolve(it) } ?: throw NotFoundException("Görsel bulunamadı")
        // Dosya adları rastgele ve içerik hiç değişmiyor: tarayıcı/uygulama sonsuza kadar önbelleğe alabilir
        call.response.header(HttpHeaders.CacheControl, "public, max-age=31536000, immutable")
        call.respondFile(file.toFile())
    }

    authenticate(AUTH_JWT) {
        post("/questions") {
            val userId = call.principal<AuthUser>()!!.id
            val (request, imageBytes) = call.readUpload()

            val clean = ContentRules.validateAndClean(
                bodyText = request.bodyText,
                options = request.options,
                correctOptionIndex = request.correctOptionIndex,
                uploaderRating = request.uploaderRating,
                solutionText = request.solutionText,
                originalityConfirmed = request.originalityConfirmed
            )
            validate(questions.topicExists(request.topicId)) { "Konu bulunamadı" }

            val stored = imageBytes?.let { images.save(it) }
            val result = try {
                questions.createQuestion(
                    NewQuestion(
                        uploaderId = userId,
                        topicId = request.topicId,
                        bodyText = clean.bodyText,
                        options = clean.options,
                        correctOptionIndex = request.correctOptionIndex,
                        uploaderRating = request.uploaderRating,
                        solutionText = clean.solutionText,
                        contentHash = ContentRules.contentHash(clean.bodyText, clean.options),
                        imagePath = stored?.fileName,
                        imagePhash = stored?.perceptualHash
                    ),
                    maxUploadsPerHour
                )
            } catch (e: Throwable) {
                stored?.let { images.delete(it.fileName) }
                throw e
            }
            if (result !is CreateQuestionResult.Created) stored?.let { images.delete(it.fileName) }

            when (result) {
                is CreateQuestionResult.Created -> call.respond(
                    HttpStatusCode.Created,
                    UploadQuestionResponse(
                        id = result.id.toString(),
                        status = result.status,
                        difficultyBucket = result.difficultyBucket,
                        message = if (result.status == "ACTIVE") "Sorun yayında" else "Sorun inceleme kuyruğuna alındı"
                    )
                )
                is CreateQuestionResult.Duplicate ->
                    call.respond(HttpStatusCode.Conflict, ErrorResponse("Bu soru zaten havuzda var"))
                CreateQuestionResult.RateLimited ->
                    call.respond(
                        HttpStatusCode.TooManyRequests,
                        ErrorResponse("Bir saatte en fazla $maxUploadsPerHour soru yükleyebilirsin")
                    )
            }
        }

        get("/me/questions") {
            val userId = call.principal<AuthUser>()!!.id
            call.respond(questions.questionsUploadedBy(userId).map {
                UploadedQuestionDto(
                    id = it.id.toString(),
                    topicName = it.topicName,
                    bodyPreview = it.bodyPreview,
                    status = it.status,
                    uploaderRating = it.uploaderRating,
                    difficultyBucket = it.difficultyBucket,
                    firstAttemptCount = it.firstAttemptCount,
                    accuracyPercent = if (it.firstAttemptCount == 0) null else it.correctFirstCount * 100 / it.firstAttemptCount,
                    keySuspect = it.keySuspect,
                    createdAt = it.createdAt.toString()
                )
            })
        }
    }
}

private suspend fun io.ktor.server.routing.RoutingCall.readUpload(): Pair<UploadQuestionRequest, ByteArray?> {
    var questionJson: String? = null
    var image: ByteArray? = null

    val multipart = try {
        receiveMultipart(formFieldLimit = ImageStorage.MAX_BYTES * 2L)
    } catch (e: Exception) {
        throw ValidationException("İstek multipart/form-data olmalı")
    }
    multipart.forEachPart { part ->
        try {
            when {
                part is PartData.FormItem && part.name == "question" -> questionJson = part.value
                part is PartData.FileItem && part.name == "image" -> {
                    val bytes = part.provider().readRemaining(ImageStorage.MAX_BYTES + 1L).readByteArray()
                    validate(bytes.size <= ImageStorage.MAX_BYTES) { "Görsel en fazla ${ImageStorage.MAX_BYTES / 1_000_000} MB olabilir" }
                    image = bytes
                }
            }
        } finally {
            part.dispose()
        }
    }

    val raw = questionJson ?: throw ValidationException("'question' alanı eksik")
    val request = try {
        json.decodeFromString<UploadQuestionRequest>(raw)
    } catch (e: SerializationException) {
        throw ValidationException("'question' alanı geçerli bir JSON değil")
    } catch (e: IllegalArgumentException) {
        throw ValidationException("'question' alanı geçerli bir JSON değil")
    }
    return request to image
}
