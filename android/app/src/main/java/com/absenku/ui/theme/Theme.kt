package com.absenku.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.isSystemInDarkTheme

/**
 * AbsenKu Theme — Material 3, blue-pastel Google palette.
 */

private val LightColors = lightColorScheme(
    primary = Color(0xFF1A73E8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE8F0FE),
    secondary = Color(0xFF34A853),
    tertiary = Color(0xFFFBBC04),
    background = Color(0xFFF5F7FA),
    onBackground = Color(0xFF1A1A1A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1A1A),
    error = Color(0xFFD93025),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE8F0FE),
    onPrimary = Color(0xFF0F3C8C),
    secondary = Color(0xFF66BB6A),
    tertiary = Color(0xFFFFD54F),
    background = Color(0xFF0F172A),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF1E293B),
    onSurface = Color(0xFFF8FAFC),
    error = Color(0xFFF2B8B5),
)

@Composable
fun AbsenKuTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = Typography(),
        content = content,
    )
}
