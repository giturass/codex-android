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
        assertTrue(rows[2] is ConversationRow.Item)
    }

    @Test
    fun `不同回合的活动不会合并`() {
        val rows = buildConversationRows(
            listOf(command("command-1", "turn-1"), command("command-2", "turn-2")),
        )

        assertEquals(2, rows.size)
        assertTrue(rows[0] is ConversationRow.Item)
        assertTrue(rows[1] is ConversationRow.Item)
    }

    private fun command(id: String, turnId: String) = ConversationItem.CommandExecution(
        id = id,
        turnId = turnId,
        command = "pwd",
        cwd = "/project",
        output = "",
        exitCode = 0,
        status = ActivityStatus.COMPLETED,
    )
}
