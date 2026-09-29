package com.hebo.codex.data.remote

import com.hebo.codex.domain.InputByteRange
import com.hebo.codex.domain.InputTextElement
import com.hebo.codex.domain.UserInputPart
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class UserInputMapperTest {
    @Test
    fun `混合输入的 wire 往返保留类型顺序媒体信息和引用`() {
        val wire = Json.parseToJsonElement(MIXED_INPUT_JSON).jsonArray
        val input = wire.map { userInputPart(it.jsonObject) }

        assertEquals(
            listOf(
                UserInputPart.Text(
                    "看📄文件",
                    listOf(
                        InputTextElement(InputByteRange(3, 7), "📄"),
                        InputTextElement(InputByteRange(7, 13)),
                    ),
                ),
                UserInputPart.Image("https://example.com/screenshot.png", "high"),
                UserInputPart.UploadedImage("file-42", "low"),
                UserInputPart.LocalImage("/workspace/截图.png", "original"),
                UserInputPart.Audio("data:audio/wav;base64,UklGRg=="),
                UserInputPart.LocalAudio("/workspace/录音.wav"),
                UserInputPart.Skill("review", "/skills/review/SKILL.md"),
                UserInputPart.Mention("助手", "app://assistant"),
            ),
            input,
        )
        assertEquals(wire, input.toWire())
    }

    @Test
    fun `文本标记范围保留 UTF8 字节偏移而不转换为字符下标`() {
        val input = Json.parseToJsonElement(MIXED_INPUT_JSON).jsonArray.map { userInputPart(it.jsonObject) }
        val text = input.first() as UserInputPart.Text
        val marker = text.textElements.first()
        val bytes = text.text.toByteArray(Charsets.UTF_8)

        assertEquals(3, marker.byteRange.start)
        assertEquals(7, marker.byteRange.end)
        assertEquals("📄", bytes.copyOfRange(marker.byteRange.start, marker.byteRange.end).toString(Charsets.UTF_8))
        assertEquals(input, input.toWire().map { userInputPart(it.jsonObject) })
    }

    @Test
    fun `未提供可选文本标记和图片清晰度时使用无标记输入`() {
        val wire = Json.parseToJsonElement(
            """[{"type":"text","text":"继续"},{"type":"image","url":"https://example.com/image.png"},{"type":"image","fileId":"file-42"},{"type":"localImage","path":"/workspace/image.png"}]""",
        ).jsonArray
        val input = wire.map { userInputPart(it.jsonObject) }

        assertEquals(
            listOf(
                UserInputPart.Text("继续"),
                UserInputPart.Image("https://example.com/image.png"),
                UserInputPart.UploadedImage("file-42"),
                UserInputPart.LocalImage("/workspace/image.png"),
            ),
            input,
        )
        assertEquals(input, input.toWire().map { userInputPart(it.jsonObject) })
    }

    @Test
    fun `原始队列输入经 JSON 持久化后可完整还原并重新发送`() {
        val wire = Json.parseToJsonElement(MIXED_INPUT_JSON).jsonArray
        val original: List<UserInputPart> = wire.map { userInputPart(it.jsonObject) }

        val stored = Json.encodeToString(original)
        val restored = Json.decodeFromString<List<UserInputPart>>(stored)

        assertEquals(original, restored)
        assertEquals(wire, restored.toWire())
    }

    private companion object {
        val MIXED_INPUT_JSON = """
            [
              {"type":"text","text":"看📄文件","text_elements":[
                {"byteRange":{"start":3,"end":7},"placeholder":"📄"},
                {"byteRange":{"start":7,"end":13}}
              ]},
              {"type":"image","url":"https://example.com/screenshot.png","detail":"high"},
              {"type":"image","fileId":"file-42","detail":"low"},
              {"type":"localImage","path":"/workspace/截图.png","detail":"original"},
              {"type":"audio","url":"data:audio/wav;base64,UklGRg=="},
              {"type":"localAudio","path":"/workspace/录音.wav"},
              {"type":"skill","name":"review","path":"/skills/review/SKILL.md"},
              {"type":"mention","name":"助手","path":"app://assistant"}
            ]
        """.trimIndent()
    }
}
