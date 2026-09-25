package com.hebo.codex.data.remote

import com.hebo.codex.domain.FeatureArea
import com.hebo.codex.domain.FeatureKind
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

class StableFeatureApiTest {
    @Test
    fun loadsMcpInventoryWhenAuthenticationStatusIsUnknown() = runTest {
        val connection = AppServerConnection(
            webSocketFactory = AppServerWebSocketFactory { listener ->
                object : AppServerWebSocket {
                    override fun start() = listener.onOpen(this)

                    override fun send(text: String): Boolean {
                        val request = Json.parseToJsonElement(text).jsonObject
                        val result = when (request.requiredString("method")) {
                            "initialize" -> """
                                {
                                  "userAgent":"test",
                                  "codexHome":"/tmp/codex",
                                  "platformFamily":"unix",
                                  "platformOs":"linux"
                                }
                            """.trimIndent()

                            "initialized" -> return true
                            "mcpServerStatus/list" -> """
                                {
                                  "data":[{
                                    "name":"example",
                                    "authStatus":"unknown",
                                    "tools":{"read":{"name":"read","description":"读取资源"}},
                                    "resources":[],
                                    "resourceTemplates":[]
                                  }],
                                  "nextCursor":null
                                }
                            """.trimIndent()

                            else -> error("不应请求 ${request.requiredString("method")}")
                        }
                        listener.onMessage(
                            this,
                            Json.encodeToString(
                                JsonElement.serializer(),
                                buildJsonObject {
                                    put("id", requireNotNull(request["id"]))
                                    put("result", Json.parseToJsonElement(result))
                                },
                            ),
                        )
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
            val catalog = StableFeatureApi(AppServerApi(connection)).catalog(FeatureArea.MCP)

            val server = catalog.entries.single { it.kind == FeatureKind.MCP_SERVER }
            val tool = catalog.entries.single { it.kind == FeatureKind.MCP_TOOL }
            assertEquals("认证：状态未知", server.description)
            assertEquals("unknown", server.metadata["authStatus"])
            assertEquals("example/read", tool.id)
        } finally {
            connection.close()
        }
    }
}
