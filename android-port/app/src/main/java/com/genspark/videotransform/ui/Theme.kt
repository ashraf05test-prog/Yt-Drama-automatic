package com.genspark.videotransform.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Explicit dark color scheme — fixes the "everything is dark, can't read text"
 * bug. Without this, MaterialTheme falls back to a light-on-light scheme that
 * looks invisible on the #0B0B0F background we set in the Activity theme.
 */
private val AppDarkColors = darkColorScheme(
    primary = Color(0xFF8B7CFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3853FF),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF9AA3B2),
    onSecondary = Color.White,
    background = Color(0xFF0B0B0F),
    onBackground = Color(0xFFEDEEF2),
    surface = Color(0xFF141722),
    onSurface = Color(0xFFEDEEF2),
    surfaceVariant = Color(0xFF1E2230),
    onSurfaceVariant = Color(0xFFB4B8C5),
    outline = Color(0xFF3A3F50),
    outlineVariant = Color(0xFF2A2F45),
    error = Color(0xFFFF6E6E),
    onError = Color.White,
)

private val AppTypography = Typography(
    bodyLarge = TextStyle(fontSize = 16.sp, color = Color(0xFFEDEEF2)),
    bodyMedium = TextStyle(fontSize = 14.sp, color = Color(0xFFEDEEF2)),
    bodySmall = TextStyle(fontSize = 12.sp, color = Color(0xFFB4B8C5)),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White),
)

@Composable
fun VideoTransformTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppDarkColors,
        typography = AppTypography,
        content = content,
    )
}
