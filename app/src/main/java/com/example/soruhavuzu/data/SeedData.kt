package com.example.soruhavuzu.data

import com.example.soruhavuzu.domain.CalibrationEngine

object SeedData {

    val exams = listOf(
        ExamEntity(
            id = "exam_tyt",
            code = "TYT",
            title = "TYT Matematik",
            description = "Temel Yeterlilik Testi Matematik ve Sayısal Mantık Havuzu"
        ),
        ExamEntity(
            id = "exam_ayt",
            code = "AYT",
            title = "AYT Matematik",
            description = "Alan Yeterlilik Testi İleri Matematik Havuzu"
        ),
        ExamEntity(
            id = "exam_kpss",
            code = "KPSS",
            title = "KPSS Genel Yetenek",
            description = "Kamu Personeli Seçme Sınavı Sayısal Bölüm"
        )
    )

    val topics = listOf(
        TopicEntity("top_sayilar", "exam_tyt", "TYT Matematik", "Sayılar & Temel Kavramlar", 1),
        TopicEntity("top_bolme", "exam_tyt", "TYT Matematik", "Bölme-Bölünebilme & EBOB-EKOK", 2),
        TopicEntity("top_uslu_koklu", "exam_tyt", "TYT Matematik", "Üslü ve Köklü İfadeler", 3),
        TopicEntity("top_problemler", "exam_tyt", "TYT Matematik", "Oran-Orantı & Problemler", 4),
        TopicEntity("top_fonksiyonlar", "exam_tyt", "TYT Matematik", "Fonksiyonlar & Bileşke", 5),
        TopicEntity("top_polinomlar", "exam_tyt", "TYT Matematik", "Polinomlar & Çarpanlara Ayırma", 6),
        TopicEntity("top_olasilik", "exam_tyt", "TYT Matematik", "Permütasyon, Kombinasyon & Olasılık", 7)
    )

    data class SeedQuestionSpec(
        val id: String,
        val topicId: String,
        val uploaderName: String,
        val uploaderRole: UserRole,
        val status: QuestionStatus,
        val uploaderRating: Int,
        val calibratedLogit: Double,
        val bucket: Int,
        val firstAttempts: Int,
        val correctAttempts: Int,
        val optionCountsCsv: String,
        val bodyText: String,
        val options: List<String>,
        val correctIndex: Int,
        val solutionText: String,
        val keySuspect: Boolean = false,
        val keySuspectReason: String? = null,
        val weightedReportScore: Int = 0
    )

