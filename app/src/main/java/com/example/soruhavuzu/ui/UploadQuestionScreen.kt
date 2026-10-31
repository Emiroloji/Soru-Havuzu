package com.example.soruhavuzu.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Publish
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.soruhavuzu.data.QuestionStatus
import com.example.soruhavuzu.domain.CalibrationEngine
import com.example.ui.theme.difficultyColorForBucket
import com.example.ui.theme.difficultyLabelForBucket
import kotlinx.coroutines.launch

@Composable
fun UploadQuestionScreen(
    uiState: SoruHavuzuUiState,
    onSanitizeImage: suspend (android.net.Uri) -> Pair<String, String>?,
    onSubmitQuestion: (
        topicId: String,
        bodyText: String,
        imageUri: String?,
        imagePhash: String?,
        options: List<String>,
        correctOptionIndex: Int,
        uploaderRating: Int,
        solutionText: String,
        originalityConfirmed: Boolean,
        onReset: () -> Unit
    ) -> Unit
) {
    val scope = rememberCoroutineScope()
    val topics = uiState.topics
    var selectedTopicId by remember(topics) {
        mutableStateOf(topics.firstOrNull()?.id ?: "top_fonksiyonlar")
    }
    var bodyText by remember { mutableStateOf("") }
    var sanitizedImageUri by remember { mutableStateOf<String?>(null) }
    var imagePhash by remember { mutableStateOf<String?>(null) }
    val options = remember { mutableStateListOf("", "", "", "", "") }
    var correctOptionIndex by remember { mutableIntStateOf(0) }
    var uploaderRating by remember { mutableIntStateOf(7) }
    var solutionText by remember { mutableStateOf("") }
    var originalityConfirmed by remember { mutableStateOf(true) }

    // Zero-permission Android Photo Picker (Play Policy compliant + EXIF stripped)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val res = onSanitizeImage(uri)
                if (res != null) {
                    sanitizedImageUri = res.first
                    imagePhash = res.second
                }
            }
        }
    }

    val mathShortcuts = listOf(
        "√(" to "√(",
        "x²" to "x²",
        "x³" to "x³",
        "π" to "π",
        "≤" to " ≤ ",
        "≥" to " ≥ ",
        "≠" to " ≠ ",
        "⇒" to " ⇒ ",
        "∈" to " ∈ ",
        "∪" to " ∪ ",
        "∩" to " ∩ ",
        "a/b" to "(a/b)",
        "f⁻¹(x)" to "f⁻¹(x)",
        "(f∘g)(x)" to "(f ∘ g)(x)"
    )

    val ratingColor = difficultyColorForBucket(uploaderRating)
    val profile = uiState.userProfile
    val initialLogit = CalibrationEngine.initialLogitFromUploaderRating(
        uploaderRating,
        profile?.uploaderBias ?: 0.0
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("upload_question_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Reputation Gate Header
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        val rep = profile?.reputation ?: 65
                        val gateTitle = if (profile?.canDirectPublish == true) {
                            "Güvenilir Yükleyen (İtibar: $rep ≥ 50) — Doğrudan Yayın Aktif"
                        } else {
                            "Standart Yükleyen (İtibar: $rep < 50) — İnceleme Kuyruğu Onaylı"
                        }
                        Text(
                            text = gateTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Verdiğiniz 1–10 zorluk puanı başlangıç tahminidir; öğrenciler çözdükçe Elo/Rasch motoruyla kalibre edilir.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }

        // Step 1: Topic Selection
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "1. Konu ve Matematik Sembol Editörü",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        topics.forEach { top ->
                            FilterChip(
                                selected = selectedTopicId == top.id,
                                onClick = { selectedTopicId = top.id },
                                label = { Text(top.name) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Hızlı Matematik Sembol Çubuğu (Dokunarak Ekle):",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        mathShortcuts.forEach { (label, insertText) ->
                            AssistChip(
                                onClick = { bodyText += insertText },
                                label = { Text(label, style = MaterialTheme.typography.labelMedium) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = bodyText,
                        onValueChange = { bodyText = it },
                        label = { Text("Soru Gövdesi (Metin & Matematik İfadeleri)") },
                        placeholder = { Text("Örn: Gerçek sayılarda tanımlı f(x) = 2x + 3 fonksiyonu için...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("upload_body_input"),
                        minLines = 3
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Image attachment via Android Photo Picker + EXIF Sanitizer
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.testTag("pick_image_button")
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (sanitizedImageUri == null) "Cihazdan Görsel Seç (EXIF Silinir)" else "Görseli Değiştir")
                        }

                        if (sanitizedImageUri != null) {
                            IconButton(onClick = {
                                sanitizedImageUri = null
                                imagePhash = null
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Görseli Kaldır", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }

                    if (sanitizedImageUri != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        AsyncImage(
                            model = sanitizedImageUri,
                            contentDescription = "Seçilen Soru Görseli",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Fit
                        )
                        Text(
                            text = "✓ EXIF konum/cihaz verileri temizlendi · Görsel pHash: #$imagePhash",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF059669)
                        )
                    }
                }
            }
        }

        // Step 2: Options (2..5) & Correct Option Selection
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "2. Şıklar (2–5 Şık) ve Doğru Cevap Anahtarı",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row {
                            if (options.size > 2) {
                                OutlinedButton(
                                    onClick = {
                                        options.removeAt(options.lastIndex)
                                        if (correctOptionIndex >= options.size) {
                                            correctOptionIndex = options.lastIndex
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("- Şık Sil")
                                }
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            if (options.size < 5) {
                                OutlinedButton(
                                    onClick = { options.add("") },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Text("Şık Ekle")
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    val optionLetters = listOf("A", "B", "C", "D", "E")

                    options.forEachIndexed { idx, textVal ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = correctOptionIndex == idx,
                                onClick = { correctOptionIndex = idx },
                                modifier = Modifier.testTag("correct_option_radio_$idx")
                            )
                            OutlinedTextField(
                                value = textVal,
                                onValueChange = { options[idx] = it },
                                label = {
                                    val suffix = if (correctOptionIndex == idx) " (DOĞRU ŞIK)" else ""
                                    Text("${optionLetters[idx]} Şıkkı$suffix")
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("upload_option_input_$idx"),
                                singleLine = true
                            )
                        }
                    }
                }
            }
        }

        // Step 3: Initial Difficulty Rating (1..10) + Mandatory Solution + Originality Checkbox
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "3. Yükleyen Zorluk Puanı: $uploaderRating / 10",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            color = ratingColor,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "${difficultyLabelForBucket(uploaderRating)} (d₀=${"%+.2f".format(initialLogit)})",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Slider(
                        value = uploaderRating.toFloat(),
                        onValueChange = { uploaderRating = it.toInt().coerceIn(1, 10) },
                        valueRange = 1f..10f,
                        steps = 8,
                        modifier = Modifier.testTag("upload_difficulty_slider")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = solutionText,
                        onValueChange = { solutionText = it },
                        label = { Text("Zorunlu Çözüm Açıklaması (Adım Adım)") },
                        placeholder = { Text("Öğrenci soruyu çözdükten hemen sonra gösterilecek detaylı çözüm...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("upload_solution_input"),
                        minLines = 3
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { originalityConfirmed = !originalityConfirmed },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = originalityConfirmed,
                            onCheckedChange = { originalityConfirmed = it },
                            modifier = Modifier.testTag("originality_checkbox")
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Bu sorunun ÖSYM çıkmış sorusu veya telifli bir yayınevi kopyası olmadığını, özgün içerik olduğunu beyan ederim.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            onSubmitQuestion(
                                selectedTopicId,
                                bodyText,
                                sanitizedImageUri,
                                imagePhash,
                                options.toList(),
                                correctOptionIndex,
                                uploaderRating,
                                solutionText,
                                originalityConfirmed
                            ) {
                                bodyText = ""
                                sanitizedImageUri = null
                                imagePhash = null
                                for (i in options.indices) options[i] = ""
                                solutionText = ""
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("publish_question_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ratingColor)
                    ) {
                        Icon(Icons.Default.Publish, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Soruyu Havuza Gönder ($uploaderRating/10)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Step 4: My Uploaded Questions & Calibration History
        if (uiState.myUploadedQuestions.isNotEmpty()) {
            item {
                Text(
                    text = "Yüklediklerim (${uiState.myUploadedQuestions.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            items(uiState.myUploadedQuestions, key = { it.question.id }) { myQ ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "#${myQ.question.id} · ${myQ.topic.name}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = QuestionStatus.valueOf(myQ.question.status).labelTr,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = myQ.version.bodyText,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "İlk Tahminin: ${myQ.question.uploaderRating}/10 → Güncel Kalibrasyon: ${myQ.question.difficultyBucket}/10 · Çözüm: ${myQ.question.firstAttemptCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
