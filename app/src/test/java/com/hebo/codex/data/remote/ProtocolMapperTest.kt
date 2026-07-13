package com.hebo.codex.data.remote

import com.hebo.codex.domain.ActivityKind
import com.hebo.codex.domain.ActivityStatus
import com.hebo.codex.domain.CommandAction
import com.hebo.codex.domain.ConversationItem
import com.hebo.codex.domain.FileChangeKind
import com.hebo.codex.domain.FileSystemAccessMode
import com.hebo.codex.domain.FileSystemPermissionTarget
import com.hebo.codex.domain.ThreadRuntimeStatus
import com.hebo.codex.domain.UserMessagePart
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
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
        assertEquals(
            listOf(UserMessagePart.Text("定位空指针")),
            (snapshot.items[0] as ConversationItem.UserMessage).content,
        )
        assertEquals(ActivityStatus.COMPLETED, (snapshot.items[2] as ConversationItem.CommandExecution).status)
    }

    @Test
    fun mapsPlanAndTokenUsageNotifications() {
        val plan = ProtocolMapper.activeTurnPlan(
            json.parseToJsonElement(
                """{"turnId":"turn-1","explanation":"先检查","plan":[{"step":"定位问题","status":"inProgress"}]}""",
            ).jsonObject,
        )
        val usage = ProtocolMapper.tokenUsage(
            json.parseToJsonElement(
                """{"tokenUsage":{"total":{"totalTokens":30,"inputTokens":20,"cachedInputTokens":5,"outputTokens":10,"reasoningOutputTokens":3},"last":{"totalTokens":10,"inputTokens":6,"cachedInputTokens":0,"outputTokens":4,"reasoningOutputTokens":1},"modelContextWindow":200000}}""",
            ).jsonObject,
        )

        assertEquals("turn-1", plan.turnId)
        assertEquals("先检查", plan.explanation)
        assertEquals("定位问题", plan.steps.single().text)
        assertEquals(30L, usage.total.totalTokens)
        assertEquals(20L, usage.total.inputTokens)
        assertEquals(5L, usage.total.cachedInputTokens)
        assertEquals(10L, usage.last.totalTokens)
        assertEquals(6L, usage.last.inputTokens)
        assertEquals(1L, usage.last.reasoningOutputTokens)
        assertEquals(200000L, usage.modelContextWindow)
    }

    @Test
    fun mapsPlanToPersistentConversationItem() {
        val item = json.parseToJsonElement(
            """{"type":"plan","id":"plan-1","text":"1. 定位问题\n2. 修改实现"}""",
        ).jsonObject

        val mapped = ProtocolMapper.conversationItem(item, "turn-1", isCompleted = true)
            as ConversationItem.Plan

        assertEquals("plan-1", mapped.id)
        assertEquals("1. 定位问题\n2. 修改实现", mapped.text)
        assertFalse(mapped.isStreaming)
    }

    @Test
    fun mapsReasoningPartsWithoutFlatteningProtocolArrays() {
        val item = json.parseToJsonElement(
            """{
              "type":"reasoning",
              "id":"reasoning-1",
              "summary":["第一段","第二段"],
              "content":["原始一","原始二"]
            }""",
        ).jsonObject

        val mapped = ProtocolMapper.conversationItem(item, "turn-1", isCompleted = true)
            as ConversationItem.Reasoning

        assertEquals(listOf("第一段", "第二段"), mapped.summaryParts)
        assertEquals(listOf("原始一", "原始二"), mapped.rawContentParts)
        assertFalse(mapped.isStreaming)
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

    @Test
    fun preservesStructuredUserInputsWithoutFlatteningPayloads() {
        val item = json.parseToJsonElement(
            """
            {
              "type": "userMessage",
              "id": "user-1",
              "clientId": null,
              "content": [
                { "type": "text", "text": "分析截图" },
                { "type": "image", "url": "data:image/png;base64,AAAA" },
                { "type": "localImage", "path": "/tmp/screenshot.png" },
                { "type": "skill", "name": "review", "path": "/skills/review/SKILL.md" },
                { "type": "mention", "name": "Demo App", "path": "app://demo" }
              ]
            }
            """.trimIndent(),
        ).jsonObject

        val mapped = ProtocolMapper.conversationItem(item, "turn-1", isCompleted = true)
            as ConversationItem.UserMessage

        assertEquals(UserMessagePart.Text("分析截图"), mapped.content[0])
        assertEquals(UserMessagePart.Image("data:image/png;base64,AAAA"), mapped.content[1])
        assertEquals(UserMessagePart.LocalImage("/tmp/screenshot.png"), mapped.content[2])
        assertEquals(UserMessagePart.Skill("review", "/skills/review/SKILL.md"), mapped.content[3])
        assertEquals(UserMessagePart.Mention("Demo App", "app://demo"), mapped.content[4])
    }

    @Test
    fun mapsActivityToHumanSummaryAndKeepsProtocolAsTechnicalDetails() {
        val item = json.parseToJsonElement(
            """{
              "type":"webSearch",
              "id":"search-1",
              "query":"Android Compose",
              "action":{"type":"search","query":"Compose Material 3"}
            }""",
        ).jsonObject

        val mapped = ProtocolMapper.conversationItem(item, "turn-1", isCompleted = true)
            as ConversationItem.Activity

        assertEquals(ActivityKind.WEB_SEARCH, mapped.kind)
        assertEquals("Compose Material 3", mapped.title)
        assertEquals(null, mapped.summary)
        assertTrue(mapped.technicalDetails.orEmpty().contains("webSearch"))
    }

    @Test
    fun mapsImageActivitiesToRemotePaths() {
        val imageView = ProtocolMapper.conversationItem(
            json.parseToJsonElement(
                """{"type":"imageView","id":"view-1","path":"/workspace/chart.png"}""",
            ).jsonObject,
            "turn-1",
            isCompleted = true,
        ) as ConversationItem.Activity
        val imageGeneration = ProtocolMapper.conversationItem(
            json.parseToJsonElement(
                """{"type":"imageGeneration","id":"generate-1","status":"completed","revisedPrompt":null,"result":"data:image/png;base64,AAAA","savedPath":"/workspace/generated.png"}""",
            ).jsonObject,
            "turn-1",
            isCompleted = true,
        ) as ConversationItem.Activity

        assertEquals("/workspace/chart.png", imageView.remotePath)
        assertEquals(ActivityKind.IMAGE_VIEW, imageView.kind)
        assertEquals("/workspace/generated.png", imageGeneration.remotePath)
        assertEquals(ActivityKind.IMAGE_GENERATION, imageGeneration.kind)
        assertFalse(imageGeneration.technicalDetails.orEmpty().contains("data:image"))
    }

    @Test
    fun mapsAppServerWebSearchActionsUsingCamelCaseTypes() {
        val cases = listOf(
            """{
              "type":"webSearch",
              "id":"open-page-1",
              "query":"Compose Material 3",
              "action":{"type":"openPage","url":"https://developer.android.com/compose"}
            }""" to "https://developer.android.com/compose",
            """{
              "type":"webSearch",
              "id":"find-in-page-1",
              "query":"Compose Material 3",
              "action":{"type":"findInPage","pattern":"MaterialTheme"}
            }""" to "MaterialTheme",
        )

        cases.forEach { (payload, expectedSummary) ->
            val mapped = ProtocolMapper.conversationItem(
                item = json.parseToJsonElement(payload).jsonObject,
                turnId = "turn-1",
                isCompleted = true,
            ) as ConversationItem.Activity

            assertEquals(expectedSummary, mapped.title)
        }
    }

    @Test
    fun mapsApprovalPayloadsToTypedModels() {
        val actions = ProtocolMapper.commandActions(
            json.parseToJsonElement(
                """[
                  {"type":"read","command":"cat README.md","name":"README.md","path":"/workspace/README.md"},
                  {"type":"search","command":"rg TODO","query":"TODO","path":"/workspace"}
                ]""",
            ),
        )
        val permissions = ProtocolMapper.requestedPermissions(
            json.parseToJsonElement(
                """{
                  "network":{"enabled":true},
                  "fileSystem":{
                    "read":null,
                    "write":null,
                    "entries":[
                      {"path":{"type":"path","path":"/workspace"},"access":"write"}
                    ]
                  }
                }""",
            ).jsonObject,
        )

        assertTrue(actions[0] is CommandAction.Read)
        assertEquals("TODO", (actions[1] as CommandAction.Search).query)
        assertEquals(true, permissions.networkEnabled)
        assertEquals(FileSystemAccessMode.WRITE, permissions.fileSystem?.entries?.single()?.access)
        assertEquals(
            FileSystemPermissionTarget.Path("/workspace"),
            permissions.fileSystem?.entries?.single()?.target,
        )
    }

    @Test
    fun rejectsMalformedApprovalPayloadsInsteadOfHidingThem() {
        assertThrows(ProtocolException::class.java) {
            ProtocolMapper.commandActions(json.parseToJsonElement("{}"))
        }
        assertThrows(ProtocolException::class.java) {
            ProtocolMapper.requestedPermissions(
                json.parseToJsonElement("""{"network":null}""").jsonObject,
            )
        }
    }
}
