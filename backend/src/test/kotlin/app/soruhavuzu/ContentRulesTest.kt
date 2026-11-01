package app.soruhavuzu

import app.soruhavuzu.questions.ContentRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ContentRulesTest {

    private fun clean(
        body: String = "Osmanlı Devleti'nin kuruluş döneminde hangi olay yaşanmıştır?",
        options: List<String> = listOf("Bafeus Savaşı", "Kosova Savaşı", "Mohaç Savaşı"),
        correct: Int = 0,
        rating: Int = 4,
        solution: String = "Bafeus Savaşı 1302'de Osman Bey döneminde yapılmıştır.",
        confirmed: Boolean = true
    ) = ContentRules.validateAndClean(body, options, correct, rating, solution, confirmed)

    @Test
    fun `gecerli soru temizlenip doner`() {
        val result = clean(body = "  Osmanlı Devleti'nin kuruluş döneminde hangi olay yaşanmıştır?  ", options = listOf(" A ", "B"))
        assertEquals("Osmanlı Devleti'nin kuruluş döneminde hangi olay yaşanmıştır?", result.bodyText)
        assertEquals(listOf("A", "B"), result.options)
    }

    @Test
    fun `kurallara uymayan alanlar reddedilir`() {
        val invalid = listOf<() -> Unit>(
            { clean(confirmed = false) },
            { clean(body = "kısa") },
            { clean(solution = "yok") },
            { clean(options = listOf("tek")) },
            { clean(options = listOf("a", "b", "c", "d", "e", "f")) },
            { clean(options = listOf("a", "  ")) },
            { clean(options = listOf("Ankara", "ANKARA")) },
            { clean(correct = 3) },
            { clean(rating = 0) },
            { clean(rating = 11) }
        )
        invalid.forEachIndexed { i, case -> assertFailsWith<ValidationException>("durum $i") { case() } }
    }

    @Test
    fun `telifli icerik isaretleri yakalanir`() {
        assertNotNull(ContentRules.copyrightViolation("Bu soru 2023 KPSS sınavında çıkmıştır"))
        assertNotNull(ContentRules.copyrightViolation("ÖSYM tarafından sorulmuştur"))
        assertNotNull(ContentRules.copyrightViolation("© Tüm hakları saklıdır"))
        assertNull(ContentRules.copyrightViolation("1923 yılında Cumhuriyet ilan edilmiştir"))
        assertFailsWith<ValidationException> { clean(solution = "Bu bir çıkmış soru, cevap A şıkkıdır.") }
    }

    @Test
    fun `icerik ozeti bicim farklarini yok sayar ama icerik farkini yakalar`() {
        val options = listOf("1453", "1071")
        fun hash(body: String) = ContentRules.contentHash(body, options)
        val original = hash("İstanbul'un fethi hangi yıl gerçekleşmiştir?")
        assertEquals(original, hash("  istanbul'un   FETHİ hangi yıl\ngerçekleşmiştir ? "))
        // Türkçe olmayan yerel ayarla büyük harfe çevrilmiş metin ("i" -> "I") de aynı sayılır
        assertEquals(original, hash("İstanbul'un fethi hangi yıl gerçekleşmiştir?".uppercase(java.util.Locale.ROOT)))
        assertNotEquals(original, hash("İzmir'in kurtuluşu hangi yıl gerçekleşmiştir?"))
        assertNotEquals(hash("x + 2 = 5"), hash("x + 3 = 5"))
    }

    @Test
    fun `ayni kalip kok farkli siklarla farkli soru, sik sirasi degisince ayni soru sayilir`() {
        val stem = "Aşağıdaki cümlelerin hangisinde bir yazım yanlışı yapılmıştır?"
        val first = ContentRules.contentHash(stem, listOf("Herşey yolunda.", "Birkaç gün kaldı."))
        assertNotEquals(first, ContentRules.contentHash(stem, listOf("Bir çok kez geldi.", "Hiçbir şey olmadı.")))
        assertEquals(first, ContentRules.contentHash(stem, listOf("Birkaç gün kaldı.", "herşey yolunda")))
    }
}
