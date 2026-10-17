package com.hebo.codex.data.remote

import com.hebo.codex.domain.ConversationEvent
import com.hebo.codex.domain.ReasoningSummary
import com.hebo.codex.domain.ThreadOptions
import com.hebo.codex.domain.TurnOptions
import com.hebo.codex.domain.UserInputPart
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReasoningSettingsProtocolTest {
    @Test
    fun modelCatalogRetainsAdvertisedEffortsDescriptionsAndDefaultsAcrossPages() = runTest {
        val fixture = ReasoningApiFixture(coroutineContext) { request ->
            assertEquals("model/list", request.requiredString("method"))
            assertTrue(request.requiredObject("params").requiredBoolean("includeHidden"))
            val cursor = request.requiredObject("params").optionalString("cursor")
            json(if (cursor == null) {
                """
                {"data":[{
                  "id":"model-a","model":"model-a","displayName":"Model A","description":"第一个模型",
                  "isDefault":true,"hidden":false,"inputModalities":["text"],
                  "supportedReasoningEfforts":[
                    {"reasoningEffort":"low","description":"较快完成"},
                    {"reasoningEffort":"ultra","description":"主动分配任务"},
                    {"reasoningEffort":"custom","description":"服务端新增深度"}
                  ],
                  "defaultReasoningEffort":"custom","serviceTiers":[],"defaultServiceTier":null
                }],"nextCursor":"page-2"}
                """
            } else {
                assertEquals("page-2", cursor)
                """
                {"data":[{
                  "id":"model-b","model":"model-b","displayName":"Model B","description":"第二个模型",
                  "isDefault":false,"hidden":true,"inputModalities":["text"],
                  "supportedReasoningEfforts":[],"defaultReasoningEffort":"none",
                  "serviceTiers":[],"defaultServiceTier":null
                }],"nextCursor":null}
                """
            })
        }

        try {
            fixture.connection.connect()
            val models = StableFeatureApi(fixture.api).models()

            assertEquals(listOf("model-a", "model-b"), models.map { it.model })
            assertEquals(listOf("low", "ultra", "custom"), models.first().reasoningEfforts.map { it.value })
            assertEquals(listOf("low", "ultra", "custom"), models.first().reasoningEfforts.map { it.label })
            assertEquals("服务端新增深度", models.first().reasoningEfforts.last().description)
            assertEquals("custom", models.first().defaultReasoningEffort)
            assertTrue(models.last().reasoningEfforts.isEmpty())
            assertEquals("none", models.last().defaultReasoningEffort)
            assertTrue(models.last().hidden)
        } finally {
            fixture.connection.close()
        }
    }

    @Test
    fun missingRequiredEffortsIsReportedInsteadOfInventingAnEmptyCatalog() = runTest {
        val fixture = ReasoningApiFixture(coroutineContext) {
            json(
                """
                {"data":[{
                  "id":"model-a","model":"model-a","displayName":"Model A","description":"模型",
                  "isDefault":true,"hidden":false,"defaultReasoningEffort":"high"
                }],"nextCursor":null}
                """,
            )
        }
        try {
            fixture.connection.connect()
            val error = runCatching { StableFeatureApi(fixture.api).models() }.exceptionOrNull()

            assertTrue(error is ProtocolException)
        } finally {
            fixture.connection.close()
        }
    }

    @Test
    fun threadStartMergesSelectedEffortIntoConfigWithoutReplacingOtherValues() = runTest {
        val fixture = ReasoningApiFixture(coroutineContext)
        try {
            fixture.connection.connect()
            val customConfig = """{"model_reasoning_effort":"low","features":{"web_search":true}}"""
            fixture.api.startThread(
                "/workspace",
                ThreadOptions(model = "model-a", reasoningEffort = "ultra", configJson = customConfig),
            )
            fixture.api.startThread("/workspace", ThreadOptions(configJson = customConfig))
            fixture.api.startThread("/workspace", ThreadOptions(reasoningEffort = "custom"))
            fixture.api.startThread("/workspace")

            val params = fixture.requests.map { it.requiredObject("params") }
            assertEquals(
                json("""{"model_reasoning_effort":"ultra","features":{"web_search":true}}"""),
                params[0].requiredObject("config"),
            )
            assertEquals("model-a", params[0].requiredString("model"))
            assertEquals(json(customConfig), params[1].requiredObject("config"))
            assertEquals(json("""{"model_reasoning_effort":"custom"}"""), params[2].requiredObject("config"))
            assertFalse(params[3].containsKey("config"))
            params.forEach {
                assertFalse(it.containsKey("effort"))
                assertFalse(it.containsKey("reasoningEffort"))
            }
        } finally {
            fixture.connection.close()
        }
    }

    @Test
    fun choosingEffortDoesNotHideInvalidAdvancedConfiguration() = runTest {
        val fixture = ReasoningApiFixture(coroutineContext)
        try {
            fixture.connection.connect()
            listOf("[]", "null", "false").forEach { invalid ->
                val error = runCatching {
                    fixture.api.startThread("/workspace", ThreadOptions(reasoningEffort = "ultra", configJson = invalid))
                }.exceptionOrNull()

                assertTrue(error is IllegalArgumentException)
            }
            assertTrue(fixture.requests.isEmpty())
        } finally {
            fixture.connection.close()
        }
    }

    @Test
    fun onlyTurnStartSendsTheSelectedEffortAndOmissionInheritsTheThreadSetting() = runTest {
        val fixture = ReasoningApiFixture(coroutineContext) { request ->
            buildJsonObject {
                if (request.requiredString("method") == "thread/queue/add") {
                    val params = request.requiredObject("params")
                    put("queuedSubmission", buildJsonObject {
                        put("id", "queued-1")
                        put("clientUserMessageId", params.requiredString("clientUserMessageId"))
                        put("input", params.getValue("input"))
                    })
                }
            }
        }
        val input = listOf(UserInputPart.Text("检查实现"))
        try {
            fixture.connection.connect()
            fixture.api.startTurn(
                "thread-1",
                "message-1",
                input,
                TurnOptions(model = "model-a", reasoningEffort = "custom", reasoningSummary = ReasoningSummary.DETAILED),
            )
            fixture.api.startTurn("thread-1", "message-2", input)
            fixture.api.steerTurn("thread-1", "turn-1", "message-3", input)
            fixture.api.queueInput("thread-1", "message-4", input)

            assertEquals(
                listOf("turn/start", "turn/start", "turn/steer", "thread/queue/add"),
                fixture.requests.map { it.requiredString("method") },
            )
            val params = fixture.requests.map { it.requiredObject("params") }
            assertEquals("custom", params[0].requiredString("effort"))
            assertEquals("detailed", params[0].requiredString("summary"))
            assertEquals("model-a", params[0].requiredString("model"))
            assertFalse(params[0].containsKey("reasoningEffort"))
            params.drop(1).forEach {
                assertFalse(it.containsKey("effort"))
                assertFalse(it.containsKey("model"))
            }
        } finally {
            fixture.connection.close()
        }
    }

    @Test
    fun settingsNotificationMapsAuthoritativeEffortAndNullableClears() {
        val settings = json(
            """
            {
              "model":"model-a","modelProvider":"openai","effort":"custom","serviceTier":"priority",
              "cwd":"/workspace/updated","approvalPolicy":"on-request","approvalsReviewer":"auto_review",
              "sandboxPolicy":{"type":"workspaceWrite","writableRoots":["/workspace"],"networkAccess":false},
              "collaborationMode":{"mode":"default","settings":{"model":"model-a","reasoning_effort":"custom"}}
            }
            """,
        )

        listOf(JsonPrimitive("custom"), JsonNull).forEach { effort ->
            val notification = ProtocolMapper.notification(
                serverId = "server-1",
                method = "thread/settings/updated",
                params = buildJsonObject {
                    put("threadId", "thread-1")
                    put("threadSettings", JsonObject(settings + mapOf("effort" to effort, "serviceTier" to JsonNull)))
                },
            )

            assertEquals("thread-1", notification.threadId)
            assertEquals(
                ConversationEvent.SettingsUpdated(
                    cwd = "/workspace/updated",
                    model = "model-a",
                    reasoningEffort = if (effort == JsonNull) null else "custom",
                    serviceTier = null,
                    approvalPolicy = "on-request",
                    approvalsReviewer = "auto_review",
                    sandbox = settings.requiredObject("sandboxPolicy").toString(),
                ),
                notification.conversationEvent,
            )
        }
    }

    private fun json(value: String): JsonObject = Json.parseToJsonElement(value).jsonObject
}

