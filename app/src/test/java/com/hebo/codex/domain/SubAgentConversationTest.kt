package com.hebo.codex.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubAgentConversationTest {
    @Test
    fun refreshRestoresParentIdentityAndInputCapability() {
        val initial = conversation()
        val snapshot = initial.copy(
            parentThreadId = "parent",
            agentNickname = "Ada",
            agentRole = "reviewer",
            canAcceptDirectInput = false,
        )

        val restored = initial.withRefreshedConversation(snapshot)

        assertEquals(snapshot, restored)
        assertTrue(restored.isViewOnly)
        assertFalse(restored.withRefreshedConversation(snapshot.copy(canAcceptDirectInput = true)).isViewOnly)
        assertFalse(snapshot.copy(canAcceptDirectInput = null).isViewOnly)
    }

    @Test
    fun newAgentStatusDoesNotRewriteEarlierToolRecords() {
        val spawned = ConversationItem.AgentCollaboration(
            id = "spawn", turnId = "turn", tool = AgentTool.SPAWN, status = ActivityStatus.COMPLETED,
            senderThreadId = "parent", receiverThreadIds = listOf("child"), prompt = "复查",
            model = null, reasoningEffort = null,
            agentsStates = mapOf("child" to AgentState(AgentStatus.RUNNING, null)),
        )
        val waiting = spawned.copy(id = "wait", tool = AgentTool.WAIT, status = ActivityStatus.IN_PROGRESS)
        val completed = waiting.copy(
            status = ActivityStatus.COMPLETED,
            agentsStates = mapOf("child" to AgentState(AgentStatus.COMPLETED, "已通过")),
        )
        val state = conversation()
            .withConversationItem(spawned, isActive = false)
            .withConversationItem(waiting, isActive = true)
            .withConversationItem(completed, isActive = false)

        assertEquals(listOf(spawned, completed), state.items)
        assertTrue(state.activeItemKeys.isEmpty())
        assertEquals(state.items, conversation().withRestoredConversation(state).items)
    }

    private fun conversation() = ConversationState(
        serverId = "server", serverName = "Server", threadId = "child", title = "Review", cwd = "/workspace",
    )
}
