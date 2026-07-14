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
    fun `恢复快照保留同一活动回合中缺失的流式条目`() {
        val completed = agentMessage("item-1", "turn-1", "旧内容")
        val streaming = agentMessage("item-2", "turn-1", "正在生成")
        val current = conversationState(
            items = listOf(completed, streaming),
            activeTurnId = "turn-1",
            activeItemKeys = setOf(streaming.key),
        )
        val restoredCompleted = completed.copy(text = "快照内容")
        val restored = conversationState(
            items = listOf(restoredCompleted),
            activeTurnId = "turn-1",
        )

        val merged = current.withRestoredConversation(restored)
        val updated = merged.withUpdatedConversationItem("turn-1", "item-2", true) { item ->
            (item as ConversationItem.AgentMessage).copy(text = item.text + "增量")
        }

        assertEquals(listOf(restoredCompleted, streaming.copy(text = "正在生成增量")), updated?.items)
        assertEquals(setOf(streaming.key), updated?.activeItemKeys)
    }

    @Test
    fun `恢复快照中的同名条目覆盖本地活动条目`() {
        val streaming = agentMessage("item-1", "turn-1", "正在生成")
        val completed = streaming.copy(text = "完整内容")
        val current = conversationState(
            items = listOf(streaming),
            activeTurnId = "turn-1",
            activeItemKeys = setOf(streaming.key),
        )
        val restored = conversationState(
            items = listOf(completed),
            activeTurnId = "turn-1",
        )

        val merged = current.withRestoredConversation(restored)

        assertEquals(listOf(completed), merged.items)
        assertEquals(emptySet<ConversationItemKey>(), merged.activeItemKeys)
    }

    @Test
    fun `恢复到其他回合时不保留旧流式条目`() {
        val streaming = agentMessage("item-1", "turn-1", "正在生成")
        val current = conversationState(
            items = listOf(streaming),
            activeTurnId = "turn-1",
            activeItemKeys = setOf(streaming.key),
        )
        val next = agentMessage("item-2", "turn-2", "新回合")
        val restored = conversationState(items = listOf(next), activeTurnId = "turn-2")

        assertEquals(restored, current.withRestoredConversation(restored))
    }

    @Test
    fun `恢复快照可识别显式进行中的活动条目`() {
        val command = ConversationItem.CommandExecution(
            id = "command-1",
            turnId = "turn-1",
            command = "pwd",
            cwd = "/project",
            output = "",
            exitCode = null,
            status = ActivityStatus.IN_PROGRESS,
        )

        assertEquals(true, command.isExplicitlyInProgress)
        assertEquals(false, command.copy(status = ActivityStatus.COMPLETED).isExplicitlyInProgress)
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

    private fun conversationState(
        items: List<ConversationItem> = emptyList(),
        activeTurnId: String? = null,
        activeItemKeys: Set<ConversationItemKey> = emptySet(),
    ) = ConversationState(
        serverId = "server",
        serverName = "服务器",
        threadId = "thread",
        title = "会话",
        cwd = "/project",
        items = items,
        activeTurnId = activeTurnId,
        activeItemKeys = activeItemKeys,
    )
}
