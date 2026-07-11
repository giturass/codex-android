package com.hebo.codex.ui.components

import com.hebo.codex.domain.ThreadAttention
import com.hebo.codex.domain.ThreadRuntimeStatus
import com.hebo.codex.domain.ThreadSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DrawerThreadStatusTest {
    @Test
    fun explicitAttentionTakesPriority() {
        val thread = thread(ThreadRuntimeStatus.ACTIVE)

        assertEquals(
            ThreadAttention.NEEDS_INPUT,
            effectiveThreadAttention(thread, ThreadAttention.NEEDS_INPUT),
        )
    }

    @Test
    fun derivesStatusWhenAttentionIsMissing() {
        assertEquals(
            ThreadAttention.RUNNING,
            effectiveThreadAttention(thread(ThreadRuntimeStatus.ACTIVE), null),
        )
        assertEquals(
            ThreadAttention.FAILED,
            effectiveThreadAttention(thread(ThreadRuntimeStatus.SYSTEM_ERROR), null),
        )
        assertNull(effectiveThreadAttention(thread(ThreadRuntimeStatus.IDLE), null))
    }

    private fun thread(status: ThreadRuntimeStatus) = ThreadSummary(
        id = "thread",
        sessionId = "session",
        title = "会话",
        preview = "",
        cwd = "/workspace/project",
        createdAt = 1,
        updatedAt = 1,
        recencyAt = null,
        status = status,
    )
}
