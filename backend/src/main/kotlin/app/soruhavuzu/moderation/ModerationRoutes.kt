package app.soruhavuzu.moderation

import app.soruhavuzu.ForbiddenException
import app.soruhavuzu.NotFoundException
import app.soruhavuzu.ValidationException
import app.soruhavuzu.auth.AUTH_JWT
import app.soruhavuzu.auth.AuthUser
import app.soruhavuzu.questions.ContentRules
import app.soruhavuzu.questions.ImageStorage
import app.soruhavuzu.users.Permissions
import app.soruhavuzu.users.User
import app.soruhavuzu.users.UserStore
import app.soruhavuzu.validate
import io.ktor.http.HttpStatusCode
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

@Serializable
data class ReportRequest(val reason: String, val note: String = "")

@Serializable
data class ReportResponse(val flagged: Boolean, val message: String)

@Serializable
data class ReportDto(val reason: String, val note: String, val weight: Int, val reporterNickname: String, val createdAt: String)

@Serializable
data class ModerationItemDto(
    val id: String,
    val status: String,
    val topicName: String,
    val uploaderNickname: String,
    val versionNumber: Int,
    val bodyText: String,
    val imageUrl: String?,
    val options: List<String>,
    val correctOptionIndex: Int,
    val solutionText: String,
    val uploaderRating: Int,
    val difficultyBucket: Int,
    val firstAttemptCount: Int,
    val correctFirstCount: Int,
    val optionSelectionCounts: List<Int>,
    val keySuspect: Boolean,
    val keySuspectReason: String?,
    val weightedReportScore: Int,
    val reports: List<ReportDto>,
    val createdAt: String
)

@Serializable
data class RemoveRequest(val reason: String)

@Serializable
data class NewVersionRequest(
    val bodyText: String,
    val options: List<String>,
    val correctOptionIndex: Int,
    val solutionText: String,
    val editorNote: String
)

@Serializable
data class NewVersionResponse(val versionNumber: Int, val calibrationReset: Boolean, val correctedUsers: Int)

@Serializable
data class OkResponse(val ok: Boolean = true)

fun Route.moderationRoutes(moderation: ModerationStore, users: UserStore, images: ImageStorage, maxReportsPerHour: Int) {
    authenticate(AUTH_JWT) {
        post("/questions/{id}/reports") {
            val reporter = call.currentUser(users)
            val req = call.receive<ReportRequest>()
            val reason = ReportReason.entries.firstOrNull { it.name == req.reason.uppercase() }
                ?: throw ValidationException("Geçersiz şikayet nedeni")
            validate(req.note.length <= 1000) { "Not en fazla 1000 karakter olabilir" }

            // Doğrulayıcıların (itibar >= 200) şikayeti 2 kat sayılır
            val weight = if (Permissions.isVerifier(reporter.role, reporter.reputation)) 2 else 1
            val flagged = moderation.report(reporter.id, call.questionId(), reason, req.note.trim(), weight, maxReportsPerHour)
            call.respond(
                HttpStatusCode.Created,
                ReportResponse(flagged, if (flagged) "Soru incelemeye alındı" else "Şikayetin kaydedildi")
            )
        }

        route("/moderation") {
            get("/queue") {
                call.requireModerator(users)
                call.respond(moderation.queue().map { it.toDto() })
            }

            post("/questions/{id}/approve") {
                val moderator = call.requireModerator(users)
                moderation.approve(moderator.id, call.questionId())
                call.respond(OkResponse())
            }

            post("/questions/{id}/remove") {
                val moderator = call.requireModerator(users)
                val req = call.receive<RemoveRequest>()
                validate(req.reason.trim().length in 3..500) { "Kaldırma nedeni 3-500 karakter olmalı" }

                val orphanImages = moderation.remove(moderator.id, call.questionId(), req.reason.trim())
                orphanImages.forEach { images.delete(it) }
                call.respond(OkResponse())
            }

            post("/questions/{id}/versions") {
                val moderator = call.requireModerator(users)
                val req = call.receive<NewVersionRequest>()
                val clean = ContentRules.validateAndClean(
                    bodyText = req.bodyText,
                    options = req.options,
                    correctOptionIndex = req.correctOptionIndex,
                    uploaderRating = 5, // versiyonda puan değişmez; doğrulamayı geçmesi için geçerli bir değer
                    solutionText = req.solutionText,
                    originalityConfirmed = true
                )
                validate(req.editorNote.trim().length in 3..500) { "Düzenleme notu 3-500 karakter olmalı" }

                val result = moderation.createVersion(
                    moderator.id,
                    call.questionId(),
                    VersionEdit(
                        bodyText = clean.bodyText,
                        options = clean.options,
                        correctOptionIndex = req.correctOptionIndex,
                        solutionText = clean.solutionText,
                        contentHash = ContentRules.contentHash(clean.bodyText, clean.options),
                        editorNote = req.editorNote.trim()
                    )
                )
                call.respond(NewVersionResponse(result.versionNumber, result.calibrationReset, result.correctedUsers))
            }
        }
    }
}

private suspend fun RoutingCall.currentUser(users: UserStore): User =
    users.findById(principal<AuthUser>()!!.id) ?: throw ForbiddenException("Kullanıcı bulunamadı")

/** Yetki her istekte veritabanındaki güncel rol/itibarla kontrol edilir. */
private suspend fun RoutingCall.requireModerator(users: UserStore): User =
    currentUser(users).also {
        if (!Permissions.canModerate(it.role, it.reputation)) throw ForbiddenException("Bu işlem için moderatör yetkisi gerekli")
    }

private fun RoutingCall.questionId(): UUID =
    parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        ?: throw NotFoundException("Soru bulunamadı")

private fun ModerationItem.toDto() = ModerationItemDto(
    id = id.toString(),
    status = status,
    topicName = topicName,
    uploaderNickname = uploaderNickname,
    versionNumber = versionNumber,
    bodyText = bodyText,
    imageUrl = imagePath?.let { "/images/$it" },
    options = options,
    correctOptionIndex = correctOptionIndex,
    solutionText = solutionText,
    uploaderRating = uploaderRating,
    difficultyBucket = difficultyBucket,
    firstAttemptCount = firstAttemptCount,
    correctFirstCount = correctFirstCount,
    optionSelectionCounts = optionSelectionCounts,
    keySuspect = keySuspect,
    keySuspectReason = keySuspectReason,
    weightedReportScore = weightedReportScore,
    reports = reports.map { ReportDto(it.reason, it.note, it.weight, it.reporterNickname, it.createdAt.toString()) },
    createdAt = createdAt.toString()
)
