# SoruHavuzu

KPSS ve TYT için topluluk soru bankası. Kullanıcılar soru yükler ve 1-10 arası bir zorluk tahmini verir. Her sorunun gerçek zorluğu, çözenlerin ilk denemelerinden Elo/Rasch modeliyle hesaplanır. Öğrenci "bana 4 zorluğunda KPSS Tarih sorusu getir" diyebilir ya da "Beni Zorla" moduyla seviyesine uygun sorular çözebilir.

```
backend/   Kotlin + Ktor API, PostgreSQL, tek Docker container
app/       Android (Jetpack Compose) istemcisi
```

Doğru cevaplar ve zorluk hesabı yalnızca sunucuda tutulur. Uygulama bir soruyu gösterirken doğru cevabı bilmez; cevap gönderildikten sonra öğrenir.

## Backend

**Gereken:** JDK 21. Testler için Docker da çalışıyor olmalı (Testcontainers gerçek bir Postgres açar).

```bash
cd backend
./gradlew test                                     # tüm testler

docker compose -f docker-compose.dev.yml up -d    # yerel Postgres
DATABASE_URL=postgresql://soru:soru@localhost:5432/soruhavuzu_dev \
JWT_SECRET=yerel-gelistirme-anahtari-en-az-32-karakter-olmali \
UPLOAD_DIR=./uploads \
./gradlew run                                      # http://localhost:8080/health
```

| Ortam değişkeni | Açıklama |
|---|---|
| `DATABASE_URL` | Zorunlu. `postgresql://kullanici:sifre@host:5432/db` ya da `jdbc:postgresql://...`. JDBC biçiminde `DATABASE_USER` ve `DATABASE_PASSWORD` da gerekir. |
| `JWT_SECRET` | Zorunlu, en az 32 karakter. Üretmek için: `openssl rand -hex 32`. Değiştirilirse herkesin oturumu kapanır. |
| `UPLOAD_DIR` | Soru görsellerinin klasörü. Varsayılan `/data/uploads` (container içi). |
| `PORT` | Varsayılan `8080`. |

Veritabanı şeması `src/main/resources/db/migration` (SQL) ve `src/main/kotlin/db/migration` (Kotlin) altındaki Flyway migration'larıyla kurulur. Sunucu her açıldığında eksik migration'ları kendisi uygular. **Uygulanmış bir migration dosyası asla değiştirilmez**; her değişiklik yeni bir `V{n}__...` dosyasıyla yapılır.

### Dokploy'a yayın

1. **Veritabanı:** Dokploy'da bir PostgreSQL servisi açın. Dış porta açmayın. Şifrede yalnızca harf ve rakam kullanın.
2. **Backend:** Compose servisi ekleyin.
   - Kaynak: bu repo, `main` dalı. Compose yolu: `./backend/docker-compose.yml`.
   - Environment sekmesine şunları yazın:
     ```
     DATABASE_URL=<Postgres servisinin Internal Connection URL'i>
     JWT_SECRET=<openssl rand -hex 32>
     ```
   - Domains sekmesinde servis `api`, port `8080`.
3. **Deploy:** Her push'tan sonra Deploy'a basın (ya da Autodeploy'u açın). Görseller `uploads` volume'ünde durur; deploy'larda silinmez.

### Yönetici hesabı

Uygulamadan normal şekilde kayıt olun. Sonra Dokploy'daki veritabanı terminalinde şunu çalıştırın:

```sql
UPDATE users SET role = 'ADMIN' WHERE email = 'eposta@adresiniz.com';
```

Yöneticiler ve moderatörler uygulamada **Denetim** sekmesini görür. İtibarı 1000'i geçen kullanıcılar da bu sekmeyi görür.

## Android

**Gereken:** JDK 21 ve Android SDK (platform `android-36.1`). Android Studio ile ya da komut satırından derlenir:

```bash
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties   # Android Studio bunu kendisi yazar
./gradlew :app:assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest
```

Uygulamanın bağlandığı sunucu adresi `gradle.properties` içindeki `soruhavuzu.apiBaseUrl` satırındadır.

## Domain ve HTTPS'e geçiş

Şu an sunucu Dokploy'un verdiği `*.sslip.io` adresinde, düz HTTP ile çalışıyor. Bu, giriş şifrelerinin ağda şifresiz gittiği anlamına gelir. **Gerçek kullanıcılardan önce** domain + HTTPS'e geçilmelidir.

1. Bir domain alın. DNS'te `A` kaydı ekleyin; örneğin `api` → sunucunun IP'si. Cloudflare kullanıyorsanız kaydı "DNS only" yapın.
2. Dokploy'da backend servisinin **Domains** sekmesine yeni host'u ekleyin: port `8080`, **HTTPS açık**, sertifika **Let's Encrypt**. Ardından yeniden Deploy edin.
3. `https://api.alanadiniz.com/health` adresinin cevap verdiğini kontrol edin.
4. `gradle.properties` içinde adresi değiştirin:
   ```
   soruhavuzu.apiBaseUrl=https://api.alanadiniz.com/
   ```
5. `app/src/main/res/xml/network_security_config.xml` içindeki `<domain-config cleartextTrafficPermitted="true">` bloğunu silin. Böylece uygulama yalnızca HTTPS kullanır.
6. Uygulamayı yeniden derleyin. Eski sslip.io adresini Dokploy'dan kaldırabilirsiniz.

## API özeti

| Uç nokta | Açıklama |
|---|---|
| `POST /auth/register`, `POST /auth/login` | Kayıt / giriş. JWT döner (30 gün). IP başına dakikada 10 istek. |
| `GET /me`, `/me/stats`, `/me/topics`, `/me/reputation`, `/me/questions` | Profil, istatistik, konu seviyeleri, itibar geçmişi, yüklenen sorular. |
| `GET /me/review?kind=wrong\|favorite` | Yanlışlarım / favoriler. |
| `GET /catalog` | Sınav → ders → konu ağacı ve soru sayıları (girişsiz). |
| `GET /questions/next?mode=PRACTICE&subjectId=..&bucket=4` | İstenen zorlukta çözülmemiş rastgele soru. `mode=CHALLENGE` seviyeye göre seçer. |
| `GET /questions/counts` | Zorluk kovası başına soru sayıları. |
| `POST /questions/{id}/attempts` | Cevap gönderir. Doğru cevap, çözüm ve seviye/zorluk değişimi döner. |
| `POST /questions` | Soru yükleme (multipart: `question` JSON + isteğe bağlı `image`). Saatte 10. |
| `POST /questions/{id}/favorite`, `DELETE /questions/{id}/wrong-note` | Favori aç/kapat, yanlışlardan çıkar. |
| `POST /questions/{id}/reports` | Şikayet. Ağırlıklı skor 3'e ulaşınca ya da telif şikayetinde soru havuzdan çekilir. |
| `GET /moderation/queue`, `POST /moderation/questions/{id}/approve\|remove\|versions` | Moderasyon. |

### İtibar kuralları

| Eşik | Ne kazandırır |
|---|---|
| İtibar ≥ 50 (ya da Editör) | Soru doğrudan yayına girer. |
| İtibar ≥ 200 | Doğrulayıcı olur: şikayeti 2 kat sayılır. |
| İtibar ≥ 1000 (ya da Moderatör/Yönetici) | Moderasyon yapabilir. |

| Olay | Etkisi |
|---|---|
| Yayına giren soru | Yükleyene +5 |
| Kaldırılan yayınlanmış soru | Yükleyene -15 |
| Haklı çıkan şikayet | Şikayet edene +2 |
