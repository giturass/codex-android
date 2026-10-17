package com.hebo.codex.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ink = Color(0xFF101010)
private val LightSurfaceSubtle = Color(0xFFF7F7F7)
private val LightSurface = Color(0xFFF1F1F1)
private val LightSurfaceStrong = Color(0xFFE8E8E8)
private val LightMuted = Color(0xFF5F5F5F)
private val DarkBackground = Color(0xFF000000)
private val DarkSurfaceSubtle = Color(0xFF1D1D1D)
private val DarkSurface = Color(0xFF242424)
private val DarkSurfaceStrong = Color(0xFF373737)
private val DarkText = Color(0xFFF4F4F4)
private val DarkMuted = Color(0xFFAAAAAA)

private val ComposerLightColors = lightColorScheme(
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
    outline = Color(0xFF747474),
    outlineVariant = Color(0xFFD9D9D9),
    inverseSurface = DarkSurface,
    inverseOnSurface = Paper,
    inversePrimary = Paper,
    scrim = Color.Black,
)

private val ComposerDarkColors = darkColorScheme(
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
    outline = Color(0xFFA1A1A1),
    outlineVariant = Color(0xFF454545),
    inverseSurface = LightSurface,
    inverseOnSurface = Ink,
    inversePrimary = Ink,
    scrim = Color.Black,
)

private val ComposerShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val ComposerTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 23.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 17.sp,
        lineHeight = 26.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
)

/** The message composer has its own neutral palette, type scale, and corner shapes. */
@Composable
internal fun ComposerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (LocalCodexDarkTheme.current) ComposerDarkColors else ComposerLightColors,
        typography = ComposerTypography,
        shapes = ComposerShapes,
        content = content,
    )
}
