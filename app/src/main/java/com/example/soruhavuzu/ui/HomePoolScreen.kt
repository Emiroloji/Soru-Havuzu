package com.example.soruhavuzu.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.soruhavuzu.data.QuestionWithDetails
import com.example.soruhavuzu.data.SessionType
import com.example.soruhavuzu.domain.CalibrationEngine
import com.example.ui.theme.AcademicTeal
import com.example.ui.theme.CalibrationAmber
import com.example.ui.theme.InkNavy
import com.example.ui.theme.InkNavyDark
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.difficultyColorForBucket
import com.example.ui.theme.difficultyLabelForBucket

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomePoolScreen(
    uiState: SoruHavuzuUiState,
    onSelectSingleBucket: (Int) -> Unit,
    onSelectRange: (Int, Int) -> Unit,
    onSelectTopic: (String?) -> Unit,
    onToggleIncludeSolved: (Boolean) -> Unit,
    onStartPractice: () -> Unit,
    onStartChallenge: () -> Unit,
    onStartReview: () -> Unit,
    onOpenQuestionDirect: (Int) -> Unit
) {
    val profile = uiState.userProfile
    val userTheta = profile?.globalTheta ?: 0.25
    val userLevel10 = CalibrationEngine.thetaToDisplayLevel(userTheta)
    val totalSolved = uiState.attempts.size
    val totalCorrect = uiState.attempts.count { it.isCorrect }
    val accuracyRate = if (totalSolved == 0) 0 else ((totalCorrect * 100) / totalSolved)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("home_pool_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. HERO ATELIER BANNER WITH LIVE THETA & ELO CALIBRATION VISUAL
        item {
            AtelierHeroCard(
                nickname = profile?.nickname ?: "MatematikGezgini",
                targetExam = profile?.targetExam ?: "TYT",
                reputation = profile?.reputation ?: 65,
                userTheta = userTheta,
                userLevel10 = userLevel10,
                totalSolved = totalSolved,
                accuracyRate = accuracyRate
            )
        }

        // 2. CORE FEATURE: "BANA 10 ÜZERİNDEN X ZORLUKTA SORU GETİR" SELECTOR CARD
        item {
            DifficultySelectorCard(
                selectedMin = uiState.selectedMinBucket,
                selectedMax = uiState.selectedMaxBucket,
                bucketCounts = uiState.bucketCounts,
                topics = uiState.topics,
                selectedTopicId = uiState.selectedTopicId,
                includeSolved = uiState.includeSolvedInPractice,
                matchingCount = uiState.matchingPracticeQuestionsCount,
                onSelectSingleBucket = onSelectSingleBucket,
                onSelectRange = onSelectRange,
                onSelectTopic = onSelectTopic,
                onToggleIncludeSolved = onToggleIncludeSolved,
                onStartPractice = onStartPractice
            )
        }

        // 3. "BENİ ZORLA" (CHALLENGE) & "YANLIŞLARIM / FAVORİLER" QUICK MODES
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ChallengeModeCard(
                    modifier = Modifier.weight(1f),
                    userTheta = userTheta,
                    onStartChallenge = onStartChallenge
                )
                ReviewQuickCard(
                    modifier = Modifier.weight(1f),
                    wrongCount = uiState.wrongNoteQuestions.size,
                    favCount = uiState.favoriteQuestions.size,
                    onStartReview = onStartReview
                )
            }
        }

        // 4. CANLI SORU HAVUZU ÖNİZLEMESİ (Zorluk Kovası & Gerçek Kalibrasyon Verisi)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Seçili Havuzdaki Sorular (${uiState.selectedMinBucket}–${uiState.selectedMaxBucket} Zorluk)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Yükleyen başlangıç puanı ve gerçek çözümlerle kalibre edilen güncel zorluklar",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        val previewQuestions = uiState.activeQuestions.filter { q ->
            val inBucket = q.question.difficultyBucket in uiState.selectedMinBucket..uiState.selectedMaxBucket
            val inTopic = uiState.selectedTopicId == null || q.topic.id == uiState.selectedTopicId
            inBucket && inTopic
        }

        if (previewQuestions.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Bu filtrede henüz soru yok",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Zorluk aralığını genişletebilir veya tüm konular seçeneğine geçebilirsiniz.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                onSelectTopic(null)
                                onSelectRange(1, 10)
                            },
                            modifier = Modifier.testTag("reset_filter_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Tüm Zorlukları Göster (1–10)")
                        }
                    }
                }
            }
        } else {
            items(previewQuestions, key = { it.question.id }) { item ->
                PoolQuestionPreviewRow(
                    item = item,
                    userTheta = userTheta,
                    onClickSolve = {
                        onOpenQuestionDirect(item.question.difficultyBucket)
                    }
                )
            }
        }
    }
}

