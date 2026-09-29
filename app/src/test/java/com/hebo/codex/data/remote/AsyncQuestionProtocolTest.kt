package com.hebo.codex.data.remote

import com.hebo.codex.domain.AgentMessageDelivery
import com.hebo.codex.domain.AsyncUserInputQuestion
import com.hebo.codex.domain.ConversationEvent
import com.hebo.codex.domain.ConversationItem
import com.hebo.codex.domain.isAsyncQuestion
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AsyncQuestionProtocolTest {
    private val itemJson = """
        {
          "type":"agentMessage",
          "id":"question-1",
          "text":"还有一个偏好需要确认",
          "phase":"commentary",
          "delivery":"async",
          "questions":[
            {"title":"选择主题","options":["  跟随系统  ","深色"]},
            {"title":"补充说明","options":null},
            {"title":"还有什么需要注意"}
          ]
        }
    """.trimIndent()

    @Test
    fun `实时事件和历史记录恢复相同的问题与原始选项`() {
        val notification = ProtocolMapper.notification(
            "server-1",
            "item/completed",
            Json.parseToJsonElement(
                """{"threadId":"thread-1","turnId":"turn-1","item":$itemJson}""",
            ).jsonObject,
        )
        val live = (notification.conversationEvent as ConversationEvent.ItemUpserted)
            .item as ConversationItem.AgentMessage
        val restored = ProtocolMapper.threadItemPage(
            Json.parseToJsonElement(
                """{"data":[{"turnId":"turn-1","item":$itemJson}],"nextCursor":null}""",
            ).jsonObject,
            "turn-1",
        ).data.single()

        assertEquals(live, restored)
        assertEquals(AgentMessageDelivery.ASYNC, live.delivery)
        assertTrue(live.isAsyncQuestion)
        assertEquals(
            listOf(
                AsyncUserInputQuestion("选择主题", listOf("  跟随系统  ", "深色")),
                AsyncUserInputQuestion("补充说明"),
                AsyncUserInputQuestion("还有什么需要注意"),
            ),
            live.questions,
        )
    }

    @Test
    fun `摘要视图保留异步问题`() {
        val page = ProtocolMapper.threadTurnPage(
            Json.parseToJsonElement(
                """{"data":[{"id":"turn-1","status":"completed","itemsView":"summary","items":[$itemJson]}],"nextCursor":null}""",
            ).jsonObject,
        )

        assertTrue((page.data.single().items.single() as ConversationItem.AgentMessage).isAsyncQuestion)
    }

    @Test
    fun `可空或缺省的问题列表不会被识别为异步提问`() {
        listOf("null", "[]").forEach { questions ->
            val item = ProtocolMapper.conversationItem(
                Json.parseToJsonElement(
                    """{"type":"agentMessage","id":"item-1","text":"说明","delivery":"async","questions":$questions}""",
                ).jsonObject,
                "turn-1",
            ) as ConversationItem.AgentMessage

            assertFalse(item.isAsyncQuestion)
            assertTrue(item.questions.isEmpty())
        }
        val plain = ProtocolMapper.conversationItem(
            Json.parseToJsonElement("""{"type":"agentMessage","id":"item-1","text":"普通消息"}""").jsonObject,
            "turn-1",
        ) as ConversationItem.AgentMessage
        assertFalse(plain.isAsyncQuestion)
    }

    @Test
    fun `拒绝未知投递方式和错误选项类型`() {
        listOf(
            """"delivery":"sync"""",
            """"delivery":"async","questions":[{"title":"主题","options":[1]}]""",
        ).forEach { fields ->
            assertThrows(ProtocolException::class.java) {
                ProtocolMapper.conversationItem(
                    Json.parseToJsonElement("""{"type":"agentMessage","id":"item-1","text":"",$fields}""").jsonObject,
                    "turn-1",
                )
            }
        }
    }
}
