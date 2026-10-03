package com.hebo.codex.ui.screens.chat

import com.hebo.codex.domain.AgentMessageDelivery
import com.hebo.codex.domain.AgentMessagePhase
import com.hebo.codex.domain.AsyncUserInputQuestion
import com.hebo.codex.domain.ConversationItem
import com.hebo.codex.domain.ConversationTurn
import com.hebo.codex.domain.ConversationTurnStatus
import com.hebo.codex.domain.TurnDetailsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FinalAnswerNavigationTest {
    @Test
    fun `查看历史回合回复时不会定位到使用相同条目编号的新回合`() {
        val firstAnswer = answer("answer", "turn-1")
        val secondAnswer = answer("answer", "turn-2")
        val rows = buildConversationRows(
            listOf(turn(firstAnswer), turn(secondAnswer).copy(status = ConversationTurnStatus.IN_PROGRESS)),
        )

        assertEquals(ConversationRow.Item(firstAnswer), rows[finalAnswerRowIndex(rows, "turn-1")!!])
        assertEquals(ConversationRow.Item(secondAnswer), rows[finalAnswerRowIndex(rows, "turn-2")!!])
        assertNull(finalAnswerRowIndex(rows, "missing-turn"))
    }

    @Test
    fun `多段回答定位到最后一段有正文的最终回复`() {
        val finalAnswer = answer("final", "turn-1")
        val rows = buildConversationRows(
            listOf(
                turn(
                    answer("earlier", "turn-1"),
                    finalAnswer,
                    answer("empty", "turn-1").copy(text = "  \n"),
                ),
            ),
        )

        assertEquals(ConversationRow.Item(finalAnswer), rows[finalAnswerRowIndex(rows, "turn-1")!!])
    }

    @Test
    fun `只有进度消息异步提问或空回复时不提供回复入口`() {
        val progress = answer("progress", "turn-1").copy(phase = AgentMessagePhase.COMMENTARY)
        val question = answer("question", "turn-1").copy(
            delivery = AgentMessageDelivery.ASYNC,
            questions = listOf(AsyncUserInputQuestion("选择哪种方式")),
        )
        val rows = buildConversationRows(
            listOf(turn(progress, question, answer("empty", "turn-1").copy(text = ""))),
        )

        assertNull(finalAnswerRowIndex(rows, "turn-1"))
    }

    @Test
    fun `历史摘要无需加载过程即可定位未标记阶段的回复`() {
        val finalAnswer = answer("answer", "turn-1").copy(phase = null)
        val rows = buildConversationRows(
            listOf(turn(finalAnswer).copy(detailsState = TurnDetailsState.Unloaded)),
        )

        assertEquals(ConversationRow.Item(finalAnswer), rows[finalAnswerRowIndex(rows, "turn-1")!!])
    }

    private fun answer(id: String, turnId: String) = ConversationItem.AgentMessage(
        id = id,
        turnId = turnId,
        text = "处理结果",
        phase = AgentMessagePhase.FINAL_ANSWER,
    )

    private fun turn(vararg items: ConversationItem) = ConversationTurn(
        id = items.first().turnId,
        status = ConversationTurnStatus.COMPLETED,
        items = items.toList(),
    )
}
