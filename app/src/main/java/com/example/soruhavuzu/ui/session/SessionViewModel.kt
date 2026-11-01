package com.example.soruhavuzu.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.soruhavuzu.SoruHavuzuApplication
import com.example.soruhavuzu.data.TokenStore
import com.example.soruhavuzu.data.remote.ApiClient
import com.example.soruhavuzu.data.remote.ApiResult
import com.example.soruhavuzu.data.remote.AuthResponse
import com.example.soruhavuzu.data.remote.LoginRequest
import com.example.soruhavuzu.data.remote.RegisterRequest
import com.example.soruhavuzu.data.remote.UserDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface SessionState {
    data object Loading : SessionState
    data object LoggedOut : SessionState
    data class LoggedIn(val user: UserDto) : SessionState
    /** Kayıtlı oturum var ama sunucuya ulaşılamadı; tekrar denenebilir. */
    data class Unreachable(val message: String) : SessionState
}

data class AuthFormState(val isSubmitting: Boolean = false, val error: String? = null)

class SessionViewModel(
    private val client: ApiClient,
    private val tokenStore: TokenStore,
    sessionExpired: SharedFlow<Unit>
) : ViewModel() {

    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _form = MutableStateFlow(AuthFormState())
    val form: StateFlow<AuthFormState> = _form.asStateFlow()

    init {
        viewModelScope.launch {
            if (tokenStore.load() == null) _state.value = SessionState.LoggedOut else restore()
        }
        viewModelScope.launch {
            sessionExpired.collect { logout(message = "Oturumun sona erdi, tekrar giriş yap.") }
        }
    }

    fun restore() {
        _state.value = SessionState.Loading
        viewModelScope.launch {
            when (val result = client.call { me() }) {
                is ApiResult.Success -> _state.value = SessionState.LoggedIn(result.value)
                // 401 durumunu ApiClient zaten sessionExpired ile bildiriyor
                is ApiResult.Failure -> if (result.httpCode != 401) {
                    _state.value = SessionState.Unreachable(result.message)
                }
            }
        }
    }

    /** Profil bilgisi değişen işlemlerden (çözüm, yükleme) sonra seviye/itibarı tazeler. */
    fun refreshUser() {
        if (_state.value !is SessionState.LoggedIn) return
        viewModelScope.launch {
            (client.call { me() } as? ApiResult.Success)?.let { _state.value = SessionState.LoggedIn(it.value) }
        }
    }

    fun login(email: String, password: String) = authenticate {
        client.call { login(LoginRequest(email.trim(), password)) }
    }

    fun register(email: String, password: String, nickname: String, targetExamId: String, birthYear: Int?) = authenticate {
        client.call { register(RegisterRequest(email.trim(), password, nickname.trim(), birthYear, targetExamId)) }
    }

    fun clearFormError() = _form.update { it.copy(error = null) }

    fun logout(message: String? = null) {
        viewModelScope.launch {
            tokenStore.clear()
            _form.value = AuthFormState(error = message)
            _state.value = SessionState.LoggedOut
        }
    }

    private fun authenticate(request: suspend () -> ApiResult<AuthResponse>) {
        if (_form.value.isSubmitting) return
        _form.value = AuthFormState(isSubmitting = true)
        viewModelScope.launch {
            when (val result = request()) {
                is ApiResult.Success -> {
                    tokenStore.save(result.value.token)
                    _form.value = AuthFormState()
                    _state.value = SessionState.LoggedIn(result.value.user)
                }
                is ApiResult.Failure -> _form.value = AuthFormState(error = result.message)
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as SoruHavuzuApplication
                SessionViewModel(app.apiClient, app.tokenStore, app.sessionExpired)
            }
        }
    }
}
