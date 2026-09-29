package com.hebo.codex.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AsyncQuestionsTest {
    @Test
    fun `最近入口按消息顺序找到问题并忽略后续助手内容`() {
        val first = question("first", "turn-1")
        val latest = question("latest", "turn-2")
        val history = historyOf(
            first,
            latest,
            ConversationItem.AgentMessage("final", "turn-2", "任务继续进行"),
        )

        assertEquals(latest, history.recentAsyncQuestion())
    }

    @Test
    fun `用户发言后入口消失但历史问题保留`() {
        val question = question()
        val history = historyOf(
            question,
            ConversationItem.UserMessage("reply", "turn-2", listOf(UserMessagePart.Text("继续"))),
        )

        assertNull(history.recentAsyncQuestion())
        assertEquals(question, history.items.first())
    }

    @Test
    fun `新的问题在用户发言后重新出现`() {
        val latest = question("latest", "turn-2")
        val history = historyOf(
            question(),
            ConversationItem.UserMessage("reply", "turn-2", listOf(UserMessagePart.Text("继续"))),
            latest,
        )

        assertEquals(latest, history.recentAsyncQuestion())
    }

    @Test
    fun `没有异步投递标记的问题不显示入口`() {
        assertNull(historyOf(question().copy(delivery = null)).recentAsyncQuestion())
    }

    @Test
    fun `最新回合详情未就绪时不根据不完整摘要显示入口`() {
        listOf(
            TurnDetailsState.Unloaded,
            TurnDetailsState.Loading(),
            TurnDetailsState.Failed(UserFacingError("加载失败")),
        ).forEach { detailsState ->
            val turn = ConversationTurn(
                id = "turn-2",
                status = ConversationTurnStatus.COMPLETED,
                items = listOf(question("latest", "turn-2")),
                detailsState = detailsState,
            )
            val history = historyOf(question()).let { it.copy(turns = it.turns + turn) }

            assertNull(history.recentAsyncQuestion())
        }
    }

    @Test
    fun `遇到缺少详情的回合时不越过它展示更早的问题`() {
        val history = ConversationHistory(
            turns = historyOf(question()).turns + listOf(
                ConversationTurn("turn-2", ConversationTurnStatus.COMPLETED, detailsState = TurnDetailsState.Unloaded),
                ConversationTurn("turn-3", ConversationTurnStatus.IN_PROGRESS),
            ),
        )

        assertNull(history.recentAsyncQuestion())
    }

    @Test
    fun `只通知实时完成事件并对同一消息去重`() {
        val notifications = AsyncQuestionNotifications()
        val event = ConversationEvent.ItemUpserted(question(), isActive = false)

        assertFalse(notifications.accept("server", "thread", "item/started", event.copy(isActive = true)))
        assertFalse(notifications.accept("server", "thread", "thread/resumed", event))
        assertTrue(notifications.accept("server", "thread", "item/completed", event))
        assertFalse(notifications.accept("server", "thread", "item/completed", event))
    }

    @Test
    fun `去重区分服务器会话和回合并在新连接清理`() {
        val notifications = AsyncQuestionNotifications()
        val event = ConversationEvent.ItemUpserted(question(), isActive = false)

        assertTrue(notifications.accept("server", "thread", "item/completed", event))
        assertTrue(notifications.accept("other", "thread", "item/completed", event))
        assertTrue(notifications.accept("server", "other", "item/completed", event))
        assertTrue(
            notifications.accept(
                "server", "thread", "item/completed", event.copy(item = question(turnId = "turn-2")),
            ),
        )
        notifications.clearServer("server")
        assertTrue(notifications.accept("server", "thread", "item/completed", event))
        assertFalse(notifications.accept("other", "thread", "item/completed", event))
    }

    @Test
    fun `普通消息完成不产生提问通知`() {
        val notifications = AsyncQuestionNotifications()
        val event = ConversationEvent.ItemUpserted(question().copy(questions = emptyList()), isActive = false)

        assertFalse(notifications.accept("server", "thread", "item/completed", event))
    }

    private fun question(id: String = "question", turnId: String = "turn-1") = ConversationItem.AgentMessage(
        id = id,
        turnId = turnId,
        text = "",
        delivery = AgentMessageDelivery.ASYNC,
        questions = listOf(AsyncUserInputQuestion("选择主题", listOf("深色", "浅色"))),
    )

    private fun historyOf(vararg items: ConversationItem): ConversationHistory = ConversationHistory(
        turns = items.groupBy(ConversationItem::turnId).map { (turnId, turnItems) ->
            ConversationTurn(turnId, ConversationTurnStatus.COMPLETED, turnItems)
        },
    )
}
