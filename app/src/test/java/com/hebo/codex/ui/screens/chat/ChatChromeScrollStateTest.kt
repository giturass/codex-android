package com.hebo.codex.ui.screens.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatChromeScrollStateTest {
    @Test
    fun `控件位移与向历史消息拖动距离一致`() {
        val state = ChatChromeScrollState()
            .scroll(deltaY = 20f)
            .scroll(deltaY = 30f)

        assertEquals(50f, state.offset, 0.0001f)
        assertFalse(state.controlsHidden)
    }

    @Test
    fun `达到完整高度后标记为隐藏`() {
        val state = ChatChromeScrollState().scroll(deltaY = 120f)

        assertEquals(100f, state.offset, 0.0001f)
        assertTrue(state.controlsHidden)
    }

    @Test
    fun `隐藏后向最新消息拖动时逐像素恢复`() {
        val state = ChatChromeScrollState()
            .scroll(deltaY = 100f)
            .scroll(deltaY = -24f)

        assertEquals(76f, state.offset, 0.0001f)
        assertFalse(state.controlsHidden)
    }

    @Test
    fun `拖动方向反转时直接反向移动`() {
        val state = ChatChromeScrollState()
            .scroll(deltaY = 30f)
            .scroll(deltaY = -10f)

        assertEquals(20f, state.offset, 0.0001f)
        assertFalse(state.controlsHidden)
    }

    @Test
    fun `未完全隐藏时松手目标为完整展示`() {
        val state = ChatChromeScrollState().scroll(deltaY = 99f)

        assertEquals(0f, state.settleTarget(hideDistance = 100f), 0.0001f)
    }

    @Test
    fun `完全隐藏后松手保持隐藏`() {
        val state = ChatChromeScrollState().scroll(deltaY = 100f)

        assertEquals(100f, state.settleTarget(hideDistance = 100f), 0.0001f)
    }

    private fun ChatChromeScrollState.scroll(deltaY: Float) = afterUserScroll(
        deltaY = deltaY,
        hideDistance = 100f,
    )
}
