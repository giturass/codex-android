package com.hebo.codex.ui.screens.chat

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatLayoutTest {
    @Test
    fun `紧凑输入区使用最小安全高度`() {
        assertEquals(
            140.dp,
            chatBottomContentPadding(
                bottomControlsHeight = 80.dp,
                bottomSystemPadding = 24.dp,
            ),
        )
    }

    @Test
    fun `展开输入区使用实际高度`() {
        assertEquals(
            180.dp,
            chatBottomContentPadding(
                bottomControlsHeight = 156.dp,
                bottomSystemPadding = 24.dp,
            ),
        )
    }
}
