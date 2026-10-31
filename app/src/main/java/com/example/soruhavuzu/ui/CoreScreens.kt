package com.example.soruhavuzu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 1. Onboarding Ekranı — Basit Compose fonksiyonu (Ekran başlığını gösteren Text bileşeni ile).
 */
@Composable
fun OnboardingScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("simple_onboarding_screen"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Onboarding",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag("title_onboarding")
        )
    }
}

/**
 * 2. Home Ekranı — Basit Compose fonksiyonu (Ekran başlığını gösteren Text bileşeni ile).
 */
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("simple_home_screen"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Home",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag("title_home")
        )
    }
}

/**
 * 3. QuestionSolving Ekranı — Basit Compose fonksiyonu (Ekran başlığını gösteren Text bileşeni ile).
 */
@Composable
fun QuestionSolvingScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("simple_question_solving_screen"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "QuestionSolving",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag("title_question_solving")
        )
    }
}

/**
 * 4. LoadQuestion Ekranı — Basit Compose fonksiyonu (Ekran başlığını gösteren Text bileşeni ile).
 */
@Composable
fun LoadQuestionScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("simple_load_question_screen"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "LoadQuestion",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag("title_load_question")
        )
    }
}

/**
 * Ekran üst başlığını gösteren ortak başlık bileşeni.
 */
@Composable
fun ScreenTitleHeader(
    title: String,
    subtitle: String,
    isSimpleMode: Boolean,
    onToggleSimpleMode: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("screen_header_text_$title")
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilterChip(
                    selected = isSimpleMode,
                    onClick = { onToggleSimpleMode(!isSimpleMode) },
                    label = {
                        Text(
                            text = if (isSimpleMode) "Basit Görünüm" else "Tam Görünüm",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    modifier = Modifier.testTag("toggle_simple_mode_chip")
                )
            }
        }
    }
}
