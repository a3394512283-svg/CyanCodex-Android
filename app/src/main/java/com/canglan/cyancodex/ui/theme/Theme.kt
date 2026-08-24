package com.canglan.cyancodex.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 沧蓝工坊 CyanCodex 主题: 草绿 + 金色 (MC 风格), 支持 白天/夜晚
val GrassGreen = Color(0xFF4ADE80)
val GrassGreenDark = Color(0xFF16A34A)
val Gold = Color(0xFFFBBF24)
val SkyBlue = Color(0xFF38BDF8)
val DeepBg = Color(0xFF0E1512)
val Surface = Color(0xFF16221C)
val SurfaceHigh = Color(0xFF1E2D24)
val ErrorRed = Color(0xFFF87171)
val TextPrimary = Color(0xFFE7F2EA)
val TextSecondary = Color(0xFF9DB4A5)

private val DarkColors = darkColorScheme(
    primary = GrassGreen,
    onPrimary = Color(0xFF052E16),
    primaryContainer = Color(0xFF14532D),
    onPrimaryContainer = GrassGreen,
    secondary = Gold,
    onSecondary = Color(0xFF451A03),
    secondaryContainer = Color(0xFF78350F),
    onSecondaryContainer = Gold,
    tertiary = SkyBlue,
    background = DeepBg,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = TextSecondary,
    error = ErrorRed,
    onError = Color(0xFF2D0A0A),
)

private val LightColors = lightColorScheme(
    primary = GrassGreenDark,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCFCE7),
    onPrimaryContainer = Color(0xFF052E16),
    secondary = Color(0xFFB45309),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFEF3C7),
    onSecondaryContainer = Color(0xFF451A03),
    tertiary = SkyBlue,
    background = Color(0xFFF6F8F6),
    onBackground = Color(0xFF16221C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF16221C),
    surfaceVariant = Color(0xFFE4EDE7),
    onSurfaceVariant = Color(0xFF4E6156),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
)

@Composable
fun CyanCodexTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = MaterialTheme.typography,
        content = content,
    )
}
