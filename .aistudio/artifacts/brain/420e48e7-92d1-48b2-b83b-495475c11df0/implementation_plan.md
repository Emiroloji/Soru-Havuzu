# SoruHavuzu — Dinamik Zorluk Kalibrasyonlu Soru Bankası

Öğrencilerin ve eğitmenlerin kendi özgün sorularını 1–10 başlangıç zorluk puanıyla yükleyebildiği, her gerçek çözüm denemesiyle soru zorluğunun (`difficultyLogit` / `difficultyBucket`) ve öğrenci seviyesinin (`theta`) Elo/Rasch modeliyle otomatik kalibre edildiği, "bana 10 üzerinden 7 zorlukta soru getir" veya "Beni Zorla" diyerek çalışılabilen kapsamlı soru bankası ve puanlama motoru.

## Kullanıcı Onayı ve Kritik Kararlar

> [!IMPORTANT]
> İlk aşamada netleştirdiğimiz tercihler doğrultusunda mimari ve ürün kapsamı aşağıdaki şekilde kesinleştirilmiştir. Onayınızla birlikte doğrudan geliştirmeye geçilecektir.

- **Onaylanan Karar 1 — Tek Servis (Docker) Uyumlu Hibrit Mimari**: AWS S3, harici kimlik sağlayıcıları gibi parçalı bulut servisleri yerine; tek bir Docker konteynerinde (REST API + Veritabanı + Yerel Disk Görsel Depolama) çalışacak yalın bir sunucu mimarisi hedeflenmiştir. Mobil uygulama **Offline-First (Önce Yerel Room Veritabanı + Retrofit REST İstemcisi)** yapısıyla çalışır: Sunucu bağlantısı olmadığında bile tüm Elo/Rasch zorluk motoru, itibar defteri, moderasyon kuyruğu ve soru havuzu cihaz üzerinde eksiksiz çalışır; Ayarlar ekranından kendi Docker sunucu adresinizi (`Base URL`) girdiğinizde tek servis üzerinden senkronize olur ve uygulama içinden hazır `docker-compose` / API şemasını dışa aktarabilirsiniz.
- **Onaylanan Karar 2 — Tam Kapsamlı v1 Özellik Seti**: Çekirdek soru çözme ve yükleme döngüsüne ek olarak; defter tabanlı İtibar (`Reputation`) sistemi, yetki kapıları (≥50 doğrudan yayın, ≥200 doğrulayıcı, ≥1000 moderatör), İnceleme Kuyruğu (`PENDING` → `IN_REVIEW` → `ACTIVE`), Şikayet (`Report` & `FLAGGED`), ve İstatistiksel Yanlış Cevap Anahtarı Şüphesi (`KeySuspect`) tam kapsamlı olarak yer alacaktır.
- **Onaylanan Karar 3 — Pratik Soru Editörü**: Karmaşık tam LaTeX derleyicisi yerine; hızlı matematik sembol kısayol çubuğu (`√`, `x²`, `π`, `∫`, `≤`, `≥`, `≠`, `∈`, `∪`, `∩`, `→`, `a/b`), canlı kart önizlemesi ve Android Photo Picker ile sıfır izinli görsel ekleme (otomatik EXIF temizleme ve görsel pHash/metin hash kopya kontrolü ile) sunulacaktır.
- **Onaylanan Karar 4 — Zengin TYT Matematik Başlangıç Havuzu**: Uygulama ilk açıldığında soğuk başlangıç yaşanmaması için TYT Matematik konularında (Sayılar, Bölme-Bölünebilme, Üslü-Köklü İfadeler, Problemler, Fonksiyonlar, Polinomlar, Olasılık) 1'den 10'a kadar tüm zorluk kovalarını (`difficultyBucket`) kapsayan, detaylı çözümlü hazır sorularla dolu gelecektir.

---

## 1. Genel Bakış ve Temel Kavram

