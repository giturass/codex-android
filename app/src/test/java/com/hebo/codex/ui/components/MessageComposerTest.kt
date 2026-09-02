package com.hebo.codex.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageComposerTest {
    @Test
    fun `键盘隐藏时输入框保持折叠`() {
        assertFalse(composerExpansionTarget(imeTargetBottom = 0, hasText = false))
    }

    @Test
    fun `键盘开始弹出时输入框独立展开`() {
        assertTrue(composerExpansionTarget(imeTargetBottom = 300, hasText = false))
    }

    @Test
    fun `键盘开始收起时输入框独立折叠`() {
        assertFalse(composerExpansionTarget(imeTargetBottom = 0, hasText = false))
    }

    @Test
    fun `键盘折叠且有文字时输入框保持展开`() {
        assertTrue(composerExpansionTarget(imeTargetBottom = 0, hasText = true))
    }

    @Test
    fun `输入框变大过程中隐藏扩展内容`() {
        assertFalse(
            composerExpandedContentVisible(
                currentExpanded = false,
                targetExpanded = true,
                isTransitionRunning = true,
            ),
        )
    }

    @Test
    fun `输入框变大完成后显示扩展内容`() {
        assertTrue(
            composerExpandedContentVisible(
                currentExpanded = true,
                targetExpanded = true,
                isTransitionRunning = false,
            ),
        )
    }

    @Test
    fun `输入框开始变紧凑时隐藏扩展内容`() {
        assertFalse(
            composerExpandedContentVisible(
                currentExpanded = true,
                targetExpanded = false,
                isTransitionRunning = true,
            ),
        )
    }

    @Test
    fun `输入框反向展开完成前隐藏扩展内容`() {
        assertFalse(
            composerExpandedContentVisible(
                currentExpanded = true,
                targetExpanded = true,
                isTransitionRunning = true,
            ),
        )
    }
}
