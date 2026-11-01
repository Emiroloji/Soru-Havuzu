package com.example.soruhavuzu.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface SoruHavuzuApi {

    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): AuthResponse

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): AuthResponse

    @GET("me")
    suspend fun me(): UserDto

    @GET("me/stats")
    suspend fun stats(): UserStatsDto

    @GET("catalog")
    suspend fun catalog(): List<CatalogExamDto>

    @GET("questions/counts")
    suspend fun bucketCounts(
        @Query("examId") examId: String?,
        @Query("subjectId") subjectId: String?,
        @Query("topicId") topicId: String?
    ): List<BucketCountDto>

    /** mode: PRACTICE (minBucket..maxBucket) veya CHALLENGE (seviyeye göre). */
    @GET("questions/next")
    suspend fun nextQuestion(
        @Query("mode") mode: String,
        @Query("examId") examId: String?,
        @Query("subjectId") subjectId: String?,
        @Query("topicId") topicId: String?,
        @Query("minBucket") minBucket: Int?,
        @Query("maxBucket") maxBucket: Int?,
        @Query("includeSolved") includeSolved: Boolean?
    ): NextQuestionResponse

    @POST("questions/{id}/attempts")
    suspend fun submitAttempt(@Path("id") questionId: String, @Body body: AttemptRequest): AttemptResponse

    @POST("questions/{id}/favorite")
    suspend fun toggleFavorite(@Path("id") questionId: String): FavoriteResponse

    @POST("questions/{id}/reports")
    suspend fun report(@Path("id") questionId: String, @Body body: ReportRequest): ReportResponse
}
