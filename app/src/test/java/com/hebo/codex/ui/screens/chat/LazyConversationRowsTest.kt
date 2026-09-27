package com.hebo.codex.ui.screens.chat

import com.hebo.codex.domain.ActivityStatus
import com.hebo.codex.domain.AgentMessagePhase
import com.hebo.codex.domain.ConversationItem
import com.hebo.codex.domain.ConversationTurn
import com.hebo.codex.domain.ConversationTurnStatus
import com.hebo.codex.domain.TurnDetailsState
import com.hebo.codex.domain.UserFacingError
import com.hebo.codex.domain.UserMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LazyConversationRowsTest {
    @Test
    fun `历史摘要在问题与回答之间提供处理过程入口`() {
        val turn = summary(userMessage(), answer())

        val rows = buildConversationRows(listOf(turn), activeTurnId = null)

        assertEquals(
            listOf(
                ConversationRow.Item(userMessage()),
                ConversationRow.TurnActivity(turn.id),
                ConversationRow.Item(answer()),
            ),
            rows,
        )
        assertFalse(rows.any { it is ConversationRow.ActivityGroup })
    }

    @Test
    fun `加载与失败状态保持相同的摘要行`() {
        val turn = summary(userMessage(), answer())
        val expected = buildConversationRows(listOf(turn), activeTurnId = null)

        listOf(
            TurnDetailsState.Loading(),
            TurnDetailsState.Failed(UserFacingError("加载失败")),
        ).forEach { state ->
            assertEquals(
                expected,
                buildConversationRows(listOf(turn.copy(detailsState = state)), activeTurnId = null),
            )
        }
    }

    @Test
    fun `空摘要和缺少用户消息的摘要仍可打开处理过程`() {
        listOf(summary(), summary(answer())).forEach { turn ->
            val rows = buildConversationRows(listOf(turn), activeTurnId = null)

            assertEquals(ConversationRow.TurnActivity(turn.id), rows.first())
        }
    }

    @Test
    fun `最后一条摘要为进度消息时不伪造活动统计`() {
        val turn = summary(userMessage(), commentary())

        val rows = buildConversationRows(listOf(turn), activeTurnId = null)

        assertEquals(
            listOf(ConversationRow.Item(userMessage()), ConversationRow.TurnActivity(turn.id)),
            rows,
        )
        assertTrue(activityDetailsContent(turn, rows, rowKey = null)!!.items.isEmpty())
    }

    @Test
    fun `完整内容替换摘要时问答列表键保持不变`() {
        val turn = summary(userMessage(), answer())
        val initialRows = buildConversationRows(listOf(turn), activeTurnId = null)
        val loaded = turn.copy(
            items = listOf(userMessage(), command(), answer()),
            detailsState = TurnDetailsState.Loaded,
        )

        val loadedRows = buildConversationRows(listOf(loaded), activeTurnId = null)

        assertEquals(
            initialRows.filterIsInstance<ConversationRow.Item>().map(ConversationRow::key),
            loadedRows.filterIsInstance<ConversationRow.Item>().map(ConversationRow::key),
        )
        assertTrue(loadedRows[1] is ConversationRow.ActivityGroup)
        assertFalse(loadedRows.any { it is ConversationRow.TurnActivity })
    }

    @Test
    fun `按回合打开的面板在摘要替换后展示完整处理过程`() {
        val turn = summary(userMessage(), answer())
        val initialRows = buildConversationRows(listOf(turn), activeTurnId = null)
        val loaded = turn.copy(
            items = listOf(userMessage(), command(), answer(), commentary()),
            detailsState = TurnDetailsState.Loaded,
        )
        val loadedRows = buildConversationRows(listOf(loaded), activeTurnId = null)

        assertNotNull(activityDetailsContent(turn, initialRows, rowKey = null))
        assertEquals(
            listOf(command(), commentary()),
            activityDetailsContent(loaded, loadedRows, rowKey = null)!!.items,
        )
    }

    @Test
    fun `纯问答加载成功后保留空处理过程面板`() {
        val loaded = summary(userMessage(), answer()).copy(detailsState = TurnDetailsState.Loaded)
        val rows = buildConversationRows(listOf(loaded), activeTurnId = null)

        val details = activityDetailsContent(loaded, rows, rowKey = null)

        assertNotNull(details)
        assertTrue(details!!.items.isEmpty())
    }

    @Test
    fun `空摘要加载出的用户Shell命令可直接在详情面板查看`() {
        val shell = ConversationItem.UserShellCommand(
            id = "shell", turnId = "turn-1", command = "pwd", cwd = "/project",
            output = "/project", exitCode = 0, durationMillis = 100,
            status = ActivityStatus.COMPLETED,
        )
        val loaded = summary(shell).copy(detailsState = TurnDetailsState.Loaded)
        val rows = buildConversationRows(listOf(loaded), activeTurnId = null)

        assertEquals(listOf(shell), activityDetailsContent(loaded, rows, rowKey = null)!!.items)
        assertTrue(activityItemHasDetails(shell))
        assertEquals(listOf(ConversationRow.Item(shell)), rows)
    }

    @Test
    fun `直接打开已加载活动组仍只展示该组`() {
        val turn = summary(command(), answer(), commentary()).copy(detailsState = TurnDetailsState.Loaded)
        val rows = buildConversationRows(listOf(turn), activeTurnId = null)
        val firstGroup = rows.first() as ConversationRow.ActivityGroup

        assertEquals(
            ActivityDetailsContent(listOf(command()), isSealed = true),
            activityDetailsContent(turn, rows, firstGroup.key),
        )
    }

    private fun summary(vararg items: ConversationItem) = ConversationTurn(
        id = "turn-1",
        status = ConversationTurnStatus.COMPLETED,
        items = items.toList(),
        detailsState = TurnDetailsState.Unloaded,
    )

    private fun userMessage() = ConversationItem.UserMessage(
        "question",
        "turn-1",
        listOf(UserMessagePart.Text("检查实现")),
    )

    private fun answer() = ConversationItem.AgentMessage(
        id = "answer",
        turnId = "turn-1",
        text = "完成",
        phase = AgentMessagePhase.FINAL_ANSWER,
    )

    private fun commentary() = ConversationItem.AgentMessage(
        id = "commentary",
        turnId = "turn-1",
        text = "正在检查",
        phase = AgentMessagePhase.COMMENTARY,
    )

    private fun command() = ConversationItem.CommandExecution(
        id = "command",
        turnId = "turn-1",
        command = "pwd",
        cwd = "/project",
        output = "",
        exitCode = 0,
        status = ActivityStatus.COMPLETED,
    )
}
