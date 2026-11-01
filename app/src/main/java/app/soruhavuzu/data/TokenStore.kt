package app.soruhavuzu.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.sessionStore by preferencesDataStore(name = "session")

/**
 * Oturum anahtarını (JWT) cihazda saklar. Ağ istekleri her seferinde diske gitmesin diye
 * son değer bellekte de tutulur.
 */
class TokenStore(private val context: Context) {

    @Volatile
    var current: String? = null
        private set

    suspend fun load(): String? {
        current = context.sessionStore.data.map { it[TOKEN] }.first()
        return current
    }

    suspend fun save(token: String) {
        context.sessionStore.edit { it[TOKEN] = token }
        current = token
    }

    suspend fun clear() {
        context.sessionStore.edit { it.remove(TOKEN) }
        current = null
    }

    private companion object {
        val TOKEN = stringPreferencesKey("token")
    }
}
