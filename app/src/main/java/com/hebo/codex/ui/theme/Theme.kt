package com.hebo.codex.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

internal val LightColors = lightColorScheme(
    primary = Ink,
    onPrimary = Paper,
    primaryContainer = LightSurfaceStrong,
    onPrimaryContainer = Ink,
    secondary = LightMuted,
    onSecondary = Paper,
    secondaryContainer = LightSurface,
    onSecondaryContainer = Ink,
    tertiary = LightMuted,
    onTertiary = Paper,
    tertiaryContainer = LightSurface,
    onTertiaryContainer = Ink,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = LightSurface,
    onSurfaceVariant = LightMuted,
    surfaceContainerLowest = Paper,
    surfaceContainerLow = LightSurfaceSubtle,
    surfaceContainer = LightSurface,
    surfaceContainerHigh = LightSurfaceStrong,
    surfaceContainerHighest = LightSurfaceStrong,
    outline = LightOutline,
    outlineVariant = LightOutlineSubtle,
    inverseSurface = DarkSurface,
    inverseOnSurface = Paper,
    inversePrimary = Paper,
    scrim = Color.Black,
)

internal val DarkColors = darkColorScheme(
    primary = DarkText,
    onPrimary = Ink,
    primaryContainer = DarkSurfaceStrong,
    onPrimaryContainer = DarkText,
    secondary = DarkMuted,
    onSecondary = Ink,
    secondaryContainer = DarkSurface,
    onSecondaryContainer = DarkText,
    tertiary = DarkMuted,
    onTertiary = Ink,
    tertiaryContainer = DarkSurface,
    onTertiaryContainer = DarkText,
    background = DarkBackground,
    onBackground = DarkText,
    surface = DarkBackground,
    onSurface = DarkText,
    surfaceVariant = DarkSurface,
    onSurfaceVariant = DarkMuted,
    surfaceContainerLowest = DarkBackground,
    surfaceContainerLow = DarkSurfaceSubtle,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurfaceStrong,
    surfaceContainerHighest = DarkSurfaceStrong,
    outline = DarkOutline,
    outlineVariant = DarkOutlineSubtle,
    inverseSurface = LightSurface,
    inverseOnSurface = Ink,
    inversePrimary = Ink,
    scrim = Color.Black,
)

internal val CodexShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Immutable
internal data class CodexStatusColors(
    val success: Color,
    val warning: Color,
    val info: Color,
)

internal val LightStatusColors = CodexStatusColors(
    success = Color(0xFF146C2E),
    warning = Color(0xFF7A5900),
    info = Color(0xFF006FC9),
)

internal val DarkStatusColors = CodexStatusColors(
    success = Color(0xFF78DC77),
    warning = Color(0xFFF2C14E),
    info = Color(0xFF68B5F8),
)

private val LocalCodexStatusColors = staticCompositionLocalOf<CodexStatusColors> {
    error("CodexStatusColors 未提供")
}

internal val MaterialTheme.statusColors: CodexStatusColors
    @Composable
    @ReadOnlyComposable
    get() = LocalCodexStatusColors.current

@Composable
fun CodexTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val statusColors = if (darkTheme) DarkStatusColors else LightStatusColors
    CompositionLocalProvider(LocalCodexStatusColors provides statusColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = CodexTypography,
            shapes = CodexShapes,
            content = content,
        )
    }
}