@Composable
private fun AtelierHeroCard(
    nickname: String,
    targetExam: String,
    reputation: Int,
    userTheta: Double,
    userLevel10: Double,
    totalSolved: Int,
    accuracyRate: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = InkNavyDark),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(InkNavyDark, InkNavy, Color(0xFF0F766E))
                    )
                )
        ) {
            // Custom mathematical logistic curve background art
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(176.dp)
            ) {
                val path = Path()
                val w = size.width
                val h = size.height
                path.moveTo(0f, h * 0.85f)
                path.cubicTo(
                    w * 0.35f, h * 0.82f,
                    w * 0.55f, h * 0.18f,
                    w, h * 0.12f
                )
                drawPath(
                    path = path,
                    color = Color.White.copy(alpha = 0.12f),
                    style = Stroke(width = 3.dp.toPx())
                )
                drawCircle(
                    color = Color(0xFFFBBF24).copy(alpha = 0.18f),
                    radius = 56.dp.toPx(),
                    center = Offset(w * 0.86f, h * 0.28f)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = Color(0xFFFBBF24).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "$targetExam MATEMATİK HAVUZU",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFFDE68A),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = Color.White.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "İtibar: $reputation ★",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Merhaba, $nickname",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White
                        )
                    }

                    // Theta Badge
                    Surface(
                        color = Color.White.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "SEVİYEN",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF99F6E4)
                            )
                            Text(
                                text = "$userLevel10 / 10",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "θ = ${"%+.2f".format(userTheta)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HeroStatPill(
                        label = "Çözülen",
                        value = "$totalSolved Soru",
                        modifier = Modifier.weight(1f)
                    )
                    HeroStatPill(
                        label = "İsabet Oranı",
                        value = "%$accuracyRate",
                        modifier = Modifier.weight(1f)
                    )
                    HeroStatPill(
                        label = "Yükleme Yetkisi",
                        value = if (reputation >= 50) "Doğrudan Yayın" else "Kuyruk Onaylı",
                        modifier = Modifier.weight(1.2f)
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroStatPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Color.Black.copy(alpha = 0.24f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DifficultySelectorCard(
    selectedMin: Int,
    selectedMax: Int,
    bucketCounts: Map<Int, Int>,
    topics: List<com.example.soruhavuzu.data.TopicEntity>,
    selectedTopicId: String?,
    includeSolved: Boolean,
    matchingCount: Int,
    onSelectSingleBucket: (Int) -> Unit,
    onSelectRange: (Int, Int) -> Unit,
    onSelectTopic: (String?) -> Unit,
    onToggleIncludeSolved: (Boolean) -> Unit,
    onStartPractice: () -> Unit
) {
    val activeColor = difficultyColorForBucket((selectedMin + selectedMax) / 2)
    val rangeLabel = if (selectedMin == selectedMax) {
        "10 üzerinden $selectedMin Zorluk (${difficultyLabelForBucket(selectedMin)})"
    } else {
        "10 üzerinden $selectedMin–$selectedMax Zorluk Aralığı"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = activeColor
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Zorluk Seçerek Soru Çöz",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                Surface(
                    color = activeColor.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "$matchingCount Soru Hazır",
                        style = MaterialTheme.typography.labelMedium,
                        color = activeColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = rangeLabel,
                style = MaterialTheme.typography.titleMedium,
                color = activeColor,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 1..10 Interactive Difficulty Bars + Direct Number Tap
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                for (bucket in 1..10) {
                    val isSelected = bucket in selectedMin..selectedMax
                    val count = bucketCounts[bucket] ?: 0
                    val bColor = difficultyColorForBucket(bucket)
                    val bgColor by animateColorAsState(
                        targetValue = if (isSelected) bColor else MaterialTheme.colorScheme.surfaceVariant,
                        label = "bucketColor"
                    )
                    val textColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelectSingleBucket(bucket) }
                            .testTag("difficulty_bucket_$bucket"),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "${count}s",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(bgColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$bucket",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // RangeSlider for selecting an interval like 6..8
            RangeSlider(
                value = selectedMin.toFloat()..selectedMax.toFloat(),
                onValueChange = { range ->
                    onSelectRange(range.start.toInt(), range.endInclusive.toInt())
                },
                valueRange = 1f..10f,
                steps = 8,
                modifier = Modifier.testTag("difficulty_range_slider")
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Topic Filter Chips
            Text(
                text = "Konu Filtresi (TYT Matematik)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedTopicId == null,
                    onClick = { onSelectTopic(null) },
                    label = { Text("Tüm Konular") },
                    modifier = Modifier.testTag("topic_chip_all")
                )
                topics.forEach { topic ->
                    FilterChip(
                        selected = selectedTopicId == topic.id,
                        onClick = { onSelectTopic(topic.id) },
                        label = { Text(topic.name) },
                        modifier = Modifier.testTag("topic_chip_${topic.id}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Daha önce çözdüğüm soruları da dahil et",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Switch(
                    checked = includeSolved,
                    onCheckedChange = onToggleIncludeSolved,
                    modifier = Modifier.testTag("include_solved_switch")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onStartPractice,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("start_practice_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = activeColor)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                val btnText = if (selectedMin == selectedMax) {
                    "10 Üzerinden $selectedMin Zorlukta Soru Getir"
                } else {
                    "$selectedMin–$selectedMax Zorluk Aralığında Başla"
                }
                Text(
                    text = btnText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ChallengeModeCard(
    modifier: Modifier = Modifier,
    userTheta: Double,
    onStartChallenge: () -> Unit
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable { onStartChallenge() }
            .testTag("start_challenge_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Beni Zorla",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Seviyene (θ=${"%+.2f".format(userTheta)}) göre başarı olasılığı ≈%60 olan soruları seçer.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Dinamik Başla →",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun ReviewQuickCard(
    modifier: Modifier = Modifier,
    wrongCount: Int,
    favCount: Int,
    onStartReview: () -> Unit
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable { onStartReview() }
            .testTag("start_review_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Bookmark,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Yanlışlarım",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "$wrongCount yanlış soru · $favCount favori (Çevrimdışı tekrar).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Tekrar Çöz →",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

@Composable
private fun PoolQuestionPreviewRow(
    item: QuestionWithDetails,
    userTheta: Double,
    onClickSolve: () -> Unit
) {
    val q = item.question
    val bColor = difficultyColorForBucket(q.difficultyBucket)
    val expectedProb = (CalibrationEngine.expectedSuccessProbability(userTheta, q.difficultyLogit) * 100).toInt()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClickSolve() }
            .testTag("pool_question_card_${q.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = bColor,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Zorluk ${q.difficultyBucket}/10",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Text(
                        text = item.topic.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (item.hasUserSolvedBefore) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Çözüldü",
                            tint = Color(0xFF059669),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Çözüldü",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF059669)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = item.version.bodyText,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Calibration telemetry footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Yükleyen Puanı: ${q.uploaderRating}/10 · Çözüm: ${q.firstAttemptCount} (%${item.accuracyPercent} doğru)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Senin Başarı Tahminin: %$expectedProb",
                    style = MaterialTheme.typography.labelSmall,
                    color = bColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
