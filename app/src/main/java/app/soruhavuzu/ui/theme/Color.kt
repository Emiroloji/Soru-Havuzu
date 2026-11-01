package app.soruhavuzu.ui.theme

import androidx.compose.ui.graphics.Color

// Academic Atelier Light Palette
val InkNavy = Color(0xFF1E3A8A)
val InkNavyDark = Color(0xFF172554)
val AcademicTeal = Color(0xFF0D9488)
val CalibrationAmber = Color(0xFFD97706)
val WarmPaperBg = Color(0xFFFAF8F5)
val PureCardSurface = Color(0xFFFFFFFF)
val StoneSurfaceVariant = Color(0xFFF2EFE9)
val SlateInkText = Color(0xFF0F172A)
val MutedSlateText = Color(0xFF475569)

// Academic Atelier Dark Palette
val DarkSlateBg = Color(0xFF0F172A)
val DarkCardSurface = Color(0xFF1E293B)
val DarkSurfaceVariant = Color(0xFF334155)
val SkyBluePrimary = Color(0xFF60A5FA)
val TealAccentLight = Color(0xFF2DD4BF)
val AmberAccentLight = Color(0xFFFBBF24)
val CrispWhiteText = Color(0xFFF8FAFC)
val MutedSilverText = Color(0xFF94A3B8)

// Difficulty Bucket Heat Colors (1..10)
val DiffEasyEmerald = Color(0xFF059669)     // 1-3: Başlangıç / Kolay
val DiffMediumTeal = Color(0xFF0284C7)      // 4-5: Orta-Temel
val DiffHardAmber = Color(0xFFD97706)       // 6-7: Seçici / Orta-Üst
val DiffVeryHardCoral = Color(0xFFEA580C)   // 8-9: İleri / Zor
val DiffExtremeCrimson = Color(0xFFDC2626)  // 10: Derece / Olimpiyat

fun difficultyColorForBucket(bucket: Int): Color {
    return when (bucket.coerceIn(1, 10)) {
        1, 2, 3 -> DiffEasyEmerald
        4, 5 -> DiffMediumTeal
        6, 7 -> DiffHardAmber
        8, 9 -> DiffVeryHardCoral
        else -> DiffExtremeCrimson
    }
}

fun difficultyLabelForBucket(bucket: Int): String {
    return when (bucket.coerceIn(1, 10)) {
        1, 2 -> "Temel Isınma"
        3, 4 -> "Standart TYT"
        5, 6 -> "Orta-Seçici"
        7, 8 -> "İleri Düzey"
        9 -> "Derece Sorusu"
        else -> "Eleyici Zirve"
    }
}
