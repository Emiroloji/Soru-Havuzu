package db.migration

import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context

/**
 * AYT Matematik konuları (V2'de AYT'nin dersi vardı ama konusu yoktu, bu yüzden katalogda görünmüyordu)
 * ve her konuya bir özgün, çözümlü başlangıç sorusu.
 */
@Suppress("ClassName", "unused")
class V8__AytMatematik : BaseJavaMigration() {

    private val topics = listOf(
        "ayt_polinom" to "Polinomlar",
        "ayt_ikinci_derece" to "İkinci Dereceden Denklemler",
        "ayt_logaritma" to "Üstel ve Logaritmik Fonksiyonlar",
        "ayt_diziler" to "Diziler",
        "ayt_trigonometri" to "Trigonometri",
        "ayt_limit" to "Limit ve Süreklilik",
        "ayt_turev" to "Türev",
        "ayt_integral" to "İntegral"
    )

    private val seeds = listOf(
        Seed(
            "ayt_ikinci_derece", 3,
            "x² − 5x + 6 = 0 denkleminin kökleri toplamı kaçtır?",
            listOf("−6", "−5", "1", "5", "6"),
            3,
            "ax² + bx + c = 0 denkleminde kökler toplamı −b/a'dır: −(−5)/1 = 5. (Kökler 2 ve 3'tür.)"
        ),
        Seed(
            "ayt_logaritma", 4,
            "log₂(x − 1) = 3 olduğuna göre x kaçtır?",
            listOf("7", "8", "9", "10", "12"),
            2,
            "Logaritma tanımından x − 1 = 2³ = 8 olur, buradan x = 9 bulunur. x − 1 > 0 koşulu da sağlanır."
        ),
        Seed(
            "ayt_diziler", 4,
            "İlk terimi 3, ortak farkı 4 olan aritmetik dizinin 10. terimi kaçtır?",
            listOf("37", "39", "40", "41", "43"),
            1,
            "Aritmetik dizide aₙ = a₁ + (n − 1)·d olduğundan a₁₀ = 3 + 9 · 4 = 39."
        ),
        Seed(
            "ayt_trigonometri", 5,
            "sin²25° + sin²65° ifadesinin değeri kaçtır?",
            listOf("0", "1/2", "1", "√2", "2"),
            2,
            "25° ile 65° tümler açılar olduğundan sin65° = cos25°'dir. İfade sin²25° + cos²25° = 1 olur."
        ),
        Seed(
            "ayt_limit", 5,
            "lim(x→2) (x² − 4) / (x − 2) limitinin değeri kaçtır?",
            listOf("0", "2", "4", "8", "Tanımsız"),
            2,
            "x² − 4 = (x − 2)(x + 2) olduğundan x ≠ 2 için ifade x + 2'ye eşittir. x → 2 iken limit 2 + 2 = 4'tür."
        ),
        Seed(
            "ayt_polinom", 6,
            "P(x) = x³ − 2x² + ax + 4 polinomunun x − 2 ile bölümünden kalan 6 olduğuna göre a kaçtır?",
            listOf("−1", "0", "1", "2", "3"),
            2,
            "Kalan teoremine göre kalan P(2)'dir: P(2) = 8 − 8 + 2a + 4 = 2a + 4 = 6, buradan a = 1."
        ),
        Seed(
            "ayt_turev", 6,
            "f(x) = x³ − 3x² + 5 fonksiyonu için f′(2) değeri kaçtır?",
            listOf("−2", "0", "2", "4", "6"),
            1,
            "f′(x) = 3x² − 6x olduğundan f′(2) = 3 · 4 − 6 · 2 = 0. (Fonksiyonun x = 2'de yerel minimumu vardır.)"
        ),
        Seed(
            "ayt_integral", 7,
            "∫₀² (3x² + 2x) dx integralinin değeri kaçtır?",
            listOf("8", "10", "12", "14", "16"),
            2,
            "Belirsiz integral x³ + x²'dir. Sınırlar yerine yazılırsa (8 + 4) − (0 + 0) = 12 bulunur."
        )
    )

    override fun migrate(context: Context) {
        val conn = context.connection
        conn.prepareStatement(
            "INSERT INTO topics (id, subject_id, name, order_index) VALUES (?, 'ayt_matematik', ?, ?) ON CONFLICT (id) DO NOTHING"
        ).use { st ->
            topics.forEachIndexed { index, (id, name) ->
                st.setString(1, id)
                st.setString(2, name)
                st.setInt(3, index + 1)
                st.addBatch()
            }
            st.executeBatch()
        }
        insertSeedQuestions(conn, seeds)
    }
}