- **Ne Yapar?**: Yayınevlerinin öznel "kolay/orta/zor" etiketleri yerine, sorunun gerçek zorluğunu topluluk verisiyle ölçen canlı bir soru bankası sunar. Yükleyen kişinin verdiği 1–10 puan başlangıç tahmini (`uploaderRating`) olarak alınır; öğrenciler soruyu çözdükçe yalnız **ilk denemeler (`isFirst = true`)** üzerinden hem öğrencinin yeteneği (`theta`) hem de sorunun iç zorluğu (`difficultyLogit`) güncellenir.
- **Hedef Kitle**: YKS (başlangıçta TYT Matematik) ve KPSS'ye hazırlanan öğrenciler ile soru üreten öğretmenler ve ileri seviye öğrenciler.
- **Temel Değer Önerisi**:
  - Öğrenci tam istediği zorluk kovasını (ör. "Sadece 7–8 zorluğundaki Fonksiyonlar sorularını getir") seçebilir veya **"Beni Zorla" (`CHALLENGE`)** moduyla kendi seviyesine göre başarı olasılığı ~%60 olan dinamik sorularla çalışabilir.
  - Açık içerik üretiminde kalite; zorunlu çözüm açıklaması, özgünlük/telif beyanı, kopya tespiti, itibar puanı kapıları ve ağırlıklı şikayet/anahtar şüphesi mekanizmalarıyla korunur.

---

## 2. Kullanıcı Deneyimi ve Görsel Tasarım

### Temel Kullanıcı Akışları

1. **Onboarding ve Hesap/Profil Kurulumu**:
   - İlk açılışta hedef sınav seçimi (TYT, AYT, KPSS), doğum yılı (18 yaş altı KVKK koruması için), takma ad ve rol/itibar simülasyon seçimi (Normal Kullanıcı, Güvenilir Yükleyen, Doğrulayıcı, Moderatör modlarını tek dokunuşla test edebilme imkanı).
2. **Ana Sayfa ve Çözme Modları (`PRACTICE`, `CHALLENGE`, `REVIEW`)**:
   - **Zorluk ve Konu Seçerek Çöz (`PRACTICE`)**: 1–10 arası tekli veya aralık seçici (ör. 6–8), TYT Matematik konu ağacı filtresi ve her kovadaki mevcut soru sayısı göstergesi. Seçilen kovada soru biterse *"7 zorluğunda çözmediğin soru kalmadı, 6–8 aralığından devam edelim mi?"* akıllı geçişi.
   - **Beni Zorla (`CHALLENGE`)**: Kullanıcının güncel `theta` seviyesine göre başarı olasılığı `P ≈ 0.60` olan soruları seçer. Üst üste 3 yanlışta hedef olasılığı `%75`'e çıkarıp soruları kolaylaştırır, üst üste 3 doğruda `%50`'ye indirip zorlaştırır.
   - **Anında Değerlendirme ve Çözüm**: Soru çözülürken cevap anahtarı gizlidir; şık işaretlendiğinde anında Doğru/Yanlış sonucu, adım adım çözüm açıklaması, sorunun önceki/yeni zorluk puanı ve kullanıcının `theta` seviye değişimi (`+0.14 θ`) gösterilir.
   - **Yanlışlarım ve Favoriler (`REVIEW` / `OFFLINE_REVIEW`)**: Yanlış çözülen veya favoriye eklenen sorular çevrimdışı okunabilir ve tekrar çözülebilir.
3. **Soru Yükleme ve Kalite Kapısı**:
   - Adım adım anlaşılır form: Konu seçimi → Matematik sembol çubuğu destekli soru gövdesi + Cihazdan görsel seçimi (EXIF temizleme) → 2–5 arası şık ve doğru cevap seçimi → 1–10 başlangıç zorluk puanı (`uploaderRating`) → Zorunlu çözüm açıklaması → ÖSYM/Telif özgünlük beyanı.
   - Gönderim anında metin hash + görsel imza ile kopya (`Duplicate`) kontrolü ve ÖSYM/telif anahtar kelime filtresi çalışır. Kullanıcının itibarı `≥ 50` ise soru doğrudan `ACTIVE` olur; `< 50` ise `IN_REVIEW` kuyruğuna düşer.
