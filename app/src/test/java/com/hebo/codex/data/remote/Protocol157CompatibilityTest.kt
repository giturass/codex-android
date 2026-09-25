package com.hebo.codex.data.remote

import com.hebo.codex.domain.ActivityStatus
import com.hebo.codex.domain.ConversationItem
import com.hebo.codex.domain.SubAgentActivityKind
import com.hebo.codex.domain.UserMessagePart
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class Protocol157CompatibilityTest {
    @Test
    fun restoresHistoryContainingNewCollaborationActionsAndCompletion() {
        val page = ProtocolMapper.threadItemPage(
            Json.parseToJsonElement(
                """{
                  "data": [
                    {"turnId":"turn-1","item":{
                      "id":"followup","type":"collabAgentToolCall","tool":"followupTask",
                      "status":"interrupted","senderThreadId":"parent","receiverThreadIds":["child"],
                      "prompt":"复查测试","model":null,"reasoningEffort":null,"agentsStates":{}
                    }},
                    {"turnId":"turn-1","item":{
                      "id":"message","type":"collabAgentToolCall","tool":"sendMessage",
                      "status":"completed","senderThreadId":"parent","receiverThreadIds":["child"],
                      "prompt":"检查完成","model":null,"reasoningEffort":null,"agentsStates":{}
                    }},
                    {"turnId":"turn-1","item":{
                      "id":"interrupt","type":"collabAgentToolCall","tool":"interruptAgent",
                      "status":"completed","senderThreadId":"parent","receiverThreadIds":["child"],
                      "prompt":null,"model":null,"reasoningEffort":null,"agentsStates":{}
                    }},
                    {"turnId":"turn-1","item":{
                      "id":"list","type":"collabAgentToolCall","tool":"listAgents",
                      "status":"completed","senderThreadId":"parent","receiverThreadIds":[],
                      "prompt":null,"model":null,"reasoningEffort":null,"agentsStates":{}
                    }},
                    {"turnId":"turn-1","item":{
                      "id":"done","type":"subAgentActivity","kind":"completed",
                      "agentThreadId":"child","agentPath":"/root/review"
                    }}
                  ],
                  "nextCursor":null
                }""".trimIndent(),
            ).jsonObject,
            expectedTurnId = "turn-1",
        )

        assertEquals(5, page.data.size)
        assertEquals(ActivityStatus.INTERRUPTED, (page.data.first() as ConversationItem.AgentCollaboration).status)
        assertEquals(SubAgentActivityKind.COMPLETED, (page.data.last() as ConversationItem.SubAgentActivity).kind)
    }

    @Test
    fun restoresAudioAndFileIdImagesWithoutLosingTheirSources() {
        val item = ProtocolMapper.conversationItem(
            Json.parseToJsonElement(
                """{
                  "id":"user-1","type":"userMessage","content":[
                    {"type":"image","fileId":"file-1"},
                    {"type":"image","url":"https://example.com/image.png","fileId":"file-2"},
                    {"type":"audio","url":"https://example.com/audio.wav"},
                    {"type":"localAudio","path":"/workspace/voice.wav"}
                  ]
                }""".trimIndent(),
            ).jsonObject,
            "turn-1",
        ) as ConversationItem.UserMessage

        assertEquals(
            listOf(
                UserMessagePart.Image(fileId = "file-1"),
                UserMessagePart.Image(url = "https://example.com/image.png", fileId = "file-2"),
                UserMessagePart.Audio("https://example.com/audio.wav"),
                UserMessagePart.LocalAudio("/workspace/voice.wav"),
            ),
            item.content,
        )
    }

    @Test
    fun rejectsImagesWithoutEitherProtocolSource() {
        assertThrows(ProtocolException::class.java) {
            ProtocolMapper.conversationItem(
                Json.parseToJsonElement(
                    """{"id":"user-1","type":"userMessage","content":[{"type":"image"}]}""",
                ).jsonObject,
                "turn-1",
            )
        }
    }

    @Test
    fun acceptsOmittedPermissionSectionsWithoutGrantingAnything() {
        val permissions = ProtocolMapper.requestedPermissions(Json.parseToJsonElement("{}").jsonObject)
        assertNull(permissions.networkEnabled)
        assertNull(permissions.fileSystem)
        assertEquals("{}", permissions.responseJson)

        val networkOnly = ProtocolMapper.requestedPermissions(
            Json.parseToJsonElement("""{"network":{"enabled":true}}""").jsonObject,
        )
        assertEquals(true, networkOnly.networkEnabled)
        assertNull(networkOnly.fileSystem)
    }

    @Test
    fun acceptsReasoningPartsWithSchemaDefaults() {
        assertEquals(
            ConversationItem.Reasoning("reasoning-1", "turn-1", emptyList(), emptyList()),
            ProtocolMapper.conversationItem(
                Json.parseToJsonElement("""{"id":"reasoning-1","type":"reasoning"}""").jsonObject,
                "turn-1",
            ),
        )
    }
}
