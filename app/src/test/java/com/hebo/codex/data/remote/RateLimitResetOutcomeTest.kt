package com.hebo.codex.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RateLimitResetOutcomeTest {
    @Test
    fun mapsKnownOutcomesToUserMessages() {
        assertEquals("限额已重置", rateLimitResetOutcomeMessage("reset"))
        assertEquals("当前没有需要重置的限额", rateLimitResetOutcomeMessage("nothingToReset"))
        assertEquals("没有可用的限额重置额度", rateLimitResetOutcomeMessage("noCredit"))
        assertEquals("该重置额度已使用", rateLimitResetOutcomeMessage("alreadyRedeemed"))
    }

    @Test
    fun rejectsUnknownOutcome() {
        assertThrows(ProtocolException::class.java) {
            rateLimitResetOutcomeMessage("unexpected")
        }
    }
}
