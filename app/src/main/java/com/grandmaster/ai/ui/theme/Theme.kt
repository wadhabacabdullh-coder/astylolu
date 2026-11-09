package com.grandmaster.ai.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object GmColors {
    val Background = Color(0xFF0E1116)
    val Surface = Color(0xFF161B22)
    val SurfaceHigh = Color(0xFF1E252F)
    val Outline = Color(0xFF2A323D)
    val Accent = Color(0xFF4CD787)
    val AccentDim = Color(0xFF2E8B57)
    val AccentSoft = Color(0x334CD787)
    val TextPrimary = Color(0xFFE8EAED)
    val TextSecondary = Color(0xFF9AA4B2)
    val Gold = Color(0xFFF2C14E)
    val Danger = Color(0xFFEF5350)
    val Warning = Color(0xFFFFA726)
    val Info = Color(0xFF5AB4F0)
    val Brilliant = Color(0xFF26C6DA)
    val Great = Color(0xFF5C9DFF)

    // Board
    val LightSquare = Color(0xFFE9EDCC)
    val DarkSquare = Color(0xFF779954)
    val LastMove = Color(0x80F5F682)
    val Selected = Color(0x9946B1E8)
    val MoveHint = Color(0x55000000)
    val Check = Color(0xFFE53935)
}

private val scheme = darkColorScheme(
    primary = GmColors.Accent,
    onPrimary = Color(0xFF06210F),
    primaryContainer = GmColors.AccentDim,
    onPrimaryContainer = Color.White,
    secondary = GmColors.Info,
    background = GmColors.Background,
    onBackground = GmColors.TextPrimary,
    surface = GmColors.Surface,
    onSurface = GmColors.TextPrimary,
    surfaceVariant = GmColors.SurfaceHigh,
    onSurfaceVariant = GmColors.TextSecondary,
    surfaceContainer = GmColors.Surface,
    surfaceContainerHigh = GmColors.SurfaceHigh,
    surfaceContainerHighest = Color(0xFF252D38),
    surfaceContainerLow = Color(0xFF12161C),
    outline = GmColors.Outline,
    outlineVariant = GmColors.Outline,
    error = GmColors.Danger,
)

val MonoFamily = FontFamily.Monospace

private val typography = Typography(
    headlineLarge = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp),
)

@Composable
fun GrandmasterTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
