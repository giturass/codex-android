package com.hebo.codex.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationItemsTest {
    @Test
    fun `同一回合中的相同条目会被替换`() {
        val started = agentMessage("item-1", "turn-1", "生成中")
        val completed = started.copy(text = "已完成")

        assertEquals(listOf(completed), listOf(started).upsertConversationItem(completed))
    }

    @Test
    fun `相同条目编号可存在于不同回合`() {
        val first = agentMessage("item-1", "turn-1", "第一回合")
        val second = agentMessage("item-1", "turn-2", "第二回合")

        val items = listOf(first).upsertConversationItem(second)

        assertEquals(listOf(first, second), items)
    }

    @Test
    fun `流式更新只替换指定回合中的条目`() {
        val first = agentMessage("item-1", "turn-1", "第一回合")
        val second = agentMessage("item-1", "turn-2", "第二回合")

        val items = listOf(first, second).replaceConversationItem("turn-2", "item-1") { item ->
            (item as ConversationItem.AgentMessage).copy(text = "第二回合已更新")
        }

        assertEquals(
            listOf(first, second.copy(text = "第二回合已更新")),
            items,
        )
    }

    @Test
    fun `条目生命周期独立维护活动状态`() {
        val startedItem = agentMessage("item-1", "turn-1", "生成中")
        val started = conversationState().withConversationItem(startedItem, isActive = true)
        val completedItem = startedItem.copy(text = "已完成")
        val completed = started.withConversationItem(completedItem, isActive = false)

        assertEquals(setOf(ConversationItemKey("turn-1", "item-1")), started.activeItemKeys)
        assertEquals(emptySet<ConversationItemKey>(), completed.activeItemKeys)
        assertEquals(listOf(completedItem), completed.items)
    }

    @Test
    fun `恢复后的增量会重新激活目标条目`() {
        val item = agentMessage("item-1", "turn-1", "已有")
        val restored = conversationState(items = listOf(item))

        val updated = restored.withUpdatedConversationItem(
            turnId = "turn-1",
            itemId = "item-1",
            markActive = true,
        ) { current ->
            (current as ConversationItem.AgentMessage).copy(text = current.text + "增量")
        }

        assertEquals(setOf(ConversationItemKey("turn-1", "item-1")), updated?.activeItemKeys)
        assertEquals("已有增量", (updated?.items?.single() as ConversationItem.AgentMessage).text)
    }

    @Test
    fun `回合完成会清除该回合的全部活动条目`() {
        val state = conversationState().copy(
            activeItemKeys = setOf(
                ConversationItemKey("turn-1", "item-1"),
                ConversationItemKey("turn-1", "item-2"),
                ConversationItemKey("turn-2", "item-3"),
            ),
        )

        assertEquals(
            setOf(ConversationItemKey("turn-2", "item-3")),
            state.withoutActiveItems("turn-1").activeItemKeys,
        )
    }

    private fun agentMessage(id: String, turnId: String, text: String) = ConversationItem.AgentMessage(
        id = id,
        turnId = turnId,
        text = text,
    )

    private fun conversationState(items: List<ConversationItem> = emptyList()) = ConversationState(
        serverId = "server",
        serverName = "服务器",
        threadId = "thread",
        title = "会话",
        cwd = "/project",
        items = items,
    )
}
