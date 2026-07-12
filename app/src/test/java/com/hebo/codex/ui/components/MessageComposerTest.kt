package com.hebo.codex.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class MessageComposerTest {
    @Test
    fun `键盘隐藏时输入框保持折叠`() {
        assertFraction(expected = 0f, current = 0, source = 0, target = 0)
    }

    @Test
    fun `键盘显示时输入框保持展开`() {
        assertFraction(expected = 1f, current = 300, source = 300, target = 300)
    }

    @Test
    fun `输入框跟随键盘弹出进度`() {
        assertFraction(expected = 0.5f, current = 150, source = 0, target = 300)
    }

    @Test
    fun `输入框跟随键盘折叠进度`() {
        assertFraction(expected = 0.5f, current = 150, source = 300, target = 0)
    }

    @Test
    fun `键盘在可见状态切换高度时输入框保持展开`() {
        assertFraction(expected = 1f, current = 350, source = 300, target = 400)
    }

    @Test
    fun `键盘折叠且有文字时输入框保持展开`() {
        assertFraction(expected = 1f, current = 0, source = 300, target = 0, hasText = true)
    }

    @Test
    fun `输入框变大过程中隐藏扩展内容`() {
        assertExpandedContentVisible(expected = false, current = 150, source = 0, target = 300)
    }

    @Test
    fun `输入框变大完成后显示扩展内容`() {
        assertExpandedContentVisible(expected = true, current = 300, source = 0, target = 300)
    }

    @Test
    fun `输入框开始变紧凑时隐藏扩展内容`() {
        assertExpandedContentVisible(expected = false, current = 300, source = 300, target = 0)
    }

    @Test
    fun `输入框保持展开时显示扩展内容`() {
        assertExpandedContentVisible(expected = true, current = 350, source = 300, target = 400)
    }

    @Test
    fun `输入框有文字时显示扩展内容`() {
        assertExpandedContentVisible(
            expected = true,
            current = 0,
            source = 0,
            target = 0,
            hasText = true,
        )
    }

    private fun assertFraction(
        expected: Float,
        current: Int,
        source: Int,
        target: Int,
        hasText: Boolean = false,
    ) {
        assertEquals(
            expected,
            composerExpansionFraction(
                currentBottom = current,
                sourceBottom = source,
                targetBottom = target,
                hasText = hasText,
            ),
            0.0001f,
        )
    }

    private fun assertExpandedContentVisible(
        expected: Boolean,
        current: Int,
        source: Int,
        target: Int,
        hasText: Boolean = false,
    ) {
        assertEquals(
            expected,
            composerExpandedContentVisible(
                currentBottom = current,
                sourceBottom = source,
                targetBottom = target,
                hasText = hasText,
            ),
        )
    }
}
