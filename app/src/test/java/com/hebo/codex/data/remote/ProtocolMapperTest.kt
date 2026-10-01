package com.hebo.codex.data.remote

import com.hebo.codex.domain.ActivityKind
import com.hebo.codex.domain.ActivityStatus
import com.hebo.codex.domain.AgentMessagePhase
import com.hebo.codex.domain.CommandAction
import com.hebo.codex.domain.ConversationEvent
import com.hebo.codex.domain.ConversationItem
import com.hebo.codex.domain.ConversationRunStatus
import com.hebo.codex.domain.FileChangeKind
import com.hebo.codex.domain.FileSystemAccessMode
import com.hebo.codex.domain.FileSystemPermissionTarget
import com.hebo.codex.domain.ThreadHistoryMode
import com.hebo.codex.domain.ThreadRuntimeStatus
import com.hebo.codex.domain.TurnDetailsState
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
    fun mapsPaginatedResumeSummaryAndHydratedItems() {
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
                "historyMode": "paginated",
                "turns": []
              },
              "initialTurnsPage": {
                "data": [
                  {
                    "id": "turn-1",
                    "status": "completed",
                    "itemsView": "summary",
                    "items": [
                      {
                        "type": "userMessage",
                        "id": "user-1",
                        "content": [{ "type": "text", "text": "定位空指针" }]
                      },
                      {
                        "type": "agentMessage",
                        "id": "answer-1",
                        "text": "已修复登录问题",
                        "phase": "final_answer"
                      }
                    ]
                  }
                ],
                "nextCursor": "older-turns"
              }
            }
            """.trimIndent(),
        ).jsonObject
        val itemsResponse = json.parseToJsonElement(
            """
            {
              "data": [
                {
                  "turnId": "turn-1",
                  "item": {
                        "type": "userMessage",
                        "id": "user-1",
                        "clientId": "client-1",
                        "content": [{ "type": "text", "text": "定位空指针" }]
                  }
                },
                {
                  "turnId": "turn-1",
                  "item": {
                        "type": "agentMessage",
                        "id": "agent-1",
                        "text": "正在检查",
                        "phase": "commentary"
                  }
                },
                {
                  "turnId": "turn-1",
                  "item": {
                        "type": "commandExecution",
                        "id": "command-1",
                        "command": "rg null LoginViewModel.kt",
                        "cwd": "/workspace/mobile",
                        "status": "completed",
                        "aggregatedOutput": "44: login(userId)",
                        "exitCode": 0
                  }
                },
                {
                  "turnId": "turn-1",
                  "item": {
                        "type": "agentMessage",
                        "id": "answer-1",
                        "text": "已修复登录问题",
                        "phase": "final_answer"
                  }
                }
              ],
              "nextCursor": null
            }
            """.trimIndent(),
        ).jsonObject

        val snapshot = ProtocolMapper.resumedThreadSnapshot(response)
        val page = ProtocolMapper.threadItemPage(itemsResponse, "turn-1")

        assertEquals("thr-1", snapshot.summary.id)
        assertEquals("session-1", snapshot.summary.sessionId)
        assertEquals("修复登录问题", snapshot.summary.title)
        assertEquals(ThreadRuntimeStatus.ACTIVE, snapshot.summary.status)
        assertEquals("older-turns", snapshot.olderTurnsCursor)
        assertEquals("turn-1", snapshot.turns.single().id)
        assertEquals(TurnDetailsState.Unloaded, snapshot.turns.single().detailsState)
        assertEquals(
            listOf(
                ConversationItem.UserMessage("user-1", "turn-1", listOf(UserMessagePart.Text("定位空指针"))),
                ConversationItem.AgentMessage("answer-1", "turn-1", "已修复登录问题", AgentMessagePhase.FINAL_ANSWER),
            ),
            snapshot.turns.single().items,
        )
        assertEquals(4, page.data.size)
        assertEquals(
            listOf(UserMessagePart.Text("定位空指针")),
            (page.data[0] as ConversationItem.UserMessage).content,
        )
        assertEquals(
            AgentMessagePhase.COMMENTARY,
            (page.data[1] as ConversationItem.AgentMessage).phase,
        )
        assertEquals(ActivityStatus.COMPLETED, (page.data[2] as ConversationItem.CommandExecution).status)
        assertEquals(snapshot.turns.single().items.last(), page.data.last())
    }

    @Test
    fun emptySummaryStillRequiresLoadingDetails() {
        val page = ProtocolMapper.threadTurnPage(
            json.parseToJsonElement(
                """{"data":[{"id":"turn-1","status":"inProgress","itemsView":"summary","items":[]}],"nextCursor":null}""",
            ).jsonObject,
        )

        assertTrue(page.data.single().items.isEmpty())
        assertEquals(TurnDetailsState.Unloaded, page.data.single().detailsState)
    }

    @Test
    fun rejectsTurnPagesWithoutRequestedSummaryView() {
        listOf("notLoaded", "full").forEach { itemsView ->
            val response = json.parseToJsonElement(
                """{"data":[{"id":"turn-1","status":"completed","itemsView":"$itemsView","items":[]}],"nextCursor":null}""",
            ).jsonObject

            val error = assertThrows(ProtocolException::class.java) {
                ProtocolMapper.threadTurnPage(response)
            }

            assertEquals("分页回合必须使用 itemsView=summary", error.message)
        }
    }

    @Test
    fun rejectsLegacyHistoryWithoutCompatibilityPath() {
        val response = json.parseToJsonElement(
            """
            {
              "thread": {
                "id": "thr-legacy",
                "sessionId": "session-legacy",
                "preview": "旧会话",
                "cwd": "/workspace/legacy",
                "createdAt": 100,
                "updatedAt": 200,
                "status": { "type": "idle" },
                "turns": []
              }
            }
            """.trimIndent(),
        ).jsonObject

        val error = assertThrows(UnsupportedThreadHistoryModeException::class.java) {
            ProtocolMapper.threadMetadata(response)
        }

        assertEquals(ThreadHistoryMode.LEGACY, error.historyMode)
    }

    @Test
    fun mapsFinalAnswerPhase() {
        val item = json.parseToJsonElement(
            """{"type":"agentMessage","id":"answer","text":"","phase":"final_answer"}""",
        ).jsonObject

        val mapped = ProtocolMapper.conversationItem(item, "turn-1")
            as ConversationItem.AgentMessage

        assertEquals(AgentMessagePhase.FINAL_ANSWER, mapped.phase)
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
    fun mapsNotificationMetadataAndTypedStatusEvent() {
        val notification = ProtocolMapper.notification(
            serverId = "server-1",
            method = "thread/status/changed",
            params = json.parseToJsonElement(
                """{"threadId":"thread-1","status":{"type":"active"}}""",
            ).jsonObject,
        )

        assertEquals("thread-1", notification.threadId)
        assertTrue(notification.bumpThreadRevision)
        assertEquals(
            ConversationEvent.StatusChanged(ConversationRunStatus.ACTIVE),
            notification.conversationEvent,
        )
    }

    @Test
    fun mapsItemDeltaToReusableTypedUpdate() {
        val notification = ProtocolMapper.notification(
            serverId = "server-1",
            method = "item/agentMessage/delta",
            params = json.parseToJsonElement(
                """{"threadId":"thread-1","turnId":"turn-1","itemId":"item-1","delta":"完成"}""",
            ).jsonObject,
        )
        val event = notification.conversationEvent as ConversationEvent.ItemChanged
        val original = ConversationItem.AgentMessage("item-1", "turn-1", "开始")

        val updated = event.update.apply(original) as ConversationItem.AgentMessage

        assertEquals("开始完成", updated.text)
        assertTrue(event.markActive)
    }

    @Test
    fun mapsPlanToPersistentConversationItem() {
        val item = json.parseToJsonElement(
            """{"type":"plan","id":"plan-1","text":"1. 定位问题\n2. 修改实现"}""",
        ).jsonObject

        val mapped = ProtocolMapper.conversationItem(item, "turn-1")
            as ConversationItem.Plan

        assertEquals("plan-1", mapped.id)
        assertEquals("1. 定位问题\n2. 修改实现", mapped.text)
    }

    @Test
    fun mapsUserShellCommandAsUserConversationItem() {
        val item = json.parseToJsonElement(
            """{
              "type":"commandExecution",
              "id":"shell-1",
              "command":"/usr/bin/zsh -lc 'git status --short'",
              "cwd":"/workspace",
              "source":"userShell",
              "status":"completed",
              "aggregatedOutput":" M README.md",
              "exitCode":0,
              "durationMs":1250
            }""",
        ).jsonObject

        val mapped = ProtocolMapper.conversationItem(item, "turn-shell")
            as ConversationItem.UserShellCommand

        assertEquals("shell-1", mapped.id)
        assertEquals("turn-shell", mapped.turnId)
        assertEquals("/workspace", mapped.cwd)
        assertEquals(" M README.md", mapped.output)
        assertEquals(0, mapped.exitCode)
        assertEquals(1250L, mapped.durationMillis)
        assertEquals(ActivityStatus.COMPLETED, mapped.status)
    }

    @Test
    fun mapsBackgroundTerminalSnapshotAndCommandProcessId() {
        val page = ProtocolMapper.backgroundTerminalPage(
            response = json.parseToJsonElement(
                """{
                  "data":[{
                    "itemId":"command-1",
                    "processId":"4242",
                    "command":"sleep 60",
                    "cwd":"/workspace",
                    "osPid":4242,
                    "cpuPercent":0.1,
                    "rssKb":1024
                  }],
                  "nextCursor":null
                }""",
            ).jsonObject,
            observedAtMillis = 12_345L,
        )
        val command = ProtocolMapper.conversationItem(
            json.parseToJsonElement(
                """{
                  "type":"commandExecution",
                  "id":"command-1",
                  "command":"sleep 60",
                  "cwd":"/workspace",
                  "status":"inProgress",
                  "processId":"4242"
                }""",
            ).jsonObject,
            "turn-1",
        ) as ConversationItem.CommandExecution

        assertEquals("4242", page.data.single().processId)
        assertEquals(12_345L, page.data.single().observedAtMillis)
        assertEquals("4242", command.processId)
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

        val mapped = ProtocolMapper.conversationItem(item, "turn-1")
            as ConversationItem.Reasoning

        assertEquals(listOf("第一段", "第二段"), mapped.summaryParts)
        assertEquals(listOf("原始一", "原始二"), mapped.rawContentParts)
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

        val mapped = ProtocolMapper.conversationItem(item, "turn-1")

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

        val mapped = ProtocolMapper.conversationItem(item, "turn-1")
            as ConversationItem.UserMessage

        assertEquals(UserMessagePart.Text("分析截图"), mapped.content[0])
        assertEquals(UserMessagePart.Image("data:image/png;base64,AAAA"), mapped.content[1])
        assertEquals(UserMessagePart.LocalImage("/tmp/screenshot.png"), mapped.content[2])
        assertEquals(UserMessagePart.Skill("review", "/skills/review/SKILL.md"), mapped.content[3])
        assertEquals(UserMessagePart.Mention("Demo App", "app://demo"), mapped.content[4])
    }

    @Test
    fun mapsWebSearchToReadableContentWithoutTechnicalDetails() {
        val item = json.parseToJsonElement(
            """{
              "type":"webSearch",
              "id":"search-1",
              "query":"Android Compose",
              "action":{"type":"search","query":"Compose Material 3"}
            }""",
        ).jsonObject

        val mapped = ProtocolMapper.conversationItem(item, "turn-1")
            as ConversationItem.Activity

        assertEquals(ActivityKind.WEB_SEARCH, mapped.kind)
        assertEquals("搜索网页", mapped.title)
        assertEquals("Compose Material 3", mapped.summary)
        assertEquals("搜索词\nCompose Material 3", mapped.details)
        assertEquals(null, mapped.technicalDetails)
    }

    @Test
    fun preservesDistinctSearchTermsAcrossHistoryAndItemNotifications() {
        val item = json.parseToJsonElement(
            """{
              "type":"webSearch",
              "id":"search-1",
              "query":"Searching Android documentation",
              "action":{
                "type":"search",
                "query":"Compose Material 3",
                "queries":["", "Compose Material 3", "Compose animation", "  ", "Android accessibility", "Compose animation"]
              }
            }""",
        ).jsonObject
        val historyItem = ProtocolMapper.conversationItem(item, "turn-1")
            as ConversationItem.Activity

        assertEquals("搜索网页 · 3 个关键词", historyItem.title)
        assertEquals("Compose Material 3", historyItem.summary)
        assertEquals(
            "搜索词\n1. Compose Material 3\n\n2. Compose animation\n\n3. Android accessibility",
            historyItem.details,
        )
        assertEquals(null, historyItem.technicalDetails)

        listOf("item/started", "item/completed").forEach { method ->
            val notification = ProtocolMapper.notification(
                serverId = "server-1",
                method = method,
                params = json.parseToJsonElement(
                    """{"threadId":"thread-1","turnId":"turn-1","item":$item}""",
                ).jsonObject,
            )
            val event = notification.conversationEvent as ConversationEvent.ItemUpserted

            assertEquals(historyItem, event.item)
            assertEquals(method == "item/started", event.isActive)
        }
    }

    @Test
    fun usesTopLevelSearchSummaryWhenActionHasNoSearchTerms() {
        val actions = listOf(
            """{"type":"search"}""",
            """{"type":"search","query":null,"queries":null}""",
            """{"type":"search","query":"","queries":["", "  "]}""",
        )

        actions.forEach { action ->
            val mapped = ProtocolMapper.conversationItem(
                json.parseToJsonElement(
                    """{"type":"webSearch","id":"search-1","query":"Compose Material 3","action":$action}""",
                ).jsonObject,
                "turn-1",
            ) as ConversationItem.Activity

            assertEquals("搜索网页", mapped.title)
            assertEquals("Compose Material 3", mapped.summary)
            assertEquals("搜索词\nCompose Material 3", mapped.details)
            assertEquals(null, mapped.technicalDetails)
        }
    }

    @Test
    fun preservesReadableSearchResultsWithoutProtocolMetadata() {
        val mapped = ProtocolMapper.conversationItem(
            json.parseToJsonElement(
                """{
                  "type":"webSearch",
                  "id":"search-1",
                  "query":"Compose Material 3",
                  "action":{"type":"search","query":"Compose Material 3"},
                  "results":[
                    {"type":"text_result","title":"Material 3 in Compose","url":"https://developer.android.com/compose/material3","snippet":"Build a Material 3 theme.","ref_id":"turn0search0"},
                    {"type":"text_result","title":"Compose animation","url":"https://developer.android.com/compose/animation","snippet":"Animate state changes.","ref_id":"turn0search1"}
                  ]
                }""",
            ).jsonObject,
            "turn-1",
        ) as ConversationItem.Activity

        assertEquals(
            "搜索词\nCompose Material 3\n\n搜索结果\n" +
                "Material 3 in Compose\nhttps://developer.android.com/compose/material3\nBuild a Material 3 theme.\n\n" +
                "Compose animation\nhttps://developer.android.com/compose/animation\nAnimate state changes.",
            mapped.details,
        )
        assertEquals(null, mapped.technicalDetails)
    }

    @Test
    fun skipsAbsentAndUnrecognizedSearchResults() {
        val resultFields = listOf(
            "",
            """, "results":null""",
            """, "results":[]""",
            """, "results":[null, "opaque", 1, {"type":"future_result","title":"Unknown result"}, {"type":"text_result","title":"","url":null,"snippet":"  ","ref_id":"turn0search0"}]""",
        )

        resultFields.forEach { resultField ->
            val mapped = ProtocolMapper.conversationItem(
                json.parseToJsonElement(
                    """{"type":"webSearch","id":"search-1","query":"Compose","action":{"type":"search","query":"Compose"}$resultField}""",
                ).jsonObject,
                "turn-1",
            ) as ConversationItem.Activity

            assertEquals("搜索词\nCompose", mapped.details)
            assertEquals(null, mapped.technicalDetails)
        }
    }

    @Test
    fun mapsImageActivitiesToRemotePaths() {
        val imageView = ProtocolMapper.conversationItem(
            json.parseToJsonElement(
                """{"type":"imageView","id":"view-1","path":"/workspace/chart.png"}""",
            ).jsonObject,
            "turn-1",
        ) as ConversationItem.Activity
        val imageGeneration = ProtocolMapper.conversationItem(
            json.parseToJsonElement(
                """{"type":"imageGeneration","id":"generate-1","status":"completed","revisedPrompt":null,"result":"data:image/png;base64,AAAA","savedPath":"/workspace/generated.png"}""",
            ).jsonObject,
            "turn-1",
        ) as ConversationItem.Activity

        assertEquals("/workspace/chart.png", imageView.remotePath)
        assertEquals(ActivityKind.IMAGE_VIEW, imageView.kind)
        assertEquals("/workspace/generated.png", imageGeneration.remotePath)
        assertEquals(ActivityKind.IMAGE_GENERATION, imageGeneration.kind)
        assertFalse(imageGeneration.technicalDetails.orEmpty().contains("data:image"))
    }

    @Test
    fun mapsImageGenerationStatuses() {
        val cases = listOf(
            "in_progress" to ActivityStatus.IN_PROGRESS,
            "completed" to ActivityStatus.COMPLETED,
            "failed" to ActivityStatus.FAILED,
        )

        cases.forEach { (status, expected) ->
            val item = json.parseToJsonElement(
                """{"type":"imageGeneration","id":"generate-1","status":"$status","revisedPrompt":null,"result":""}""",
            ).jsonObject
            val mapped = ProtocolMapper.conversationItem(
                item,
                "turn-1",
            ) as ConversationItem.Activity

            assertEquals(expected, mapped.status)
        }
    }

    @Test
    fun mapsOpenPageToReadableUrl() {
        val mapped = ProtocolMapper.conversationItem(
            json.parseToJsonElement(
                """{
              "type":"webSearch",
              "id":"open-page-1",
              "query":"Compose Material 3",
              "action":{"type":"openPage","url":"https://developer.android.com/compose"}
            }""",
            ).jsonObject,
            "turn-1",
        ) as ConversationItem.Activity

        assertEquals("打开网页", mapped.title)
        assertEquals("https://developer.android.com/compose", mapped.summary)
        assertEquals("网址\nhttps://developer.android.com/compose", mapped.details)
        assertEquals(null, mapped.technicalDetails)
    }

    @Test
    fun preservesFindInPagePatternAndUrl() {
        val mapped = ProtocolMapper.conversationItem(
            json.parseToJsonElement(
                """{
              "type":"webSearch",
              "id":"find-in-page-1",
              "query":"Compose Material 3",
              "action":{"type":"findInPage","pattern":"MaterialTheme","url":"https://developer.android.com/compose"}
            }""",
            ).jsonObject,
            "turn-1",
        ) as ConversationItem.Activity

        assertEquals("页内查找", mapped.title)
        assertEquals("MaterialTheme", mapped.summary)
        assertEquals("查找内容\nMaterialTheme\n\n网址\nhttps://developer.android.com/compose", mapped.details)
        assertEquals(null, mapped.technicalDetails)
    }

    @Test
    fun leavesEmptyWebSearchActionsWithoutInventedContent() {
        val cases = listOf(
            """{"type":"search","query":null,"queries":null}""" to "搜索网页",
            """{"type":"search","query":"","queries":[]}""" to "搜索网页",
            """{"type":"openPage"}""" to "打开网页",
            """{"type":"openPage","url":null}""" to "打开网页",
            """{"type":"openPage","url":"  "}""" to "打开网页",
            """{"type":"findInPage"}""" to "页内查找",
            """{"type":"findInPage","pattern":null,"url":null}""" to "页内查找",
            """{"type":"findInPage","pattern":"","url":"  "}""" to "页内查找",
            """{"type":"other"}""" to "网页搜索",
            "null" to "网页搜索",
        )

        cases.forEach { (action, expectedTitle) ->
            val mapped = ProtocolMapper.conversationItem(
                json.parseToJsonElement(
                    """{"type":"webSearch","id":"search-1","query":"  ","action":$action}""",
                ).jsonObject,
                "turn-1",
            ) as ConversationItem.Activity

            assertEquals(expectedTitle, mapped.title)
            assertEquals(null, mapped.summary)
            assertEquals("", mapped.details)
            assertEquals(null, mapped.technicalDetails)
        }
    }

    @Test
    fun preservesOperationSummaryForOtherAndAbsentWebSearchActions() {
        val actionFields = listOf("", """, "action":null""", """, "action":{"type":"other"}""")

        actionFields.forEach { actionField ->
            val mapped = ProtocolMapper.conversationItem(
                json.parseToJsonElement(
                    """{"type":"webSearch","id":"search-1","query":"Checking Android documentation"$actionField}""",
                ).jsonObject,
                "turn-1",
            ) as ConversationItem.Activity

            assertEquals("网页搜索", mapped.title)
            assertEquals("Checking Android documentation", mapped.summary)
            assertEquals("操作内容\nChecking Android documentation", mapped.details)
            assertEquals(null, mapped.technicalDetails)
        }
    }

    @Test
    fun rejectsUnknownWebSearchActionTypes() {
        val error = assertThrows(ProtocolException::class.java) {
            ProtocolMapper.conversationItem(
                json.parseToJsonElement(
                    """{"type":"webSearch","id":"search-1","query":"Compose","action":{"type":"unknown"}}""",
                ).jsonObject,
                "turn-1",
            )
        }

        assertEquals("未知网页搜索操作：unknown", error.message)
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
                json.parseToJsonElement("""{"network":true}""").jsonObject,
            )
        }
    }
}
