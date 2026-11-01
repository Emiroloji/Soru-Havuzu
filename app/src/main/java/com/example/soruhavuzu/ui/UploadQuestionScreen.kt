package com.example.soruhavuzu.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
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
import com.example.soruhavuzu.data.remote.CatalogExamDto
import com.example.soruhavuzu.data.remote.UploadedQuestionDto
import com.example.soruhavuzu.data.remote.UserDto
import com.example.soruhavuzu.ui.upload.UploadDraft
import com.example.ui.theme.difficultyColorForBucket
import com.example.ui.theme.difficultyLabelForBucket
import kotlinx.coroutines.launch

@Composable
fun UploadQuestionScreen(
    user: UserDto,
    catalog: List<CatalogExamDto>,
    myQuestions: List<UploadedQuestionDto>,
    isSubmitting: Boolean,
    /** Gönderim başarılı olursa formu temizlemek için onSuccess çağrılır. */
    onSubmitQuestion: (draft: UploadDraft, onSuccess: () -> Unit) -> Unit
) {
    // Konu seçimi: önce hedef sınav, sonra ders ve konu
    val topicChoices = remember(catalog) {
        catalog.flatMap { exam ->
            exam.subjects.flatMap { subject -> subject.topics.map { Triple(exam, subject, it) } }
        }
    }
    var selectedExamId by remember(catalog) {
        mutableStateOf(catalog.firstOrNull { it.id == user.targetExamId }?.id ?: catalog.firstOrNull()?.id)
    }
    var selectedTopicId by remember(catalog) { mutableStateOf<String?>(null) }
    var bodyText by remember { mutableStateOf("") }
    var pickedImage by remember { mutableStateOf<android.net.Uri?>(null) }
    val options = remember { mutableStateListOf("", "", "", "", "") }
    var correctOptionIndex by remember { mutableIntStateOf(0) }
    var uploaderRating by remember { mutableIntStateOf(7) }
    var solutionText by remember { mutableStateOf("") }
    // Beyan bilerek verilmeli: varsayılan olarak işaretsiz
    var originalityConfirmed by remember { mutableStateOf(false) }

    // Zero-permission Android Photo Picker (Play Policy compliant + EXIF stripped)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) pickedImage = uri
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
    val canPublishDirectly = user.reputation >= 50 || user.role in setOf("EDITOR", "MODERATOR", "ADMIN")

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
                        val rep = user.reputation
                        val gateTitle = if (canPublishDirectly) {
                            "İtibarın $rep: soruların doğrudan yayına girer"
                        } else {
                            "İtibarın $rep: soruların önce incelenir (50 itibarda doğrudan yayın)"
                        }
                        Text(
                            text = gateTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Verdiğin 1–10 zorluk puanı sadece başlangıç tahmini; sorunun gerçek zorluğu çözümlerle belirlenir.",
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
                        catalog.forEach { exam ->
                            FilterChip(
                                selected = selectedExamId == exam.id,
                                onClick = {
                                    selectedExamId = exam.id
                                    selectedTopicId = null
                                },
                                label = { Text(exam.code) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        topicChoices.filter { it.first.id == selectedExamId }.forEach { (_, subject, topic) ->
                            FilterChip(
                                selected = selectedTopicId == topic.id,
                                onClick = { selectedTopicId = topic.id },
                                label = { Text("${subject.name} · ${topic.name}") },
                                modifier = Modifier.testTag("upload_topic_${topic.id}")
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
                            Text(if (pickedImage == null) "Görsel Ekle (isteğe bağlı)" else "Görseli Değiştir")
                        }

                        if (pickedImage != null) {
                            IconButton(onClick = { pickedImage = null }) {
                                Icon(Icons.Default.Delete, contentDescription = "Görseli Kaldır", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }

                    if (pickedImage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        AsyncImage(
                            model = pickedImage,
                            contentDescription = "Seçilen Soru Görseli",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Fit
                        )
                        Text(
                            text = "Görsel sunucuda yeniden kaydedilir; konum ve cihaz bilgileri silinir.",
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
                                text = difficultyLabelForBucket(uploaderRating),
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

                    // Live Question Card Preview (S8: Canlı Önizleme)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("upload_live_preview_card")
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "CANLI SORU KARTI ÖNİZLEMESİ",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Surface(
                                    color = ratingColor,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "Zorluk $uploaderRating/10",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = bodyText.ifBlank { "Soru metni yazdığınızda burada öğrencinin göreceği şekilde canlı önizlenir..." },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            val optionLabels = listOf("A", "B", "C", "D", "E")
                            options.forEachIndexed { idx, opt ->
                                if (opt.isNotBlank() || idx < 2) {
                                    val prefix = if (idx == correctOptionIndex) "✓" else "○"
                                    Text(
                                        text = "$prefix ${optionLabels.getOrElse(idx) { "" }}) ${opt.ifBlank { "..." }}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (idx == correctOptionIndex) Color(0xFF059669) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    val topicId = selectedTopicId
                    if (topicId == null) {
                        Text(
                            text = "Göndermek için yukarıdan bir konu seç.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    Button(
                        onClick = {
                            onSubmitQuestion(
                                UploadDraft(
                                    topicId = topicId ?: return@Button,
                                    bodyText = bodyText,
                                    image = pickedImage,
                                    options = options.toList(),
                                    correctOptionIndex = correctOptionIndex,
                                    uploaderRating = uploaderRating,
                                    solutionText = solutionText,
                                    originalityConfirmed = originalityConfirmed
                                )
                            ) {
                                bodyText = ""
                                pickedImage = null
                                for (i in options.indices) options[i] = ""
                                solutionText = ""
                                originalityConfirmed = false
                            }
                        },
                        enabled = topicId != null && originalityConfirmed && !isSubmitting,
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
                            text = if (isSubmitting) "Gönderiliyor…" else "Soruyu Havuza Gönder ($uploaderRating/10)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Step 4: My Uploaded Questions & Calibration History
        if (myQuestions.isNotEmpty()) {
            item {
                Text(
                    text = "Yüklediklerim (${myQuestions.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            items(myQuestions, key = { it.id }) { myQ ->
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
                                text = myQ.topicName,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = statusLabel(myQ.status),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = myQ.bodyPreview,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Senin tahminin: ${myQ.uploaderRating}/10 → güncel zorluk: ${myQ.difficultyBucket}/10 · ${myQ.firstAttemptCount} çözüm" +
                                (myQ.accuracyPercent?.let { " · %$it doğru" } ?: ""),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun statusLabel(status: String) = when (status) {
    "ACTIVE" -> "Yayında"
    "IN_REVIEW" -> "İncelemede"
    "FLAGGED" -> "Şikayet nedeniyle incelemede"
    "REMOVED" -> "Kaldırıldı"
    else -> status
}
