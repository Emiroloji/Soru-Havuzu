package com.example.soruhavuzu

import android.app.Application
import com.example.BuildConfig
import com.example.soruhavuzu.data.TokenStore
import com.example.soruhavuzu.data.remote.ApiClient
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** Uygulama genelinde tek örnek tutulan bağımlılıklar. */
class SoruHavuzuApplication : Application() {

    lateinit var tokenStore: TokenStore
        private set
    lateinit var apiClient: ApiClient
        private set

    private val _sessionExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Sunucu oturumu geçersiz saydığında yayılır; oturum ekranı bunu dinleyip girişe döner. */
    val sessionExpired: SharedFlow<Unit> = _sessionExpired

    override fun onCreate() {
        super.onCreate()
        tokenStore = TokenStore(this)
        apiClient = ApiClient(
            baseUrl = BuildConfig.API_BASE_URL,
            tokenProvider = { tokenStore.current },
            onUnauthorized = { _sessionExpired.tryEmit(Unit) }
        )
    }

    /** Görsel yolları ("/images/...") tam adrese çevirir. */
    fun absoluteUrl(path: String): String = BuildConfig.API_BASE_URL.trimEnd('/') + path
}
