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
    fun `显示时隐藏未超过一半松手保持显示`() {
        val state = ChatChromeScrollState().scroll(deltaY = 49f)

        assertEquals(0f, state.settleTarget(hideDistance = 100f), 0.0001f)
    }

    @Test
    fun `显示时隐藏超过一半松手改为隐藏`() {
        val state = ChatChromeScrollState().scroll(deltaY = 51f)

        assertEquals(100f, state.settleTarget(hideDistance = 100f), 0.0001f)
    }

    @Test
    fun `显示时隐藏刚好一半松手保持显示`() {
        val state = ChatChromeScrollState().scroll(deltaY = 50f)

        assertEquals(0f, state.settleTarget(hideDistance = 100f), 0.0001f)
    }

    @Test
    fun `隐藏时显示未超过一半松手保持隐藏`() {
        val state = hiddenState().scroll(deltaY = -49f)

        assertEquals(100f, state.settleTarget(hideDistance = 100f), 0.0001f)
    }

    @Test
    fun `隐藏时显示超过一半松手改为显示`() {
        val state = hiddenState().scroll(deltaY = -51f)

        assertEquals(0f, state.settleTarget(hideDistance = 100f), 0.0001f)
    }

    @Test
    fun `隐藏时显示刚好一半松手保持隐藏`() {
        val state = hiddenState().scroll(deltaY = -50f)

        assertEquals(100f, state.settleTarget(hideDistance = 100f), 0.0001f)
    }

    private fun hiddenState() = ChatChromeScrollState(
        controlsHidden = true,
        offset = 100f,
        settledHidden = true,
    )

    private fun ChatChromeScrollState.scroll(deltaY: Float) = afterUserScroll(
        deltaY = deltaY,
        hideDistance = 100f,
    )
}
