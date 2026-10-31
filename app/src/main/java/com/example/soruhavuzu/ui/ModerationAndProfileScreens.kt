package com.example.soruhavuzu.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.soruhavuzu.data.DockerClientFactory
import com.example.soruhavuzu.data.QuestionStatus
import com.example.soruhavuzu.data.QuestionWithDetails
import com.example.soruhavuzu.data.UserRole
import com.example.soruhavuzu.domain.CalibrationEngine
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.difficultyColorForBucket

@Composable
fun SavedReviewScreen(
    uiState: SoruHavuzuUiState,
    onStartReviewSession: () -> Unit,
    onRemoveFromWrongNotes: (String) -> Unit,
    onToggleFavorite: (String) -> Unit
) {
    val wrongList = uiState.wrongNoteQuestions
    val favList = uiState.favoriteQuestions

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("saved_review_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Yanlışlarım ve Favoriler (Çevrimdışı Okunabilir)",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Yanlış çözdüğün sorular otomatik olarak buraya kaydedilir. Tekrar modunda (REVIEW) çözdüğün sorular iç zorluk kalibrasyonunu (isFirst = false) bozmaz.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onStartReviewSession,
                        enabled = wrongList.isNotEmpty() || favList.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("start_saved_review_button")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Kayıtlı Soruları Tekrar Çöz (${wrongList.size + favList.size})")
                    }
                }
            }
        }

        item {
            Text(
                text = "Yanlış Çözdüğüm Sorular (${wrongList.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (wrongList.isEmpty()) {
            item {
                Text(
                    text = "Henüz yanlışlarım listesinde soru yok. Çözüm sırasında hata yaptığın sorular çözüm açıklamalarıyla burada saklanır.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(wrongList, key = { "wrong_${it.question.id}" }) { item ->
                OfflineSavedQuestionCard(
                    item = item,
                    isWrongCard = true,
                    onRemoveWrong = { onRemoveFromWrongNotes(item.question.id) },
                    onToggleFav = { onToggleFavorite(item.question.id) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Favori Sorularım (${favList.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (favList.isEmpty()) {
            item {
                Text(
                    text = "Soru çözme ekranında yer imi ikonuna dokunarak soruları favorilerine ekleyebilirsin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(favList, key = { "fav_${it.question.id}" }) { item ->
                OfflineSavedQuestionCard(
                    item = item,
                    isWrongCard = false,
                    onRemoveWrong = {},
                    onToggleFav = { onToggleFavorite(item.question.id) }
                )
            }
        }
    }
}

@Composable
private fun OfflineSavedQuestionCard(
    item: QuestionWithDetails,
    isWrongCard: Boolean,
    onRemoveWrong: () -> Unit,
    onToggleFav: () -> Unit
) {
    val optionLetters = listOf("A", "B", "C", "D", "E")
    val correctLetter = optionLetters.getOrElse(item.version.correctOptionIndex) { "?" }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${item.topic.name} · Zorluk ${item.question.difficultyBucket}/10",
                    style = MaterialTheme.typography.labelLarge,
                    color = difficultyColorForBucket(item.question.difficultyBucket),
                    fontWeight = FontWeight.Bold
                )
                if (isWrongCard) {
                    TextButton(onClick = onRemoveWrong) {
                        Text("Öğrendim / Listeden Çıkar")
                    }
                } else {
                    TextButton(onClick = onToggleFav) {
                        Text("Favoriden Çıkar")
                    }
                }
            }
            Text(text = item.version.bodyText, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "Doğru Cevap: $correctLetter (${item.options.getOrElse(item.version.correctOptionIndex) { "" }})",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF059669)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.version.solutionText,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
fun ModerationPanelScreen(
    uiState: SoruHavuzuUiState,
    onApproveQuestion: (String) -> Unit,
    onRemoveQuestion: (String, String) -> Unit,
    onCreateNewVersion: (String, String, List<String>, Int, String, String) -> Unit
) {
    var editingQuestion by remember { mutableStateOf<QuestionWithDetails?>(null) }
    val queue = uiState.moderationQueueQuestions

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
                            text = "Moderasyon, İnceleme Kuyruğu & Anahtar Şüphesi",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = "IN_REVIEW, FLAGGED ve istatistiksel olarak cevap anahtarı hatalı olabilecek (KeySuspect) sorular burada denetlenir. Düzenleme yeni bir QuestionVersion üretir.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }

        if (queue.isEmpty()) {
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
            items(queue, key = { it.question.id }) { item ->
                ModerationQuestionCard(
                    item = item,
                    onApprove = { onApproveQuestion(item.question.id) },
                    onEditNewVersion = { editingQuestion = item },
                    onRemove = { onRemoveQuestion(item.question.id, "Moderasyon incelemesinde uygun bulunmadı") }
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
                onCreateNewVersion(targetEdit.question.id, body, opts, correctIdx, sol, note)
            }
        )
    }
}

@Composable
private fun ModerationQuestionCard(
    item: QuestionWithDetails,
    onApprove: () -> Unit,
    onEditNewVersion: () -> Unit,
    onRemove: () -> Unit
) {
    val q = item.question
    val ver = item.version
    val optionCounts = CalibrationEngine.parseOptionCounts(q.optionSelectionCountsCsv, item.options.size)
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
                        text = if (q.keySuspect) "⚠ ANAHTAR ŞÜPHESİ (KeySuspect)" else "Durum: ${QuestionStatus.valueOf(q.status).labelTr}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (q.keySuspect) Color(0xFF92400E) else MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Text(
                    text = "Versiyon v${ver.versionNumber} · @${q.uploaderName}",
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
            Text(text = ver.bodyText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))

            // Option selection telemetry bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item.options.forEachIndexed { idx, optText ->
                    val picks = optionCounts.getOrElse(idx) { 0 }
                    val isKeyed = idx == ver.correctOptionIndex
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
                text = "Çözüm: ${ver.solutionText}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onRemove,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Kaldır")
                }
                OutlinedButton(
                    onClick = onEditNewVersion,
                    modifier = Modifier
                        .weight(1.3f)
                        .testTag("edit_version_button_${q.id}")
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Yeni Versiyon")
                }
                Button(
                    onClick = onApprove,
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
    item: QuestionWithDetails,
    onDismiss: () -> Unit,
    onSaveNewVersion: (String, List<String>, Int, String, String) -> Unit
) {
    var bodyText by remember { mutableStateOf(item.version.bodyText) }
    val options = remember { mutableStateListOf(*item.options.toTypedArray()) }
    var correctIdx by remember { mutableIntStateOf(item.version.correctOptionIndex) }
    var solutionText by remember { mutableStateOf(item.version.solutionText) }
    var editorNote by remember { mutableStateOf("Cevap anahtarı ve çözüm açıklaması doğrulandı") }
    val letters = listOf("A", "B", "C", "D", "E")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yeni Soru Versiyonu Oluştur (v${item.version.versionNumber + 1})") },
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
                Text("v${item.version.versionNumber + 1} Kaydet & Yayına Al")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("İptal") }
        }
    )
}

@Composable
fun ProfileAndDockerScreen(
    uiState: SoruHavuzuUiState,
    onSaveSettings: (String, String, Int, UserRole, Int, String, Boolean) -> Unit,
    onTestDocker: (String) -> Unit,
    onExportData: () -> Unit,
    onDismissExportDialog: () -> Unit,
    onResetAccount: () -> Unit
) {
    val profile = uiState.userProfile ?: return
    var nickname by remember(profile.nickname) { mutableStateOf(profile.nickname) }
    var targetExam by remember(profile.targetExam) { mutableStateOf(profile.targetExam) }
    var birthYearText by remember(profile.birthYear) { mutableStateOf(profile.birthYear.toString()) }
    var selectedRole by remember(profile.role) { mutableStateOf(UserRole.valueOf(profile.role)) }
    var reputation by remember(profile.reputation) { mutableIntStateOf(profile.reputation) }
    var dockerUrl by remember(profile.dockerServerUrl) { mutableStateOf(profile.dockerServerUrl) }
    var syncDocker by remember(profile.syncWithDocker) { mutableStateOf(profile.syncWithDocker) }
    var showDockerComposeModal by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("profile_docker_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Topic-based Theta Mastery Breakdown
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Konu Bazlı Yetenek (θ) ve İstatistikler",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val masteryMap = uiState.topicMasteries.associateBy { it.topicId }
                    uiState.topics.forEach { topic ->
                        val m = masteryMap[topic.id]
                        val topicTheta = m?.theta ?: profile.globalTheta
                        val level10 = CalibrationEngine.thetaToDisplayLevel(topicTheta)
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = topic.name, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = "$level10/10 (θ=${"%+.2f".format(topicTheta)} · ${m?.solvedCount ?: 0} çözüm)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { (level10 / 10.0).toFloat().coerceIn(0.08f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(7.dp)
                            )
                        }
                    }
                }
            }
        }

        // Single-Service Docker Backend Configuration Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudSync, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Tek Servis (Docker) Sunucu Bağlantısı",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "AWS S3 ve parçalı servisler olmadan, iOS ve Android istemcilerinin bağlandığı tek konteynerlik Docker sunucu adresiniz:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = dockerUrl,
                        onValueChange = { dockerUrl = it },
                        label = { Text("Docker API Base URL") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("docker_url_input"),
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Çözüm ve soruları Docker sunucusuyla eşitle", style = MaterialTheme.typography.bodySmall)
                        Switch(checked = syncDocker, onCheckedChange = { syncDocker = it })
                    }

                    if (uiState.dockerConnectionStatus != null) {
                        Text(
                            text = uiState.dockerConnectionStatus,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onTestDocker(dockerUrl) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Bağlantıyı Test Et")
                        }
                        OutlinedButton(
                            onClick = { showDockerComposeModal = true },
                            modifier = Modifier
                                .weight(1.2f)
                                .testTag("show_docker_blueprint_button")
                        ) {
                            Text("docker-compose Şablonu")
                        }
                    }
                }
            }
        }

        // User Profile, Reputation Gate Simulator & KVKK Settings
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Profil, İtibar Kapıları & KVKK Ayarları",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = nickname,
                        onValueChange = { nickname = it },
                        label = { Text("Takma Ad (Ad-Soyad Zorunlu Değildir)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("TYT", "AYT", "KPSS").forEach { ex ->
                            FilterChip(
                                selected = targetExam == ex,
                                onClick = { targetExam = ex },
                                label = { Text(ex) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "İtibar Kapısı Test Kısayolları (Mevcut: $reputation ★)",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = { reputation = 20; selectedRole = UserRole.USER },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(4.dp)
                        ) {
                            Text("Yeni (20★)", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = { reputation = 65; selectedRole = UserRole.USER },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(4.dp)
                        ) {
                            Text("Güvenilir (65★)", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = { reputation = 250; selectedRole = UserRole.USER },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(4.dp)
                        ) {
                            Text("Doğrulayıcı (250★)", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = { reputation = 1200; selectedRole = UserRole.MODERATOR },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(4.dp)
                        ) {
                            Text("Mod (1200★)", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            onSaveSettings(
                                nickname,
                                targetExam,
                                birthYearText.toIntOrNull() ?: 2007,
                                selectedRole,
                                reputation,
                                dockerUrl,
                                syncDocker
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("save_profile_settings_button")
                    ) {
                        Text("Profil ve Sunucu Ayarlarını Kaydet")
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onExportData,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("export_kvkk_data_button")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Verilerimi İndir")
                        }
                        OutlinedButton(
                            onClick = onResetAccount,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Hesabı Sıfırla")
                        }
                    }
                }
            }
        }

        // Reputation Ledger History
        item {
            Text(
                text = "İtibar Defteri (Reputation Ledger)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        items(uiState.reputationEvents, key = { it.id }) { ev ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = ev.reason,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (ev.delta >= 0) "+${ev.delta} ★" else "${ev.delta} ★",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (ev.delta >= 0) Color(0xFF059669) else Color(0xFFDC2626),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    if (showDockerComposeModal) {
        AlertDialog(
            onDismissRequest = { showDockerComposeModal = false },
            title = { Text("Tek Servis Docker Mimarisi (docker-compose.yml)") },
            text = {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = DockerClientFactory.singleServiceDockerBlueprint,
                        fontFamily = JetBrainsMonoFamily,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showDockerComposeModal = false }) {
                    Text("Tamam")
                }
            }
        )
    }

    if (uiState.exportedJsonData != null) {
        AlertDialog(
            onDismissRequest = onDismissExportDialog,
            title = { Text("KVKK Veri İndirme Çıktısı (JSON)") },
            text = {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = uiState.exportedJsonData,
                        fontFamily = JetBrainsMonoFamily,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(onClick = onDismissExportDialog) {
                    Text("Kapat")
                }
            }
        )
    }
}
