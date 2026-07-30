package com.hebo.codex.ui.screens.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundCommandsTest {
    @Test
    fun `后台命令耗时使用紧凑格式`() {
        assertEquals("0 秒", formatBackgroundCommandDuration(999L))
        assertEquals("12 秒", formatBackgroundCommandDuration(12_500L))
        assertEquals("2 分 3 秒", formatBackgroundCommandDuration(123_000L))
        assertEquals("1 小时 2 分", formatBackgroundCommandDuration(3_723_000L))
    }
}
