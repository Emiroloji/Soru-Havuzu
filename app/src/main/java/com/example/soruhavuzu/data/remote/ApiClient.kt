package com.example.soruhavuzu.data.remote

import com.squareup.moshi.Moshi
import kotlinx.coroutines.CancellationException
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Ağ çağrısının sonucu: ekranlar hata mesajını doğrudan gösterebilsin diye Türkçe mesajla döner. */
sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>
    data class Failure(val message: String, val httpCode: Int? = null) : ApiResult<Nothing>
}

class ApiClient(
    baseUrl: String,
    /** O anki oturum anahtarı; yoksa istek anahtarsız gider. */
    private val tokenProvider: () -> String?,
    /** Sunucu oturumu reddettiğinde (401) çağrılır: kullanıcı giriş ekranına döner. */
    private val onUnauthorized: () -> Unit
) {
    private val moshi = Moshi.Builder().build()
    private val errorAdapter = moshi.adapter(ErrorResponse::class.java)

    val api: SoruHavuzuApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .addInterceptor(Interceptor { chain ->
                    val token = tokenProvider()
                    val request = if (token == null) chain.request()
                    else chain.request().newBuilder().header("Authorization", "Bearer $token").build()
                    chain.proceed(request)
                })
                .build()
        )
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(SoruHavuzuApi::class.java)

    suspend fun <T> call(block: suspend SoruHavuzuApi.() -> T): ApiResult<T> = try {
        ApiResult.Success(api.block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        // Giriş/kayıt isteğindeki 401 "şifre hatalı" demektir, oturum düşmesi değil
        val isAuthRequest = e.response()?.raw()?.request?.url?.encodedPath?.startsWith("/auth/") == true
        if (e.code() == 401 && !isAuthRequest && tokenProvider() != null) onUnauthorized()
        ApiResult.Failure(errorMessage(e), e.code())
    } catch (e: IOException) {
        ApiResult.Failure("Sunucuya ulaşılamadı. İnternet bağlantını kontrol et.")
    }

    private fun errorMessage(e: HttpException): String {
        val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
        val parsed = body?.let { runCatching { errorAdapter.fromJson(it)?.error }.getOrNull() }
        return parsed ?: when (e.code()) {
            429 -> "Çok fazla istek gönderdin, biraz bekle."
            in 500..599 -> "Sunucuda bir sorun oluştu, birazdan tekrar dene."
            else -> "İstek başarısız oldu (${e.code()})."
        }
    }
}
