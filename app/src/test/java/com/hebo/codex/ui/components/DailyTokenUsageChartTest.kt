package com.hebo.codex.ui.components

import com.hebo.codex.domain.DailyTokenUsage
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class DailyTokenUsageChartTest {
    @Test
    fun normalizesDateRangeAndDailyTotals() {
        val endDate = LocalDate.of(2026, 7, 11)
        val values = normalizeDailyTokenUsage(
            buckets = listOf(
                DailyTokenUsage(LocalDate.of(2026, 7, 8), 100),
                DailyTokenUsage(LocalDate.of(2026, 7, 9), -5),
                DailyTokenUsage(LocalDate.of(2026, 7, 10), 12_000),
                DailyTokenUsage(LocalDate.of(2026, 7, 10), 8_000),
                DailyTokenUsage(LocalDate.of(2026, 7, 12), 100),
            ),
            endDate = endDate,
            dayCount = 3,
        )

        assertEquals(
            listOf(
                LocalDate.of(2026, 7, 9),
                LocalDate.of(2026, 7, 10),
                LocalDate.of(2026, 7, 11),
            ),
            values.map(DailyTokenUsage::date),
        )
        assertEquals(listOf(0L, 20_000L, 0L), values.map(DailyTokenUsage::tokens))
    }

    @Test
    fun roundsAxisMaximumToReadableValue() {
        assertEquals(0L, dailyTokenUsageAxisMaximum(0))
        assertEquals(2_000L, dailyTokenUsageAxisMaximum(1_250))
        assertEquals(5_000L, dailyTokenUsageAxisMaximum(4_200))
        assertEquals(10_000L, dailyTokenUsageAxisMaximum(8_000))
    }

    @Test
    fun mapsChartPositionToBoundedIndex() {
        assertEquals(0, dailyTokenUsageIndexAt(-10f, 100f, 10))
        assertEquals(0, dailyTokenUsageIndexAt(9.9f, 100f, 10))
        assertEquals(1, dailyTokenUsageIndexAt(10f, 100f, 10))
        assertEquals(9, dailyTokenUsageIndexAt(100f, 100f, 10))
    }
}
