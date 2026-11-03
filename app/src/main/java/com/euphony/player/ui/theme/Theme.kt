package com.euphony.player.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

@Immutable
data class EuphonyColors(
    val accent: Color,
    val accentSoft: Color,
    val onAccent: Color,
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val outline: Color,
    val isLight: Boolean,
)

val DarkColors = EuphonyColors(
    accent = Color(0xFF4FD1C5),
    accentSoft = Color(0xFF7AE0D6),
    onAccent = Color(0xFF00201D),
    background = Color(0xFF0E0F13),
    surface = Color(0xFF171920),
    surfaceHigh = Color(0xFF20232C),
    textPrimary = Color(0xFFF2F4F8),
    textSecondary = Color(0xFF9AA1B2),
    outline = Color(0xFF3A3F4B),
    isLight = false,
)

/** True black saves power on OLED screens; cards stay just visible against it. */
val BlackColors = DarkColors.copy(
    background = Color(0xFF000000),
    surface = Color(0xFF0D0E11),
    surfaceHigh = Color(0xFF181A20),
    outline = Color(0xFF2C3039),
)

val LightColors = EuphonyColors(
    accent = Color(0xFF0B7F75),
    accentSoft = Color(0xFF2A9D92),
    onAccent = Color(0xFFFFFFFF),
    background = Color(0xFFF6F7F9),
    surface = Color(0xFFFFFFFF),
    surfaceHigh = Color(0xFFE7EAF0),
    textPrimary = Color(0xFF14161B),
    textSecondary = Color(0xFF5B6272),
    outline = Color(0xFFC5CAD4),
    isLight = true,
)

val LocalEuphonyColors = compositionLocalOf { DarkColors }

// Read through the current theme, so screens written for one fixed palette follow the chosen one.
val Accent: Color @Composable @ReadOnlyComposable get() = LocalEuphonyColors.current.accent
val AccentSoft: Color @Composable @ReadOnlyComposable get() = LocalEuphonyColors.current.accentSoft
val Background: Color @Composable @ReadOnlyComposable get() = LocalEuphonyColors.current.background
val Surface: Color @Composable @ReadOnlyComposable get() = LocalEuphonyColors.current.surface
val SurfaceHigh: Color @Composable @ReadOnlyComposable get() = LocalEuphonyColors.current.surfaceHigh
val TextPrimary: Color @Composable @ReadOnlyComposable get() = LocalEuphonyColors.current.textPrimary
val TextSecondary: Color @Composable @ReadOnlyComposable get() = LocalEuphonyColors.current.textSecondary

/** [accent] replaces the theme's own accent, e.g. a colour picked from the album art. */
@Composable
fun EuphonyTheme(base: EuphonyColors = DarkColors, accent: Color? = null, content: @Composable () -> Unit) {
    val target = accent ?: base.accent
    val animatedAccent by animateColorAsState(target, tween(600), label = "accent")
    val colors = base.copy(
        accent = animatedAccent,
        accentSoft = if (accent == null) base.accentSoft else animatedAccent,
        onAccent = if (accent == null) base.onAccent else if (target.luminance() > 0.45f) Color(0xFF101114) else Color.White,
    )
    val scheme = if (colors.isLight) {
        lightColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            secondary = colors.accentSoft,
            background = colors.background,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceHigh,
            onSurfaceVariant = colors.textSecondary,
            surfaceContainer = colors.surface,
            surfaceContainerHigh = colors.surfaceHigh,
            outline = colors.outline,
        )
    } else {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            secondary = colors.accentSoft,
            background = colors.background,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceHigh,
            onSurfaceVariant = colors.textSecondary,
            surfaceContainer = colors.surface,
            surfaceContainerHigh = colors.surfaceHigh,
            outline = colors.outline,
        )
    }
    CompositionLocalProvider(LocalEuphonyColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
    }
}
