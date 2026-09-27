package com.hebo.codex.data.remote

import com.hebo.codex.domain.ConversationTurnStatus
import com.hebo.codex.domain.SortDirection
import com.hebo.codex.domain.SubAgentEvent
import com.hebo.codex.domain.SubAgentRuntimeStatus
import com.hebo.codex.domain.ThreadActiveFlag
import com.hebo.codex.domain.ThreadSortKey
import com.hebo.codex.domain.ThreadSourceKind
import com.hebo.codex.domain.subAgentSummary
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

class ComposerStatusProtocolTest {
    @Test
    fun descendantsAndLatestTurnUseServerFilteringWithoutResumingChildThreads() = runTest {
        val requests = mutableListOf<JsonObject>()
        val connection = AppServerConnection(
            webSocketFactory = AppServerWebSocketFactory { listener ->
                object : AppServerWebSocket {
                    override fun start() = listener.onOpen(this)
                    override fun send(text: String): Boolean {
                        val request = Json.parseToJsonElement(text).jsonObject
                        val method = request.requiredString("method")
                        if (method == "initialized") return true
                        val result = if (method == "initialize") {
                            json("""{"userAgent":"test","codexHome":"/tmp/codex","platformFamily":"unix","platformOs":"linux"}""")
                        } else {
                            requests += request
                            json("""{"data":[],"nextCursor":null}""")
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
            api.listThreads(
                ancestorThreadId = "parent", cursor = "next",
                sourceKinds = setOf(ThreadSourceKind.SUB_AGENT_THREAD_SPAWN),
                sortKey = ThreadSortKey.CREATED_AT, sortDirection = SortDirection.ASC,
            )
            api.listThreadTurns("child", limit = 1)
            api.listQueuedInputs("parent", "next-input")

            assertEquals(listOf("thread/list", "thread/turns/list", "thread/queue/list"), requests.map { it.requiredString("method") })
            val filter = requests[0].requiredObject("params")
            assertEquals("parent", filter.requiredString("ancestorThreadId"))
            assertEquals("next", filter.requiredString("cursor"))
            assertEquals("created_at", filter.requiredString("sortKey"))
            assertEquals("asc", filter.requiredString("sortDirection"))
            assertEquals(Json.parseToJsonElement("""["subAgentThreadSpawn"]"""), filter["sourceKinds"])
            assertFalse(filter.containsKey("parentThreadId"))
            assertEquals(json("""{"threadId":"child","limit":1,"sortDirection":"desc","itemsView":"summary"}"""), requests[1].requiredObject("params"))
            assertEquals("next-input", requests[2].requiredObject("params").requiredString("cursor"))
        } finally {
            connection.close()
        }
    }

    @Test
    fun startedThreadKeepsPathRoleAndApprovalFlags() {
        val event = ProtocolMapper.subAgentEvent("thread/started", json("""{
          "thread":{
            "id":"child","sessionId":"session","parentThreadId":"parent",
            "preview":"review","cwd":"/workspace","createdAt":1,"updatedAt":2,
            "historyMode":"paginated","agentNickname":"Ada","agentRole":"reviewer",
            "source":{"subAgent":{"thread_spawn":{"parent_thread_id":"parent","depth":1,"agent_path":"/root/review"}}},
            "status":{"type":"active","activeFlags":["waitingOnApproval","waitingOnUserInput"]}
          }
        }""")) as SubAgentEvent.Started

        val agent = event.thread.subAgentSummary(ConversationTurnStatus.COMPLETED)
        assertEquals("/root/review", agent.name)
        assertEquals("reviewer", agent.role)
        assertEquals(SubAgentRuntimeStatus.WAITING_APPROVAL, agent.status)
        assertEquals(setOf(ThreadActiveFlag.WAITING_APPROVAL, ThreadActiveFlag.WAITING_INPUT), event.thread.activeFlags)
    }

    @Test
    fun globalStatusAndDeletionAreAvailableWithoutOpeningChildConversation() {
        val event = ProtocolMapper.subAgentEvent("thread/status/changed", json(
            """{"threadId":"child","status":{"type":"active","activeFlags":["waitingOnUserInput"]}}""",
        )) as SubAgentEvent.StatusChanged
        assertEquals(setOf(ThreadActiveFlag.WAITING_INPUT), event.flags)
        assertTrue((ProtocolMapper.subAgentEvent("thread/deleted", json("""{"threadId":"child"}""")) as SubAgentEvent.Removed).deleted)
        assertFalse((ProtocolMapper.subAgentEvent("thread/closed", json("""{"threadId":"child"}""")) as SubAgentEvent.Removed).deleted)
    }

    private fun json(value: String) = Json.parseToJsonElement(value).jsonObject
}
