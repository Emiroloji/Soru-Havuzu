package com.example.soruhavuzu.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.InkNavy
import com.example.ui.theme.InkNavyDark
import com.example.ui.theme.difficultyColorForBucket
import com.example.ui.theme.difficultyLabelForBucket

/**
 * proje.md §8 — S1 Onboarding (Sınav/hedef seçimi, doğum yılı, aydınlatma metni)
 * ve S2 Giriş / Kayıt (Apple, Google, e-posta) ekranı.
 */
@Composable
fun OnboardingScreen(
    initialNickname: String,
    initialExam: String,
    initialBirthYear: Int,
    onCompleteOnboarding: (
        nickname: String,
        email: String,
        authProvider: String,
        targetExam: String,
        birthYear: Int,
        initialSelfLevel: Int
    ) -> Unit
) {
    var selectedExam by remember { mutableStateOf(initialExam.ifBlank { "TYT" }) }
    var nickname by remember { mutableStateOf(initialNickname.ifBlank { "MatematikGezgini" }) }
    var email by remember { mutableStateOf("ogrenci@soruhavuzu.app") }
    var selectedAuthProvider by remember { mutableStateOf("E-posta") }
    var birthYear by remember { mutableIntStateOf(initialBirthYear.coerceIn(1975, 2014)) }
    var selfEstimatedLevel by remember { mutableIntStateOf(6) }
    var kvkkAccepted by remember { mutableStateOf(true) }

    val isUnder18 = (2026 - birthYear) < 18
    val levelColor = difficultyColorForBucket(selfEstimatedLevel)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("onboarding_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Welcome Header Banner
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = InkNavyDark)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(InkNavyDark, InkNavy, Color(0xFF0F766E))
                            )
                        )
                        .padding(22.dp)
                ) {
                    Column {
                        Surface(
                            color = Color(0xFFFBBF24).copy(alpha = 0.22f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "SORUHAVUZU · KALİBRASYON MOTORU",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFFDE68A),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Seviyene Göre Kalibre Edilen Açık Soru Bankası",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Her sorunun zorluğu yükleyenin ilk tahminiyle başlar, gerçek çözümlerle (Elo/Rasch) ölçülür. İstediğin zorluğu (1–10) seçerek hemen çalışmaya başla.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }

        // S1: Sınav/Hedef Seçimi & Başlangıç Seviyesi
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "1. Hedef Sınav ve Başlangıç Seviyen",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    val exams = listOf(
                        "TYT" to "TYT Matematik (Aktif Havuz)",
                        "AYT" to "AYT Matematik",
                        "KPSS" to "KPSS Sayısal"
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        exams.forEach { (code, _) ->
                            FilterChip(
                                selected = selectedExam == code,
                                onClick = { selectedExam = code },
                                label = { Text(code) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("onboarding_exam_$code")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Matematik Başlangıç Tahminin: $selfEstimatedLevel / 10",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Surface(
                            color = levelColor.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = difficultyLabelForBucket(selfEstimatedLevel),
                                style = MaterialTheme.typography.labelSmall,
                                color = levelColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Slider(
                        value = selfEstimatedLevel.toFloat(),
                        onValueChange = { selfEstimatedLevel = it.toInt().coerceIn(1, 10) },
                        valueRange = 1f..10f,
                        steps = 8,
                        modifier = Modifier.testTag("onboarding_level_slider")
                    )
                }
            }
        }

        // S2: Giriş / Kayıt (Apple, Google, E-posta) & Takma Ad
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "2. Giriş Yöntemi ve Takma Ad",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    val providers = listOf("Google", "Apple", "E-posta")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        providers.forEach { provider ->
                            val isSelected = selectedAuthProvider == provider
                            OutlinedButton(
                                onClick = { selectedAuthProvider = provider },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("auth_provider_$provider"),
                                border = BorderStroke(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                )
                            ) {
                                Text(
                                    text = provider,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = nickname,
                        onValueChange = { nickname = it },
                        label = { Text("Takma Ad (Ad-Soyad zorunlu değildir)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("onboarding_nickname_input"),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("$selectedAuthProvider Hesabı / E-posta") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("onboarding_email_input"),
                        singleLine = true
                    )
                }
            }
        }

        // Doğum Yılı (18 Yaş Altı Koruması) & KVKK Aydınlatma Metni
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "3. Doğum Yılı ve KVKK Aydınlatma Metni",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Doğum Yılı: $birthYear",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Surface(
                            color = if (isUnder18) Color(0xFFFEF3C7) else Color(0xFFDCFCE7),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (isUnder18) "18 Yaş Altı Koruması Aktif (Profilleme Kapalı)" else "Yetişkin Kullanıcı",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isUnder18) Color(0xFF92400E) else Color(0xFF065F46),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Slider(
                        value = birthYear.toFloat(),
                        onValueChange = { birthYear = it.toInt() },
                        valueRange = 1985f..2012f,
                        steps = 26,
                        modifier = Modifier.testTag("onboarding_birthyear_slider")
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { kvkkAccepted = !kvkkAccepted }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = kvkkAccepted,
                            onCheckedChange = { kvkkAccepted = it },
                            modifier = Modifier.testTag("onboarding_kvkk_checkbox")
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "KVKK Aydınlatma Metnini, görsellerden EXIF verilerinin silindiğini ve ÖSYM/yayınevi telifli soruların yüklenemeyeceğini okudum, kabul ediyorum.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            onCompleteOnboarding(
                                nickname.trim().ifBlank { "MatematikGezgini" },
                                email.trim().ifBlank { "ogrenci@soruhavuzu.app" },
                                selectedAuthProvider,
                                selectedExam,
                                birthYear,
                                selfEstimatedLevel
                            )
                        },
                        enabled = kvkkAccepted,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("complete_onboarding_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = "Soru Havuzuna Başla",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                    }
                }
            }
        }
    }
}
