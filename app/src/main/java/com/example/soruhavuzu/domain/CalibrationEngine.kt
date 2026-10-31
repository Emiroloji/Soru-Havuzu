package com.example.soruhavuzu.domain

import com.example.soruhavuzu.data.QuestionEntity
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * SoruHavuzu Hibrit Zorluk (Elo / Rasch 1PL IRT) ve Kalite Motoru.
 *
 * - Yükleyen puanı (1..10) + yükleyen yanlılığı (uploaderBias) başlangıç logit tahminini (d0) üretir.
 * - Her ilk denemede (isFirst == true), Rasch modeli P(X=1) = 1 / (1 + exp(-(theta - d))) ile
 *   hem öğrencinin yeteneği (theta) hem sorunun zorluğu (d) güncellenir.
 * - Çözüm sayısı arttıkça yükleyen tahmininin etkisi azalır (~5 çözümde %50, ~45 çözümde %10).
 * - Kullanıcıya gösterilen 1..10 zorluk kovası (difficultyBucket), iç ölçek ve aktif havuz sırasından türetilir.
 */
object CalibrationEngine {

    private const val LOGIT_MIN = -3.0
    private const val LOGIT_MAX = 3.0
    private const val STUDENT_K = 0.26
    private const val QUESTION_K0 = 0.48
    private const val HALF_LIFE_ATTEMPTS = 5.0

    /**
     * Yükleyenin 1..10 puanını ve geçmiş yanlılığını (uploaderBias) kullanarak
     * başlangıç zorluk logit değerini (d) hesaplar.
     */
    fun initialLogitFromUploaderRating(uploaderRating: Int, uploaderBias: Double = 0.0): Double {
        val clamped = uploaderRating.coerceIn(1, 10)
        // 1 -> -2.7, 5.5 -> 0.0, 10 -> +2.7
        val rawLogit = (clamped - 5.5) * 0.60
        // Sürekli abartılı yüksek puan veren yükleyenin (bias > 0) başlangıç tahmini aşağı çekilir
        return (rawLogit - (uploaderBias * 0.5)).coerceIn(LOGIT_MIN, LOGIT_MAX)
    }

    /**
     * Rasch 1PL başarı olasılığı: P(doğru | theta, d) = 1 / (1 + e^-(theta - d))
     */
    fun expectedSuccessProbability(userTheta: Double, questionLogit: Double): Double {
        val diff = (userTheta - questionLogit).coerceIn(-6.0, 6.0)
        return 1.0 / (1.0 + exp(-diff))
    }

    /**
     * Logit değerini doğrudan 1..10 kovasına eşler.
     */
    fun logitToBucket(logit: Double): Int {
        val normalized = ((logit - LOGIT_MIN) / (LOGIT_MAX - LOGIT_MIN)).coerceIn(0.0, 0.9999)
        return (normalized * 10.0).toInt() + 1
    }

    /**
     * Kullanıcıya gösterilen 1..10 zorluk kovasını, hem iç logit ölçeği hem de
     * aktif sorular içindeki yüzdelik sırasıyla (hibrit) kalibre eder.
     * Bu sayede her kovada dengeli soru dağılımı korunur.
     */
    fun recalibrateBucketsForActivePool(activeQuestions: List<QuestionEntity>): Map<String, Int> {
        if (activeQuestions.isEmpty()) return emptyMap()
        val sorted = activeQuestions.sortedBy { it.difficultyLogit }
        val total = sorted.size
        val result = mutableMapOf<String, Int>()
        sorted.forEachIndexed { index, q ->
            val rankBucket = (((index.toDouble() / total.toDouble()) * 10.0).toInt() + 1).coerceIn(1, 10)
            val absoluteBucket = logitToBucket(q.difficultyLogit)
            // Havuz küçükken logit ağırlıklı, havuz büyüdükçe sıra (rank) ağırlıklı
            val blended = ((rankBucket * 0.65) + (absoluteBucket * 0.35)).roundToInt().coerceIn(1, 10)
            result[q.id] = blended
        }
        return result
    }

    /**
     * Kullanıcının yetenek (theta) değerini 1..10 ölçeğinde anlaşılır seviyeye çevirir.
     */
    fun thetaToDisplayLevel(theta: Double): Double {
        val level = 5.5 + (theta / 0.60)
        return ((level.coerceIn(1.0, 10.0) * 10.0).roundToInt() / 10.0)
    }

    data class CalibrationStepOutput(
        val newUserTheta: Double,
        val newQuestionLogit: Double,
        val expectedProbability: Double,
        val updatedOptionCountsCsv: String,
        val isKeySuspect: Boolean,
        val keySuspectExplanation: String?
    )

