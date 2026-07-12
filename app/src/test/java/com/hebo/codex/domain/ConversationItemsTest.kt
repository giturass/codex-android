package com.hebo.codex.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationItemsTest {
    @Test
    fun `同一回合中的相同条目会被替换`() {
        val started = agentMessage("item-1", "turn-1", "生成中").copy(isStreaming = true)
        val completed = started.copy(text = "已完成", isStreaming = false)

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
    fun `乐观用户消息仍按客户端编号替换`() {
        val pending = ConversationItem.UserMessage(
            id = "pending-client-1",
            turnId = "pending",
            clientId = "client-1",
            content = listOf(UserMessagePart.Text("消息")),
            isPending = true,
        )
        val confirmed = pending.copy(
            id = "item-1",
            turnId = "turn-1",
            isPending = false,
        )

        assertEquals(listOf(confirmed), listOf(pending).upsertConversationItem(confirmed))
    }

    private fun agentMessage(id: String, turnId: String, text: String) = ConversationItem.AgentMessage(
        id = id,
        turnId = turnId,
        text = text,
        isStreaming = false,
    )
}
