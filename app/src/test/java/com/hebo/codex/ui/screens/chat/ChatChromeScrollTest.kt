package com.hebo.codex.ui.screens.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatChromeScrollTest {
    @Test
    fun `安全距离内不隐藏`() {
        val delta = chromeScrollDelta(
            scrollDelta = 20f,
            distanceToBottom = 100f,
            hideStartDistance = 120f,
        )

        assertEquals(0f, delta, 0.0001f)
    }

    @Test
    fun `越过安全距离时只使用越界部分隐藏`() {
        val delta = chromeScrollDelta(
            scrollDelta = 20f,
            distanceToBottom = 125f,
            hideStartDistance = 120f,
        )

        assertEquals(5f, delta, 0.0001f)
    }

    @Test
    fun `安全距离外完整跟随隐藏方向滚动`() {
        val delta = chromeScrollDelta(
            scrollDelta = 20f,
            distanceToBottom = 160f,
            hideStartDistance = 120f,
        )

        assertEquals(20f, delta, 0.0001f)
    }

    @Test
    fun `安全距离外不跟随显示方向滚动`() {
        val delta = chromeScrollDelta(
            scrollDelta = -20f,
            distanceToBottom = 140f,
            hideStartDistance = 120f,
        )

        assertEquals(0f, delta, 0.0001f)
    }

    @Test
    fun `进入安全距离时只使用越界部分展开`() {
        val delta = chromeScrollDelta(
            scrollDelta = -20f,
            distanceToBottom = 115f,
            hideStartDistance = 120f,
        )

        assertEquals(-5f, delta, 0.0001f)
    }

    @Test
    fun `安全距离内完整跟随显示方向滚动`() {
        val delta = chromeScrollDelta(
            scrollDelta = -20f,
            distanceToBottom = 80f,
            hideStartDistance = 120f,
        )

        assertEquals(-20f, delta, 0.0001f)
    }
}
