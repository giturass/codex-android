package com.hebo.codex.data.remote

import com.hebo.codex.domain.UserInputPart
import com.hebo.codex.util.runSuspendCatching
import java.net.SocketTimeoutException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InputSubmissionTimeoutTest {
    @Test
    fun embeddedMediaKeepsOriginalPayloadAndCanWaitBeyondThirtySecondsInEverySubmissionRoute() = runTest {
        val fixture = SlowSubmissionFixture(backgroundScope)
        try {
            fixture.connection.connect()
            val inputs = listOf(
                UserInputPart.Image("data:image/jpeg;base64,${"AQID".repeat(1024)}", "original"),
                UserInputPart.Audio("data:audio/wav;base64,${"BAUG".repeat(1024)}"),
            )
            for (input in inputs) {
                val parts = listOf(UserInputPart.Text("说明附件内容"), input)
                fixture.api.startTurn("thread", "message", parts)
                fixture.api.steerTurn("thread", "turn", "message", parts)
                fixture.api.queueInput("thread", "message", parts)

                fixture.requests.takeLast(3).forEach { request ->
                    assertEquals(parts.toWire(), request.requiredObject("params")["input"])
                }
            }
            assertEquals(6 * 45_000L, testScheduler.currentTime)
            assertEquals(
                listOf("turn/start", "turn/steer", "thread/queue/add").let { it + it },
                fixture.requests.map { it.requiredString("method") },
            )
        } finally {
            fixture.connection.close()
        }
    }

    @Test
    fun inputsWithoutEmbeddedBytesKeepTheNormalTimeout() = runTest {
        val fixture = SlowSubmissionFixture(backgroundScope)
        try {
            fixture.connection.connect()
            val inputs = listOf(
                UserInputPart.Text("普通问题"),
                UserInputPart.Image("https://example.com/photo.jpg"),
                UserInputPart.UploadedImage("file-1"),
                UserInputPart.LocalImage("/tmp/photo.jpg"),
            )
            for (input in inputs) {
                val before = testScheduler.currentTime
                val result = runSuspendCatching {
                    fixture.api.startTurn("thread", "message", listOf(input))
                }
                assertTrue(result.exceptionOrNull() is SocketTimeoutException)
                assertEquals(30_000L, testScheduler.currentTime - before)
            }
        } finally {
            fixture.connection.close()
        }
    }
}

private class SlowSubmissionFixture(scope: CoroutineScope) {
    val requests = mutableListOf<JsonObject>()
    val connection = AppServerConnection(
        webSocketFactory = AppServerWebSocketFactory { listener ->
            object : AppServerWebSocket {
                override fun start() = listener.onOpen(this)

                override fun send(text: String): Boolean {
                    val request = Json.parseToJsonElement(text).jsonObject
                    when (request.requiredString("method")) {
                        "initialized" -> Unit
                        "initialize" -> respond(request, buildJsonObject {
                            put("userAgent", "test")
                            put("codexHome", "/tmp/codex")
                            put("platformFamily", "unix")
                            put("platformOs", "linux")
                        })
                        else -> {
                            requests += request
                            scope.launch {
                                delay(45_000)
                                respond(request, buildJsonObject {
                                    put("queuedSubmission", buildJsonObject {
                                        put("id", "submission")
                                        put("clientUserMessageId", "message")
                                        put("input", request.requiredObject("params").getValue("input"))
                                    })
                                })
                            }
                        }
                    }
                    return true
                }

                private fun respond(request: JsonObject, result: JsonObject) {
                    listener.onMessage(this, buildJsonObject {
                        put("id", request.getValue("id"))
                        put("result", result)
                    }.toString())
                }

                override fun setResponseTimeout(timeoutMillis: Long) = Unit
                override fun close(code: Int, reason: String): Boolean = true
                override fun cancel() = Unit
            }
        },
        json = Json,
        connectTimeoutMillis = 1_000,
        parentContext = scope.coroutineContext,
    )
    val api = AppServerApi(connection)
}
