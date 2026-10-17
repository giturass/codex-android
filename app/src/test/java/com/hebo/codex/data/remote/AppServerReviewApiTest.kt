package com.hebo.codex.data.remote

import com.hebo.codex.domain.ReviewTarget
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

class AppServerReviewApiTest {
    @Test
    fun everyReviewTargetUsesInlineDeliveryOnTheSelectedThread() = runTest {
        val requests = mutableListOf<JsonObject>()
        val connection = AppServerConnection(
            webSocketFactory = AppServerWebSocketFactory { listener ->
                object : AppServerWebSocket {
                    override fun start() = listener.onOpen(this)

                    override fun setResponseTimeout(timeoutMillis: Long) = Unit

                    override fun send(text: String): Boolean {
                        val request = Json.parseToJsonElement(text).jsonObject
                        val result = when (request.requiredString("method")) {
                            "initialized" -> return true
                            "initialize" -> json(
                                """{"userAgent":"test","codexHome":"/tmp/codex","platformFamily":"unix","platformOs":"linux"}""",
                            )
                            "review/start" -> {
                                requests += request
                                json(
                                    """{"turn":{"id":"review-turn","status":"inProgress","items":[],"error":null},"reviewThreadId":"review-thread"}""",
                                )
                            }
                            else -> error("不应请求 ${request.requiredString("method")}")
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
            parentContext = coroutineContext,
        )

        try {
            connection.connect()
            val api = AppServerApi(connection)
            val targets = listOf(
                ReviewTarget.UncommittedChanges to json("""{"type":"uncommittedChanges"}"""),
                ReviewTarget.BaseBranch("main") to json("""{"type":"baseBranch","branch":"main"}"""),
                ReviewTarget.Commit("commit-sha", "检查提交") to
                    json("""{"type":"commit","sha":"commit-sha","title":"检查提交"}"""),
                ReviewTarget.Custom("检查安全问题") to
                    json("""{"type":"custom","instructions":"检查安全问题"}"""),
            )

            targets.forEach { (target, expected) ->
                assertEquals("review-thread", api.review("review-thread", target).requiredString("reviewThreadId"))
                assertEquals(
                    buildJsonObject {
                        put("threadId", "review-thread")
                        put("delivery", "inline")
                        put("target", expected)
                    },
                    requests.last().requiredObject("params"),
                )
            }
            assertEquals(targets.size, requests.size)
        } finally {
            connection.close()
        }
    }

    private fun json(value: String) = Json.parseToJsonElement(value).jsonObject
}
