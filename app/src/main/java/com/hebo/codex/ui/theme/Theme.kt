package com.hebo.codex.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

internal val LightColors = lightColorScheme(
    primary = Purple,
    onPrimary = Color.White,
    primaryContainer = PurpleContainer,
    onPrimaryContainer = Color(0xFF24105F),
    secondary = Color(0xFF65558F),
    surface = Color(0xFFFFFBFF),
    surfaceContainer = Color(0xFFF5F2FA),
)

internal val DarkColors = darkColorScheme(
    primary = PurpleDark,
    onPrimary = Color(0xFF2A1479),
    primaryContainer = PurpleContainerDark,
    onPrimaryContainer = Color(0xFFE8E0FF),
    secondary = Color(0xFFCEC2DC),
    surface = Color(0xFF121217),
    surfaceContainer = Color(0xFF1E1D24),
)

@Immutable
internal data class CodexStatusColors(
    val success: Color,
    val warning: Color,
)

internal val LightStatusColors = CodexStatusColors(
    success = Color(0xFF146C2E),
    warning = Color(0xFF7A5900),
)

internal val DarkStatusColors = CodexStatusColors(
    success = Color(0xFF78DC77),
    warning = Color(0xFFF2C14E),
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
            content = content,
        )
    }
}
