package com.churchmedia.intercom.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Comms is dark-only by design: it runs in dim booths and at the back of a live room,
 * and a bright screen is a distraction for everyone nearby.
 */
object CommsColors {
    val Base = Color(0xFF08090C)
    val Surface = Color(0xFF101320)
    val SurfaceHigh = Color(0xFF171B26)
    val Outline = Color(0xFF262C3A)

    val TextPrimary = Color(0xFFE8ECF2)
    val TextSecondary = Color(0xFF98A2B3)

    val Accent = Color(0xFF4CE0B3)
    val AccentDeep = Color(0xFF0E3A2E)
    val AccentInk = Color(0xFF04120E)

    val Warning = Color(0xFFFFB347)
    val Danger = Color(0xFFFF5C5C)
    val DangerDeep = Color(0xFF3A1418)

    val Live = Color(0xFFFF4D6D)
    val LiveDeep = Color(0xFF3A0E18)
}

private val CommsDarkScheme = darkColorScheme(
    primary = CommsColors.Accent,
    onPrimary = CommsColors.AccentInk,
    primaryContainer = CommsColors.AccentDeep,
    onPrimaryContainer = CommsColors.Accent,
    secondary = CommsColors.TextSecondary,
    onSecondary = CommsColors.Base,
    background = CommsColors.Base,
    onBackground = CommsColors.TextPrimary,
    surface = CommsColors.Surface,
    onSurface = CommsColors.TextPrimary,
    surfaceVariant = CommsColors.SurfaceHigh,
    onSurfaceVariant = CommsColors.TextSecondary,
    outline = CommsColors.Outline,
    error = CommsColors.Danger,
    onError = CommsColors.Base,
    errorContainer = CommsColors.DangerDeep,
    onErrorContainer = CommsColors.Danger,
)

private val CommsTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.3).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        letterSpacing = 0.4.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.8.sp,
    ),
)

@Composable
fun CommsTheme(content: @Composable () -> Unit) {
    // Ignoring the system theme on purpose — see class docs.
    isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = CommsDarkScheme,
        typography = CommsTypography,
        content = content,
    )
}
