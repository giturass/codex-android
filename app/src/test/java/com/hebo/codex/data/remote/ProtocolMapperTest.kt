package com.hebo.codex.data.remote

import com.hebo.codex.domain.ActivityStatus
import com.hebo.codex.domain.ConversationItem
import com.hebo.codex.domain.FileChangeKind
import com.hebo.codex.domain.ThreadRuntimeStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolMapperTest {
    private val json = Json

    @Test
    fun mapsResumedThreadAndTranscriptItems() {
        val response = json.parseToJsonElement(
            """
            {
              "thread": {
                "id": "thr-1",
                "sessionId": "session-1",
                "preview": "修复登录问题",
                "name": null,
                "cwd": "/workspace/mobile",
                "createdAt": 100,
                "updatedAt": 200,
                "recencyAt": 210,
                "status": { "type": "active", "activeFlags": [] },
                "turns": [
                  {
                    "id": "turn-1",
                    "status": "inProgress",
                    "items": [
                      {
                        "type": "userMessage",
                        "id": "user-1",
                        "clientId": "client-1",
                        "content": [{ "type": "text", "text": "定位空指针" }]
                      },
                      {
                        "type": "agentMessage",
                        "id": "agent-1",
                        "text": "正在检查"
                      },
                      {
                        "type": "commandExecution",
                        "id": "command-1",
                        "command": "rg null LoginViewModel.kt",
                        "cwd": "/workspace/mobile",
                        "status": "completed",
                        "aggregatedOutput": "44: login(userId)",
                        "exitCode": 0
                      }
                    ]
                  }
                ]
              }
            }
            """.trimIndent(),
        ).jsonObject

        val snapshot = ProtocolMapper.threadSnapshot(response)

        assertEquals("thr-1", snapshot.summary.id)
        assertEquals("session-1", snapshot.summary.sessionId)
        assertEquals("修复登录问题", snapshot.summary.title)
        assertEquals(ThreadRuntimeStatus.ACTIVE, snapshot.summary.status)
        assertEquals("turn-1", snapshot.activeTurnId)
        assertEquals(3, snapshot.items.size)
        assertEquals("定位空指针", (snapshot.items[0] as ConversationItem.UserMessage).text)
        assertEquals(ActivityStatus.COMPLETED, (snapshot.items[2] as ConversationItem.CommandExecution).status)
    }

    @Test
    fun mapsPlanAndTokenUsageNotifications() {
        val plan = ProtocolMapper.turnPlan(
            json.parseToJsonElement(
                """{"explanation":"先检查","plan":[{"step":"定位问题","status":"inProgress"}]}""",
            ).jsonObject,
        )
        val usage = ProtocolMapper.tokenUsage(
            json.parseToJsonElement(
                """{"tokenUsage":{"total":{"totalTokens":30,"inputTokens":20,"cachedInputTokens":5,"outputTokens":10,"reasoningOutputTokens":3},"last":{"totalTokens":10,"inputTokens":6,"cachedInputTokens":0,"outputTokens":4,"reasoningOutputTokens":1},"modelContextWindow":200000}}""",
            ).jsonObject,
        )

        assertEquals("先检查", plan.explanation)
        assertEquals("定位问题", plan.steps.single().text)
        assertEquals(30L, usage.totalTokens)
        assertEquals(200000L, usage.modelContextWindow)
    }

    @Test
    fun mapsTypedFileChangeKinds() {
        val item = json.parseToJsonElement(
            """
            {
              "type": "fileChange",
              "id": "patch-1",
              "changes": [
                {
                  "path": "Added.kt",
                  "kind": { "type": "add" },
                  "diff": "val added = true"
                },
                {
                  "path": "Deleted.kt",
                  "kind": { "type": "delete" },
                  "diff": "val deleted = true"
                },
                {
                  "path": "LoginViewModel.kt",
                  "kind": { "type": "update", "move_path": "UserViewModel.kt" },
                  "diff": "@@ -1 +1 @@"
                }
              ],
              "status": "inProgress"
            }
            """.trimIndent(),
        ).jsonObject

        val mapped = ProtocolMapper.conversationItem(item, "turn-1", isCompleted = false)

        assertTrue(mapped is ConversationItem.FileChange)
        mapped as ConversationItem.FileChange
        assertEquals(FileChangeKind.Add, mapped.changes[0].kind)
        assertEquals(FileChangeKind.Delete, mapped.changes[1].kind)
        assertEquals(
            FileChangeKind.Update(movePath = "UserViewModel.kt"),
            mapped.changes[2].kind,
        )
        assertEquals(ActivityStatus.IN_PROGRESS, mapped.status)
    }
}
