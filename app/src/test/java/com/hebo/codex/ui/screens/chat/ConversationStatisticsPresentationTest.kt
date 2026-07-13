package com.hebo.codex.ui.screens.chat

import com.hebo.codex.domain.TokenUsage
import com.hebo.codex.domain.TokenUsageBreakdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationStatisticsPresentationTest {
    @Test
    fun combinesCumulativeAndLatestUsageByCategory() {
        val rows = tokenUsageComparisonRows(
            TokenUsage(
                total = usage(total = 120, input = 90, cached = 40, output = 30, reasoning = 10),
                last = usage(total = 20, input = 15, cached = 8, output = 5, reasoning = 2),
                modelContextWindow = 200_000,
            ),
        )

        assertEquals(listOf("总计", "输入", "其中缓存", "输出", "其中推理"), rows.map { it.label })
        assertEquals(listOf(120L, 90L, 40L, 30L, 10L), rows.map { it.cumulativeTokens })
        assertEquals(listOf(20L, 15L, 8L, 5L, 2L), rows.map { it.latestTokens })
        assertEquals(listOf(100L, 75L, 32L, 25L, 8L), rows.map { it.previousTokens })
        assertFalse(rows[1].isDetail)
        assertTrue(rows[2].isDetail)
        assertFalse(rows[3].isDetail)
        assertTrue(rows[4].isDetail)
    }

    @Test
    fun retainsZeroValueCategoriesForStableLayout() {
        val rows = tokenUsageComparisonRows(
            TokenUsage(
                total = usage(total = 10, input = 8, output = 2),
                last = usage(total = 10, input = 8, output = 2),
                modelContextWindow = null,
            ),
        )

        assertEquals(5, rows.size)
        assertEquals(0L, rows.single { it.label == "其中缓存" }.cumulativeTokens)
        assertEquals(0L, rows.single { it.label == "其中推理" }.latestTokens)
    }

    private fun usage(
        total: Long,
        input: Long,
        cached: Long = 0,
        output: Long,
        reasoning: Long = 0,
    ) = TokenUsageBreakdown(
        totalTokens = total,
        inputTokens = input,
        cachedInputTokens = cached,
        outputTokens = output,
        reasoningOutputTokens = reasoning,
    )
}
