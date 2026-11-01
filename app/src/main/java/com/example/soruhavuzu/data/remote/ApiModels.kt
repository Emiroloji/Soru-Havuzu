package com.example.soruhavuzu.data.remote

import com.squareup.moshi.JsonClass

// Backend'in JSON gövdeleriyle birebir aynı alanlar (backend/src/main/kotlin/... *Routes.kt).

@JsonClass(generateAdapter = true)
data class ErrorResponse(val error: String)

@JsonClass(generateAdapter = true)
data class RegisterRequest(
    val email: String,
    val password: String,
    val nickname: String,
    val birthYear: Int? = null,
    val targetExamId: String? = null
)

@JsonClass(generateAdapter = true)
data class LoginRequest(val email: String, val password: String)

@JsonClass(generateAdapter = true)
data class UserDto(
    val id: String,
    val email: String,
    val nickname: String,
    val role: String,
    val reputation: Int,
    val theta: Double,
    /** θ'nın 1..10 ölçeğindeki karşılığı. */
    val level: Double,
    val birthYear: Int?,
    val targetExamId: String?
)

@JsonClass(generateAdapter = true)
data class AuthResponse(val token: String, val user: UserDto)

@JsonClass(generateAdapter = true)
data class CatalogTopicDto(val id: String, val name: String, val activeQuestionCount: Int)

@JsonClass(generateAdapter = true)
data class CatalogSubjectDto(val id: String, val name: String, val topics: List<CatalogTopicDto>)

@JsonClass(generateAdapter = true)
data class CatalogExamDto(val id: String, val code: String, val title: String, val subjects: List<CatalogSubjectDto>)

@JsonClass(generateAdapter = true)
data class QuestionDto(
    val id: String,
    val examId: String,
    val subjectId: String,
    val subjectName: String,
    val topicId: String,
    val topicName: String,
    val bodyText: String,
    /** Sunucu köküne göre yol, ör. "/images/q_....jpg". */
    val imageUrl: String?,
    val options: List<String>,
    val difficultyBucket: Int,
    val firstAttemptCount: Int,
    val alreadySolved: Boolean,
    val isFavorite: Boolean
)

@JsonClass(generateAdapter = true)
data class BucketRangeDto(val min: Int, val max: Int)

@JsonClass(generateAdapter = true)
data class NextQuestionResponse(val question: QuestionDto?, val suggestedRange: BucketRangeDto?)

@JsonClass(generateAdapter = true)
data class AttemptRequest(val selectedOptionIndex: Int, val responseTimeMs: Long, val sessionType: String)

@JsonClass(generateAdapter = true)
data class AttemptResponse(
    val isCorrect: Boolean,
    val correctOptionIndex: Int,
    val solutionText: String,
    val wasFirstAttempt: Boolean,
    val expectedProbabilityPercent: Int,
    val oldDifficultyBucket: Int,
    val newDifficultyBucket: Int,
    val oldLevel: Double,
    val newLevel: Double
)

@JsonClass(generateAdapter = true)
data class BucketCountDto(val bucket: Int, val total: Int, val unsolved: Int)

@JsonClass(generateAdapter = true)
data class UserStatsDto(val solvedCount: Int, val correctCount: Int, val wrongNoteCount: Int, val favoriteCount: Int)

@JsonClass(generateAdapter = true)
data class FavoriteResponse(val isFavorite: Boolean)

@JsonClass(generateAdapter = true)
data class ReportRequest(val reason: String, val note: String)

@JsonClass(generateAdapter = true)
data class ReportResponse(val flagged: Boolean, val message: String)

@JsonClass(generateAdapter = true)
data class UploadQuestionRequest(
    val topicId: String,
    val bodyText: String,
    val options: List<String>,
    val correctOptionIndex: Int,
    val uploaderRating: Int,
    val solutionText: String,
    val originalityConfirmed: Boolean
)

@JsonClass(generateAdapter = true)
data class UploadQuestionResponse(val id: String, val status: String, val difficultyBucket: Int, val message: String)

@JsonClass(generateAdapter = true)
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

@JsonClass(generateAdapter = true)
data class TopicMasteryDto(
    val examCode: String,
    val subjectName: String,
    val topicName: String,
    val level: Double,
    val solvedCount: Int,
    val correctCount: Int
)

@JsonClass(generateAdapter = true)
data class ReputationEventDto(val delta: Int, val reason: String, val createdAt: String)

@JsonClass(generateAdapter = true)
data class ReportDto(val reason: String, val note: String, val weight: Int, val reporterNickname: String, val createdAt: String)

/** Moderatörün gördüğü tam soru: doğru cevap, çözüm, şık dağılımı ve açık şikayetler dahil. */
@JsonClass(generateAdapter = true)
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

@JsonClass(generateAdapter = true)
data class RemoveRequest(val reason: String)

@JsonClass(generateAdapter = true)
data class NewVersionRequest(
    val bodyText: String,
    val options: List<String>,
    val correctOptionIndex: Int,
    val solutionText: String,
    val editorNote: String
)

@JsonClass(generateAdapter = true)
data class NewVersionResponse(val versionNumber: Int, val calibrationReset: Boolean)

/** Şikayet nedenleri; name alanı backend'deki ReportReason ile aynı olmalı. */
enum class ReportReason(val labelTr: String) {
    WRONG_KEY("Cevap anahtarı veya çözüm hatalı"),
    COPYRIGHT_OSYM("ÖSYM / yayınevi telif ihlali"),
    DUPLICATE("Havuzda aynısı var"),
    UNCLEAR("Soru kökü eksik veya anlaşılmaz"),
    INAPPROPRIATE("Uygunsuz içerik veya spam"),
    OTHER("Diğer")
}

enum class SessionType(val labelTr: String) {
    PRACTICE("Zorluk Seçerek Çöz"),
    CHALLENGE("Beni Zorla"),
    REVIEW("Yanlışlarım & Favoriler")
}
