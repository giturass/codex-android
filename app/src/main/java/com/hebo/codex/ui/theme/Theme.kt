package com.hebo.codex.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal val CodexShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
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

internal val LocalCodexDarkTheme = staticCompositionLocalOf<Boolean> {
    error("CodexTheme 未提供")
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
    CompositionLocalProvider(
        LocalCodexDarkTheme provides darkTheme,
        LocalCodexStatusColors provides statusColors,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = CodexTypography,
            shapes = CodexShapes,
            content = content,
        )
    }
}