    /**
     * Bir soru çözüldüğünde Elo/Rasch güncellemesini ve Anahtar Şüphesi (KeySuspect) analizini çalıştırır.
     * Sadece isFirst == true olduğunda theta ve questionLogit değişir.
     */
    fun evaluateAttempt(
        userTheta: Double,
        question: QuestionEntity,
        selectedOptionIndex: Int,
        correctOptionIndex: Int,
        optionCount: Int,
        isFirstAttempt: Boolean
    ): CalibrationStepOutput {
        val isCorrect = selectedOptionIndex == correctOptionIndex
        val expectedProb = expectedSuccessProbability(userTheta, question.difficultyLogit)
        val outcome = if (isCorrect) 1.0 else 0.0

        // Şık seçilim dağılımını güncelle
        val counts = parseOptionCounts(question.optionSelectionCountsCsv, optionCount).toMutableList()
        if (isFirstAttempt && selectedOptionIndex in counts.indices) {
            counts[selectedOptionIndex] = counts[selectedOptionIndex] + 1
        }
        val updatedCsv = counts.joinToString(",")

        if (!isFirstAttempt) {
            return CalibrationStepOutput(
                newUserTheta = userTheta,
                newQuestionLogit = question.difficultyLogit,
                expectedProbability = expectedProb,
                updatedOptionCountsCsv = updatedCsv,
                isKeySuspect = question.keySuspect,
                keySuspectExplanation = question.keySuspectReason
            )
        }

        // Öğrenci theta güncellemesi: sürpriz doğru çok artırır, sürpriz yanlış çok düşürür
        val deltaTheta = STUDENT_K * (outcome - expectedProb)
        val newUserTheta = (userTheta + deltaTheta).coerceIn(LOGIT_MIN, LOGIT_MAX)

        // Soru zorluk güncellemesi: öğrenci yanlış yaptıysa soru zorlaşır (expectedProb - outcome > 0)
        // Çözüm sayısı arttıkça adım boyu küçülür (belirsizlik azalır)
        val n = question.firstAttemptCount.toDouble()
        val kQuestion = QUESTION_K0 / (1.0 + (n / HALF_LIFE_ATTEMPTS))
        val deltaQuestion = kQuestion * (expectedProb - outcome)
        val newQuestionLogit = (question.difficultyLogit + deltaQuestion).coerceIn(LOGIT_MIN, LOGIT_MAX)

        // İstatistiksel Anahtar Şüphesi (KeySuspect) Tespiti
        val newFirstCount = question.firstAttemptCount + 1
        val newCorrectCount = question.correctFirstCount + (if (isCorrect) 1 else 0)
        val keyAnalysis = detectKeySuspect(
            firstAttemptCount = newFirstCount,
            correctFirstCount = newCorrectCount,
            correctOptionIndex = correctOptionIndex,
            optionCounts = counts,
            userTheta = userTheta,
            isCorrect = isCorrect,
            existingSuspect = question.keySuspect,
            existingReason = question.keySuspectReason
        )

        return CalibrationStepOutput(
            newUserTheta = newUserTheta,
            newQuestionLogit = newQuestionLogit,
            expectedProbability = expectedProb,
            updatedOptionCountsCsv = updatedCsv,
            isKeySuspect = keyAnalysis.first,
            keySuspectExplanation = keyAnalysis.second
        )
    }

    /**
     * "Beni Zorla" (CHALLENGE) modu için hedef başarı olasılığını ve hedef logit'i hesaplar.
     * - Varsayılan hedef başarı olasılığı: %60
     * - 3 yanlış üst üste -> hedef %75'e çıkar (sorular kolaylaşır)
     * - 3 doğru üst üste -> hedef %50'ye iner (sorular zorlaşır)
     */
    fun computeChallengeTargetProbability(
        consecutiveCorrects: Int,
        consecutiveWrongs: Int
    ): Double {
        return when {
            consecutiveWrongs >= 3 -> 0.75
            consecutiveCorrects >= 3 -> 0.50
            else -> 0.60
        }
    }

    fun targetLogitForProbability(userTheta: Double, targetProbability: Double): Double {
        val p = targetProbability.coerceIn(0.20, 0.85)
        // P = 1 / (1 + exp(-(theta - d))) => theta - d = ln(P / (1 - P)) => d = theta - ln(P / (1 - P))
        val logitOffset = ln(p / (1.0 - p))
        return (userTheta - logitOffset).coerceIn(LOGIT_MIN, LOGIT_MAX)
    }

