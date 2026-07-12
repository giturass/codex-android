package com.hebo.codex.ui.screens.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatChromeScrollStateTest {
    @Test
    fun `向历史消息滚动达到阈值后隐藏控件`() {
        val state = ChatChromeScrollState()
            .scroll(deltaY = 20f)
            .scroll(deltaY = 28f)

        assertFalse(state.controlsVisible)
    }

    @Test
    fun `向历史消息轻微滚动时保持控件显示`() {
        val state = ChatChromeScrollState().scroll(deltaY = 47f)

        assertTrue(state.controlsVisible)
    }

    @Test
    fun `隐藏后向最新消息滚动达到阈值时恢复控件`() {
        val state = ChatChromeScrollState()
            .scroll(deltaY = 48f)
            .scroll(deltaY = -12f)
            .scroll(deltaY = -12f)

        assertTrue(state.controlsVisible)
    }

    @Test
    fun `切换滚动方向时重新累计距离`() {
        val state = ChatChromeScrollState()
            .scroll(deltaY = 30f)
            .scroll(deltaY = -1f)
            .scroll(deltaY = 20f)

        assertTrue(state.controlsVisible)
    }

    private fun ChatChromeScrollState.scroll(deltaY: Float) = afterUserScroll(
        deltaY = deltaY,
        hideThreshold = 48f,
        showThreshold = 24f,
    )
}
