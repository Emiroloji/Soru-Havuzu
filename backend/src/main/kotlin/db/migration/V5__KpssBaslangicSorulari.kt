package db.migration

import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context

/**
 * KPSS için özgün başlangıç soruları (sistem kullanıcısı adına, yayında).
 * Zorlukları yazarın 1-10 tahmininden başlar; gerçek çözümlerle kalibre olur.
 * Ekleme kuralları için bkz. insertSeedQuestions.
 * "Güncel Bilgiler"e bilerek soru eklenmedi: kısa sürede eskir.
 */
@Suppress("ClassName", "unused")
class V5__KpssBaslangicSorulari : BaseJavaMigration() {

    private val seeds = listOf(
        // TÜRKÇE
        Seed(
            "kpss_tr_sozcuk", 3,
            "Aşağıdaki cümlelerin hangisinde tırnak içindeki sözcük mecaz anlamıyla kullanılmıştır?",
            listOf(
                "Kışın \"soğuk\" havada dışarı çıkmamaya özen gösterdi.",
                "Toplantıda bize karşı çok \"soğuk\" davrandı.",
                "\"Soğuk\" suyu bir dikişte içti.",
                "Buzdolabındaki \"soğuk\" içecekleri masaya koydu.",
                "Odanın \"soğuk\" duvarlarına elini dayadı."
            ),
            1,
            "\"Soğuk davranmak\" ilgisiz, sevgisiz davranmak anlamındadır; sözcük gerçek anlamından (düşük sıcaklık) uzaklaşmıştır. Diğer seçeneklerde \"soğuk\" sıcaklık bildiren gerçek anlamıyla kullanılmıştır."
        ),
        Seed(
            "kpss_tr_yazim", 4,
            "Aşağıdaki cümlelerin hangisinde bir yazım yanlışı yapılmıştır?",
            listOf(
                "Bu konuyu birkaç kez anlattım.",
                "Herşeyi zamanında yapmaya çalışır.",
                "Yarın akşam size uğrayacağız.",
                "Bu kitabı da okumalısın.",
                "Hiçbir şey söylemeden gitti."
            ),
            1,
            "\"Her şey\" ayrı yazılır. \"Birkaç\" ve \"hiçbir\" bitişik yazılan kalıplaşmış sözcüklerdir; bağlaç olan \"da\" ise ayrı yazılır."
        ),
        Seed(
            "kpss_tr_ses", 5,
            "Aşağıdaki sözcüklerin hangisinde ünlü düşmesi vardır?",
            listOf("ağzı", "kitabı", "gözlük", "çiçeği", "okulu"),
            0,
            "\"Ağız\" sözcüğü ünlüyle başlayan bir ek alınca ikinci hecedeki dar ünlü düşer: ağız + ı → ağzı. \"Kitabı\" ve \"çiçeği\"nde ünsüz yumuşaması vardır; \"gözlük\" ve \"okulu\"nda ses olayı yoktur."
        ),
        Seed(
            "kpss_tr_dilbilgisi", 6,
            "\"Akşam olunca bütün köy derin bir uykuya daldı.\" cümlesinin ögeleri sırasıyla aşağıdakilerden hangisidir?",
            listOf(
                "Zarf tümleci – Özne – Dolaylı tümleç – Yüklem",
                "Özne – Zarf tümleci – Nesne – Yüklem",
                "Zarf tümleci – Nesne – Dolaylı tümleç – Yüklem",
                "Dolaylı tümleç – Özne – Zarf tümleci – Yüklem",
                "Zarf tümleci – Özne – Nesne – Yüklem"
            ),
            0,
            "\"Ne zaman?\" sorusunun cevabı \"akşam olunca\" zarf tümleci, \"daldı\" yüklemi, \"kim daldı?\" sorusunun cevabı \"bütün köy\" öznedir. \"Nereye daldı?\" sorusunun cevabı \"derin bir uykuya\" ise yönelme eki almış dolaylı tümleçtir."
        ),
        Seed(
            "kpss_tr_paragraf", 4,
            "Kitap okumak yalnızca bilgi edinmenin değil, başkalarının hayatına tanıklık etmenin de yoludur. Okur, bir romanın sayfalarında hiç gitmediği şehirleri dolaşır, hiç tanımadığı insanların sevinçlerine ve acılarına ortak olur. Böylece kendi dar dünyasının sınırlarını aşar ve insanı daha iyi anlamaya başlar.\n\nBu parçada asıl anlatılmak istenen aşağıdakilerden hangisidir?",
            listOf(
                "Okumak, insanın başka hayatları tanıyarak anlayışını genişletmesini sağlar.",
                "Romanlar, gezi kitaplarından daha fazla bilgi verir.",
                "Okurlar en çok yabancı şehirleri anlatan kitapları sever.",
                "Kitap okumak yalnızca bilgi edinmek için yapılmalıdır.",
                "İnsanlar acılarını kitaplar aracılığıyla unutur."
            ),
            0,
            "Parça, okumanın bilgi vermenin ötesinde okura başka hayatları yaşatarak onun dünyasını genişlettiğini ve insanı anlamasını sağladığını vurgular. Diğer seçenekler parçada söylenmeyen ya da parçayla çelişen yargılardır."
        ),

        // MATEMATİK
        Seed(
            "kpss_mat_temel", 2,
            "Ardışık üç çift sayının toplamı 48 olduğuna göre bu sayıların en büyüğü kaçtır?",
            listOf("14", "16", "18", "20", "22"),
            2,
            "Ortanca sayı 48 / 3 = 16'dır. Ardışık çift sayılar 14, 16 ve 18 olduğundan en büyüğü 18'dir."
        ),
        Seed(
            "kpss_mat_problemler", 4,
            "Bir işçi bir işi tek başına 12 günde, başka bir işçi aynı işi tek başına 6 günde bitirebilmektedir. İkisi birlikte çalışırsa bu işi kaç günde bitirirler?",
            listOf("3", "4", "5", "6", "9"),
            1,
            "Bir günde birinci işçi işin 1/12'sini, ikinci işçi 1/6'sını yapar. Birlikte bir günde 1/12 + 2/12 = 3/12 = 1/4'ünü yaparlar; işin tamamı 4 günde biter."
        ),
        Seed(
            "kpss_mat_problemler", 5,
            "Bir ürünün fiyatı önce %20 artırılmış, ardından yeni fiyat üzerinden %20 indirim yapılmıştır. Son fiyat ilk fiyata göre nasıl değişmiştir?",
            listOf("Değişmemiştir", "%4 azalmıştır", "%4 artmıştır", "%2 azalmıştır", "%10 azalmıştır"),
            1,
            "İlk fiyata 100 diyelim. %20 artışla 120 olur; 120'nin %20'si 24 olduğundan indirimle 96'ya düşer. 100'den 96'ya inen fiyat %4 azalmıştır."
        ),
        Seed(
            "kpss_mat_problemler", 3,
            "Bir annenin yaşı kızının yaşının 4 katıdır. 5 yıl sonra annenin yaşı kızının yaşının 3 katı olacağına göre kızın bugünkü yaşı kaçtır?",
            listOf("5", "8", "10", "12", "15"),
            2,
            "Kızın yaşı x, annenin yaşı 4x olsun. 5 yıl sonra: 4x + 5 = 3(x + 5) → 4x + 5 = 3x + 15 → x = 10."
        ),
        Seed(
            "kpss_mat_mantik", 6,
            "2, 6, 12, 20, 30, ? dizisinde soru işareti yerine hangi sayı gelmelidir?",
            listOf("36", "40", "42", "44", "48"),
            2,
            "Ardışık terimler arasındaki farklar 4, 6, 8, 10 şeklinde ikişer artar; sonraki fark 12 olduğundan 30 + 12 = 42. (Dizinin terimleri n·(n+1) biçimindedir: 6·7 = 42.)"
        ),
        Seed(
            "kpss_mat_geometri", 5,
            "Dik kenarlarının uzunlukları 6 cm ve 8 cm olan bir dik üçgende hipotenüse ait yükseklik kaç cm'dir?",
            listOf("4", "4,8", "5", "5,6", "6"),
            1,
            "Hipotenüs √(6² + 8²) = 10 cm'dir. Alan (6 · 8) / 2 = 24 cm²'dir; aynı alan 10 · h / 2 olduğundan h = 48 / 10 = 4,8 cm bulunur."
        ),

        // TARİH
        Seed(
            "kpss_tar_oncesi", 3,
            "\"Türk\" adıyla kurulan ilk Türk devleti aşağıdakilerden hangisidir?",
            listOf("Asya Hun Devleti", "Göktürk Devleti", "Uygur Devleti", "Avar Devleti", "Hazar Devleti"),
            1,
            "552'de Bumin Kağan önderliğinde kurulan Göktürk (I. Kök Türk) Devleti, \"Türk\" adını devlet adı olarak kullanan ilk Türk devletidir."
        ),
        Seed(
            "kpss_tar_islam", 4,
            "Anadolu'nun kapılarını Türklere açan savaş aşağıdakilerden hangisidir?",
            listOf("Dandanakan Savaşı", "Malazgirt Savaşı", "Miryokefalon Savaşı", "Kösedağ Savaşı", "Talas Savaşı"),
            1,
            "1071'de Büyük Selçuklu Sultanı Alparslan'ın Bizans'a karşı kazandığı Malazgirt Savaşı, Anadolu'nun kapılarını Türklere açmıştır. Miryokefalon (1176) ise Anadolu'nun Türk yurdu olduğunu kesinleştiren savaştır."
        ),
        Seed(
            "kpss_tar_osmanli", 5,
            "Tanzimat Fermanı hangi padişah döneminde ilan edilmiştir?",
            listOf("III. Selim", "II. Mahmut", "Abdülmecit", "Abdülaziz", "II. Abdülhamit"),
            2,
            "Tanzimat Fermanı 1839'da Abdülmecit döneminde, Sadrazam Mustafa Reşit Paşa tarafından Gülhane Parkı'nda okunarak ilan edilmiştir."
        ),
        Seed(
            "kpss_tar_inkilap", 3,
            "Türkiye Büyük Millet Meclisi hangi tarihte açılmıştır?",
            listOf("19 Mayıs 1919", "23 Nisan 1920", "30 Ağustos 1922", "29 Ekim 1923", "3 Mart 1924"),
            1,
            "Türkiye Büyük Millet Meclisi 23 Nisan 1920'de Ankara'da açılmıştır. 29 Ekim 1923 Cumhuriyetin ilanı, 3 Mart 1924 ise halifeliğin kaldırılması tarihidir."
        ),
        Seed(
            "kpss_tar_inkilap", 6,
            "Aşağıdakilerden hangisi Lozan Barış Antlaşması'nda çözümü sonraya bırakılan konulardan biridir?",
            listOf("Kapitülasyonlar", "Musul (Irak sınırı) sorunu", "Savaş tazminatı", "Batı Trakya", "İmroz ve Bozcaada"),
            1,
            "Lozan'da Irak sınırı (Musul) konusunda anlaşma sağlanamamış, sorun İngiltere ile ikili görüşmelere bırakılmıştır. Kapitülasyonlar kaldırılmış, savaş tazminatı konusu çözülmüş, İmroz ve Bozcaada Türkiye'ye verilmiş, Batı Trakya Yunanistan'a bırakılmıştır."
        ),
        Seed(
            "kpss_tar_cagdas", 3,
            "Birleşmiş Milletler hangi yıl kurulmuştur?",
            listOf("1919", "1939", "1945", "1949", "1955"),
            2,
            "Birleşmiş Milletler, II. Dünya Savaşı'nın ardından 1945'te San Francisco Konferansı'nda imzalanan antlaşmayla kurulmuştur. 1919'da kurulan örgüt Milletler Cemiyeti'dir."
        ),

        // COĞRAFYA
        Seed(
            "kpss_cog_konum", 4,
            "Türkiye'de aşağıdaki il çiftlerinden hangisi arasında yerel saat farkı en fazladır?",
            listOf("Edirne – Kars", "Çanakkale – Iğdır", "İzmir – Van", "Sinop – Hatay", "Kırklareli – Hakkari"),
            1,
            "Yerel saat farkı boylam farkına bağlıdır. Türkiye'nin en batı noktası Çanakkale'ye bağlı Gökçeada, en doğu noktası Iğdır'daki Dilucu'dur; bu nedenle en büyük boylam ve yerel saat farkı Çanakkale ile Iğdır arasındadır."
        ),
        Seed(
            "kpss_cog_iklim", 4,
            "Karadeniz kıyılarının her mevsim yağış almasının temel nedeni aşağıdakilerden hangisidir?",
            listOf(
                "Dağların kıyıya paralel uzanması ve nemli hava kütlelerinin yamaçlar boyunca yükselmesi",
                "Ekvatora yakın olması",
                "Bir okyanusa kıyısının olması",
                "Karasal iklimin etkisinde kalması",
                "Denizden karaya doğru hiç rüzgâr esmemesi"
            ),
            0,
            "Kıyıya paralel uzanan dağlar, denizden gelen nemli hava kütlelerini yükselmeye zorlar; hava soğuyup yoğunlaşır ve yamaçlarda yıl boyunca orografik yağış oluşur."
        ),
        Seed(
            "kpss_cog_fiziki", 5,
            "Aşağıdaki göllerden hangisi volkanik set gölüdür?",
            listOf("Tuz Gölü", "Van Gölü", "Abant Gölü", "Bafa Gölü", "Manyas Gölü"),
            1,
            "Van Gölü, Nemrut Dağı'ndan çıkan lavların bir çanağın önünü kapatmasıyla oluşmuş volkanik set gölüdür. Tuz ve Manyas tektonik, Abant heyelan set, Bafa ise alüvyal set gölüdür."
        ),
        Seed(
            "kpss_cog_nufus", 4,
            "Türkiye'de nüfusun kıyı kesimlerde yoğunlaşmasının nedenleri arasında aşağıdakilerden hangisi yer almaz?",
            listOf(
                "Sanayi ve ticaretin gelişmiş olması",
                "Ulaşım olanaklarının fazla olması",
                "Turizm faaliyetlerinin yaygın olması",
                "İklimin ılıman olması",
                "Yükseltinin fazla olması"
            ),
            4,
            "Yükseltinin fazla olması iklimi sertleştirir, tarım ve ulaşımı zorlaştırır; bu nedenle nüfusu azaltan bir etkendir. Diğer seçenekler kıyılarda nüfusu artıran etkenlerdir."
        ),
        Seed(
            "kpss_cog_ekonomi", 2,
            "Türkiye'de çay üretiminin en fazla yapıldığı il aşağıdakilerden hangisidir?",
            listOf("Trabzon", "Rize", "Artvin", "Giresun", "Ordu"),
            1,
            "Çay, bol yağış ve asitli toprak isteyen bir bitkidir; Türkiye'de üretimin en büyük kısmı Rize'de yapılır. Trabzon ve Artvin'de de çay tarımı vardır ancak üretim Rize'den azdır."
        ),

        // VATANDAŞLIK
        Seed(
            "kpss_vat_anayasa", 3,
            "1982 Anayasası'na göre Türkiye Büyük Millet Meclisi kaç milletvekilinden oluşur?",
            listOf("450", "500", "550", "600", "650"),
            3,
            "2017 Anayasa değişikliğiyle milletvekili sayısı 550'den 600'e çıkarılmıştır."
        ),
        Seed(
            "kpss_vat_anayasa", 4,
            "1982 Anayasası'na göre Cumhurbaşkanının görev süresi kaç yıldır?",
            listOf("4", "5", "6", "7", "8"),
            1,
            "Cumhurbaşkanının görev süresi 5 yıldır ve bir kişi en fazla iki defa Cumhurbaşkanı seçilebilir."
        ),
        Seed(
            "kpss_vat_anayasa", 7,
            "1982 Anayasası'na göre Anayasa Mahkemesi kaç üyeden oluşur?",
            listOf("11", "13", "15", "17", "21"),
            2,
            "Anayasa Mahkemesi 15 üyeden oluşur. 2010 değişikliğiyle üye sayısı 17'ye çıkarılmış, 2017'de askeri yargının kaldırılmasıyla 15'e inmiştir."
        ),
        Seed(
            "kpss_vat_hukuk", 5,
            "Aşağıdakilerden hangisi hukukun yazılı kaynaklarından biri değildir?",
            listOf("Anayasa", "Kanun", "Cumhurbaşkanlığı kararnamesi", "Örf ve âdet hukuku", "Yönetmelik"),
            3,
            "Örf ve âdet hukuku, toplumda uzun süre uygulanarak bağlayıcı hâle gelen yazısız kurallardan oluşur. Anayasa, kanun, Cumhurbaşkanlığı kararnamesi ve yönetmelik yazılı kaynaklardır."
        ),
        Seed(
            "kpss_vat_idare", 6,
            "Aşağıdakilerden hangisi mahalli idarelerden biri değildir?",
            listOf("Belediye", "Köy", "İl özel idaresi", "Büyükşehir belediyesi", "Valilik"),
            4,
            "Mahalli idareler il özel idaresi, belediye (büyükşehir belediyesi dahil) ve köydür. Valilik ise merkezi idarenin taşra teşkilatıdır."
        )
    )

    override fun migrate(context: Context) = insertSeedQuestions(context.connection, seeds)
}