    private fun detectKeySuspect(
        firstAttemptCount: Int,
        correctFirstCount: Int,
        correctOptionIndex: Int,
        optionCounts: List<Int>,
        userTheta: Double,
        isCorrect: Boolean,
        existingSuspect: Boolean,
        existingReason: String?
    ): Pair<Boolean, String?> {
        if (firstAttemptCount < 5) return existingSuspect to existingReason

        val correctPicks = optionCounts.getOrElse(correctOptionIndex) { correctFirstCount }
        var maxDistractorIndex = -1
        var maxDistractorCount = 0
        optionCounts.forEachIndexed { idx, count ->
            if (idx != correctOptionIndex && count > maxDistractorCount) {
                maxDistractorCount = count
                maxDistractorIndex = idx
            }
        }

        val optionLabels = listOf("A", "B", "C", "D", "E")
        // Bir yanlış şık (çeldirici), doğru şıktan en az 2 kat ve belirgin oranda fazla seçildiyse:
        if (maxDistractorIndex >= 0 && maxDistractorCount >= 4 && maxDistractorCount > correctPicks * 1.5) {
            val distractorLabel = optionLabels.getOrElse(maxDistractorIndex) { "?" }
            val correctLabel = optionLabels.getOrElse(correctOptionIndex) { "?" }
            return true to "Çözücülerin büyük çoğunluğu ($maxDistractorCount kişi) $distractorLabel şıkkını işaretlerken anahtar $correctLabel ($correctPicks kişi) görünüyor."
        }

        // Yüksek yetenekli (theta >= 1.2) kullanıcı yanlış yapıp toplam başarı %18 altındaysa:
        val accuracy = correctFirstCount.toDouble() / firstAttemptCount.toDouble()
        if (!isCorrect && userTheta >= 1.2 && accuracy < 0.20) {
            return true to "Yüksek seviyeli (θ ≥ 1.2) öğrenciler bu soruda sistematik olarak hata yapıyor (Doğruluk: %${(accuracy * 100).toInt()})."
        }

        return existingSuspect to existingReason
    }

    fun parseOptionCounts(csv: String, optionCount: Int): List<Int> {
        val parts = csv.split(",").mapNotNull { it.trim().toIntOrNull() }
        return List(optionCount) { idx -> parts.getOrElse(idx) { 0 } }
    }

    /**
     * Metin normalizasyonu ve SHA-256 tabanlı kopya (Duplicate) hash üretimi.
     */
    fun computeQuestionContentHash(bodyText: String): String {
        val normalized = bodyText
            .lowercase()
            .replace(Regex("\\s+"), "")
            .replace(Regex("[^a-z0-9çğıöşü+-/*=^()]"), "")
        val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray())
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }

    /**
     * ÖSYM ve yayınevi telif kelimeleri + spam kontrolü.
     */
    data class ContentCheckResult(
        val isBlockedCopyright: Boolean,
        val isNeedsReviewWarning: Boolean,
        val message: String?
    )

    fun checkCopyrightAndQuality(bodyText: String, solutionText: String): ContentCheckResult {
        val combined = "$bodyText $solutionText".lowercase()
        val bannedMarkers = listOf(
            "ösym", "osym", "çıkmış soru", "2024 tyt", "2023 tyt", "2022 tyt",
            "2024 ayt", "kpss çıkmış", "telif hakkı saklıdır", "izinsiz çoğaltılamaz"
        )
        val matchedBanned = bannedMarkers.firstOrNull { combined.contains(it) }
        if (matchedBanned != null) {
            return ContentCheckResult(
                isBlockedCopyright = true,
                isNeedsReviewWarning = false,
                message = "Telif Koruması: '$matchedBanned' ifadesi tespit edildi. ÖSYM ve yayınevi çıkmış soruları hukuki kurallar gereği yüklenemez."
            )
        }
        if (bodyText.trim().length < 15) {
            return ContentCheckResult(
                isBlockedCopyright = true,
                isNeedsReviewWarning = false,
                message = "Soru gövdesi çok kısa (en az 15 karakter olmalıdır)."
            )
        }
        if (solutionText.trim().length < 12) {
            return ContentCheckResult(
                isBlockedCopyright = true,
                isNeedsReviewWarning = false,
                message = "Çözüm açıklaması zorunludur ve en az bir işlem/açıklama adımı içermelidir."
            )
        }
        return ContentCheckResult(false, false, null)
    }

    /**
     * Yükleyenin verdiği puan ile gerçekleşen soru zorluğu arasındaki farktan yanlılığı (uploaderBias) günceller.
     */
    fun updatedUploaderBias(
        currentBias: Double,
        uploaderRating: Int,
        newQuestionLogit: Double
    ): Double {
        val expectedFromRating = (uploaderRating - 5.5) * 0.60
        val error = expectedFromRating - newQuestionLogit
        // Üstel hareketli ortalama ile yumuşak güncelleme
        return (currentBias * 0.85 + error * 0.15).coerceIn(-1.5, 1.5)
    }
}
