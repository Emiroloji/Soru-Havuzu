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
import com.example.soruhavuzu.data.remote.CatalogExamDto
import com.example.soruhavuzu.data.remote.UserDto
import com.example.soruhavuzu.ui.practice.HomeState
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
    user: UserDto,
    state: HomeState,
    onSelectExam: (String) -> Unit,
    onSelectSubject: (String?) -> Unit,
    onSelectTopic: (String?) -> Unit,
    onSelectSingleBucket: (Int) -> Unit,
    onSelectRange: (Int, Int) -> Unit,
    onToggleIncludeSolved: (Boolean) -> Unit,
    onStartPractice: () -> Unit,
    onStartChallenge: () -> Unit,
    onOpenReview: () -> Unit,
    onRetry: () -> Unit
) {
    val stats = state.stats
    val totalSolved = stats?.solvedCount ?: 0
    val accuracyRate = if (totalSolved == 0) 0 else (stats!!.correctCount * 100) / totalSolved
    val filter = state.filter

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("home_pool_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            AtelierHeroCard(
                nickname = user.nickname,
                targetExam = state.catalog.firstOrNull { it.id == user.targetExamId }?.code ?: "Sınav seçilmedi",
                reputation = user.reputation,
                level = user.level,
                totalSolved = totalSolved,
                accuracyRate = accuracyRate
            )
        }

        if (state.error != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = state.error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(onClick = onRetry) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Tekrar Dene")
                        }
                    }
                }
            }
        }

        // "BANA 10 ÜZERİNDEN X ZORLUKTA SORU GETİR"
        item {
            DifficultySelectorCard(
                selectedMin = filter.minBucket,
                selectedMax = filter.maxBucket,
                bucketCounts = state.bucketCounts.associate {
                    it.bucket to if (filter.includeSolved) it.total else it.unsolved
                },
                exams = state.catalog,
                selectedExam = state.selectedExam,
                selectedSubjectId = filter.subjectId,
                selectedTopicId = filter.topicId,
                includeSolved = filter.includeSolved,
                matchingCount = state.matchingCount,
                isLoading = state.isLoading,
                onSelectSingleBucket = onSelectSingleBucket,
                onSelectRange = onSelectRange,
                onSelectExam = onSelectExam,
                onSelectSubject = onSelectSubject,
                onSelectTopic = onSelectTopic,
                onToggleIncludeSolved = onToggleIncludeSolved,
                onStartPractice = onStartPractice
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ChallengeModeCard(
                    modifier = Modifier.weight(1f),
                    level = user.level,
                    onStartChallenge = onStartChallenge
                )
                ReviewQuickCard(
                    modifier = Modifier.weight(1f),
                    wrongCount = stats?.wrongNoteCount ?: 0,
                    favCount = stats?.favoriteCount ?: 0,
                    onStartReview = onOpenReview
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
    level: Double,
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
                                text = "$level / 10",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "çözdükçe güncellenir",
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
    exams: List<CatalogExamDto>,
    selectedExam: CatalogExamDto?,
    selectedSubjectId: String?,
    selectedTopicId: String?,
    includeSolved: Boolean,
    matchingCount: Int,
    isLoading: Boolean,
    onSelectSingleBucket: (Int) -> Unit,
    onSelectRange: (Int, Int) -> Unit,
    onSelectExam: (String) -> Unit,
    onSelectSubject: (String?) -> Unit,
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
                        text = if (isLoading) "Yükleniyor…" else "$matchingCount Soru Hazır",
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

            // Sınav -> Ders -> Konu filtresi
            FilterChipRow(
                label = "Sınav",
                chips = exams.map { it.id to it.code },
                selectedId = selectedExam?.id,
                allLabel = null,
                onSelect = { id -> id?.let(onSelectExam) },
                tagPrefix = "exam_chip"
            )
            val subjects = selectedExam?.subjects.orEmpty()
            if (subjects.size > 1) {
                FilterChipRow(
                    label = "Ders",
                    chips = subjects.map { it.id to it.name },
                    selectedId = selectedSubjectId,
                    allLabel = "Tüm Dersler",
                    onSelect = onSelectSubject,
                    tagPrefix = "subject_chip"
                )
            }
            val topics = (if (subjects.size == 1) subjects.first() else subjects.firstOrNull { it.id == selectedSubjectId })
                ?.topics.orEmpty()
            if (topics.isNotEmpty()) {
                FilterChipRow(
                    label = "Konu",
                    chips = topics.map { it.id to "${it.name} (${it.activeQuestionCount})" },
                    selectedId = selectedTopicId,
                    allLabel = "Tüm Konular",
                    onSelect = onSelectTopic,
                    tagPrefix = "topic_chip"
                )
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
                enabled = matchingCount > 0,
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
    level: Double,
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
                text = "Seviyene ($level/10) göre çözme ihtimalin ≈%60 olan soruları seçer.",
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
                text = "$wrongCount yanlış soru · $favCount favori.",
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
private fun FilterChipRow(
    label: String,
    chips: List<Pair<String, String>>,
    selectedId: String?,
    allLabel: String?,
    onSelect: (String?) -> Unit,
    tagPrefix: String
) {
    Text(
        text = label,
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
        if (allLabel != null) {
            FilterChip(
                selected = selectedId == null,
                onClick = { onSelect(null) },
                label = { Text(allLabel) },
                modifier = Modifier.testTag("${tagPrefix}_all")
            )
        }
        chips.forEach { (id, text) ->
            FilterChip(
                selected = selectedId == id,
                onClick = { onSelect(id) },
                label = { Text(text) },
                modifier = Modifier.testTag("${tagPrefix}_$id")
            )
        }
    }
    Spacer(modifier = Modifier.height(10.dp))
}
