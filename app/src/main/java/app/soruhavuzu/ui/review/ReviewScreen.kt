package app.soruhavuzu.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.soruhavuzu.SoruHavuzuApplication
import app.soruhavuzu.data.remote.ApiClient
import app.soruhavuzu.data.remote.ApiResult
import app.soruhavuzu.data.remote.QuestionDto
import app.soruhavuzu.ui.theme.difficultyColorForBucket
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReviewState(
    val wrongNotes: List<QuestionDto> = emptyList(),
    val favorites: List<QuestionDto> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

class ReviewViewModel(private val client: ApiClient) : ViewModel() {

    private val _state = MutableStateFlow(ReviewState())
    val state: StateFlow<ReviewState> = _state.asStateFlow()

    fun load() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val wrong = async { client.call { review("wrong") } }
            val favorite = async { client.call { review("favorite") } }
            val w = wrong.await()
            val f = favorite.await()
            _state.update {
                it.copy(
                    wrongNotes = (w as? ApiResult.Success)?.value ?: it.wrongNotes,
                    favorites = (f as? ApiResult.Success)?.value ?: it.favorites,
                    isLoading = false,
                    error = (w as? ApiResult.Failure)?.message ?: (f as? ApiResult.Failure)?.message
                )
            }
        }
    }

    fun removeFromWrongNotes(questionId: String) {
        // İyimser güncelleme: listeden hemen düşer, hata olursa liste yeniden yüklenir
        _state.update { s -> s.copy(wrongNotes = s.wrongNotes.filterNot { it.id == questionId }) }
        viewModelScope.launch {
            if (client.call { clearWrongNote(questionId) } is ApiResult.Failure) load()
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { ReviewViewModel((this[APPLICATION_KEY] as SoruHavuzuApplication).apiClient) }
        }
    }
}

@Composable
fun ReviewScreen(
    state: ReviewState,
    onStartReview: (List<QuestionDto>) -> Unit,
    onRemoveWrongNote: (String) -> Unit,
    onRetry: () -> Unit
) {
    var showFavorites by rememberSaveable { mutableStateOf(false) }
    val list = if (showFavorites) state.favorites else state.wrongNotes

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("review_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !showFavorites,
                    onClick = { showFavorites = false },
                    shape = SegmentedButtonDefaults.itemShape(0, 2)
                ) { Text("Yanlışlarım (${state.wrongNotes.size})") }
                SegmentedButton(
                    selected = showFavorites,
                    onClick = { showFavorites = true },
                    shape = SegmentedButtonDefaults.itemShape(1, 2)
                ) { Text("Favoriler (${state.favorites.size})") }
            }
        }

        state.error?.let { error ->
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = onRetry) { Text("Tekrar Dene") }
                }
            }
        }

        if (list.isEmpty() && !state.isLoading) {
            item {
                Text(
                    text = if (showFavorites) {
                        "Henüz favorin yok. Soru çözerken yer imi simgesine dokunarak ekleyebilirsin."
                    } else {
                        "Yanlış yaptığın sorular burada birikir. Tekrarda doğru çözdüğün soru listeden çıkar."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            }
        } else if (list.isNotEmpty()) {
            item {
                Button(
                    onClick = { onStartReview(list) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("start_review_button")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("${list.size} soruyu tekrar çöz", style = MaterialTheme.typography.titleMedium)
                }
            }
            items(list, key = { it.id }) { question ->
                ReviewQuestionCard(
                    question = question,
                    onRemove = if (showFavorites) null else ({ onRemoveWrongNote(question.id) })
                )
            }
        }
    }
}

@Composable
private fun ReviewQuestionCard(question: QuestionDto, onRemove: (() -> Unit)?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = difficultyColorForBucket(question.difficultyBucket), shape = RoundedCornerShape(6.dp)) {
                    Text(
                        text = "${question.difficultyBucket}/10",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${question.subjectName} · ${question.topicName}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (onRemove != null) {
                    TextButton(onClick = onRemove) { Text("Çıkar") }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(question.bodyText, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}
