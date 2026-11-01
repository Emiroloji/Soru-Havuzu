package app.soruhavuzu.ui.moderation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.soruhavuzu.SoruHavuzuApplication
import app.soruhavuzu.data.remote.ApiClient
import app.soruhavuzu.data.remote.ApiResult
import app.soruhavuzu.data.remote.ModerationItemDto
import app.soruhavuzu.data.remote.NewVersionRequest
import app.soruhavuzu.data.remote.RemoveRequest
import app.soruhavuzu.data.remote.SoruHavuzuApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ModerationState(
    val queue: List<ModerationItemDto> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    /** İşlemi süren sorular; kartlarındaki düğmeler kapatılır. */
    val busyIds: Set<String> = emptySet(),
    val message: String? = null
)

class ModerationViewModel(private val client: ApiClient) : ViewModel() {

    private val _state = MutableStateFlow(ModerationState())
    val state: StateFlow<ModerationState> = _state.asStateFlow()

    fun load() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = client.call { moderationQueue() }) {
                is ApiResult.Success -> _state.update { it.copy(queue = result.value, isLoading = false) }
                is ApiResult.Failure -> _state.update { it.copy(isLoading = false, error = result.message) }
            }
        }
    }

    fun approve(id: String) = act(id, "Soru yayına alındı") { approve(id) }

    fun remove(id: String, reason: String) = act(id, "Soru kaldırıldı") { remove(id, RemoveRequest(reason)) }

    fun createVersion(id: String, request: NewVersionRequest) = act(id, null) {
        val result = createVersion(id, request)
        _state.update {
            it.copy(
                message = if (result.calibrationReset) {
                    "v${result.versionNumber} yayında. Anahtar değiştiği için sorunun zorluğu sıfırdan hesaplanacak."
                } else {
                    "v${result.versionNumber} yayında."
                }
            )
        }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    /** İşlem başarılıysa soru kuyruktan düşer; hata olursa mesaj gösterilir ve kart yerinde kalır. */
    private fun act(id: String, successMessage: String?, request: suspend SoruHavuzuApi.() -> Unit) {
        if (id in _state.value.busyIds) return
        _state.update { it.copy(busyIds = it.busyIds + id) }
        viewModelScope.launch {
            when (val result = client.call(request)) {
                is ApiResult.Success -> _state.update { s ->
                    s.copy(
                        queue = s.queue.filterNot { it.id == id },
                        busyIds = s.busyIds - id,
                        message = successMessage ?: s.message
                    )
                }
                is ApiResult.Failure -> _state.update { it.copy(busyIds = it.busyIds - id, message = result.message) }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { ModerationViewModel((this[APPLICATION_KEY] as SoruHavuzuApplication).apiClient) }
        }
    }
}
