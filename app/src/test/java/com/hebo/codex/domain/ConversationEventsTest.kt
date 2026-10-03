package com.hebo.codex.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationEventsTest {
    @Test
    fun `新用量和目标与同步标记一起应用且互不清除对方标记`() {
        val state = conversationState().copy(runtimeStatus = RuntimeStatus().markStale())
        val breakdown = TokenUsageBreakdown(123, 100, 0, 23, 0)
        val usage = TokenUsage(breakdown, breakdown, 1000)
        val used = state.reduceConversationEvent(ConversationEvent.TokenUsageUpdated(usage))

        assertEquals(usage, used.tokenUsage)
        assertFalse(used.runtimeStatus.tokenUsageStale)
        assertTrue(used.runtimeStatus.goalStale)

        val goal = ThreadGoal("检查任务", ThreadGoalStatus.ACTIVE, 1000, 100, 10)
        val updated = used.reduceConversationEvent(ConversationEvent.GoalChanged(goal))
        assertEquals(goal, updated.goal)
        assertFalse(updated.runtimeStatus.goalStale)
        assertTrue(updated.goalLoaded)
    }

    @Test
    fun `运行状态变化统一清理活动条目`() {
        val state = conversationState().copy(
            activeItemKeys = setOf(ConversationItemKey("turn-1", "item-1")),
            isRunning = true,
        )

        val result = state.reduceConversationEvent(
            ConversationEvent.StatusChanged(ConversationRunStatus.INACTIVE),
        )

        assertFalse(result.isRunning)
        assertTrue(result.activeItemKeys.isEmpty())
    }

    @Test
    fun `回合完成统一重置瞬时状态并保留错误`() {
        val error = UserFacingError("执行失败")
        val state = conversationState().copy(
            activeTurnId = "turn-1",
            activeItemKeys = setOf(ConversationItemKey("turn-1", "item-1")),
            isRunning = true,
            plan = ActiveTurnPlan("turn-1", null, emptyList()),
        )

        val result = state.reduceConversationEvent(
            ConversationEvent.TurnCompleted(
                turnId = "turn-1",
                status = ConversationTurnStatus.FAILED,
                error = error,
            ),
        )

        assertNull(result.activeTurnId)
        assertFalse(result.isRunning)
        assertNull(result.plan)
        assertEquals(error, result.errorMessage)
        assertTrue(result.activeItemKeys.isEmpty())
        assertEquals(ConversationTurnStatus.FAILED, result.history.turns.single().status)
        assertEquals(error, result.history.turns.single().error)
    }

    @Test
    fun `失败原因保留在已存在回合中且新回合不会清除`() {
        val error = UserFacingError("任务执行失败", "服务端执行错误")
        val item = ConversationItem.AgentMessage("item-1", "turn-1", "正在处理")
        val running = conversationState()
            .reduceConversationEvent(ConversationEvent.TurnStarted("turn-1"))
            .withConversationItem(item, isActive = true)

        val failed = running.reduceConversationEvent(
            ConversationEvent.TurnCompleted("turn-1", ConversationTurnStatus.FAILED, error),
        )
        val next = failed.reduceConversationEvent(ConversationEvent.TurnStarted("turn-2"))

        assertNull(next.errorMessage)
        assertEquals(listOf(item), next.history.turns.first().items)
        assertEquals(error, next.history.turns.first().error)
        assertNull(next.history.turns.last().error)
    }

    @Test
    fun `条目增量通过类型化更新应用`() {
        val state = conversationState().copy(
            history = ConversationHistory(
                turns = listOf(
                    ConversationTurn(
                        id = "turn-1",
                        status = ConversationTurnStatus.IN_PROGRESS,
                        items = listOf(ConversationItem.AgentMessage("item-1", "turn-1", "开始")),
                    ),
                ),
            ),
        )
        val event = ConversationEvent.ItemChanged(
            method = "item/agentMessage/delta",
            turnId = "turn-1",
            itemId = "item-1",
            markActive = true,
            update = ConversationItemUpdate { item ->
                val message = item as ConversationItem.AgentMessage
                message.copy(text = message.text + "完成")
            },
        )

        val result = state.reduceConversationEvent(event)
        val item = result.items.single() as ConversationItem.AgentMessage

        assertEquals("开始完成", item.text)
        assertEquals(setOf(ConversationItemKey("turn-1", "item-1")), result.activeItemKeys)
    }

    private fun conversationState() = ConversationState(
        serverId = "server-1",
        serverName = "测试服务器",
        threadId = "thread-1",
        title = "测试会话",
        cwd = "/workspace",
    )
}
