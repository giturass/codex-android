package com.hebo.codex.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class RelativeTimeTest {
    private val now = Instant.parse("2026-07-10T04:00:00Z")

    @Test
    fun formatsRecentTimes() {
        assertEquals("刚刚", relativeTime(now.epochSecond - 20, now, ZoneOffset.UTC))
        assertEquals("5 分钟前", relativeTime(now.epochSecond - 300, now, ZoneOffset.UTC))
        assertEquals("2 小时前", relativeTime(now.epochSecond - 7_200, now, ZoneOffset.UTC))
    }

    @Test
    fun formatsYesterday() {
        assertEquals(
            "昨天",
            relativeTime(Instant.parse("2026-07-09T12:00:00Z").epochSecond, now, ZoneOffset.UTC),
        )
    }
}
