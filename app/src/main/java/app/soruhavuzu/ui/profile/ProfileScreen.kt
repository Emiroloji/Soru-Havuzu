package app.soruhavuzu.ui.profile

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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.soruhavuzu.SoruHavuzuApplication
import app.soruhavuzu.data.remote.ApiClient
import app.soruhavuzu.data.remote.ApiResult
import app.soruhavuzu.data.remote.ReputationEventDto
import app.soruhavuzu.data.remote.TopicMasteryDto
import app.soruhavuzu.data.remote.UserDto
import app.soruhavuzu.data.remote.UserStatsDto
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileState(
    val stats: UserStatsDto? = null,
    val topics: List<TopicMasteryDto> = emptyList(),
    val reputation: List<ReputationEventDto> = emptyList(),
    val error: String? = null
)

class ProfileViewModel(private val client: ApiClient) : ViewModel() {

    private val _state = MutableStateFlow(ProfileState())
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            val stats = async { client.call { stats() } }
            val topics = async { client.call { topics() } }
            val reputation = async { client.call { reputation() } }
            val s = stats.await()
            val t = topics.await()
            val r = reputation.await()
            _state.value = ProfileState(
                stats = (s as? ApiResult.Success)?.value,
                topics = (t as? ApiResult.Success)?.value.orEmpty(),
                reputation = (r as? ApiResult.Success)?.value.orEmpty(),
                error = listOf(s, t, r).filterIsInstance<ApiResult.Failure>().firstOrNull()?.message
            )
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { ProfileViewModel((this[APPLICATION_KEY] as SoruHavuzuApplication).apiClient) }
        }
    }
}

@Composable
fun ProfileScreen(user: UserDto, state: ProfileState, onLogout: () -> Unit) {
    val stats = state.stats
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("profile_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(user.nickname, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(user.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Genel seviye: ${user.level} / 10", style = MaterialTheme.typography.bodyLarge)
                    if (stats != null) {
                        val accuracy = if (stats.solvedCount == 0) 0 else stats.correctCount * 100 / stats.solvedCount
                        Text("${stats.solvedCount} soru çözdün · %$accuracy doğru", style = MaterialTheme.typography.bodyLarge)
                    }
                    Text("İtibar: ${user.reputation} · ${permissionText(user)}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) } }

        if (state.topics.isNotEmpty()) {
            item { SectionTitle("Konulara göre seviyen") }
            items(state.topics, key = { "${it.examCode}/${it.subjectName}/${it.topicName}" }) { topic ->
                TopicRow(topic)
            }
        }

        if (state.reputation.isNotEmpty()) {
            item { SectionTitle("İtibar geçmişi") }
            items(state.reputation) { event ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (event.delta > 0) "+${event.delta}" else "${event.delta}",
                        color = if (event.delta > 0) Color(0xFF059669) else Color(0xFFDC2626),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(44.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(event.reason, style = MaterialTheme.typography.bodySmall)
                        Text(event.createdAt.take(10), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        item {
            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .testTag("logout_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Çıkış Yap")
            }
        }
    }
}

@Composable
private fun TopicRow(topic: TopicMasteryDto) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "${topic.examCode} · ${topic.subjectName} · ${topic.topicName}",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f)
                )
                Text("${topic.level}/10", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { ((topic.level - 1) / 9).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${topic.solvedCount} soru · ${topic.correctCount} doğru",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
}

/** Backend'deki Permissions kurallarının kullanıcıya anlatımı. */
private fun permissionText(user: UserDto): String = when {
    user.role == "ADMIN" -> "Yönetici"
    user.role == "MODERATOR" || user.reputation >= 1000 -> "Moderasyon yetkin var"
    user.reputation >= 200 -> "Doğrulayıcısın: şikayetlerin 2 kat sayılır"
    user.role == "EDITOR" || user.reputation >= 50 -> "Soruların doğrudan yayına girer"
    else -> "50 itibarda soruların doğrudan yayına girer"
}
