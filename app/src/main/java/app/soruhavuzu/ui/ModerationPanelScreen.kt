package app.soruhavuzu.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import app.soruhavuzu.data.remote.ModerationItemDto
import app.soruhavuzu.data.remote.NewVersionRequest
import app.soruhavuzu.data.remote.ReportReason
import app.soruhavuzu.ui.moderation.ModerationState

@Composable
fun ModerationPanelScreen(
    state: ModerationState,
    resolveImageUrl: (String) -> String,
    onApproveQuestion: (String) -> Unit,
    onRemoveQuestion: (id: String, reason: String) -> Unit,
    onCreateNewVersion: (id: String, NewVersionRequest) -> Unit,
    onRetry: () -> Unit
) {
    var editingQuestion by remember { mutableStateOf<ModerationItemDto?>(null) }
    var removingQuestion by remember { mutableStateOf<ModerationItemDto?>(null) }
    val queue = state.queue

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("moderation_panel_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Gavel,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "İnceleme Kuyruğu",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = "İncelemeyi bekleyen, şikayet edilen ve cevap anahtarı şüpheli sorular. Düzeltme yeni bir versiyon oluşturur; anahtar değişirse sorunun zorluğu sıfırdan hesaplanır.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                        )
                    }
                }
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

        if (queue.isEmpty() && !state.isLoading) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("İnceleme kuyruğunda bekleyen soru bulunmuyor.", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        } else {
            items(queue, key = { it.id }) { item ->
                ModerationQuestionCard(
                    item = item,
                    resolveImageUrl = resolveImageUrl,
                    isBusy = item.id in state.busyIds,
                    onApprove = { onApproveQuestion(item.id) },
                    onEditNewVersion = { editingQuestion = item },
                    onRemove = { removingQuestion = item }
                )
            }
        }
    }

    val targetEdit = editingQuestion
    if (targetEdit != null) {
        EditQuestionVersionDialog(
            item = targetEdit,
            onDismiss = { editingQuestion = null },
            onSaveNewVersion = { body, opts, correctIdx, sol, note ->
                editingQuestion = null
                onCreateNewVersion(targetEdit.id, NewVersionRequest(body, opts, correctIdx, sol, note))
            }
        )
    }

    val targetRemove = removingQuestion
    if (targetRemove != null) {
        RemoveQuestionDialog(
            onDismiss = { removingQuestion = null },
            onConfirm = { reason ->
                removingQuestion = null
                onRemoveQuestion(targetRemove.id, reason)
            }
        )
    }
}

@Composable
private fun RemoveQuestionDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Soruyu Kaldır") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Yayınlanmış bir soruysa yükleyen 15 itibar kaybeder ve bu nedeni itibar geçmişinde görür. Haklı çıkan şikayetçiler 2 itibar kazanır.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it.take(500) },
                    label = { Text("Kaldırma nedeni") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(reason.trim()) },
                enabled = reason.trim().length >= 3,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text("Kaldır") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )
}

