package com.hebo.codex.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConversationAttentionTest {
    @Test
    fun viewedCompletionDoesNotRequireAttention() {
        assertNull(completedThreadAttention(hasError = false, isViewed = true))
        assertNull(completedThreadAttention(hasError = true, isViewed = true))
    }

    @Test
    fun unviewedCompletionReflectsOutcome() {
        assertEquals(
            ThreadAttention.READY,
            completedThreadAttention(hasError = false, isViewed = false),
        )
        assertEquals(
            ThreadAttention.FAILED,
            completedThreadAttention(hasError = true, isViewed = false),
        )
    }
}