4. **Topluluk Denetimi, Şikayet ve Moderasyon Paneli**:
   - Soru ekranından **Şikayet Et** (Yanlış cevap anahtarı, Telif/ÖSYM, Uygunsuz, Kopya, Anlaşılmaz). Doğrulayıcıların (`Reputation ≥ 200`) şikayeti 2 kat ağırlıklıdır; eşik aşılırsa soru otomatik `FLAGGED` durumuna geçip havuzdan çekilir.
   - **Anahtar Şüphesi (`KeySuspect`)**: Yüksek seviyeli (`theta` yüksek) öğrencilerin yanlış yapıp düşük seviyeli öğrencilerin doğru yaptığı veya belirli bir çeldirici şıkkın aşırı seçildiği sorular otomatik olarak `KeySuspect` bayrağı alır.
   - Uygulama içi **Moderasyon ve İnceleme Merkezi**: İnceleme kuyruğundaki (`IN_REVIEW`), şikayetle işaretlenen (`FLAGGED`) ve anahtar şüphesi (`KeySuspect`) taşıyan soruları inceleme, yeni versiyonla (`QuestionVersion`) düzeltip yayına alma veya kaldırma (`REMOVED`).

### Görsel Kimlik ve Tema (Akademik Odak & Modern Editöryal)

- **Estetik Yönelim**: "Odaklı Akademik Atölye" — Göz yormayan sıcak kağıt/mürekkep tonları, net matematiksel tipografi, abartıdan uzak ama güçlü görsel hiyerarşi ve anlaşılır zorluk ısı renkleri (1–3 Zümrüt Yeşili, 4–6 Kehribar/Altın, 7–8 Mercan Turuncusu, 9–10 Koyu Kızıl/Bordo).
- **Renk Paleti**:
  - Açık Tema: Arka plan sıcak fildişi (`#FAF8F5`), Kart yüzeyleri saf beyaz (`#FFFFFF`) ve taş grisi (`#F2EFE9`), Birincil vurgu Derin Çivit/Mürekkep Mavisi (`#1E3A8A`), İkincil vurgu Akademik Turkuaz (`#0D9488`).
  - Koyu Tema: Gece çalışma modu için Derin Arduvaz (`#0F172A`), Kart yüzeyi (`#1E293B`), Yumuşak Gökyüzü Mavisi (`#60A5FA`) ve Zümrüt (`#34D399`) vurgular.
- **Tipografi ve Hiyerarşi**:
  - Başlıklar ve skor göstergeleri için yerel paketlenmiş karakteristik **Space Grotesk** / **Plus Jakarta Sans**; soru metinleri ve uzun çözümler için yüksek okunabilirlik sunan **Inter**; matematiksel ifadeler, `theta`/`logit` metrikleri ve kodlamalar için **JetBrains Mono** / **Roboto Mono**.
- **Etkileşim ve Hareket**:
  - Zorluk kaydırıcısı değiştirildiğinde kova içindeki soru sayısının anlık yay animasyonuyla (`spring()`) güncellenmesi, şık seçiminde net dokunsal/görsel dalga (`ripple`) geri bildirimi ve oturum sonunda seviye değişim grafiği.

---

## 3. Temel Ürün Kararları ve Ödünleşimler

- **Karar 1 — Hibrit Elo / Rasch Kalibrasyon Motoru ve Yüzdelik Kova Sıralaması**
  - *Seçilen Yaklaşım*: Yükleyenin verdiği 1–10 puan (`uploaderRating`) ve varsa yükleyen yanlılığı (`uploaderBias`) kullanılarak başlangıç `difficultyLogit` değeri hesaplanır. Her ilk çözümde (`isFirst = true`), beklenen başarı olasılığı `P = 1 / (1 + exp(-(theta - d)))` üzerinden hem kullanıcının `theta` değeri hem de sorunun `d` (`difficultyLogit`) değeri güncellenir. Sorunun çözümlenme sayısı arttıkça belirsizlik azalır (ilk çözümlerde hızlı, ~45 çözümden sonra oturmuş kalibrasyon). Kullanıcıya gösterilen 1–10 `difficultyBucket` değeri, aktif sorular arasındaki sıralamadan ve logit ölçeğinden türetilir.
  - *Neden*: Sabit ortalamalar, soruyu çözen kitlenin seviyesini hesaba katmaz (sadece derece öğrencilerinin çözdüğü zor bir soru kolaymış gibi görünebilir). Elo/Rasch modeli, güçlü öğrencinin yaptığı soruyu az, zayıf öğrencinin yaptığı soruyu çok güncelleyerek gerçek zorluğu yakalar.
