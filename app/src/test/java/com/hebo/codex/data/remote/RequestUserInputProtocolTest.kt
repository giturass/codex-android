package com.hebo.codex.data.remote

import com.hebo.codex.domain.ConversationState
import com.hebo.codex.domain.PendingServerRequest
import com.hebo.codex.domain.reduceConversationEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestUserInputProtocolTest {
    @Test
    fun blockingFlagIsAuthoritativeEvenWhenDeprecatedTimeoutIsZero() {
        val request = questions(params(""""isBlocking":true,"autoResolutionMs":0"""))

        assertTrue(request.isBlocking)
        assertEquals("theme", request.questions.single().id)
        assertTrue(request.questions.single().allowsOther)
    }

    @Test
    fun nonBlockingQuestionDoesNotNeedAnAutomaticResolutionTimeout() {
        val request = questions(params(""""isBlocking":false"""))

        assertFalse(request.isBlocking)
        assertEquals("选择主题", request.questions.single().question)
    }

    @Test
    fun missingOrNullBlockingFlagIsRejected() {
        listOf("", """"isBlocking":null""").forEach { fields ->
            assertThrows(ProtocolException::class.java) {
                questions(params(fields))
            }
        }
    }

    @Test
    fun serverResolutionClearsOnlyTheAnsweredQuestionWhileTheTurnContinues() {
        val first = questions(params(""""isBlocking":false"""), requestKey = "server-1:42")
        val second = questions(params(""""isBlocking":true"""), requestKey = "server-1:43")
        val state = ConversationState(
            serverId = "server-1",
            serverName = "服务器",
            threadId = "thread-1",
            title = "会话",
            cwd = "/workspace",
            activeTurnId = "turn-1",
            isRunning = true,
            pendingRequests = listOf(first, second),
        )
        val notification = ProtocolMapper.notification(
            serverId = "server-1",
            method = "serverRequest/resolved",
            params = Json.parseToJsonElement("""{"threadId":"thread-1","requestId":42}""").jsonObject,
        )

        val resolved = state.reduceConversationEvent(requireNotNull(notification.conversationEvent))

        assertEquals(listOf(second), resolved.pendingRequests)
        assertEquals("turn-1", resolved.activeTurnId)
        assertTrue(resolved.isRunning)
    }

    private fun questions(params: JsonObject, requestKey: String = "server-1:42") =
        ProtocolMapper.pendingServerRequest(
            method = "item/tool/requestUserInput",
            params = params,
            requestKey = requestKey,
        ) as PendingServerRequest.Questions

    private fun params(fields: String): JsonObject = Json.parseToJsonElement(
        """{
          "threadId":"thread-1","turnId":"turn-1","itemId":"item-1",
          ${fields.takeIf(String::isNotEmpty)?.let { "$it," }.orEmpty()}
          "questions":[{"id":"theme","header":"主题","question":"选择主题","isOther":true}]
        }""".trimIndent(),
    ).jsonObject
}
