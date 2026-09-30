package com.hebo.codex.ui.screens.chat

import com.hebo.codex.domain.DraftAttachment
import com.hebo.codex.domain.InputByteRange
import com.hebo.codex.domain.InputTextElement
import com.hebo.codex.domain.TurnOptions
import com.hebo.codex.domain.UserInputPart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueuedDraftTest {
    @Test
    fun `移回后合并文字并保留附件和当前选项成为普通草稿`() {
        val original = mixedInput()
        val draft = ChatDraft(options = TurnOptions(model = "model")).restoreQueuedInput(original)

        assertEquals("看📄文件\n继续检查", draft.message)
        assertEquals(listOf(original[0], original[2], original[4]), draft.attachments.map { it.input })
        assertEquals(3, draft.attachments.map { it.id }.toSet().size)
        assertEquals("model", draft.options.model)
        assertEquals(
            ChatDraft(message = "看📄文件\n继续检查", attachments = draft.attachments, options = draft.options),
            draft,
        )
        assertEquals(
            listOf(UserInputPart.Text("看📄文件\n继续检查"), original[0], original[2], original[4]),
            draft.toInput(),
        )
    }

    @Test
    fun `移回后编辑文字使用普通草稿发送顺序且没有原始字节标记`() {
        val original = mixedInput()
        val draft = ChatDraft().restoreQueuedInput(original).copy(message = "改为检查其他文件")

        assertEquals(
            listOf(UserInputPart.Text("改为检查其他文件"), original[0], original[2], original[4]),
            draft.toInput(),
        )
        assertTrue(draft.toInput().filterIsInstance<UserInputPart.Text>().single().textElements.isEmpty())
    }

    @Test
    fun `清空文本时仅发送保留下来的附件`() {
        val original = mixedInput()
        val draft = ChatDraft().restoreQueuedInput(original).copy(message = "")

        assertEquals(listOf(original[0], original[2], original[4]), draft.toInput())
        assertTrue(draft.hasContent)
    }

    @Test
    fun `移除附件保留文字和剩余附件顺序`() {
        val original = mixedInput()
        val restored = ChatDraft().restoreQueuedInput(original)
        val removedId = restored.attachments[1].id
        val draft = restored.copy(attachments = restored.attachments.filterNot { it.id == removedId })

        assertEquals(listOf(UserInputPart.Text(restored.message), original[0], original[4]), draft.toInput())
    }

    @Test
    fun `新增附件追加到移回的附件之后`() {
        val original = mixedInput()
        val restored = ChatDraft().restoreQueuedInput(original)
        val added = DraftAttachment("device-image", UserInputPart.Image("data:image/png;base64,new-image"))
        val draft = restored.copy(attachments = restored.attachments + added)

        assertEquals(restored.toInput() + added.input, draft.toInput())
    }

    @Test
    fun `仅附件的队列补充文字时文字置前且附件保持原序`() {
        val original = listOf(UserInputPart.UploadedImage("file-42", "high"), UserInputPart.LocalAudio("/tmp/voice.wav"))
        val draft = ChatDraft().restoreQueuedInput(original).copy(message = "分析附件")

        assertEquals(listOf(UserInputPart.Text("分析附件")) + original, draft.toInput())
    }

    @Test
    fun `移回以感叹号开头的文字遵循普通输入框的 Shell 规则`() {
        val original = listOf(UserInputPart.Text("!继续解释这个命令，不要执行"))
        val draft = ChatDraft().restoreQueuedInput(original)

        assertEquals("!继续解释这个命令，不要执行", draft.message)
        assertEquals(ChatDraft(message = original.single().text), draft)
        assertTrue(isShellCommandInput(draft.message))
        assertEquals(original, draft.toInput())
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
