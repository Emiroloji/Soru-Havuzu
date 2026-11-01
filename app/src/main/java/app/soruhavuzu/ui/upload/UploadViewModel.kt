package app.soruhavuzu.ui.upload

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.soruhavuzu.SoruHavuzuApplication
import app.soruhavuzu.data.remote.ApiClient
import app.soruhavuzu.data.remote.ApiResult
import app.soruhavuzu.data.remote.UploadQuestionRequest
import app.soruhavuzu.data.remote.UploadedQuestionDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream

/** Formdan gelen ham değerler; temizleme ve gönderme ViewModel'de yapılır. */
data class UploadDraft(
    val topicId: String,
    val bodyText: String,
    val image: Uri?,
    val options: List<String>,
    val correctOptionIndex: Int,
    val uploaderRating: Int,
    val solutionText: String,
    val originalityConfirmed: Boolean
)

data class UploadState(
    val myQuestions: List<UploadedQuestionDto> = emptyList(),
    val isSubmitting: Boolean = false,
    val message: String? = null
)

class UploadViewModel(
    private val client: ApiClient,
    private val contentResolver: ContentResolver
) : ViewModel() {

    private val _state = MutableStateFlow(UploadState())
    val state: StateFlow<UploadState> = _state.asStateFlow()

    private val requestAdapter = client.moshi.adapter(UploadQuestionRequest::class.java)

    fun loadMyQuestions() {
        viewModelScope.launch {
            (client.call { myQuestions() } as? ApiResult.Success)?.let { result ->
                _state.update { it.copy(myQuestions = result.value) }
            }
        }
    }

    fun submit(draft: UploadDraft, onSuccess: () -> Unit) {
        if (_state.value.isSubmitting) return

        // Boş bırakılan şık kutularını at; doğru şıkkın sırası da buna göre kayar
        val filled = draft.options.withIndex().filter { it.value.isNotBlank() }
        val correctIndex = filled.indexOfFirst { it.index == draft.correctOptionIndex }
        if (correctIndex < 0) {
            _state.update { it.copy(message = "Doğru şık olarak işaretlediğin kutu boş.") }
            return
        }

        _state.update { it.copy(isSubmitting = true, message = null) }
        viewModelScope.launch {
            val imageBytes = draft.image?.let { uri ->
                runCatching { withContext(Dispatchers.IO) { prepareImage(uri) } }.getOrElse {
                    _state.update { it.copy(isSubmitting = false, message = "Görsel okunamadı, başka bir görsel dene.") }
                    return@launch
                }
            }
            val json = requestAdapter.toJson(
                UploadQuestionRequest(
                    topicId = draft.topicId,
                    bodyText = draft.bodyText,
                    options = filled.map { it.value },
                    correctOptionIndex = correctIndex,
                    uploaderRating = draft.uploaderRating,
                    solutionText = draft.solutionText,
                    originalityConfirmed = draft.originalityConfirmed
                )
            )
            val result = client.call {
                uploadQuestion(
                    question = json.toRequestBody(JSON),
                    image = imageBytes?.let { MultipartBody.Part.createFormData("image", "soru.jpg", it.toRequestBody(JPEG)) }
                )
            }
            when (result) {
                is ApiResult.Success -> {
                    _state.update { it.copy(isSubmitting = false, message = result.value.message) }
                    onSuccess()
                    loadMyQuestions()
                }
                is ApiResult.Failure -> _state.update { it.copy(isSubmitting = false, message = result.message) }
            }
        }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    /**
     * Görseli en uzun kenarı 1600 piksel olacak şekilde küçültüp JPEG'e çevirir; kamera fotoğrafları
     * EXIF yönüne göre döndürülür. Sunucu da aynı sınırı uyguladığı için tam boy dosyayı göndermek
     * yalnızca mobil veri harcar ve 5 MB sınırına takılır.
     */
    private fun prepareImage(uri: Uri): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2

        val decoded = contentResolver.openInputStream(uri).use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("Görsel çözülemedi")

        val rotation = contentResolver.openInputStream(uri).use { stream ->
            when (stream?.let { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }
        val scale = minOf(1f, MAX_SIDE.toFloat() / maxOf(decoded.width, decoded.height))
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postRotate(rotation)
        }
        val output = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        return ByteArrayOutputStream().use { out ->
            output.compress(Bitmap.CompressFormat.JPEG, 85, out)
            out.toByteArray()
        }
    }

    companion object {
        private const val MAX_SIDE = 1600
        private val JSON = "application/json".toMediaType()
        private val JPEG = "image/jpeg".toMediaType()

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as SoruHavuzuApplication
                UploadViewModel(app.apiClient, app.contentResolver)
            }
        }
    }
}
