package com.euphony.player.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Accent = Color(0xFF4FD1C5)
val AccentSoft = Color(0xFF7AE0D6)
val Background = Color(0xFF0E0F13)
val Surface = Color(0xFF171920)
val SurfaceHigh = Color(0xFF20232C)
val TextPrimary = Color(0xFFF2F4F8)
val TextSecondary = Color(0xFF9AA1B2)

private val colors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF00201D),
    secondary = AccentSoft,
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = Surface,
    surfaceContainerHigh = SurfaceHigh,
    outline = Color(0xFF3A3F4B),
)

@Composable
fun EuphonyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = Typography(), content = content)
}
