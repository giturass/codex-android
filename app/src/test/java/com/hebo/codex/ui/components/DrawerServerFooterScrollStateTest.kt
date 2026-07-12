package com.hebo.codex.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawerServerFooterScrollStateTest {
    @Test
    fun `浏览更早会话时服务器栏逐像素隐藏`() {
        val state = ScrollChromeState().scroll(deltaY = -36f)

        assertEquals(36f, state.offset, 0.0001f)
        assertFalse(state.fullyHidden)
    }

    @Test
    fun `浏览更早会话达到完整高度后服务器栏隐藏`() {
        val state = ScrollChromeState().scroll(deltaY = -120f)

        assertEquals(100f, state.offset, 0.0001f)
        assertTrue(state.fullyHidden)
    }

    @Test
    fun `返回最近会话时服务器栏逐像素恢复`() {
        val state = hiddenState().scroll(deltaY = 24f)

        assertEquals(76f, state.offset, 0.0001f)
        assertFalse(state.fullyHidden)
    }

    @Test
    fun `拖动方向反转时服务器栏立即跟随`() {
        val state = ScrollChromeState()
            .scroll(deltaY = -60f)
            .scroll(deltaY = 20f)

        assertEquals(40f, state.offset, 0.0001f)
        assertFalse(state.fullyHidden)
    }

    private fun hiddenState() = ScrollChromeState(
        fullyHidden = true,
        offset = 100f,
        settledHidden = true,
    )

    private fun ScrollChromeState.scroll(deltaY: Float) = afterDrawerUserScroll(
        deltaY = deltaY,
        hideDistance = 100f,
    )
}
