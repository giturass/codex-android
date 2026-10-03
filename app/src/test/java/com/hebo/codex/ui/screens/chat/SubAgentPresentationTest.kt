package com.hebo.codex.ui.screens.chat

import com.hebo.codex.domain.ActivityStatus
import com.hebo.codex.domain.AgentState
import com.hebo.codex.domain.AgentStatus
import com.hebo.codex.domain.AgentTool
import com.hebo.codex.domain.ConversationItem
import com.hebo.codex.domain.ConversationTurn
import com.hebo.codex.domain.ConversationTurnStatus
import com.hebo.codex.domain.SubAgentActivityKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubAgentPresentationTest {
    @Test
    fun completedSpawnDoesNotDisplayAnActiveToolWhileItsAgentRuns() {
        val call = collaboration()

        assertEquals(ActivityStatus.COMPLETED, call.activityStatus())
        assertNull(activityFollowTargetIndex(listOf(call), isSealed = true))
        assertTrue(activityItemHasDetails(call))
        assertEquals("复查登录流程", activityItemSubtitle(call))
    }

    @Test
    fun agentActionsRemainNavigableInSealedTurnDetails() {
        val items = listOf(
            collaboration(),
            ConversationItem.SubAgentActivity(
                "done", "turn", SubAgentActivityKind.COMPLETED, "child", "/root/review",
            ),
        )
        val turn = ConversationTurn("turn", ConversationTurnStatus.COMPLETED, items)
        val details = activityDetailsContent(turn, activeTurnId = null)

        assertEquals(listOf(ConversationRow.TurnActivity(turn.id)), buildConversationRows(listOf(turn)))
        assertTrue(details.isSealed)
        assertTrue(details.items.all(::activityItemHasDetails))
        assertEquals(items, details.items)
        assertEquals(listOf(ActivityGroupMetric(ActivityGroupMetricType.AGENT, 2)), activityGroupMetrics(details.items))
        assertEquals("/root/review", activityItemSubtitle(details.items.last()))
    }

    @Test
    fun interruptedToolRetainsItsDistinctOutcome() {
        val interrupted = collaboration().copy(tool = AgentTool.WAIT, status = ActivityStatus.INTERRUPTED)
        assertEquals("等待子代理 · 已中断", activityItemTitle(interrupted))
        assertEquals(ActivityStatus.INTERRUPTED, interrupted.activityStatus())
    }

    private fun collaboration() = ConversationItem.AgentCollaboration(
        id = "spawn", turnId = "turn", tool = AgentTool.SPAWN, status = ActivityStatus.COMPLETED,
        senderThreadId = "parent", receiverThreadIds = listOf("child"), prompt = "复查登录流程",
        model = "gpt-6", reasoningEffort = "high",
        agentsStates = mapOf("child" to AgentState(AgentStatus.RUNNING, null)),
    )
}
