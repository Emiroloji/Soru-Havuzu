package app.soruhavuzu.domain

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Hibrit Elo / Rasch (1PL IRT) zorluk motoru. Android'deki CalibrationEngine'in sunucu sürümü;
 * saf fonksiyonlardan oluşur, veritabanına dokunmaz.
 *
 * - Rasch: P(doğru) = 1 / (1 + e^-(θ - d)), θ öğrenci yeteneği, d soru zorluğu (logit).
 * - Yalnızca ilk deneme θ ve d'yi günceller.
 * - Sorunun adım boyu çözüm sayısı arttıkça küçülür (~5 çözümde yarıya iner).
 */
object CalibrationEngine {

    const val LOGIT_MIN = -3.0
    const val LOGIT_MAX = 3.0
    private const val STUDENT_K = 0.26
    private const val QUESTION_K0 = 0.48
    private const val HALF_LIFE_ATTEMPTS = 5.0
    private const val RATING_TO_LOGIT = 0.60

    /** Yanlılık güncellemesi bu kadar ilk denemede bir yapılır; tek tek çözümler gürültülü. */
    const val BIAS_UPDATE_EVERY = 10

    /** Yükleyenin 1..10 puanı ve geçmiş yanlılığından başlangıç zorluğu: 1 -> -2.7, 10 -> +2.7. */
    fun initialLogit(uploaderRating: Int, uploaderBias: Double): Double {
        val raw = (uploaderRating.coerceIn(1, 10) - 5.5) * RATING_TO_LOGIT
        // Sürekli abartılı yüksek puan veren yükleyenin (bias > 0) tahmini aşağı çekilir
        return (raw - uploaderBias * 0.5).coerceIn(LOGIT_MIN, LOGIT_MAX)
    }

    fun expectedSuccessProbability(theta: Double, logit: Double): Double {
        val diff = (theta - logit).coerceIn(-6.0, 6.0)
        return 1.0 / (1.0 + exp(-diff))
    }

    fun logitToBucket(logit: Double): Int {
        val normalized = ((logit - LOGIT_MIN) / (LOGIT_MAX - LOGIT_MIN)).coerceIn(0.0, 0.9999)
        return (normalized * 10.0).toInt() + 1
    }

    /** Kova aralığının logit sınırları; "4 zorluğu" sorgusunu logit'e çevirmek için. */
    fun bucketToLogitRange(bucket: Int): ClosedFloatingPointRange<Double> {
        val width = (LOGIT_MAX - LOGIT_MIN) / 10.0
        val start = LOGIT_MIN + (bucket.coerceIn(1, 10) - 1) * width
        return start..(start + width)
    }

    /** θ'yı kullanıcıya gösterilecek 1..10 seviyesine çevirir (0.1 hassasiyetle). */
    fun thetaToLevel(theta: Double): Double {
        val level = (5.5 + theta / RATING_TO_LOGIT).coerceIn(1.0, 10.0)
        return (level * 10.0).roundToInt() / 10.0
    }

    data class AttemptInput(
        val theta: Double,
        val questionLogit: Double,
        val firstAttemptCount: Int,
        val correctFirstCount: Int,
        val isCorrect: Boolean
    )

    data class AttemptOutput(
        val expectedProbability: Double,
        val newTheta: Double,
        val newQuestionLogit: Double
    )

    /** Bir ilk denemenin θ ve d güncellemesi. Tekrar denemelerde çağrılmaz. */
    fun evaluateFirstAttempt(input: AttemptInput): AttemptOutput {
        val expected = expectedSuccessProbability(input.theta, input.questionLogit)
        val outcome = if (input.isCorrect) 1.0 else 0.0

        // Sürpriz doğru θ'yı çok artırır, sürpriz yanlış çok düşürür
        val newTheta = (input.theta + STUDENT_K * (outcome - expected)).coerceIn(LOGIT_MIN, LOGIT_MAX)

        // Yanlış yapıldıysa soru zorlaşır; çözüm sayısı arttıkça adım küçülür
        val kQuestion = QUESTION_K0 / (1.0 + input.firstAttemptCount / HALF_LIFE_ATTEMPTS)
        val newLogit = (input.questionLogit + kQuestion * (expected - outcome)).coerceIn(LOGIT_MIN, LOGIT_MAX)

        return AttemptOutput(expected, newTheta, newLogit)
    }

    /**
     * "Beni Zorla" hedef başarı olasılığı: varsayılan %60, 3 yanlış üst üste %75 (kolaylaşır),
     * 3 doğru üst üste %50 (zorlaşır).
     */
    fun challengeTargetProbability(consecutiveCorrects: Int, consecutiveWrongs: Int): Double = when {
        consecutiveWrongs >= 3 -> 0.75
        consecutiveCorrects >= 3 -> 0.50
        else -> 0.60
    }

    /** Hedef olasılığı verecek soru zorluğu: d = θ - ln(P / (1 - P)). */
    fun targetLogitForProbability(theta: Double, targetProbability: Double): Double {
        val p = targetProbability.coerceIn(0.20, 0.85)
        return (theta - ln(p / (1.0 - p))).coerceIn(LOGIT_MIN, LOGIT_MAX)
    }

    /**
     * Cevap anahtarı şüphesi. Sadece ilk denemelerin şık dağılımına bakar:
     * bir çeldirici doğru şıktan belirgin şekilde fazla seçildiyse ya da
     * yeterince çözüme rağmen doğruluk %20'nin altındaysa işaretler.
     */
    fun detectKeySuspect(optionCounts: List<Int>, correctOptionIndex: Int): String? {
        val total = optionCounts.sum()
        if (total < 5) return null

        val correctPicks = optionCounts.getOrElse(correctOptionIndex) { 0 }
        val (topDistractor, topCount) = optionCounts.withIndex()
            .filter { it.index != correctOptionIndex }
            .maxByOrNull { it.value }
            ?.let { it.index to it.value }
            ?: return null

        if (topCount >= 4 && topCount > correctPicks * 1.5) {
            return "Çözenlerin çoğu (${topCount} kişi) ${optionLabel(topDistractor)} şıkkını seçerken " +
                "anahtar ${optionLabel(correctOptionIndex)} (${correctPicks} kişi)."
        }
        if (total >= 10 && correctPicks.toDouble() / total < 0.20) {
            return "İlk denemelerde doğruluk %${correctPicks * 100 / total}; anahtar veya soru kökü kontrol edilmeli."
        }
        return null
    }

    /**
     * Yükleyenin verdiği puanla gerçekleşen zorluk arasındaki farktan yanlılığı günceller (üstel ortalama).
     * Pozitif yanlılık = soruları olduğundan zor gösteriyor.
     */
    fun updatedUploaderBias(currentBias: Double, uploaderRating: Int, calibratedLogit: Double): Double {
        val expectedFromRating = (uploaderRating.coerceIn(1, 10) - 5.5) * RATING_TO_LOGIT
        val error = expectedFromRating - calibratedLogit
        return (currentBias * 0.85 + error * 0.15).coerceIn(-1.5, 1.5)
    }

    fun shouldUpdateUploaderBias(newFirstAttemptCount: Int): Boolean =
        newFirstAttemptCount > 0 && newFirstAttemptCount % BIAS_UPDATE_EVERY == 0

    private fun optionLabel(index: Int): String = ('A' + index).toString()
}