- **Karar 2 — Değişmez Soru Versiyonlama (`Question` + `QuestionVersion`)**
  - *Seçilen Yaklaşım*: Sorunun kimliği, konusu, durumu ve kalibrasyon istatistikleri `Question` varlığında tutulurken; soru metni, şıklar, doğru cevap (`AnswerSpec`) ve çözüm açıklaması değişmez `QuestionVersion` kaydında saklanır. Bir soru düzenlendiğinde yeni bir `QuestionVersion` oluşturulur ve her deneme (`Attempt`) hangi versiyonda çözüldüğünü bilir.
  - *Neden*: Cevap anahtarı hatalı olan bir soru düzeltildiğinde geçmiş çözüm verilerinin ve deneme kayıtlarının bütünlüğü bozulmaz.
- **Karar 3 — Tek Servis (Docker) Mimarisi ve Cihaz İçi Tam Çalışabilirlik**
  - *Seçilen Yaklaşım*: Parçalı bulut servisleri (S3, Cognito, ayrı kuyruk servisleri) kullanılmaz. Uygulama tüm veri şemasını yerel Room veritabanında barındırır ve aynı zamanda tek bir Docker sunucusuna bağlanabilen REST istemci katmanı + hazır Docker sunucu şablonu içerir.
  - *Neden*: Hem geliştirme/test aşamasında hiçbir harici kurulum gerektirmeden anında çalışır, hem de üretime çıkarken tek bir `docker compose up` komutuyla ayağa kalkan sade bir arka uçla hem iOS hem Android istemcilerine hizmet verebilir.

---

## 4. Teknik Mimari ve Veri Stratejisi

### Sistem ve Bileşen Mimarisi

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                        SORUHAVUZU MOBİL İSTEMCİ (UI)                         │
├──────────────────┬──────────────────┬───────────────────┬────────────────────┤
│   Ana Sayfa &    │  Soru Çözme &    │   Soru Yükleme    │  İtibar, Profil &  │
│  Zorluk Seçici   │  Beni Zorla Modu │  & Önizleme Formu │  Moderasyon Paneli │
└────────┬─────────┴────────┬─────────┴─────────┬─────────┴─────────┬──────────┘
         │                  │                   │                   │
         ▼                  ▼                   ▼                   ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                 DURUM YÖNETİMİ & İŞ MANTIĞI (ViewModel Katmanı)              │
├─────────────────────────────────────┬────────────────────────────────────────┤
│  • PracticeSession & Akıllı Seçici  │  • Otomatik Kontroller (Telif, Hash,   │
│  • Elo/Rasch Kalibrasyon Motoru     │    EXIF Silme, KeySuspect Analizi)     │
│  • Beni Zorla (%60 → %75/%50)       │  • İtibar Defteri & Durum Makinesi     │
└─────────────────────────────────────┴───────────────────┬────────────────────┘
                                                          │
                                                          ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                     VERİ HAVUZU KATMANI (SoruHavuzuRepository)               │
└─────────────────────────┬───────────────────────────────┬────────────────────┘
                          │                               │
                          ▼                               ▼
