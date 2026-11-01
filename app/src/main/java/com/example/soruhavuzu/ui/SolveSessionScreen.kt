package com.example.soruhavuzu.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.material3.CircularProgressIndicator
import com.example.soruhavuzu.data.remote.AttemptResponse
import com.example.soruhavuzu.data.remote.ReportReason
import com.example.soruhavuzu.data.remote.SessionType
import com.example.soruhavuzu.ui.practice.SessionState
import com.example.ui.theme.difficultyColorForBucket
import com.example.ui.theme.difficultyLabelForBucket

@Composable
fun SolveSessionScreen(
    session: SessionState,
    resolveImageUrl: (String) -> String,
    onSelectOption: (Int) -> Unit,
    onSubmitAnswer: () -> Unit,
    onNextQuestion: () -> Unit,
    onExpandToNeighborRange: (IntRange) -> Unit,
    onToggleFavorite: () -> Unit,
    onReportQuestion: (ReportReason, String) -> Unit,
    onFinishSession: () -> Unit,
    onCloseSession: () -> Unit,
    onDismissError: () -> Unit
) {
    BackHandler {
        onCloseSession()
    }

    var showReportModal by remember { mutableStateOf(false) }

    if (session.isFinished) {
        SessionSummaryView(session = session, onCloseSession = onCloseSession)
        return
    }

    if (session.question == null && (session.isLoading || session.error != null)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (session.isLoading) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(12.dp))
                Text("Soru getiriliyor…", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(
                    text = session.error.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onNextQuestion) { Text("Tekrar Dene") }
                TextButton(onClick = onCloseSession) { Text("Ana Sayfaya Dön") }
            }
        }
        return
    }

    // Seçilen kovada soru biterse: "7'de soru kalmadı, 6-8 aralığından devam edelim mi?" Boş ekran gösterilmez!
    if (session.question == null) {
        val suggested = session.suggestedRange
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    if (suggested != null) {
                        Text(
                            text = if (session.filter.minBucket == session.filter.maxBucket) "${session.filter.minBucket} zorlukta çözmediğin soru kalmadı!" else "${session.filter.minBucket}–${session.filter.maxBucket} aralığında çözmediğin soru kalmadı!",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${suggested.first}–${suggested.last} aralığından devam edelim mi?",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = { onExpandToNeighborRange(suggested) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("expand_neighbor_bucket_button")
                        ) {
                            Text("${suggested.first}–${suggested.last} Zorluk Aralığından Devam Et")
                        }
                    } else {
                        Text(
                            text = "Bu seçimde çözmediğin soru kalmadı!",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onFinishSession,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Oturum Özetini Gör")
                    }
                }
            }
        }
        return
    }

    val q = session.question
    val eval = session.evaluation
    val bucketColor = difficultyColorForBucket(q.difficultyBucket)
    val optionLabels = listOf("A", "B", "C", "D", "E")

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("solve_session_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top Session Bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onCloseSession,
                        modifier = Modifier.testTag("close_session_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Oturumu Kapat")
                    }
                    Column {
                        Text(
                            text = session.type.labelTr,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        val subLabel = if (session.type == SessionType.CHALLENGE) {
                            "Hedef başarı: %${session.challengeTargetPercent} · Seri: ${session.consecutiveCorrects}D / ${session.consecutiveWrongs}Y"
                        } else {
                            "Çözülen: ${session.solved} · Doğru: ${session.correct}"
                        }
                        Text(
                            text = subLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.testTag("favorite_question_button")
                    ) {
                        Icon(
                            imageVector = if (q.isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Favoriye Ekle",
                            tint = if (q.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { showReportModal = true },
                        modifier = Modifier.testTag("report_question_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Flag,
                            contentDescription = "Soruyu Şikayet Et",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        // Question Metadata Badges (Difficulty Bucket, Uploader Prior, Topic)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = bucketColor,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "Zorluk: ${q.difficultyBucket}/10 (${difficultyLabelForBucket(q.difficultyBucket)})",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "${q.subjectName} · ${q.topicName}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Question Body Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (q.firstAttemptCount == 0) "Bu soruyu ilk çözen sensin" else "${q.firstAttemptCount} kişi çözdü",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (q.alreadySolved) {
                            Text(
                                text = "Daha önce çözdün",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = q.bodyText,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )

                    if (!q.imageUrl.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        AsyncImage(
                            model = resolveImageUrl(q.imageUrl),
                            contentDescription = "Soru Görseli",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(190.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
        }

        // Şıklar (A..E). Doğru cevap ancak sunucu değerlendirmeyi döndürünce görünür.
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                q.options.forEachIndexed { idx, optionText ->
                    val isSelected = session.selectedOption == idx
                    val isCorrectOption = eval != null && eval.correctOptionIndex == idx
                    val isWrongSelected = eval != null && isSelected && !eval.isCorrect

                    val containerColor = when {
                        isCorrectOption -> Color(0xFFDCFCE7)
                        isWrongSelected -> Color(0xFFFEE2E2)
                        isSelected -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surface
                    }
                    val borderColor = when {
                        isCorrectOption -> Color(0xFF059669)
                        isWrongSelected -> Color(0xFFDC2626)
                        isSelected -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(enabled = eval == null) { onSelectOption(idx) }
                            .testTag("option_row_$idx"),
                        shape = RoundedCornerShape(14.dp),
                        color = containerColor,
                        border = BorderStroke(width = if (isSelected || isCorrectOption) 2.dp else 1.dp, color = borderColor)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = borderColor,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = optionLabels.getOrElse(idx) { "${idx + 1}" },
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = optionText,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isCorrectOption || isWrongSelected) Color(0xFF0F172A) else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // Submit Button OR Instant Evaluation + Elo Calibration Feedback + Solution Card
        item {
            if (eval == null) {
                session.error?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDismissError() }
                            .padding(bottom = 8.dp)
                    )
                }
                Button(
                    onClick = onSubmitAnswer,
                    enabled = session.selectedOption != null && !session.isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("submit_answer_button"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = if (session.isLoading) "Gönderiliyor…" else "Cevabı Onayla",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                EvaluationAndSolutionCard(
                    eval = eval,
                    isLoadingNext = session.isLoading,
                    onNextQuestion = onNextQuestion,
                    onFinishSession = onFinishSession
                )
            }
        }
    }

    if (showReportModal) {
        ReportQuestionDialog(
            onDismiss = { showReportModal = false },
            onSubmit = { reason, note ->
                showReportModal = false
                onReportQuestion(reason, note)
            }
        )
    }
}

@Composable
private fun EvaluationAndSolutionCard(
    eval: AttemptResponse,
    isLoadingNext: Boolean,
    onNextQuestion: () -> Unit,
    onFinishSession: () -> Unit
) {
    val headerColor = if (eval.isCorrect) Color(0xFF059669) else Color(0xFFDC2626)
    val deltaLevel = eval.newLevel - eval.oldLevel

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("evaluation_result_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(2.dp, headerColor)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (eval.isCorrect) Icons.Default.CheckCircle else Icons.Default.Close,
                    contentDescription = null,
                    tint = headerColor,
                    modifier = Modifier.size(28.dp)
                )
                Column {
                    Text(
                        text = if (eval.isCorrect) "Doğru Cevap!" else "Yanlış Cevap (Yanlışlarıma Eklendi)",
                        style = MaterialTheme.typography.titleLarge,
                        color = headerColor,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (eval.wasFirstAttempt) {
                            "İlk denemen olduğu için seviyen ve sorunun zorluğu güncellendi."
                        } else {
                            "Bu soruyu daha önce çözdüğün için seviyen değişmedi."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Elo / Rasch Live Telemetry Box
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "KALİBRASYON",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Seviyen", style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = "${eval.oldLevel} → ${eval.newLevel} (${"%+.1f".format(deltaLevel)})",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Soru Zorluğu", style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = "${eval.oldDifficultyBucket}/10 → ${eval.newDifficultyBucket}/10",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Bu soruyu çözme ihtimalin %${eval.expectedProbabilityPercent} olarak hesaplanmıştı.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Mandatory Solution Explanation
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = Color(0xFFD97706),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Adım Adım Çözüm Açıklaması",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = eval.solutionText,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onFinishSession,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("finish_session_button")
                ) {
                    Text("Oturumu Bitir")
                }
                Button(
                    onClick = onNextQuestion,
                    enabled = !isLoadingNext,
                    modifier = Modifier
                        .weight(1.4f)
                        .height(48.dp)
                        .testTag("next_question_button")
                ) {
                    Text("Sonraki Soru")
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun SessionSummaryView(
    session: SessionState,
    onCloseSession: () -> Unit
) {
    val netLevelDelta = (session.currentLevel ?: 0.0) - (session.startLevel ?: 0.0)
    val accuracy = if (session.solved == 0) 0 else (session.correct * 100) / session.solved

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("session_summary_screen"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = Color(0xFFD97706),
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Oturum Özeti",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = session.type.labelTr,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Çözülen", style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = "${session.solved}",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Doğruluk", style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = "%$accuracy",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF059669)
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Seviye Değişimi", style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = "${"%+.1f".format(netLevelDelta)} seviye",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (netLevelDelta >= 0) Color(0xFF059669) else Color(0xFFDC2626)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onCloseSession,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("return_home_from_summary_button")
                ) {
                    Text("Ana Havuza Dön")
                }
            }
        }
    }
}

@Composable
private fun ReportQuestionDialog(
    onDismiss: () -> Unit,
    onSubmit: (ReportReason, String) -> Unit
) {
    var selectedReason by remember { mutableStateOf(ReportReason.WRONG_KEY) }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Soruyu Bildir") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Yeterince şikayet gelen soru incelenmek üzere havuzdan çekilir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ReportReason.entries.forEach { reason ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedReason = reason }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedReason == reason,
                            onClick = { selectedReason = reason }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = reason.labelTr, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Açıklama / Hata Notu") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSubmit(selectedReason, note) }) {
                Text("Şikayeti Gönder")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal")
            }
        }
    )
}
