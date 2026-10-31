package app.soruhavuzu.questions

import app.soruhavuzu.ValidationException
import app.soruhavuzu.validate
import java.security.MessageDigest
import java.util.Locale

/** Yüklenen sorunun içerik kuralları: alan sınırları, telif kontrolü ve kopya tespiti için metin özeti. */
object ContentRules {

    private val TURKISH = Locale.forLanguageTag("tr")

    const val MAX_BODY = 5_000
    const val MAX_SOLUTION = 5_000
    const val MAX_OPTION = 500

    /** ÖSYM ve yayınevi çıkmış soru işaretleri; görülürse yükleme reddedilir. */
    private val COPYRIGHT_MARKERS = listOf(
        "ösym", "osym", "çıkmış soru", "cikmis soru", "kpss çıkmış", "telif hakkı saklıdır",
        "izinsiz çoğaltılamaz", "tüm hakları saklıdır"
    )
    private val EXAM_YEAR_MARKER = Regex("\\b20\\d\\d\\s*(tyt|ayt|kpss|ales|dgs|yks)\\b")

    data class CleanQuestion(
        val bodyText: String,
        val options: List<String>,
        val solutionText: String
    )

    /** Alanları temizler ve kurallara uymuyorsa ValidationException fırlatır. */
    fun validateAndClean(
        bodyText: String,
        options: List<String>,
        correctOptionIndex: Int,
        uploaderRating: Int,
        solutionText: String,
        originalityConfirmed: Boolean
    ): CleanQuestion {
        validate(originalityConfirmed) {
            "Sorunun ÖSYM'ye veya telifli bir yayına ait olmadığını onaylaman gerekiyor"
        }
        val body = bodyText.trim()
        val solution = solutionText.trim()
        val cleanOptions = options.map { it.trim() }

        validate(body.length in 15..MAX_BODY) { "Soru metni 15 ile $MAX_BODY karakter arasında olmalı" }
        validate(solution.length in 12..MAX_SOLUTION) { "Çözüm açıklaması zorunlu ve en az 12 karakter olmalı" }
        validate(cleanOptions.size in 2..5) { "2 ile 5 arasında şık girmelisin" }
        validate(cleanOptions.none { it.isEmpty() }) { "Boş şık bırakılamaz" }
        validate(cleanOptions.all { it.length <= MAX_OPTION }) { "Bir şık en fazla $MAX_OPTION karakter olabilir" }
        validate(cleanOptions.map { it.lowercase(TURKISH) }.toSet().size == cleanOptions.size) { "Aynı şık iki kez yazılamaz" }
        validate(correctOptionIndex in cleanOptions.indices) { "Doğru şık geçerli değil" }
        validate(uploaderRating in 1..10) { "Zorluk puanı 1 ile 10 arasında olmalı" }

        copyrightViolation("$body $solution")?.let { marker ->
            throw ValidationException("Telif koruması: '$marker' ifadesi bulundu. ÖSYM ve yayınevi soruları yüklenemez.")
        }
        return CleanQuestion(body, cleanOptions, solution)
    }

    fun copyrightViolation(text: String): String? {
        val lower = text.lowercase(TURKISH)
        return COPYRIGHT_MARKERS.firstOrNull { lower.contains(it) }
            ?: EXAM_YEAR_MARKER.find(lower)?.value
    }

    /**
     * Kopya tespiti için metin özeti. Büyük/küçük harf, boşluk ve noktalama farkları yok sayılır;
     * yani yalnızca biçimi değiştirilmiş aynı soru yakalanır. Rakam veya kelime değişirse farklı sayılır.
     */
    fun contentHash(bodyText: String): String {
        val normalized = bodyText
            .lowercase(TURKISH)
            // Türkçe olmayan klavye/yerel ayarla yazılmış I/İ farkları aynı metni farklı göstermesin
            .replace('ı', 'i')
            .replace(Regex("[^\\p{L}\\p{N}+\\-*/=^()√π<>≤≥≠]"), "")
        return MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
