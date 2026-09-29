package com.hebo.codex.ui.screens.chat

import com.hebo.codex.domain.DraftAttachment
import com.hebo.codex.domain.InputByteRange
import com.hebo.codex.domain.InputTextElement
import com.hebo.codex.domain.TurnOptions
import com.hebo.codex.domain.UserInputPart
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueuedDraftTest {
    @Test
    fun `撤回后未改动文本保留文本标记与原始输入顺序`() {
        val original = mixedInput()
        val draft = ChatDraft(options = TurnOptions(model = "model")).restoreQueuedInput(original)

        assertEquals("看📄文件\n继续检查", draft.message)
        assertEquals(listOf("queued:0", "queued:2", "queued:4"), draft.attachments.map { it.id })
        assertEquals("model", draft.options.model)
        assertEquals(original, draft.queuedInput)
        assertEquals(original, draft.toInput())
    }

    @Test
    fun `编辑文本会合并文本段并移除已失效的字节标记`() {
        val original = mixedInput()
        val draft = ChatDraft().restoreQueuedInput(original).copy(message = "改为检查其他文件")

        assertEquals(
            listOf(original[0], UserInputPart.Text("改为检查其他文件"), original[2], original[4]),
            draft.toInput(),
        )
        assertTrue(draft.toInput().filterIsInstance<UserInputPart.Text>().single().textElements.isEmpty())
        assertEquals(original, draft.queuedInput)
    }

    @Test
    fun `清空文本时仅发送保留下来的附件`() {
        val original = mixedInput()
        val draft = ChatDraft().restoreQueuedInput(original).copy(message = "")

        assertEquals(listOf(original[0], original[2], original[4]), draft.toInput())
        assertTrue(draft.hasContent)
    }

    @Test
    fun `移除附件不改变剩余文本标记和附件顺序`() {
        val original = mixedInput()
        val restored = ChatDraft().restoreQueuedInput(original)
        val draft = restored.copy(attachments = restored.attachments.filterNot { it.id == "queued:2" })

        assertEquals(listOf(original[0], original[1], original[3], original[4]), draft.toInput())
    }

    @Test
    fun `新增附件追加到原始队列内容之后`() {
        val original = mixedInput()
        val restored = ChatDraft().restoreQueuedInput(original)
        val added = DraftAttachment("device-image", UserInputPart.Image("data:image/png;base64,new-image"))
        val draft = restored.copy(attachments = restored.attachments + added)

        assertEquals(original + added.input, draft.toInput())
    }

    @Test
    fun `仅附件的队列补充文字时文字置前且附件保持原序`() {
        val original = listOf(UserInputPart.UploadedImage("file-42", "high"), UserInputPart.LocalAudio("/tmp/voice.wav"))
        val draft = ChatDraft().restoreQueuedInput(original).copy(message = "分析附件")

        assertEquals(listOf(UserInputPart.Text("分析附件")) + original, draft.toInput())
    }

    @Test
    fun `以感叹号开头的原始文本保留队列标志和字面内容`() {
        val original = listOf(UserInputPart.Text("!继续解释这个命令，不要执行"))
        val draft = ChatDraft().restoreQueuedInput(original)

        assertNotNull(draft.queuedInput)
        assertEquals(original, draft.queuedInput)
        assertEquals("!继续解释这个命令，不要执行", draft.message)
        assertEquals(original, draft.toInput())
        assertNotNull(draft.copy(message = "!修改后的内容").queuedInput)
    }

    @Test
    fun `持久化的原始输入恢复后仍保留撤回编辑语义`() {
        val original = mixedInput()
        val stored = Json.encodeToString(original)
        val restoredInput = Json.decodeFromString<List<UserInputPart>>(stored)
        val draft = ChatDraft().restoreQueuedInput(restoredInput)

        assertEquals(original, draft.queuedInput)
        assertEquals(original, draft.toInput())
        assertEquals(original, draft.copy(message = "临时编辑").copy(message = draft.message).toInput())
    }

    @Test
    fun `普通草稿保持文本在前附件在后的发送顺序`() {
        val image = UserInputPart.LocalImage("/tmp/image.png")
        val draft = ChatDraft(message = "说明", attachments = listOf(DraftAttachment("image", image)))

        assertEquals(listOf(UserInputPart.Text("说明"), image), draft.toInput())
        assertFalse(ChatDraft().hasContent)
    }

    private fun mixedInput(): List<UserInputPart> = listOf(
        UserInputPart.UploadedImage("file-42", "high"),
        UserInputPart.Text("看📄文件", listOf(InputTextElement(InputByteRange(3, 7), "📄"))),
        UserInputPart.Mention("助手", "app://assistant"),
        UserInputPart.Text("继续检查"),
        UserInputPart.Audio("https://example.com/voice.wav"),
    )
}
