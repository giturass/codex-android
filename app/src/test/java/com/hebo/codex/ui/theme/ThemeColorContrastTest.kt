package com.hebo.codex.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeColorContrastTest {
    @Test
    fun errorTextMeetsContrastRequirement() {
        assertContrast(
            label = "浅色错误文字",
            foreground = LightColors.error,
            background = LightColors.background,
            minimum = TEXT_CONTRAST,
        )
        assertContrast(
            label = "深色错误文字",
            foreground = DarkColors.error,
            background = DarkColors.background,
            minimum = TEXT_CONTRAST,
        )
    }

    @Test
    fun codeBlockTextMeetsContrastRequirement() {
        assertContrast(
            label = "浅色代码框文字",
            foreground = LightColors.onSurface,
            background = LightColors.surfaceContainerHighest,
            minimum = TEXT_CONTRAST,
        )
        assertContrast(
            label = "深色代码框文字",
            foreground = DarkColors.onSurface,
            background = DarkColors.surfaceContainerHighest,
            minimum = TEXT_CONTRAST,
        )
    }

    @Test
    fun semanticGraphicsMeetContrastRequirement() {
        assertStatusColors(
            theme = "浅色",
            colors = LightStatusColors,
            colorScheme = LightColors,
        )
        assertStatusColors(
            theme = "深色",
            colors = DarkStatusColors,
            colorScheme = DarkColors,
        )
    }

    private fun assertStatusColors(
        theme: String,
        colors: CodexStatusColors,
        colorScheme: ColorScheme,
    ) {
        val background = colorScheme.surfaceContainerLow
        assertContrast("${theme}成功状态", colors.success, background, GRAPHIC_CONTRAST)
        assertContrast("${theme}警告状态", colors.warning, background, GRAPHIC_CONTRAST)
        assertContrast("${theme}失败状态", colorScheme.error, background, GRAPHIC_CONTRAST)
        assertContrast("${theme}未连接状态", colorScheme.outline, background, GRAPHIC_CONTRAST)
        assertContrast("${theme}主色图标", colorScheme.primary, background, GRAPHIC_CONTRAST)
    }

    private fun assertContrast(
        label: String,
        foreground: Color,
        background: Color,
        minimum: Double,
    ) {
        val ratio = contrastRatio(foreground, background)
        assertTrue("$label 对比度为 $ratio，最低要求为 $minimum", ratio >= minimum)
    }

    private fun contrastRatio(first: Color, second: Color): Double {
        val firstLuminance = first.relativeLuminance()
        val secondLuminance = second.relativeLuminance()
        return (max(firstLuminance, secondLuminance) + 0.05) /
            (min(firstLuminance, secondLuminance) + 0.05)
    }

    private fun Color.relativeLuminance(): Double =
        0.2126 * red.linearized() +
            0.7152 * green.linearized() +
            0.0722 * blue.linearized()

    private fun Float.linearized(): Double {
        val component = toDouble()
        return if (component <= 0.04045) {
            component / 12.92
        } else {
            ((component + 0.055) / 1.055).pow(2.4)
        }
    }

    private companion object {
        const val TEXT_CONTRAST = 4.5
        const val GRAPHIC_CONTRAST = 3.0
    }
}
