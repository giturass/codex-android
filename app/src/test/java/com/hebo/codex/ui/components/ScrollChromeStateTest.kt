package com.hebo.codex.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollChromeStateTest {
    @Test
    fun `控件位移与隐藏方向拖动距离一致`() {
        val state = ScrollChromeState()
            .scroll(hideDelta = 20f)
            .scroll(hideDelta = 30f)

        assertEquals(50f, state.offset, 0.0001f)
        assertFalse(state.fullyHidden)
    }

    @Test
    fun `达到完整高度后标记为隐藏`() {
        val state = ScrollChromeState().scroll(hideDelta = 120f)

        assertEquals(100f, state.offset, 0.0001f)
        assertTrue(state.fullyHidden)
    }

    @Test
    fun `隐藏后向显示方向拖动时逐像素恢复`() {
        val state = ScrollChromeState()
            .scroll(hideDelta = 100f)
            .scroll(hideDelta = -24f)

        assertEquals(76f, state.offset, 0.0001f)
        assertFalse(state.fullyHidden)
    }

    @Test
    fun `拖动方向反转时直接反向移动`() {
        val state = ScrollChromeState()
            .scroll(hideDelta = 30f)
            .scroll(hideDelta = -10f)

        assertEquals(20f, state.offset, 0.0001f)
        assertFalse(state.fullyHidden)
    }

    @Test
    fun `显示时隐藏未超过一半松手保持显示`() {
        val state = ScrollChromeState().scroll(hideDelta = 49f)

        assertEquals(0f, state.settleTarget(hideDistance = 100f), 0.0001f)
    }

    @Test
    fun `显示时隐藏超过一半松手改为隐藏`() {
        val state = ScrollChromeState().scroll(hideDelta = 51f)

        assertEquals(100f, state.settleTarget(hideDistance = 100f), 0.0001f)
    }

    @Test
    fun `显示时隐藏刚好一半松手保持显示`() {
        val state = ScrollChromeState().scroll(hideDelta = 50f)

        assertEquals(0f, state.settleTarget(hideDistance = 100f), 0.0001f)
    }

    @Test
    fun `隐藏时显示未超过一半松手保持隐藏`() {
        val state = hiddenState().scroll(hideDelta = -49f)

        assertEquals(100f, state.settleTarget(hideDistance = 100f), 0.0001f)
    }

    @Test
    fun `隐藏时显示超过一半松手改为显示`() {
        val state = hiddenState().scroll(hideDelta = -51f)

        assertEquals(0f, state.settleTarget(hideDistance = 100f), 0.0001f)
    }

    @Test
    fun `隐藏时显示刚好一半松手保持隐藏`() {
        val state = hiddenState().scroll(hideDelta = -50f)

        assertEquals(100f, state.settleTarget(hideDistance = 100f), 0.0001f)
    }

    private fun hiddenState() = ScrollChromeState(
        fullyHidden = true,
        offset = 100f,
        settledHidden = true,
    )

    private fun ScrollChromeState.scroll(hideDelta: Float) = afterUserScroll(
        hideDelta = hideDelta,
        hideDistance = 100f,
    )
}
