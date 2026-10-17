package com.hebo.codex.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationEventsTest {
    @Test
    fun `新回合确认后的模型与深度由后续回合继承`() {
        val before = conversationState().copy(model = "gpt-5", reasoningEffort = "low")
        val accepted = before.withAcceptedTurnOptions(
            TurnOptions(model = "gpt-6", reasoningEffort = "ultra", serviceTier = "priority"),
            before.settingsRevision,
        )
        val completed = accepted.reduceConversationEvent(
            ConversationEvent.TurnCompleted("turn-1", ConversationTurnStatus.COMPLETED, null),
        )
        val next = completed.withAcceptedTurnOptions(TurnOptions(), completed.settingsRevision)
            .reduceConversationEvent(ConversationEvent.TurnStarted("turn-2"))

        assertEquals("gpt-6", next.model)
        assertEquals("ultra", next.reasoningEffort)
        assertEquals("priority", next.serviceTier)
    }

    @Test
    fun `设置通知同时同步模型深度工作目录和权限并清除可空设置`() {
        val before = conversationState().copy(
            model = "gpt-5",
            reasoningEffort = "high",
            serviceTier = "priority",
        )
        val updated = before.reduceConversationEvent(settingsUpdated(reasoningEffort = null))

        assertEquals("gpt-6", updated.model)
        assertNull(updated.reasoningEffort)
        assertNull(updated.serviceTier)
        assertEquals("/workspace/updated", updated.cwd)
        assertEquals("on-request", updated.approvalPolicy)
        assertEquals("auto_review", updated.approvalsReviewer)
        assertEquals("{\"type\":\"workspaceWrite\"}", updated.sandbox)
    }

    @Test
    fun `设置通知无论在提交确认前后到达都保留服务端实际深度`() {
        val before = conversationState().copy(model = "gpt-5", reasoningEffort = "low")
        val requested = TurnOptions(model = "requested-model", reasoningEffort = "ultra", serviceTier = "priority")
        val notification = settingsUpdated(reasoningEffort = "custom")

        val notificationFirst = before.reduceConversationEvent(notification)
            .withAcceptedTurnOptions(requested, before.settingsRevision)
        val responseFirst = before.withAcceptedTurnOptions(requested, before.settingsRevision)
            .reduceConversationEvent(notification)

        listOf(notificationFirst, responseFirst).forEach { state ->
            assertEquals("gpt-6", state.model)
            assertEquals("custom", state.reasoningEffort)
            assertNull(state.serviceTier)
        }
    }

    @Test
    fun `值未变化的服务端确认也不能被较旧提交覆盖`() {
        val notification = settingsUpdated(reasoningEffort = "low")
        val before = conversationState().reduceConversationEvent(notification)
        val confirmed = before.reduceConversationEvent(notification)

        val accepted = confirmed.withAcceptedTurnOptions(
            TurnOptions(reasoningEffort = "ultra"),
            before.settingsRevision,
        )

        assertEquals("low", accepted.reasoningEffort)
    }

    @Test
    fun `恢复快照先到达时提交确认不会覆盖恢复的设置`() {
        val before = conversationState().copy(model = "gpt-5", reasoningEffort = "low")
        val resumed = before.withRefreshedConversation(
            conversationState().copy(model = "gpt-6", reasoningEffort = "custom"),
        )

        val accepted = resumed.withAcceptedTurnOptions(
            TurnOptions(model = "requested-model", reasoningEffort = "ultra"),
            before.settingsRevision,
        )

        assertEquals("gpt-6", accepted.model)
        assertEquals("custom", accepted.reasoningEffort)
    }

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

    private fun settingsUpdated(reasoningEffort: String?) = ConversationEvent.SettingsUpdated(
        cwd = "/workspace/updated",
        model = "gpt-6",
        serviceTier = null,
        reasoningEffort = reasoningEffort,
        approvalPolicy = "on-request",
        approvalsReviewer = "auto_review",
        sandbox = "{\"type\":\"workspaceWrite\"}",
    )
}
