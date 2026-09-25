package com.hebo.codex.data.remote

import com.hebo.codex.domain.ActivityStatus
import com.hebo.codex.domain.AgentState
import com.hebo.codex.domain.AgentStatus
import com.hebo.codex.domain.AgentTool
import com.hebo.codex.domain.ConversationEvent
import com.hebo.codex.domain.ConversationItem
import com.hebo.codex.domain.SubAgentActivityKind
import com.hebo.codex.domain.ThreadHistoryMode
import com.hebo.codex.domain.targetThreadIds
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubAgentProtocolTest {
    @Test
    fun spawnCompletionDoesNotCompleteTheRunningChildAndMatchesRestoredHistory() {
        val startedItem = collaborationItem(
            tool = "spawnAgent",
            status = "inProgress",
            receiverThreadIds = emptyList(),
            states = "{}",
        )
        val started = notification("item/started", startedItem)
        val startedCall = started.item as ConversationItem.AgentCollaboration

        assertTrue(started.isActive)
        assertEquals(ActivityStatus.IN_PROGRESS, startedCall.status)
        assertTrue(startedCall.targetThreadIds.isEmpty())
        assertEquals(startedCall, restoredItem(startedItem))

        val completedItem = collaborationItem(
            tool = "spawnAgent",
            status = "completed",
            receiverThreadIds = listOf("child-1"),
            states = """{"child-1":{"status":"running","message":null}}""",
        )
        val completed = notification("item/completed", completedItem)
        val completedCall = completed.item as ConversationItem.AgentCollaboration

        assertFalse(completed.isActive)
        assertEquals(
            ConversationItem.AgentCollaboration(
                id = "collab-1",
                turnId = "turn-1",
                tool = AgentTool.SPAWN,
                status = ActivityStatus.COMPLETED,
                senderThreadId = "parent-1",
                receiverThreadIds = listOf("child-1"),
                prompt = "检查登录问题",
                model = "test-model",
                reasoningEffort = "high",
                agentsStates = mapOf("child-1" to AgentState(AgentStatus.RUNNING, null)),
            ),
            completedCall,
        )
        assertEquals(completedCall, restoredItem(completedItem))
    }

    @Test
    fun interruptedWaitPreservesEachTargetsOwnOutcome() {
        val item = collaborationItem(
            tool = "wait",
            status = "interrupted",
            receiverThreadIds = listOf("done", "failed", "running"),
            states = """
                {
                  "done":{"status":"completed","message":"定位到登录竞态"},
                  "failed":{"status":"errored","message":"无法读取测试数据"},
                  "running":{"status":"running","message":null}
                }
            """.trimIndent(),
        )

        val call = notification("item/completed", item).item as ConversationItem.AgentCollaboration

        assertEquals(AgentTool.WAIT, call.tool)
        assertEquals(ActivityStatus.INTERRUPTED, call.status)
        assertEquals(
            mapOf(
                "done" to AgentState(AgentStatus.COMPLETED, "定位到登录竞态"),
                "failed" to AgentState(AgentStatus.ERRORED, "无法读取测试数据"),
                "running" to AgentState(AgentStatus.RUNNING, null),
            ),
            call.agentsStates,
        )
        assertEquals(listOf("done", "failed", "running"), call.targetThreadIds)
        assertEquals(call, restoredItem(item))
    }

    @Test
    fun listAgentsExposesStateOnlyTargetsAndDeduplicatesReceiverTargets() {
        val item = collaborationItem(
            tool = "listAgents",
            status = "completed",
            receiverThreadIds = emptyList(),
            states = """
                {
                  "new":{"status":"pendingInit","message":null},
                  "paused":{"status":"interrupted","message":"等待后续任务"},
                  "closed":{"status":"shutdown","message":null},
                  "missing":{"status":"notFound","message":null}
                }
            """.trimIndent(),
        )
        val call = restoredItem(item) as ConversationItem.AgentCollaboration

        assertEquals(AgentTool.LIST, call.tool)
        assertTrue(call.receiverThreadIds.isEmpty())
        assertEquals(listOf("new", "paused", "closed", "missing"), call.targetThreadIds)
        assertEquals(AgentState(AgentStatus.PENDING_INIT, null), call.agentsStates["new"])
        assertEquals(AgentState(AgentStatus.INTERRUPTED, "等待后续任务"), call.agentsStates["paused"])
        assertEquals(AgentState(AgentStatus.SHUTDOWN, null), call.agentsStates["closed"])
        assertEquals(AgentState(AgentStatus.NOT_FOUND, null), call.agentsStates["missing"])
        assertEquals(call, notification("item/completed", item).item)

        val overlappingTargets = JsonObject(
            item + ("receiverThreadIds" to JsonArray(listOf(JsonPrimitive("paused")))),
        )
        val overlappingCall = restoredItem(overlappingTargets) as ConversationItem.AgentCollaboration
        assertEquals(listOf("paused", "new", "closed", "missing"), overlappingCall.targetThreadIds)
    }

    @Test
    fun taskAndResultRemainCompleteBeyondThePreviousTechnicalDetailLimit() {
        val prompt = "逐项检查登录行为\n".repeat(700) + "任务末尾"
        val result = "已检查的结果包含完整上下文\n".repeat(700) + "结果末尾"
        val states = buildJsonObject {
            put("child-1", buildJsonObject {
                put("status", "completed")
                put("message", result)
            })
        }
        val item = JsonObject(
            collaborationItem(
                tool = "followupTask",
                status = "completed",
                receiverThreadIds = listOf("child-1"),
                states = states.toString(),
            ) + ("prompt" to JsonPrimitive(prompt)),
        )

        val call = notification("item/completed", item).item as ConversationItem.AgentCollaboration

        assertTrue(prompt.length > 4_000)
        assertTrue(result.length > 4_000)
        assertEquals(AgentTool.FOLLOWUP_TASK, call.tool)
        assertEquals(prompt, call.prompt)
        assertEquals(result, call.agentsStates["child-1"]?.message)
        assertEquals(call, restoredItem(item))
    }

    @Test
    fun completedSubAgentActivityRetainsItsNavigableThreadAndPath() {
        val item = Json.parseToJsonElement(
            """
            {
              "type":"subAgentActivity",
              "id":"activity-1",
              "kind":"completed",
              "agentThreadId":"child-1",
              "agentPath":"/root/login_review"
            }
            """.trimIndent(),
        ).jsonObject

        val completed = notification("item/completed", item)

        assertFalse(completed.isActive)
        assertEquals(
            ConversationItem.SubAgentActivity(
                id = "activity-1",
                turnId = "turn-1",
                kind = SubAgentActivityKind.COMPLETED,
                agentThreadId = "child-1",
                agentPath = "/root/login_review",
            ),
            completed.item,
        )
        assertEquals(completed.item, restoredItem(item))
    }

    @Test
    fun threadSummaryPreservesParentMetadataAndExplicitInputCapability() {
        val thread = Json.parseToJsonElement(
            """
            {
              "id":"child-1",
              "sessionId":"session-1",
              "parentThreadId":"parent-1",
              "agentNickname":"Ada",
              "agentRole":"reviewer",
              "canAcceptDirectInput":false,
              "preview":"检查登录问题",
              "cwd":"/workspace/mobile",
              "createdAt":100,
              "updatedAt":200,
              "status":{"type":"idle"},
              "historyMode":"paginated"
            }
            """.trimIndent(),
        ).jsonObject

        val summary = ProtocolMapper.threadSummary(thread)

        assertEquals("parent-1", summary.parentThreadId)
        assertEquals("Ada", summary.agentNickname)
        assertEquals("reviewer", summary.agentRole)
        assertEquals(ThreadHistoryMode.PAGINATED, summary.historyMode)
        assertEquals(false, summary.canAcceptDirectInput)
        assertEquals(
            true,
            ProtocolMapper.threadSummary(
                JsonObject(thread + ("canAcceptDirectInput" to JsonPrimitive(true))),
            ).canAcceptDirectInput,
        )
        assertNull(
            ProtocolMapper.threadSummary(
                JsonObject(thread + ("canAcceptDirectInput" to JsonNull)),
            ).canAcceptDirectInput,
        )
    }

    private fun collaborationItem(
        tool: String,
        status: String,
        receiverThreadIds: List<String>,
        states: String,
    ): JsonObject = buildJsonObject {
        put("type", "collabAgentToolCall")
        put("id", "collab-1")
        put("tool", tool)
        put("status", status)
        put("senderThreadId", "parent-1")
        put("receiverThreadIds", buildJsonArray { receiverThreadIds.forEach { add(JsonPrimitive(it)) } })
        put("prompt", "检查登录问题")
        put("model", "test-model")
        put("reasoningEffort", "high")
        put("agentsStates", Json.parseToJsonElement(states))
    }

    private fun notification(method: String, item: JsonObject): ConversationEvent.ItemUpserted =
        ProtocolMapper.notification(
            serverId = "server-1",
            method = method,
            params = buildJsonObject {
                put("threadId", "parent-1")
                put("turnId", "turn-1")
                put("item", item)
            },
        ).conversationEvent as ConversationEvent.ItemUpserted

    private fun restoredItem(item: JsonObject): ConversationItem = ProtocolMapper.threadItemPage(
        response = buildJsonObject {
            put("data", buildJsonArray {
                add(buildJsonObject {
                    put("turnId", "turn-1")
                    put("item", item)
                })
            })
            put("nextCursor", JsonNull)
        },
        expectedTurnId = "turn-1",
    ).data.single()
}