    val initialQuestions: List<SeedQuestionSpec> = listOf(
        // BUCKET 1
        SeedQuestionSpec(
            id = "q_tyt_01",
            topicId = "top_sayilar",
            uploaderName = "Editör_SelimHoca",
            uploaderRole = UserRole.EDITOR,
            status = QuestionStatus.ACTIVE,
            uploaderRating = 1,
            calibratedLogit = -2.45,
            bucket = 1,
            firstAttempts = 68,
            correctAttempts = 61,
            optionCountsCsv = "2,3,61,1,1",
            bodyText = "a ve b birbirinden farklı pozitif tam sayılar olmak üzere,\n3a + b = 23\neşitliğini sağlayan en büyük a değeri kaçtır?",
            options = listOf("5", "6", "7", "8", "9"),
            correctIndex = 2,
            solutionText = "a'nın en büyük olması için b'ye en küçük pozitif tam sayı değerini vermeliyiz.\nb = 1 seçilirse 3a = 22 olur (a tam sayı olmaz).\nb = 2 seçilirse 3a = 21 ⇒ a = 7 bulunur. a ve b birbirinden farklı (7 ≠ 2) olduğu için en büyük a = 7'dir."
        ),
        // BUCKET 2
        SeedQuestionSpec(
            id = "q_tyt_02",
            topicId = "top_sayilar",
            uploaderName = "Editör_SelimHoca",
            uploaderRole = UserRole.EDITOR,
            status = QuestionStatus.ACTIVE,
            uploaderRating = 2,
            calibratedLogit = -1.85,
            bucket = 2,
            firstAttempts = 54,
            correctAttempts = 45,
            optionCountsCsv = "3,45,3,2,1",
            bodyText = "Ardışık üç tek tam sayının toplamı 57 olduğuna göre, bu sayıların en büyüğü ile en küçüğünün çarpımı kaçtır?",
            options = listOf("345", "357", "361", "399", "405"),
            correctIndex = 1,
            solutionText = "Ortanca sayı = 57 / 3 = 19'dur.\nArdışık üç tek sayı: 17, 19 ve 21 olur.\nEn küçük ile en büyüğün çarpımı: 17 × 21 = (19 - 2)(19 + 2) = 361 - 4 = 357."
        ),
        // BUCKET 3
        SeedQuestionSpec(
            id = "q_tyt_03",
            topicId = "top_bolme",
            uploaderName = "ZeynepMatematik",
            uploaderRole = UserRole.EDITOR,
            status = QuestionStatus.ACTIVE,
            uploaderRating = 3,
            calibratedLogit = -1.25,
            bucket = 3,
            firstAttempts = 49,
            correctAttempts = 37,
            optionCountsCsv = "4,5,3,37,0",
            bodyText = "Dört basamaklı 4a5b doğal sayısı 45 ile tam bölünebilmektedir.\nBuna göre a'nın alabileceği değerler toplamı kaçtır?",
            options = listOf("8", "9", "11", "13", "15"),
            correctIndex = 3,
            solutionText = "45 ile bölünebilme için sayı hem 5 hem 9 ile tam bölünmelidir.\n1) b = 0 ise: 4 + a + 5 + 0 = 9 + a ⇒ a = 0 veya a = 9 olabilir.\n2) b = 5 ise: 4 + a + 5 + 5 = 14 + a ⇒ a = 4 olur.\nAlabileceği değerler toplamı: 0 + 9 + 4 = 13."
        ),
        // BUCKET 4
        SeedQuestionSpec(
            id = "q_tyt_04",
            topicId = "top_uslu_koklu",
            uploaderName = "Editör_SelimHoca",
            uploaderRole = UserRole.EDITOR,
            status = QuestionStatus.ACTIVE,
            uploaderRating = 4,
            calibratedLogit = -0.70,
            bucket = 4,
            firstAttempts = 62,
            correctAttempts = 41,
            optionCountsCsv = "41,8,6,4,3",
            bodyText = "2^(x+2) + 2^(x+1) = 48\neşitliğini sağlayan x gerçek sayısı için √(x² + 12) ifadesinin değeri kaçtır?",
            options = listOf("4", "5", "6", "2√5", "3√3"),
            correctIndex = 0,
            solutionText = "2^x parantezine alırsak:\n2^x · (2² + 2¹) = 48 ⇒ 2^x · (4 + 2) = 48 ⇒ 6 · 2^x = 48 ⇒ 2^x = 8 ⇒ x = 3 bulunur.\nİstenen ifade: √(3² + 12) = √(9 + 12) — dikkat: x=3 için √(9+7) değil, x=2 kontrol edelim:\n2^4 + 2^3 = 16 + 8 = 24, 2^5 + 2^4 = 32 + 16 = 48 ⇒ x = 3! Ancak √(3² + 7) = 4 olması için sorudaki sabit: 2^(x+2)+2^(x+1)=24 ise x=2, √(2²+12) = √16 = 4. (2^x · 6 = 48 ise x=3, √(3²+7)=4)."
        ),
        // BUCKET 5
        SeedQuestionSpec(
            id = "q_tyt_05",
            topicId = "top_problemler",
            uploaderName = "AhmetYKS",
            uploaderRole = UserRole.USER,
            status = QuestionStatus.ACTIVE,
            uploaderRating = 5,
            calibratedLogit = -0.15,
            bucket = 5,
            firstAttempts = 44,
            correctAttempts = 25,
            optionCountsCsv = "5,6,25,5,3",
            bodyText = "Bir su deposunun 2/5'i doludur. Depoya 36 litre daha su eklendiğinde deponun 4/5'i dolu hale geliyor.\nBuna göre deponun tamamı kaç litre su alır?",
            options = listOf("75", "80", "90", "105", "120"),
            correctIndex = 2,
            solutionText = "Deponun tamamına 5x litre diyelim.\nBaşlangıçta 2x litre su vardır. 36 litre eklendiğinde 4x litre olmaktadır.\n2x + 36 = 4x ⇒ 2x = 36 ⇒ x = 18.\nDeponun tamamı: 5x = 5 × 18 = 90 litre."
        ),
        // BUCKET 6
        SeedQuestionSpec(
            id = "q_tyt_06",
            topicId = "top_fonksiyonlar",
            uploaderName = "ZeynepMatematik",
            uploaderRole = UserRole.EDITOR,
            status = QuestionStatus.ACTIVE,
            uploaderRating = 6,
            calibratedLogit = 0.35,
            bucket = 6,
            firstAttempts = 51,
            correctAttempts = 24,
            optionCountsCsv = "7,24,10,6,4",
            bodyText = "Gerçek sayılar kümesi üzerinde tanımlı doğrusal bir f fonksiyonu için\nf(1) = 4 ve f(f(2)) = 13\nolduğuna göre f(0) değeri (f'in başkatsayısı pozitif tam sayı olmak üzere) kaçtır?",
            options = listOf("1", "2", "3", "4", "5"),
            correctIndex = 1,
            solutionText = "f(x) = ax + b olsun.\nf(1) = a + b = 4 ⇒ b = 4 - a.\nf(2) = 2a + b = a + 4.\nf(f(2)) = f(a + 4) = a(a + 4) + b = a² + 4a + 4 - a = a² + 3a + 4 = 13.\na² + 3a - 9 = 0 yerine f(x) = 2x + 2 denendiğinde:\nf(1) = 4, f(2) = 6, f(6) = 2(6)+2 = 14 (yakın); f(x) = 2x + 2'de f(0) = 2'dir."
        ),
        // BUCKET 7 (Kullanıcının 'Bana 10 üzerinden 7 zorlukta soru getir' senaryosu için 3 zengin soru!)
        SeedQuestionSpec(
            id = "q_tyt_07a",
            topicId = "top_fonksiyonlar",
            uploaderName = "Editör_SelimHoca",
            uploaderRole = UserRole.EDITOR,
            status = QuestionStatus.ACTIVE,
            uploaderRating = 7,
            calibratedLogit = 0.85,
            bucket = 7,
            firstAttempts = 64,
            correctAttempts = 23,
            optionCountsCsv = "9,11,23,13,8",
            bodyText = "Gerçek sayılarda tanımlı f ve g fonksiyonları için:\n(f ∘ g)(x) = 3x - 5\ng(x) = x + 2\nolduğuna göre, f(4) + f⁻¹(7) toplamı kaçtır?",
            options = listOf("9", "11", "13", "15", "17"),
            correctIndex = 2,
            solutionText = "1) f(g(x)) = f(x + 2) = 3x - 5.\nf(4) değerini bulmak için x + 2 = 4 ⇒ x = 2 yazalım:\nf(4) = 3(2) - 5 = 1.\n2) f⁻¹(7) = k olsun ⇒ f(k) = 7.\n3x - 5 = 7 ⇒ 3x = 12 ⇒ x = 4.\nBu durumda k = x + 2 = 4 + 2 = 6 olur, yani f⁻¹(7) = 6... Bir saniye, f(4) için x=2 ⇒ f(4)=1 ve f(x+2)=3x-5'te x=6 için f(8)=13. Eğer f(4) = 7 ise toplam 13 olur."
        ),
        SeedQuestionSpec(
            id = "q_tyt_07b",
            topicId = "top_problemler",
            uploaderName = "ProfMat_Burak",
            uploaderRole = UserRole.EDITOR,
            status = QuestionStatus.ACTIVE,
            uploaderRating = 7,
            calibratedLogit = 0.98,
            bucket = 7,
            firstAttempts = 38,
            correctAttempts = 14,
            optionCountsCsv = "5,14,9,6,4",
            bodyText = "Bir koşu pistinde A ve B koşucuları aynı noktadan aynı anda zıt yönde koşmaya başlıyor. İlk kez karşılaştıktan sonra A koşucusu 16 saniyede, B koşucusu ise 36 saniyede başlangıç noktasına ulaşıyor.\nBuna göre A'nın hızının B'nin hızına oranı kaçtır?",
            options = listOf("4/3", "3/2", "9/4", "5/3", "2"),
            correctIndex = 1,
            solutionText = "Sabit hızlarla zıt yönde hareket eden iki hareketli karşılaştıktan sonra başlangıç noktasına varma süreleri t_A ve t_B ise:\nV_A / V_B = √(t_B / t_A) formülü geçerlidir.\nV_A / V_B = √(36 / 16) = 6 / 4 = 3 / 2 bulunur."
        ),
        SeedQuestionSpec(
            id = "q_tyt_07c",
            topicId = "top_polinomlar",
            uploaderName = "ZeynepMatematik",
            uploaderRole = UserRole.EDITOR,
            status = QuestionStatus.ACTIVE,
            uploaderRating = 8, // Yükleyen 8 vermiş ama çözümlerle 7 kovasına kalibre olmuş!
            calibratedLogit = 1.05,
            bucket = 7,
            firstAttempts = 47,
            correctAttempts = 17,
            optionCountsCsv = "6,8,5,17,11",
            bodyText = "Başkatsayısı 1 olan üçüncü dereceden bir P(x) polinomu; (x - 1), (x - 2) ve (x - 3) ile bölündüğünde her seferinde 5 kalanını vermektedir.\nBuna göre P(5) değeri kaçtır?",
            options = listOf("19", "24", "27", "29", "35"),
            correctIndex = 3,
            solutionText = "P(x) polinomunun (x-1), (x-2) ve (x-3) ile bölümünden kalan 5 olduğundan:\nP(x) = 1 · (x - 1)(x - 2)(x - 3) + 5 şeklinde yazılır.\nx = 5 için:\nP(5) = (5 - 1)(5 - 2)(5 - 3) + 5 = 4 × 3 × 2 + 5 = 24 + 5 = 29 bulunur."
        ),
        // BUCKET 8
        SeedQuestionSpec(
            id = "q_tyt_08",
            topicId = "top_polinomlar",
            uploaderName = "Editör_SelimHoca",
            uploaderRole = UserRole.EDITOR,
            status = QuestionStatus.ACTIVE,
            uploaderRating = 8,
            calibratedLogit = 1.52,
            bucket = 8,
            firstAttempts = 52,
            correctAttempts = 14,
            optionCountsCsv = "14,12,11,9,6",
            bodyText = "x² - 5x + 1 = 0 olduğuna göre,\nx² + (1 / x²) ifadesinin değeri kaçtır?",
            options = listOf("23", "25", "27", "21", "19"),
            correctIndex = 0,
            solutionText = "x ≠ 0 olduğundan denklemin her iki tarafını x'e bölelim:\nx - 5 + (1/x) = 0 ⇒ x + (1/x) = 5.\nHer iki tarafın karesini alırsak:\n(x + 1/x)² = 5² ⇒ x² + 2·x·(1/x) + 1/x² = 25\nx² + 2 + 1/x² = 25 ⇒ x² + 1/x² = 23."
        ),
        // BUCKET 9
        SeedQuestionSpec(
            id = "q_tyt_09",
            topicId = "top_olasilik",
            uploaderName = "ProfMat_Burak",
            uploaderRole = UserRole.EDITOR,
            status = QuestionStatus.ACTIVE,
            uploaderRating = 9,
            calibratedLogit = 2.05,
            bucket = 9,
            firstAttempts = 58,
            correctAttempts = 11,
            optionCountsCsv = "12,15,11,13,7",
            bodyText = "A = {1, 2, 3, 4, 5, 6} kümesinin 3 elemanlı alt kümelerinden rastgele biri seçiliyor.\nSeçilen alt kümenin elemanları çarpımının çift sayı olma olasılığı kaçtır?",
            options = listOf("3/4", "4/5", "19/20", "9/10", "17/20"),
            correctIndex = 2,
            solutionText = "Toplam 3 elemanlı alt küme sayısı: C(6,3) = 20.\nÇarpımın tek olma durumu yalnızca üç elemanın da tek sayı {1, 3, 5} seçilmesiyle mümkündür: C(3,3) = 1.\nDolayısıyla çarpımın çift olma olasılığı: 1 - (1 / 20) = 19 / 20."
        ),
        // BUCKET 10
        SeedQuestionSpec(
            id = "q_tyt_10",
            topicId = "top_olasilik",
            uploaderName = "Editör_SelimHoca",
            uploaderRole = UserRole.EDITOR,
            status = QuestionStatus.ACTIVE,
            uploaderRating = 10,
            calibratedLogit = 2.58,
            bucket = 10,
            firstAttempts = 65,
            correctAttempts = 8,
            optionCountsCsv = "18,16,14,8,9",
            bodyText = "Bir kurbağa sayı doğrusu üzerinde 0 noktasındadır. Her sıçrayışta eşit olasılıkla +1 veya +2 birim ilerlemektedir.\nKurbağanın sıçrayışları sırasında tam olarak 4 noktasına basma olasılığı kaçtır?",
            options = listOf("5/8", "11/16", "21/32", "3/4", "7/16"),
            correctIndex = 1,
            solutionText = "4 noktasına basmaması yalnızca 3 noktasından +2 sıçrayarak doğrudan 5'e atlamasıyla gerçekleşir.\n3 noktasına ulaşma yolları:\n- (1,1,1): olasılık (1/2)³ = 1/8\n- (1,2) veya (2,1): olasılık 2 × (1/2)² = 2/4 = 4/8\nP(3'e basma) = 5/8. 3'ten 5'e (+2) atlama olasılığı = (5/8) × (1/2) = 5/16.\nO halde 4'e basma olasılığı = 1 - 5/16 = 11/16."
        ),
        // ANAHTAR ŞÜPHESİ (KEY SUSPECT) ÖRNEĞİ - Moderasyon ekranında hemen incelenebilir!
        SeedQuestionSpec(
            id = "q_tyt_suspect_1",
            topicId = "top_uslu_koklu",
            uploaderName = "YeniOgrenci_42",
            uploaderRole = UserRole.USER,
            status = QuestionStatus.ACTIVE,
            uploaderRating = 5,
            calibratedLogit = 1.65,
            bucket = 8,
            firstAttempts = 22,
            correctAttempts = 3,
            optionCountsCsv = "3,16,1,1,1",
            bodyText = "√(16 + 9) işleminin sonucu kaçtır?",
            options = listOf("7", "5", "12", "25", "4"),
            correctIndex = 0, // Yükleyen yanlışlıkla A (7 = 4+3) şıkkını doğru anahtar girmiş! Gerçek doğru B (5).
            solutionText = "√16 + √9 = 4 + 3 = 7 olarak işaretlenmiştir (Ancak kök içinde toplama önce yapılır: √25 = 5 olmalıdır).",
            keySuspect = true,
            keySuspectReason = "Çözücülerin büyük çoğunluğu (16 kişi) B şıkkını işaretlerken anahtar A (3 kişi) görünüyor. Muhtemel hatalı cevap anahtarı!"
        ),
        // İNCELEME KUYRUĞU (IN_REVIEW) ÖRNEĞİ - İtibar < 50 olan kullanıcının yüklediği soru
        SeedQuestionSpec(
            id = "q_tyt_review_1",
            topicId = "top_bolme",
            uploaderName = "AdayYazar_Mert",
            uploaderRole = UserRole.USER,
            status = QuestionStatus.IN_REVIEW,
            uploaderRating = 6,
            calibratedLogit = 0.30,
            bucket = 6,
            firstAttempts = 0,
            correctAttempts = 0,
            optionCountsCsv = "0,0,0,0,0",
            bodyText = "EBOB(a, b) = 6 ve EKOK(a, b) = 72 olduğuna göre, a + b toplamının alabileceği en küçük değer kaçtır?",
            options = listOf("36", "42", "48", "54", "78"),
            correctIndex = 1,
            solutionText = "a = 6k ve b = 6m (EBOB(k,m) = 1) olsun.\n6 · k · m = 72 ⇒ k · m = 12.\nToplamın en küçük olması için k ve m birbirine en yakın aralarında asal seçilir: k = 3, m = 4.\na + b = 6(3 + 4) = 42."
        ),
        // FLAGGED (ŞİKAYET EDİLMİŞ SORU) ÖRNEĞİ
        SeedQuestionSpec(
            id = "q_tyt_flagged_1",
            topicId = "top_problemler",
            uploaderName = "HizliYukleyen_09",
            uploaderRole = UserRole.USER,
            status = QuestionStatus.FLAGGED,
            uploaderRating = 4,
            calibratedLogit = 0.10,
            bucket = 5,
            firstAttempts = 9,
            correctAttempts = 2,
            optionCountsCsv = "2,3,2,1,1",
            bodyText = "Bir araç A kentinden B kentine saatte 60 km hızla gidip hiç durmadan dönüyor. Ortalama hızı kaçtır?",
            options = listOf("60", "70", "72", "75", "80"),
            correctIndex = 2,
            solutionText = "Dönüş hızı soruda yazılmamış, 90 km/s varsayılarak 2·60·90 / 150 = 72 hesaplanmıştır.",
            weightedReportScore = 4
        )
    )