@Composable
private fun ModerationQuestionCard(
    item: ModerationItemDto,
    resolveImageUrl: (String) -> String,
    isBusy: Boolean,
    onApprove: () -> Unit,
    onEditNewVersion: () -> Unit,
    onRemove: () -> Unit
) {
    val q = item
    val optionCounts = item.optionSelectionCounts
    val optionLetters = listOf("A", "B", "C", "D", "E")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("moderation_card_${q.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = 1.5.dp,
            color = if (q.keySuspect) Color(0xFFD97706) else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = if (q.keySuspect) Color(0xFFFEF3C7) else MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (q.keySuspect) "⚠ Cevap anahtarı şüpheli" else statusLabel(q.status),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (q.keySuspect) Color(0xFF92400E) else MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Text(
                    text = "v${q.versionNumber} · @${q.uploaderNickname} · ${q.topicName} · ${q.difficultyBucket}/10",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (q.keySuspect && !q.keySuspectReason.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = q.keySuspectReason,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB45309),
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(text = q.bodyText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            q.imageUrl?.let { url ->
                Spacer(modifier = Modifier.height(8.dp))
                AsyncImage(
                    model = resolveImageUrl(url),
                    contentDescription = "Soru görseli",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Fit
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Option selection telemetry bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item.options.forEachIndexed { idx, optText ->
                    val picks = optionCounts.getOrElse(idx) { 0 }
                    val isKeyed = idx == q.correctOptionIndex
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = if (isKeyed) Color(0xFFDCFCE7) else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "${optionLetters.getOrElse(idx) { "?" }}: $optText",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isKeyed) Color(0xFF065F46) else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                            Text(
                                text = "$picks seçim",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isKeyed) Color(0xFF065F46) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Çözüm: ${q.solutionText}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Yükleyenin tahmini ${q.uploaderRating}/10 · ${q.firstAttemptCount} çözüm, ${q.correctFirstCount} doğru",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (q.reports.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Şikayetler (ağırlıklı skor ${q.weightedReportScore})",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                q.reports.forEach { report ->
                    Text(
                        text = "• @${report.reporterNickname} (x${report.weight}): " +
                            (ReportReason.entries.firstOrNull { it.name == report.reason }?.labelTr ?: report.reason) +
                            report.note.takeIf { it.isNotBlank() }?.let { " — $it" }.orEmpty(),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onRemove,
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Kaldır")
                }
                OutlinedButton(
                    onClick = onEditNewVersion,
                    enabled = !isBusy,
                    modifier = Modifier
                        .weight(1.3f)
                        .testTag("edit_version_button_${q.id}")
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Düzelt")
                }
                Button(
                    onClick = onApprove,
                    enabled = !isBusy,
                    modifier = Modifier
                        .weight(1.1f)
                        .testTag("approve_question_button_${q.id}")
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Onayla")
                }
            }
        }
    }
}

@Composable
private fun EditQuestionVersionDialog(
    item: ModerationItemDto,
    onDismiss: () -> Unit,
    onSaveNewVersion: (String, List<String>, Int, String, String) -> Unit
) {
    var bodyText by remember { mutableStateOf(item.bodyText) }
    val options = remember { mutableStateListOf(*item.options.toTypedArray()) }
    var correctIdx by remember { mutableIntStateOf(item.correctOptionIndex) }
    var solutionText by remember { mutableStateOf(item.solutionText) }
    var editorNote by remember { mutableStateOf("Cevap anahtarı ve çözüm açıklaması doğrulandı") }
    val letters = listOf("A", "B", "C", "D", "E")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Düzelt ve Yayına Al (v${item.versionNumber + 1})") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    OutlinedTextField(
                        value = bodyText,
                        onValueChange = { bodyText = it },
                        label = { Text("Soru Gövdesi") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                items(options.size) { idx ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = correctIdx == idx,
                            onClick = { correctIdx = idx }
                        )
                        OutlinedTextField(
                            value = options[idx],
                            onValueChange = { options[idx] = it },
                            label = { Text("${letters.getOrElse(idx) { "" }} Şıkkı") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                }
                item {
                    OutlinedTextField(
                        value = solutionText,
                        onValueChange = { solutionText = it },
                        label = { Text("Çözüm Açıklaması") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = editorNote,
                        onValueChange = { editorNote = it },
                        label = { Text("Versiyon Notu") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSaveNewVersion(bodyText, options.toList(), correctIdx, solutionText, editorNote)
            }) {
                Text("Kaydet ve Yayına Al")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("İptal") }
        }
    )
}

private fun statusLabel(status: String) = when (status) {
    "IN_REVIEW" -> "İnceleme bekliyor"
    "FLAGGED" -> "Şikayet nedeniyle havuzdan çekildi"
    "ACTIVE" -> "Yayında"
    else -> status
}