private class ReasoningApiFixture(
    parentContext: CoroutineContext,
    response: (JsonObject) -> JsonElement = { buildJsonObject {} },
) {
    val requests = mutableListOf<JsonObject>()
    val connection = AppServerConnection(
        webSocketFactory = AppServerWebSocketFactory { listener ->
            object : AppServerWebSocket {
                override fun start() = listener.onOpen(this)

                override fun send(text: String): Boolean {
                    val request = Json.parseToJsonElement(text).jsonObject
                    val result = when (request.requiredString("method")) {
                        "initialized" -> return true
                        "initialize" -> buildJsonObject {
                            put("userAgent", "test")
                            put("codexHome", "/tmp/codex")
                            put("platformFamily", "unix")
                            put("platformOs", "linux")
                        }
                        else -> {
                            requests += request
                            response(request)
                        }
                    }
                    listener.onMessage(this, buildJsonObject {
                        put("id", request.getValue("id"))
                        put("result", result)
                    }.toString())
                    return true
                }

                override fun setResponseTimeout(timeoutMillis: Long) = Unit
                override fun close(code: Int, reason: String): Boolean = true
                override fun cancel() = Unit
            }
        },
        json = Json,
        connectTimeoutMillis = 1_000,
        parentContext = parentContext,
    )
    val api = AppServerApi(connection)
}