┌─────────────────────────────────────────┐   ┌────────────────────────────────┐
│   YEREL KALICI VERİTABANI (Room SQLite) │   │   TEK SERVİS DOCKER API İSTEMCİ│
│  • Questions & QuestionVersions         │   │  • Retrofit REST İstemcisi     │
│  • Attempts (isFirst, süre, θ/d anlık)  │   │  • Tek Konteyner: API + DB +   │
│  • Topics, Users, ReputationLedger      │   │    Yerel Disk Görsel Depolama  │
│  • Reports, Bookmarks, PracticeSessions │   │  • Uygulama İçi Docker Şablonu │
└─────────────────────────────────────────┘   └────────────────────────────────┘
```

### Veri Modeli ve Temel Varlıklar (`proje.md` Sözlüğü ile Birebir Uyumlu)

1. **`Exam` & `Topic`**: Taksonomi ağacı (`TYT` › `Matematik` › `Fonksiyonlar`, `Problemler`, `Sayılar`, vb.). Kod değiştirmeden yeni sınav ve konu eklenebilir.
2. **`Question` & `QuestionVersion`**:
   - `Question`: `id`, `uploaderId`, `topicId`, `currentVersionId`, `status` (`PENDING`, `IN_REVIEW`, `ACTIVE`, `FLAGGED`, `REMOVED`), `uploaderRating` (1–10), `difficultyLogit` (`d`), `difficultyBucket` (1–10), `attemptCount`, `firstAttemptCount`, `correctFirstCount`, `keySuspect` (Boolean), `discriminationIndex`, `contentHash`, `imagePhash`.
   - `QuestionVersion`: `id`, `questionId`, `versionNumber`, `bodyText`, `imageUri`, `optionsJson` (2–5 şık), `correctOptionIndex` (`AnswerSpec`), `solutionText`, `createdAt`.
3. **`Attempt`**: `id`, `userId`, `questionId`, `versionId`, `sessionId`, `sessionType` (`PRACTICE`, `CHALLENGE`, `REVIEW`, `OFFLINE_REVIEW`), `selectedOptionIndex`, `isCorrect`, `isFirst` (kalibrasyonu yalnızca `true` olanlar etkiler), `responseTimeMs`, `userThetaBefore`, `userThetaAfter`, `questionLogitBefore`, `questionLogitAfter`, `createdAt`.
4. **`UserProfile` & `ReputationEvent`**:
   - Kullanıcı yeteneği (`globalTheta`, konu bazlı `topicThetas`), `uploaderBias` (yükleyenin puanlama sapması), `reputationScore`, `role` (`USER`, `EDITOR`, `MODERATOR`, `ADMIN`), `birthYear`.
   - Her itibar değişimi (+5 soru onaylandı, +2 kaliteli soru oyu, -15 haklı şikayet/kaldırma, +3 doğrulayıcı katkısı) silinmez bir defter kaydı (`ReputationEvent`) olarak tutulur.
5. **`Report` (Şikayet)**: `id`, `questionId`, `reporterId`, `reason` (`WRONG_KEY`, `COPYRIGHT_OSYM`, `INAPPROPRIATE`, `DUPLICATE`, `UNCLEAR`, `OTHER`), `weight` (doğrulayıcı için 2x), `note`, `status`.

### Etkileşimli Bileşen ve Durum Eşlemesi

- **Soru Seçim ve Havuz Genişletme Mantığı**: Kullanıcı `PRACTICE` modunda örneğin Zorluk `7` ve Konu `Fonksiyonlar` seçtiğinde, kendi yüklemediği ve daha önce çözmediği `ACTIVE` sorular getirilir. Eğer `7` kovasında soru kalmamışsa sistem otomatik olarak komşu kovaları (`6–8`) tarar ve kullanıcıya tek dokunuşla komşu aralığa geçme seçeneği sunar.
- **Elo / Rasch Güncelleme Döngüsü**: Kullanıcı şıkkı onayladığında:
  1. Kullanıcının o sorudaki ilk denemesi mi (`isFirst`) kontrol edilir.
  2. İlk denemeyse: `P = 1 / (1 + exp(-(θ - d)))` hesaplanır.
  3. Öğrenci `θ` değeri ve soru `d` (`difficultyLogit`) değeri belirsizlik katsayısı (`K_question = K0 / (1 + n / n0)`) ile güncellenir; yükleyen puanının ağırlığı çözüm sayısı arttıkça doğal olarak azalır.
  4. Yükleyenin verdiği ilk puan ile gerçekleşen logit arasındaki farktan `uploaderBias` güncellenir.
  5. En az 5 ilk denemeye ulaşan sorularda, üst çeyrek öğrencilerin başarı oranı alt çeyrek öğrencilerden düşükse veya tek bir yanlış şık doğru şıktan fazla seçiliyorsa soru otomatik olarak `keySuspect = true` işaretlenir ve moderasyon ekranına düşer.
