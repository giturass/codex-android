package com.hebo.codex.ui.screens.chat

import com.hebo.codex.domain.ActivityStatus
import com.hebo.codex.domain.ConversationItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationRowsTest {
    @Test
    fun `连续活动按回合合并`() {
        val items = listOf(
            command("command-1", "turn-1"),
            command("command-2", "turn-1"),
            ConversationItem.AgentMessage("answer", "turn-1", "完成", false),
            command("command-3", "turn-2"),
        )

        val rows = buildConversationRows(items)

        assertEquals(3, rows.size)
        assertTrue(rows[0] is ConversationRow.ActivityGroup)
        assertEquals(2, (rows[0] as ConversationRow.ActivityGroup).items.size)
        assertTrue(rows[1] is ConversationRow.Item)
        assertTrue(rows[2] is ConversationRow.ActivityGroup)
    }

    @Test
    fun `不同回合的活动不会合并`() {
        val rows = buildConversationRows(
            listOf(command("command-1", "turn-1"), command("command-2", "turn-2")),
        )

        assertEquals(2, rows.size)
        assertTrue(rows.all { it is ConversationRow.ActivityGroup })
    }

    @Test
    fun `相同条目编号在不同回合中具有不同列表键`() {
        val rows = buildConversationRows(
            listOf(
                ConversationItem.AgentMessage("item-1", "turn-1", "第一回合", false),
                ConversationItem.AgentMessage("item-1", "turn-2", "第二回合", false),
            ),
        )

        assertEquals(2, rows.size)
        assertEquals(2, rows.map(ConversationRow::key).toSet().size)
    }

    @Test
    fun `相同起始条目编号在不同回合中具有不同活动组键`() {
        val rows = buildConversationRows(
            listOf(
                command("item-1", "turn-1"),
                command("item-2", "turn-1"),
                command("item-1", "turn-2"),
                command("item-2", "turn-2"),
            ),
        )

        assertEquals(2, rows.size)
        assertTrue(rows.all { it is ConversationRow.ActivityGroup })
        assertEquals(2, rows.map(ConversationRow::key).toSet().size)
    }

    @Test
    fun `推理和操作收纳到同一活动组`() {
        val reasoning = ConversationItem.Reasoning(
            id = "reasoning",
            turnId = "turn-1",
            summary = "分析项目",
            details = "先检查目录",
            isStreaming = false,
        )

        val rows = buildConversationRows(listOf(reasoning, command("command", "turn-1")))

        assertEquals(1, rows.size)
        assertEquals(2, (rows.single() as ConversationRow.ActivityGroup).items.size)
        assertEquals(
            "思考过程",
            activityGroupLabel((rows.single() as ConversationRow.ActivityGroup).items),
        )
    }

    @Test
    fun `运行中的操作提供明确摘要`() {
        val rows = buildConversationRows(
            listOf(command("command", "turn-1", ActivityStatus.IN_PROGRESS)),
        )

        assertEquals(
            "正在执行 pwd",
            activityGroupLabel((rows.single() as ConversationRow.ActivityGroup).items),
        )
    }

    @Test
    fun `计划作为独立的持久化记录展示`() {
        val plan = ConversationItem.Plan(
            id = "plan-1",
            turnId = "turn-1",
            text = "1. 检查实现",
            isStreaming = false,
        )

        val rows = buildConversationRows(listOf(command("command", "turn-1"), plan))

        assertEquals(2, rows.size)
        assertTrue(rows[0] is ConversationRow.ActivityGroup)
        assertEquals(plan, (rows[1] as ConversationRow.Item).item)
    }

    private fun command(
        id: String,
        turnId: String,
        status: ActivityStatus = ActivityStatus.COMPLETED,
    ) = ConversationItem.CommandExecution(
        id = id,
        turnId = turnId,
        command = "pwd",
        cwd = "/project",
        output = "",
        exitCode = 0,
        status = status,
    )
}
