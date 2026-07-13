package com.hebo.codex.ui.components

import com.hebo.codex.domain.ThreadAttention
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavigationAttentionTest {
    @Test
    fun excludesCurrentAndRunningThreads() {
        val result = navigationAttention(
            attentionByThread = mapOf(
                "current" to ThreadAttention.NEEDS_INPUT,
                "running" to ThreadAttention.RUNNING,
                "ready" to ThreadAttention.READY,
            ),
            currentThreadId = "current",
        )

        assertEquals(
            NavigationAttention(count = 1, highestPriority = ThreadAttention.READY),
            result,
        )
    }

    @Test
    fun usesHighestPriorityAttention() {
        val result = navigationAttention(
            mapOf(
                "ready" to ThreadAttention.READY,
                "input" to ThreadAttention.NEEDS_INPUT,
                "failed" to ThreadAttention.FAILED,
            ),
        )

        assertEquals(
            NavigationAttention(count = 3, highestPriority = ThreadAttention.FAILED),
            result,
        )
    }

    @Test
    fun returnsNullWithoutPendingAttention() {
        assertNull(navigationAttention(mapOf("running" to ThreadAttention.RUNNING)))
        assertNull(navigationAttention(emptyMap()))
    }
}
