package com.hebo.codex.ui.screens.chat

import com.hebo.codex.domain.TurnOptions
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatDraftSubmissionTest {
    @Test
    fun `新回合已确认后清除覆盖并由会话实际设置驱动后续回合`() {
        val submitted = ChatDraft(message = "开始", options = TurnOptions(model = "model", reasoningEffort = "ultra"))

        assertEquals(ChatDraft(), submitted.afterSuccessfulSubmission(submitted, optionsApplied = true))
    }

    @Test
    fun `引导和排队不会消耗尚未应用的思考深度`() {
        val submitted = ChatDraft(message = "接下来", options = TurnOptions(reasoningEffort = "high"))

        assertEquals(
            ChatDraft(options = submitted.options),
            submitted.afterSuccessfulSubmission(submitted, optionsApplied = false),
        )
    }

    @Test
    fun `发送中编辑的文本保留且已确认的选项改为继承会话`() {
        val submitted = ChatDraft(message = "第一条", options = TurnOptions(reasoningEffort = "high"))
        val edited = submitted.copy(message = "下一条")

        assertEquals(
            ChatDraft(message = "下一条"),
            edited.afterSuccessfulSubmission(submitted, optionsApplied = true),
        )
    }

    @Test
    fun `较早发送的确认不会清除更新的深度选择`() {
        val submitted = ChatDraft(message = "第一条", options = TurnOptions(reasoningEffort = "high"))
        val edited = submitted.copy(options = TurnOptions(reasoningEffort = "ultra"))

        assertEquals(edited, edited.afterSuccessfulSubmission(submitted, optionsApplied = true))
    }

    @Test
    fun `快捷短语消费设置时保留输入框草稿`() {
        val draft = ChatDraft(message = "尚未发送的草稿", options = TurnOptions(reasoningEffort = "high"))

        assertEquals(ChatDraft(message = draft.message), draft.withoutAppliedOptions(draft.options))
    }
}
