package com.hebo.codex.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Purple,
    onPrimary = Color.White,
    primaryContainer = PurpleContainer,
    onPrimaryContainer = Color(0xFF24105F),
    secondary = Color(0xFF65558F),
    surface = Color(0xFFFFFBFF),
    surfaceContainer = Color(0xFFF5F2FA),
    error = Danger,
)

private val DarkColors = darkColorScheme(
    primary = PurpleDark,
    onPrimary = Color(0xFF2A1479),
    primaryContainer = PurpleContainerDark,
    onPrimaryContainer = Color(0xFFE8E0FF),
    secondary = Color(0xFFCEC2DC),
    surface = Color(0xFF121217),
    surfaceContainer = Color(0xFF1E1D24),
    error = Color(0xFFFFB4AB),
)

@Composable
fun CodexTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = CodexTypography,
        content = content,
    )
}

