package com.hebo.codex.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationEventsTest {
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
