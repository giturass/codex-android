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
import org.junit.Assert.assertTrue
import org.junit.Test

class LazyConversationRowsTest {
    @Test
    fun `历史摘要在问题与回答之间提供处理过程入口`() {
        val turn = summary(userMessage(), answer())

        val rows = buildConversationRows(listOf(turn))

        assertEquals(
            listOf(
                ConversationRow.Item(userMessage()),
                ConversationRow.TurnActivity(turn.id),
                ConversationRow.Item(answer()),
            ),
            rows,
        )
    }

    @Test
    fun `加载失败和已加载状态保持相同的入口`() {
        val turn = summary(userMessage(), answer())
        val expected = buildConversationRows(listOf(turn))

        listOf(
            TurnDetailsState.Loading(),
            TurnDetailsState.Failed(UserFacingError("加载失败")),
            TurnDetailsState.Loaded,
        ).forEach { state ->
            assertEquals(
                expected,
                buildConversationRows(listOf(turn.copy(detailsState = state))),
            )
        }
    }

    @Test
    fun `空摘要和缺少用户消息的摘要仍可打开处理过程`() {
        listOf(summary(), summary(answer())).forEach { turn ->
            val rows = buildConversationRows(listOf(turn))

            assertEquals(ConversationRow.TurnActivity(turn.id), rows.first())
        }
    }

    @Test
    fun `最后一条摘要为进度消息时不伪造活动统计`() {
        val turn = summary(userMessage(), commentary())

        val rows = buildConversationRows(listOf(turn))

        assertEquals(
            listOf(ConversationRow.Item(userMessage()), ConversationRow.TurnActivity(turn.id)),
            rows,
        )
        assertTrue(activityDetailsContent(turn, activeTurnId = null).items.isEmpty())
    }

    @Test
    fun `完整内容替换摘要时入口位置与列表键保持不变`() {
        val turn = summary(userMessage(), answer())
        val initialRows = buildConversationRows(listOf(turn))
        val loaded = turn.copy(
            items = listOf(userMessage(), command(), answer()),
            detailsState = TurnDetailsState.Loaded,
        )

        val loadedRows = buildConversationRows(listOf(loaded))

        assertEquals(initialRows, loadedRows)
        assertEquals(initialRows.map(ConversationRow::key), loadedRows.map(ConversationRow::key))
    }

    @Test
    fun `按回合打开的面板在摘要替换后展示完整处理过程`() {
        val turn = summary(userMessage(), answer())
        val loaded = turn.copy(
            items = listOf(userMessage(), command(), answer(), commentary()),
            detailsState = TurnDetailsState.Loaded,
        )

        assertTrue(activityDetailsContent(turn, activeTurnId = null).items.isEmpty())
        assertEquals(
            listOf(command(), commentary()),
            activityDetailsContent(loaded, activeTurnId = null).items,
        )
    }

    @Test
    fun `纯问答加载成功后保留空处理过程面板`() {
        val loaded = summary(userMessage(), answer()).copy(detailsState = TurnDetailsState.Loaded)
        val rows = buildConversationRows(listOf(loaded))

        val details = activityDetailsContent(loaded, activeTurnId = null)

        assertTrue(details.items.isEmpty())
        assertEquals(
            listOf(
                ConversationRow.Item(userMessage()),
                ConversationRow.TurnActivity(loaded.id),
                ConversationRow.Item(answer()),
            ),
            rows,
        )
    }

    @Test
    fun `空摘要加载出的用户Shell命令可直接在详情面板查看`() {
        val shell = ConversationItem.UserShellCommand(
            id = "shell", turnId = "turn-1", command = "pwd", cwd = "/project",
            output = "/project", exitCode = 0, durationMillis = 100,
            status = ActivityStatus.COMPLETED,
        )
        val loaded = summary(shell).copy(detailsState = TurnDetailsState.Loaded)
        val rows = buildConversationRows(listOf(loaded))

        assertEquals(listOf(shell), activityDetailsContent(loaded, activeTurnId = null).items)
        assertTrue(activityItemHasDetails(shell))
        assertEquals(listOf(ConversationRow.TurnActivity(loaded.id), ConversationRow.Item(shell)), rows)
    }

    @Test
    fun `已加载回合的多个活动段共用入口并完整展示`() {
        val turn = summary(command(), answer(), commentary()).copy(detailsState = TurnDetailsState.Loaded)
        val rows = buildConversationRows(listOf(turn))

        assertEquals(
            listOf(ConversationRow.TurnActivity(turn.id), ConversationRow.Item(answer())),
            rows,
        )
        assertEquals(
            ActivityDetailsContent(listOf(command(), commentary()), isSealed = true),
            activityDetailsContent(turn, activeTurnId = null),
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
