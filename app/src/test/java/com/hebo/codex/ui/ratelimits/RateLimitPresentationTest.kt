package com.hebo.codex.ui.ratelimits

import androidx.compose.ui.unit.LayoutDirection
import com.hebo.codex.domain.AccountRateLimit
import com.hebo.codex.domain.AccountRateLimits
import com.hebo.codex.domain.RateLimitWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class RateLimitPresentationTest {
    @Test
    fun selectsCanonicalCodexWindowsByDuration() {
        val fiveHour = window(usedPercent = 28.0, durationMinutes = 300)
        val weekly = window(usedPercent = 57.0, durationMinutes = 10_080)
        val rateLimits = AccountRateLimits(
            limits = listOf(
                limit(id = "codex_other", primary = window(10.0, 300), secondary = null),
                limit(id = "codex", primary = weekly, secondary = fiveHour),
            ),
            resetCredits = null,
        )

        val windows = requireNotNull(rateLimits.codexWindows())

        assertEquals("codex", windows.limit.id)
        assertSame(fiveHour, windows.fiveHour)
        assertSame(weekly, windows.weekly)
    }

    @Test
    fun acceptsFivePercentWindowDurationVariation() {
        val rateLimits = AccountRateLimits(
            limits = listOf(
                limit(
                    id = "codex",
                    primary = window(20.0, 285),
                    secondary = window(30.0, 10_584),
                ),
            ),
            resetCredits = null,
        )

        val windows = requireNotNull(rateLimits.codexWindows())

        assertEquals(285L, windows.fiveHour.windowDurationMinutes)
        assertEquals(10_584L, windows.weekly?.windowDurationMinutes)
    }

    @Test
    fun doesNotPresentAnotherLimitBucketAsCodex() {
        val rateLimits = AccountRateLimits(
            limits = listOf(limit(id = "codex_other", primary = window(10.0, 300), secondary = null)),
            resetCredits = null,
        )

        assertNull(rateLimits.codexWindows())
    }

    @Test
    fun convertsUsedPercentToClampedRemainingPercent() {
        assertEquals(100.0, window(-4.0, 300).remainingPercent(), 0.0)
        assertEquals(66.5, window(33.5, 300).remainingPercent(), 0.0)
        assertEquals(0.0, window(120.0, 300).remainingPercent(), 0.0)
    }

    @Test
    fun formatsResetCountdownAtUsefulPrecision() {
        val now = 1_000L

        assertEquals("等待额度更新", resetCountdownLabel(now, now))
        assertEquals("不到 1 分钟后重置", resetCountdownLabel(now + 59, now))
        assertEquals("1 分钟后重置", resetCountdownLabel(now + 60, now))
        assertEquals("2 小时 18 分钟后重置", resetCountdownLabel(now + 2 * 3_600 + 17 * 60 + 1, now))
        assertEquals("3 天 6 小时后重置", resetCountdownLabel(now + 3 * 86_400 + 6 * 3_600, now))
    }

    @Test
    fun alignsRateLimitPopupToWindowEnd() {
        assertEquals(
            684,
            rateLimitPopupHorizontalPosition(
                windowWidth = 1_000,
                popupWidth = 300,
                layoutDirection = LayoutDirection.Ltr,
                windowMarginPx = 16,
            ),
        )
        assertEquals(
            16,
            rateLimitPopupHorizontalPosition(
                windowWidth = 1_000,
                popupWidth = 300,
                layoutDirection = LayoutDirection.Rtl,
                windowMarginPx = 16,
            ),
        )
    }

    private fun window(usedPercent: Double, durationMinutes: Long) = RateLimitWindow(
        usedPercent = usedPercent,
        windowDurationMinutes = durationMinutes,
        resetsAt = null,
    )

    private fun limit(
        id: String,
        primary: RateLimitWindow?,
        secondary: RateLimitWindow?,
    ) = AccountRateLimit(
        id = id,
        name = null,
        primary = primary,
        secondary = secondary,
        credits = null,
        individualLimit = null,
        planType = null,
        reachedType = null,
    )
}
