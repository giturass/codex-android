package com.hebo.codex.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawerServerHeaderScrollStateTest {
    @Test
    fun `上滑距离由服务器行优先逐像素消耗`() {
        val result = DrawerServerHeaderScrollState().scroll(deltaY = -36f)

        assertEquals(36f, result.state.offset, 0.0001f)
        assertEquals(-36f, result.consumedY, 0.0001f)
        assertFalse(result.state.hidden)
    }

    @Test
    fun `完全隐藏后剩余距离留给会话列表`() {
        val result = DrawerServerHeaderScrollState().scroll(deltaY = -120f)

        assertEquals(100f, result.state.offset, 0.0001f)
        assertEquals(-100f, result.consumedY, 0.0001f)
        assertTrue(result.state.hidden)
    }

    @Test
    fun `未完全隐藏时松手恢复显示`() {
        val state = DrawerServerHeaderScrollState().scroll(deltaY = -99f).state

        assertEquals(0f, state.settleTarget(hideDistance = 100f), 0.0001f)
    }

    @Test
    fun `隐藏后下滑距离由服务器行优先逐像素消耗`() {
        val state = DrawerServerHeaderScrollState(hidden = true, offset = 100f)
        val result = state.scroll(deltaY = 42f)

        assertEquals(58f, result.state.offset, 0.0001f)
        assertEquals(42f, result.consumedY, 0.0001f)
        assertTrue(result.state.hidden)
    }

    @Test
    fun `未完全显示时松手恢复隐藏`() {
        val state = DrawerServerHeaderScrollState(hidden = true, offset = 100f)
            .scroll(deltaY = 99f)
            .state

        assertEquals(100f, state.settleTarget(hideDistance = 100f), 0.0001f)
    }

    @Test
    fun `完全显示后剩余距离留给会话列表`() {
        val state = DrawerServerHeaderScrollState(hidden = true, offset = 100f)
        val result = state.scroll(deltaY = 120f)

        assertEquals(0f, result.state.offset, 0.0001f)
        assertEquals(100f, result.consumedY, 0.0001f)
        assertFalse(result.state.hidden)
    }

    @Test
    fun `拖动反向时服务器行立即跟随`() {
        val result = DrawerServerHeaderScrollState()
            .scroll(deltaY = -60f)
            .state
            .scroll(deltaY = 20f)

        assertEquals(40f, result.state.offset, 0.0001f)
        assertEquals(20f, result.consumedY, 0.0001f)
        assertFalse(result.state.hidden)
    }

    private fun DrawerServerHeaderScrollState.scroll(deltaY: Float) = consumeUserScroll(
        deltaY = deltaY,
        hideDistance = 100f,
    )
}
