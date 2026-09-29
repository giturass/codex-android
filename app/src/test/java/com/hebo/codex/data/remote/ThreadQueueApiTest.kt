package com.hebo.codex.data.remote

import com.hebo.codex.domain.InputByteRange
import com.hebo.codex.domain.InputTextElement
import com.hebo.codex.domain.UserInputPart
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadQueueApiTest {
    @Test
    fun queueAddRequiresAnAcknowledgementForTheSubmittedMessage() = runTest {
        for (response in listOf(
            "{}",
            """{"queuedSubmission":{"id":"entry","clientUserMessageId":"another-message","input":[]}}""",
            """{"queuedSubmission":{"id":"entry","clientUserMessageId":"message-1"}}""",
        )) {
            val connection = connection(coroutineContext, mutableListOf()) { json(response) }
            try {
                connection.connect()
                val result = runCatching {
                    AppServerApi(connection).queueInput("thread-1", "message-1", listOf(UserInputPart.Text("保留草稿")))
                }
                assertTrue(result.exceptionOrNull() is ProtocolException)
            } finally {
                connection.close()
            }
        }
    }

    @Test
    fun queueAddPreservesAllInputKindsAndStructuredTextMetadata() = runTest {
        val requests = mutableListOf<JsonObject>()
        val expectedInput = """
            [
              {"type":"text","text":"检查 [图片]","text_elements":[{"byteRange":{"start":7,"end":15},"placeholder":"附件"}]},
              {"type":"image","url":"data:image/png;base64,aW1hZ2U=","detail":"original"},
              {"type":"image","fileId":"file-1","detail":"high"},
              {"type":"localImage","path":"/workspace/图片.png","detail":"low"},
              {"type":"audio","url":"https://example.test/audio.wav"},
              {"type":"localAudio","path":"/workspace/audio.wav"},
              {"type":"skill","name":"review","path":"/skills/review/SKILL.md"},
              {"type":"mention","name":"README.md","path":"/workspace/README.md"},
              {"type":"text","text":"保留结尾说明","text_elements":[]}
            ]
        """.trimIndent()
        val expectedResponse = json(
            """{"queuedSubmission":{"id":"queue-1","clientUserMessageId":"message-1","input":$expectedInput}}""",
        )
        val connection = connection(coroutineContext, requests) { expectedResponse }

        try {
            connection.connect()
            val input = listOf(
                UserInputPart.Text("检查 [图片]", listOf(InputTextElement(InputByteRange(7, 15), "附件"))),
                UserInputPart.Image("data:image/png;base64,aW1hZ2U=", "original"),
                UserInputPart.UploadedImage("file-1", "high"),
                UserInputPart.LocalImage("/workspace/图片.png", "low"),
                UserInputPart.Audio("https://example.test/audio.wav"),
                UserInputPart.LocalAudio("/workspace/audio.wav"),
                UserInputPart.Skill("review", "/skills/review/SKILL.md"),
                UserInputPart.Mention("README.md", "/workspace/README.md"),
                UserInputPart.Text("保留结尾说明"),
            )

            val response = AppServerApi(connection).queueInput("thread-1", "message-1", input)

            assertEquals(expectedResponse, response)
            assertEquals("thread/queue/add", requests.single().requiredString("method"))
            assertEquals(
                json("""{"threadId":"thread-1","clientUserMessageId":"message-1","input":$expectedInput}"""),
                requests.single().requiredObject("params"),
            )
        } finally {
            connection.close()
        }
    }

    @Test
    fun queueListUsesPaginationAndReturnsTheOriginalSubmission() = runTest {
        val requests = mutableListOf<JsonObject>()
        val expectedResponse = json(
            """{"data":[{"id":"queue-1","clientUserMessageId":"message-1","input":[{"type":"text","text":"完整输入"}]}],"nextCursor":"next-page"}""",
        )
        val connection = connection(coroutineContext, requests) { expectedResponse }

        try {
            connection.connect()
            val api = AppServerApi(connection)

            assertEquals(expectedResponse, api.listQueuedInputs("thread-1"))
            assertEquals(expectedResponse, api.listQueuedInputs("thread-1", "next-page"))

            assertEquals(listOf("thread/queue/list", "thread/queue/list"), requests.map { it.requiredString("method") })
            assertEquals(
                json("""{"threadId":"thread-1","limit":50}"""),
                requests[0].requiredObject("params"),
            )
            assertEquals(
                json("""{"threadId":"thread-1","limit":50,"cursor":"next-page"}"""),
                requests[1].requiredObject("params"),
            )
        } finally {
            connection.close()
        }
    }

    @Test
    fun queueDeleteUsesSubmissionIdAndDistinguishesAlreadyConsumedEntries() = runTest {
        val requests = mutableListOf<JsonObject>()
        val connection = connection(coroutineContext, requests) { request ->
            val id = request.requiredObject("params").requiredString("queuedSubmissionId")
            json("""{"deleted":${id == "queue-1"}}""")
        }

        try {
            connection.connect()
            val api = AppServerApi(connection)

            assertTrue(api.deleteQueuedInput("thread-1", "queue-1"))
            assertFalse(api.deleteQueuedInput("thread-1", "consumed-entry"))

            assertEquals(listOf("thread/queue/delete", "thread/queue/delete"), requests.map { it.requiredString("method") })
            assertEquals(
                json("""{"threadId":"thread-1","queuedSubmissionId":"queue-1"}"""),
                requests[0].requiredObject("params"),
            )
            assertEquals(
                json("""{"threadId":"thread-1","queuedSubmissionId":"consumed-entry"}"""),
                requests[1].requiredObject("params"),
            )
        } finally {
            connection.close()
        }
    }

    @Test
    fun queueDeleteRequiresTheServerToConfirmWhetherDeletionSucceeded() = runTest {
        val requests = mutableListOf<JsonObject>()
        val connection = connection(coroutineContext, requests) { json("{}") }

        try {
            connection.connect()

            val failure = runCatching { AppServerApi(connection).deleteQueuedInput("thread-1", "queue-1") }.exceptionOrNull()

            assertTrue(failure is ProtocolException)
        } finally {
            connection.close()
        }
    }

    private fun connection(
        parentContext: CoroutineContext,
        requests: MutableList<JsonObject>,
        response: (JsonObject) -> JsonObject,
    ) = AppServerConnection(
        webSocketFactory = AppServerWebSocketFactory { listener ->
            object : AppServerWebSocket {
                override fun start() = listener.onOpen(this)

                override fun send(text: String): Boolean {
                    val request = Json.parseToJsonElement(text).jsonObject
                    val result = when (request.requiredString("method")) {
                        "initialized" -> return true
                        "initialize" -> json(
                            """{"userAgent":"test","codexHome":"/tmp/codex","platformFamily":"unix","platformOs":"linux"}""",
                        )
                        else -> {
                            requests += request
                            response(request)
                        }
                    }
                    listener.onMessage(this, buildJsonObject {
                        put("id", requireNotNull(request["id"]))
                        put("result", result)
                    }.toString())
                    return true
                }

                override fun close(code: Int, reason: String): Boolean = true

                override fun cancel() = Unit
            }
        },
        json = Json,
        connectTimeoutMillis = 1_000,
        parentContext = parentContext,
    )

    private fun json(value: String) = Json.parseToJsonElement(value).jsonObject
}
