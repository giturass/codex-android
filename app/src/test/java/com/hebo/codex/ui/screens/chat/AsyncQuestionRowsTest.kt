package com.hebo.codex.ui.screens.chat

import com.hebo.codex.domain.AgentMessageDelivery
import com.hebo.codex.domain.AgentMessagePhase
import com.hebo.codex.domain.AsyncUserInputQuestion
import com.hebo.codex.domain.ConversationItem
import com.hebo.codex.domain.ConversationTurn
import com.hebo.codex.domain.ConversationTurnStatus
import com.hebo.codex.domain.TurnDetailsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AsyncQuestionRowsTest {
    @Test
    fun `异步问题单独展示且不会结束仍在运行的活动组`() {
        val commentary = ConversationItem.AgentMessage(
            id = "progress", turnId = "turn-1", text = "正在检查", phase = AgentMessagePhase.COMMENTARY,
        )
        val question = question()

        val rows = buildLoadedConversationRows(listOf(commentary, question), activeTurnId = "turn-1")

        assertEquals(2, rows.size)
        assertFalse((rows.first() as ConversationRow.ActivityGroup).isSealed)
        assertEquals(question, (rows.last() as ConversationRow.Item).item)
    }

    @Test
    fun `摘要中的异步问题不需要展开活动详情即可回复`() {
        val question = question()
        val rows = buildConversationRows(
            listOf(
                ConversationTurn(
                    id = "turn-1",
                    status = ConversationTurnStatus.COMPLETED,
                    items = listOf(question),
                    detailsState = TurnDetailsState.Unloaded,
                ),
            ),
            activeTurnId = null,
        )

        assertEquals(question, (rows.last() as ConversationRow.Item).item)
    }

    private fun question() = ConversationItem.AgentMessage(
        id = "question", turnId = "turn-1", text = "", phase = AgentMessagePhase.COMMENTARY,
        delivery = AgentMessageDelivery.ASYNC,
        questions = listOf(AsyncUserInputQuestion("偏好是什么")),
    )
}
