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
    fun `显式状态条目不写入瞬时活动集合`() {
        val command = command("command-1", ActivityStatus.IN_PROGRESS)

        val started = conversationState().withConversationItem(command, isActive = true)

        assertEquals(emptySet<ConversationItemKey>(), started.activeItemKeys)
        assertEquals(listOf(command), started.items)
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
    fun `前台刷新保留已分页加载的更早回合和游标`() {
        val older = agentMessage("item-old", "turn-old", "更早内容")
        val recent = agentMessage("item-recent", "turn-recent", "最近内容")
        val current = conversationState(items = listOf(older, recent)).copy(
            history = conversationState(items = listOf(older, recent)).history.copy(
                olderTurnsCursor = "older-cursor",
            ),
        )
        val refreshedRecent = recent.copy(text = "最近内容已刷新")
        val refreshed = conversationState(items = listOf(refreshedRecent)).copy(
            history = conversationState(items = listOf(refreshedRecent)).history.copy(
                olderTurnsCursor = "initial-cursor",
            ),
        )

        val merged = current.withRestoredConversation(refreshed)

        assertEquals(listOf(older, refreshedRecent), merged.items)
        assertEquals("older-cursor", merged.history.olderTurnsCursor)
    }

    @Test
    fun `恢复快照不保留缺失的显式状态条目`() {
        val command = command("command-1", ActivityStatus.IN_PROGRESS)
        val streaming = agentMessage("agent-1", "turn-1", "正在生成")
        val current = conversationState(
            items = listOf(command, streaming),
            activeTurnId = "turn-1",
            activeItemKeys = setOf(command.key, streaming.key),
        )
        val restored = conversationState(activeTurnId = "turn-1")

        val merged = current.withRestoredConversation(restored)

        assertEquals(listOf(streaming), merged.items)
        assertEquals(setOf(streaming.key), merged.activeItemKeys)
    }

    @Test
    fun `前台刷新只替换会话快照字段`() {
        val current = conversationState(
            items = listOf(command("command-1", ActivityStatus.IN_PROGRESS)),
            activeTurnId = "turn-1",
        ).copy(
            model = "gpt-5",
            goalLoaded = true,
            notices = listOf(ConversationNotice("notice", "保留提示")),
        )
        val refreshed = conversationState(
            items = listOf(command("command-1", ActivityStatus.COMPLETED)),
        ).copy(title = "新标题")

        val merged = current.withRefreshedConversation(refreshed)

        assertEquals("新标题", merged.title)
        assertEquals(
            ActivityStatus.COMPLETED,
            (merged.items.single() as ConversationItem.CommandExecution).status,
        )
        assertEquals("gpt-5", merged.model)
        assertEquals(true, merged.goalLoaded)
        assertEquals(listOf(ConversationNotice("notice", "保留提示")), merged.notices)
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

    private fun command(id: String, status: ActivityStatus) = ConversationItem.CommandExecution(
        id = id,
        turnId = "turn-1",
        command = "pwd",
        cwd = "/project",
        output = "",
        exitCode = null,
        status = status,
    )

    private fun conversationState(
        items: List<ConversationItem> = emptyList(),
        activeTurnId: String? = null,
        activeItemKeys: Set<ConversationItemKey> = emptySet(),
    ): ConversationState {
        val turns = items
            .groupBy(ConversationItem::turnId)
            .map { (turnId, turnItems) ->
                ConversationTurn(
                    id = turnId,
                    status = if (turnId == activeTurnId) {
                        ConversationTurnStatus.IN_PROGRESS
                    } else {
                        ConversationTurnStatus.COMPLETED
                    },
                    items = turnItems,
                )
            }
            .let { groupedTurns ->
                if (activeTurnId == null || groupedTurns.any { it.id == activeTurnId }) {
                    groupedTurns
                } else {
                    groupedTurns + ConversationTurn(activeTurnId, ConversationTurnStatus.IN_PROGRESS)
                }
            }
        return ConversationState(
            serverId = "server",
            serverName = "服务器",
            threadId = "thread",
            title = "会话",
            cwd = "/project",
            history = ConversationHistory(turns = turns),
            activeTurnId = activeTurnId,
            activeItemKeys = activeItemKeys,
        )
    }
}
