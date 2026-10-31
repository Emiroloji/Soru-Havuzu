package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = SkyBluePrimary,
    onPrimary = DarkSlateBg,
    primaryContainer = InkNavy,
    onPrimaryContainer = CrispWhiteText,
    secondary = TealAccentLight,
    onSecondary = DarkSlateBg,
    secondaryContainer = Color(0xFF134E4A),
    onSecondaryContainer = CrispWhiteText,
    tertiary = AmberAccentLight,
    onTertiary = DarkSlateBg,
    background = DarkSlateBg,
    onBackground = CrispWhiteText,
    surface = DarkCardSurface,
    onSurface = CrispWhiteText,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = MutedSilverText,
    error = Color(0xFFF87171),
    onError = DarkSlateBg
)

private val LightColorScheme = lightColorScheme(
    primary = InkNavy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = InkNavyDark,
    secondary = AcademicTeal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCFBF1),
    onSecondaryContainer = Color(0xFF115E59),
    tertiary = CalibrationAmber,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF78350F),
    background = WarmPaperBg,
    onBackground = SlateInkText,
    surface = PureCardSurface,
    onSurface = SlateInkText,
    surfaceVariant = StoneSurfaceVariant,
    onSurfaceVariant = MutedSlateText,
    error = Color(0xFFDC2626),
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
