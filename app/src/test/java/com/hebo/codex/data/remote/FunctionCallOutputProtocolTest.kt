package com.hebo.codex.data.remote

import com.hebo.codex.domain.ActivityKind
import com.hebo.codex.domain.ActivityStatus
import com.hebo.codex.domain.ConversationEvent
import com.hebo.codex.domain.ConversationItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class FunctionCallOutputProtocolTest {
    @Test
    fun persistedAndLiveToolOutputsShareTheSameContent() {
        val item = output(JsonPrimitive("3 tests passed\nNo failures"))
        val persisted = ProtocolMapper.conversationItem(item, "turn-1") as ConversationItem.Activity
        val live = ProtocolMapper.notification(
            serverId = "server-1",
            method = "item/completed",
            params = buildJsonObject {
                put("threadId", "thread-1")
                put("turnId", "turn-1")
                put("item", item)
            },
        ).conversationEvent as ConversationEvent.ItemUpserted

        assertEquals(persisted, live.item)
        assertEquals("output-1", persisted.id)
        assertEquals("turn-1", persisted.turnId)
        assertEquals(ActivityKind.TOOL_OUTPUT, persisted.kind)
        assertEquals("run_tests", persisted.title)
        assertEquals("tools", persisted.summary)
        assertEquals("3 tests passed\nNo failures", persisted.details)
        assertEquals(ActivityStatus.COMPLETED, persisted.status)
        assertFalse(live.isActive)
    }

    @Test
    fun mixedContentPreservesTextOrderWithoutShowingBinaryPayloads() {
        val item = output(Json.parseToJsonElement(
            """[
              {"type":"input_text","text":"Screenshot:"},
              {"type":"input_image","image_url":"data:image/png;base64,binary-image"},
              {"type":"input_image","file_id":"file-1"},
              {"type":"input_audio","audio_url":"data:audio/wav;base64,binary-audio"},
              {"type":"encrypted_content","encrypted_content":"ciphertext"},
              {"type":"input_text","text":"Finished."}
            ]""".trimIndent(),
        ))

        val mapped = ProtocolMapper.conversationItem(item, "turn-1") as ConversationItem.Activity

        assertEquals("Screenshot:\n[图片]\n[图片]\n[音频]\n[加密内容]\nFinished.", mapped.details)
        assertEquals(null, mapped.technicalDetails)
    }

    @Test
    fun invalidOutputBodiesAreRejected() {
        listOf("null", "42", "{}", "[{}]", "[{\"type\":\"unknown\"}]").forEach { body ->
            assertThrows(ProtocolException::class.java) {
                ProtocolMapper.conversationItem(output(Json.parseToJsonElement(body)), "turn-1")
            }
        }
    }

    private fun output(body: JsonElement): JsonObject = buildJsonObject {
        put("id", "output-1")
        put("type", "functionCallOutput")
        put("name", "run_tests")
        put("namespace", "tools")
        put("output", body)
    }
}
