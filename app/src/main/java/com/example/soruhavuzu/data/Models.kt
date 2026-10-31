package com.example.soruhavuzu.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class UserRole(val labelTr: String, val badgeColorHex: Long) {
    USER("Öğrenci / Üye", 0xFF0284C7),
    EDITOR("Editör", 0xFF059669),
    MODERATOR("Moderatör", 0xFFD97706),
    ADMIN("Yönetici", 0xFFDC2626)
}

enum class QuestionStatus(val labelTr: String) {
    PENDING("Kontrolde"),
    IN_REVIEW("İnceleme Kuyruğunda"),
    ACTIVE("Yayında"),
    FLAGGED("Şikayet / İncelemede"),
    REMOVED("Kaldırıldı")
}

enum class SessionType(val labelTr: String, val descriptionTr: String) {
    PRACTICE("Zorluk Seçerek Çöz", "1-10 arası zorluk kovası ve konu seçerek çalış"),
    CHALLENGE("Beni Zorla", "Seviyene (θ) göre %60 başarı olasılıklı dinamik sorular"),
    REVIEW("Yanlışlarım & Favoriler", "Hata yaptığın veya kaydettiğin soruları tekrar çöz"),
    OFFLINE_REVIEW("Çevrimdışı Tekrar", "İnternet olmadan kayıtlı soruları incele")
}

enum class ReportReason(val labelTr: String, val isPriorityCopyright: Boolean = false) {
    WRONG_KEY("Cevap anahtarı veya çözüm hatalı"),
    COPYRIGHT_OSYM("ÖSYM / Yayınevi telif ihlali (Öncelikli - 48s)", isPriorityCopyright = true),
    DUPLICATE("Havuzda bulunan mükerrer (kopya) soru"),
    UNCLEAR("Soru kökü eksik veya anlaşılmaz"),
    INAPPROPRIATE("Uygunsuz içerik veya spam"),
    OTHER("Diğer nedenler")
}

@Entity(tableName = "exams")
data class ExamEntity(
    @PrimaryKey val id: String,
    val code: String,
    val title: String,
    val description: String
)

@Entity(tableName = "topics")
data class TopicEntity(
    @PrimaryKey val id: String,
    val examId: String,
    val subjectName: String,
    val name: String,
    val orderIndex: Int
)

@Entity(tableName = "questions")
data class QuestionEntity(
    @PrimaryKey val id: String,
    val uploaderId: String,
    val uploaderName: String,
    val uploaderRole: String,
    val topicId: String,
    val currentVersionId: String,
    val status: String, // QuestionStatus.name
    val uploaderRating: Int, // 1..10 immutable initial estimate
    val difficultyLogit: Double, // internal Rasch/Elo logit 'd'
    val difficultyBucket: Int, // 1..10 derived bucket shown to users
    val attemptCount: Int,
    val firstAttemptCount: Int,
    val correctFirstCount: Int,
    val keySuspect: Boolean,
    val keySuspectReason: String?,
    val weightedReportScore: Int,
    val contentHash: String,
    val imagePhash: String?,
    val optionSelectionCountsCsv: String, // e.g. "4,12,3,2,1" for options A..E
    val createdAt: Long
)

@Entity(tableName = "question_versions")
data class QuestionVersionEntity(
    @PrimaryKey val id: String,
    val questionId: String,
    val versionNumber: Int,
    val bodyText: String,
    val imageUri: String?,
    val optionsDelimited: String, // separated by "|||"
    val correctOptionIndex: Int,
    val solutionText: String,
    val editorNote: String?,
    val createdAt: Long
) {
    fun optionsList(): List<String> = optionsDelimited.split("|||").filter { it.isNotBlank() }
}

@Entity(tableName = "attempts")
data class AttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String,
    val questionId: String,
    val versionId: String,
    val sessionId: String,
    val sessionType: String, // SessionType.name
    val selectedOptionIndex: Int,
    val isCorrect: Boolean,
    val isFirst: Boolean, // ONLY isFirst == true updates Elo/Rasch difficulty & theta
    val responseTimeMs: Long,
    val userThetaBefore: Double,
    val userThetaAfter: Double,
    val questionLogitBefore: Double,
    val questionLogitAfter: Double,
    val createdAt: Long
)

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: String = "active_user",
    val nickname: String,
    val email: String,
    val targetExam: String,
    val birthYear: Int,
    val role: String, // UserRole.name
    val reputation: Int,
    val globalTheta: Double,
    val uploaderBias: Double,
    val uploaderRatedCount: Int,
    val onboardingCompleted: Boolean,
    val dockerServerUrl: String,
    val syncWithDocker: Boolean
) {
    val isUnder18: Boolean
        get() = (2026 - birthYear) < 18

    val canDirectPublish: Boolean
        get() = reputation >= 50 || role == UserRole.EDITOR.name || role == UserRole.MODERATOR.name || role == UserRole.ADMIN.name

    val isVerifier: Boolean
        get() = reputation >= 200 || role == UserRole.MODERATOR.name || role == UserRole.ADMIN.name

    val canModerateCommunity: Boolean
        get() = reputation >= 1000 || role == UserRole.MODERATOR.name || role == UserRole.ADMIN.name
}

@Entity(tableName = "topic_mastery", primaryKeys = ["userId", "topicId"])
data class TopicMasteryEntity(
    val userId: String,
    val topicId: String,
    val theta: Double,
    val solvedCount: Int,
    val correctCount: Int
)

@Entity(tableName = "bookmarks", primaryKeys = ["userId", "questionId"])
data class BookmarkEntity(
    val userId: String,
    val questionId: String,
    val isFavorite: Boolean,
    val isWrongNote: Boolean,
    val updatedAt: Long
)

@Entity(tableName = "reputation_events")
data class ReputationEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String,
    val delta: Int,
    val reason: String,
    val relatedQuestionId: String?,
    val createdAt: Long
)

@Entity(tableName = "reports")
data class ReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val questionId: String,
    val reporterId: String,
    val reporterName: String,
    val reason: String, // ReportReason.name
    val weight: Int, // 2 if verifier, 1 otherwise
    val note: String,
    val status: String, // OPEN, RESOLVED_FIXED, RESOLVED_REMOVED, DISMISSED
    val createdAt: Long
)

data class QuestionWithDetails(
    val question: QuestionEntity,
    val version: QuestionVersionEntity,
    val topic: TopicEntity,
    val isFavorite: Boolean,
    val isWrongNote: Boolean,
    val hasUserSolvedBefore: Boolean
) {
    val options: List<String> get() = version.optionsList()
    val accuracyPercent: Int
        get() = if (question.firstAttemptCount == 0) 0
        else ((question.correctFirstCount.toDouble() / question.firstAttemptCount.toDouble()) * 100).toInt()

    val priorWeightPercent: Int
        get() {
            // ~5 attempts -> ~50%, ~45 attempts -> ~10%
            val n = question.firstAttemptCount.toDouble()
            return ((5.0 / (5.0 + n)) * 100.0).toInt().coerceIn(5, 100)
        }
}

data class AttemptEvaluationResult(
    val attempt: AttemptEntity,
    val isCorrect: Boolean,
    val correctOptionIndex: Int,
    val solutionText: String,
    val oldDifficultyBucket: Int,
    val newDifficultyBucket: Int,
    val oldLogit: Double,
    val newLogit: Double,
    val oldUserTheta: Double,
    val newUserTheta: Double,
    val expectedProbabilityPercent: Int,
    val wasFirstAttempt: Boolean,
    val newChallengeTargetProbabilityPercent: Int
)