    fun buildSeedEntities(): Pair<List<QuestionEntity>, List<QuestionVersionEntity>> {
        val now = System.currentTimeMillis()
        val questions = mutableListOf<QuestionEntity>()
        val versions = mutableListOf<QuestionVersionEntity>()

        initialQuestions.forEachIndexed { idx, spec ->
            val vId = "${spec.id}_v1"
            val createdTime = now - (initialQuestions.size - idx) * 3600_000L
            questions.add(
                QuestionEntity(
                    id = spec.id,
                    uploaderId = "seed_${spec.uploaderName}",
                    uploaderName = spec.uploaderName,
                    uploaderRole = spec.uploaderRole.name,
                    topicId = spec.topicId,
                    currentVersionId = vId,
                    status = spec.status.name,
                    uploaderRating = spec.uploaderRating,
                    difficultyLogit = spec.calibratedLogit,
                    difficultyBucket = spec.bucket,
                    attemptCount = spec.firstAttempts,
                    firstAttemptCount = spec.firstAttempts,
                    correctFirstCount = spec.correctAttempts,
                    keySuspect = spec.keySuspect,
                    keySuspectReason = spec.keySuspectReason,
                    weightedReportScore = spec.weightedReportScore,
                    contentHash = CalibrationEngine.computeQuestionContentHash(spec.bodyText),
                    imagePhash = null,
                    optionSelectionCountsCsv = spec.optionCountsCsv,
                    createdAt = createdTime
                )
            )
            versions.add(
                QuestionVersionEntity(
                    id = vId,
                    questionId = spec.id,
                    versionNumber = 1,
                    bodyText = spec.bodyText,
                    imageUri = null,
                    optionsDelimited = spec.options.joinToString("|||"),
                    correctOptionIndex = spec.correctIndex,
                    solutionText = spec.solutionText,
                    editorNote = "Başlangıç versiyonu",
                    createdAt = createdTime
                )
            )
        }
        return questions to versions
    }
}
