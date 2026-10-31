package com.example.soruhavuzu.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

data class DockerHealthResponse(
    val status: String,
    val service: String,
    val activeQuestions: Int
)

data class DockerAttemptPayload(
    val userId: String,
    val questionId: String,
    val versionId: String,
    val selectedOptionIndex: Int,
    val isFirst: Boolean,
    val responseTimeMs: Long
)

data class DockerQuestionUploadPayload(
    val topicId: String,
    val uploaderRating: Int,
    val bodyText: String,
    val options: List<String>,
    val correctOptionIndex: Int,
    val solutionText: String
)

interface DockerApiService {
    @GET("api/v1/health")
    suspend fun checkHealth(): DockerHealthResponse

    @POST("api/v1/attempts")
    suspend fun pushAttempt(@Body payload: DockerAttemptPayload): Map<String, Any>

    @POST("api/v1/questions")
    suspend fun pushQuestion(@Body payload: DockerQuestionUploadPayload): Map<String, Any>
}

object DockerClientFactory {
    fun create(baseUrl: String): DockerApiService {
        val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
        val okHttp = OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl(normalizedUrl)
            .client(okHttp)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(DockerApiService::class.java)
    }

    /**
     * Kullanıcının AWS S3 veya parçalı bulut servislerine ihtiyaç duymadan
     * tek bir sunucuda hem iOS hem Android için ayağa kaldırabileceği
     * hazır tek servis Docker Compose + API şablonu.
     */
    val singleServiceDockerBlueprint: String = """
# docker-compose.yml — SoruHavuzu Tek Servis Mimarisi (S3 Yok, Parçalı Servis Yok)
version: "3.9"
services:
  soruhavuzu-core:
    image: node:20-alpine
    container_name: soruhavuzu_single_service
    working_dir: /srv/app
    ports:
      - "8080:8080"
    environment:
      - PORT=8080
      - DB_PATH=/srv/data/soruhavuzu.sqlite
      - UPLOAD_DIR=/srv/data/uploads
      - ELO_STUDENT_K=0.26
      - ELO_QUESTION_K0=0.48
    volumes:
      - soruhavuzu_data:/srv/data
    restart: unless-stopped

volumes:
  soruhavuzu_data:
    driver: local

# Uç Noktalar (iOS & Android Ortak REST API):
# GET  /api/v1/health       -> Servis ve aktif soru havuzu durumu
# GET  /api/v1/questions    -> Zorluk kovası (1..10) ve konu filtreli soru çekme
# POST /api/v1/questions    -> Soru + görsel (multipart) yükleme (yerel volume'e EXIF silerek yazar)
# POST /api/v1/attempts     -> Çözüm gönderimi, sunucu taraflı cevap kontrolü ve Elo/Rasch güncellemesi
# POST /api/v1/reports      -> Şikayet ve otomatik FLAGGED / KeySuspect tetikleme
""".trimIndent()
}
