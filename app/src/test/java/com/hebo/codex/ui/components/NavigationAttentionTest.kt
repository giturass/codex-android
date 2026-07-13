package com.hebo.codex.ui.components

import com.hebo.codex.domain.ThreadAttention
import com.hebo.codex.domain.ThreadAttentionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavigationAttentionTest {
    @Test
    fun excludesCurrentAndRunningThreads() {
        val result = navigationAttention(
            attentionByThread = mapOf(
                "current" to attention(ThreadAttention.NEEDS_INPUT, 1),
                "running" to attention(ThreadAttention.RUNNING, 2),
                "ready" to attention(ThreadAttention.READY, 3),
            ),
            currentThreadId = "current",
        )

        assertEquals(
            NavigationAttention(
                count = 1,
                targetThreadId = "ready",
                targetAttention = ThreadAttention.READY,
            ),
            result,
        )
    }

    @Test
    fun usesHighestPriorityAttention() {
        val result = navigationAttention(
            mapOf(
                "ready" to attention(ThreadAttention.READY, 3),
                "input" to attention(ThreadAttention.NEEDS_INPUT, 2),
                "failed" to attention(ThreadAttention.FAILED, 1),
            ),
        )

        assertEquals(
            NavigationAttention(
                count = 3,
                targetThreadId = "failed",
                targetAttention = ThreadAttention.FAILED,
            ),
            result,
        )
    }

    @Test
    fun usesNewestAttentionWithinSamePriority() {
        val result = navigationAttention(
            mapOf(
                "older" to attention(ThreadAttention.NEEDS_INPUT, 1),
                "newer" to attention(ThreadAttention.NEEDS_INPUT, 2),
            ),
        )

        assertEquals(
            NavigationAttention(
                count = 2,
                targetThreadId = "newer",
                targetAttention = ThreadAttention.NEEDS_INPUT,
            ),
            result,
        )
    }

    @Test
    fun returnsNullWithoutPendingAttention() {
        assertNull(navigationAttention(mapOf("running" to attention(ThreadAttention.RUNNING, 1))))
        assertNull(navigationAttention(emptyMap()))
    }

    private fun attention(attention: ThreadAttention, revision: Long) =
        ThreadAttentionState(attention, revision)
}
