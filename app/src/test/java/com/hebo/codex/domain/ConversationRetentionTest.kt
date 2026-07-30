package com.hebo.codex.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationRetentionTest {
    @Test
    fun leaseRemainsHeldUntilEveryObserverReleasesIt() {
        val registry = ConversationLeaseRegistry<String>()

        registry.acquire("thread-1")
        registry.acquire("thread-1")
        registry.release("thread-1")

        assertTrue(registry.isHeld("thread-1"))

        registry.release("thread-1")

        assertFalse(registry.isHeld("thread-1"))
    }

    @Test
    fun runningTurnRetainsConversationWithoutObserver() {
        assertTrue(conversationState(isRunning = true).requiresRuntimeRetention)
        assertTrue(conversationState(activeTurnId = "turn-1").requiresRuntimeRetention)
    }

    @Test
    fun pendingRequestRetainsConversationWithoutObserver() {
        val request = PendingServerRequest.Questions(
            requestKey = "request-1",
            itemId = "item-1",
            turnId = "turn-1",
            questions = emptyList(),
            autoResolutionMillis = null,
            receivedAtMillis = 0,
        )

        assertTrue(conversationState(pendingRequests = listOf(request)).requiresRuntimeRetention)
    }

    @Test
    fun backgroundCommandRetainsConversationWithoutObserver() {
        val terminal = BackgroundTerminal(
            itemId = "command-1",
            processId = "4242",
            command = "sleep 60",
            cwd = "/workspace",
            observedAtMillis = 0L,
        )

        assertTrue(conversationState(backgroundTerminals = listOf(terminal)).requiresRuntimeRetention)
    }

    @Test
    fun completedConversationCanBeReleasedWithoutObserver() {
        assertFalse(conversationState().requiresRuntimeRetention)
    }

    @Test
    fun acceptsPlanOnlyForActiveTurn() {
        val conversation = conversationState(activeTurnId = "turn-2")
        val stalePlan = ActiveTurnPlan("turn-1", null, emptyList())
        val activePlan = ActiveTurnPlan("turn-2", null, emptyList())

        assertTrue(conversation === conversation.withActiveTurnPlan(stalePlan))
        assertTrue(activePlan === conversation.withActiveTurnPlan(activePlan).plan)
    }

    private fun conversationState(
        activeTurnId: String? = null,
        isRunning: Boolean = false,
        pendingRequests: List<PendingServerRequest> = emptyList(),
        backgroundTerminals: List<BackgroundTerminal> = emptyList(),
    ) = ConversationState(
        serverId = "server-1",
        serverName = "本地",
        threadId = "thread-1",
        title = "测试",
        cwd = "/workspace",
        activeTurnId = activeTurnId,
        isRunning = isRunning,
        pendingRequests = pendingRequests,
        backgroundTerminals = backgroundTerminals,
    )
}
