package com.hebo.codex.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DraftModelsTest {
    @Test
    fun summaryUsesFirstNonBlankLineWithoutChangingStoredMessage() {
        val message = "\n  修复登录流程  \n保留后续说明"
        val draft = ConversationDraft.NewTask(
            serverId = "server-1",
            draftId = "draft-1",
            message = message,
            cwd = "/workspace/app",
            options = ThreadOptions(),
            attachments = emptyList(),
            updatedAtMillis = 1000,
            revision = "revision-1",
        )

        assertEquals("修复登录流程", draft.toSummary().preview)
        assertEquals(message, draft.message)
    }

    @Test
    fun attachmentOnlyConversationIsStillADraft() {
        val draft = ConversationDraft.Thread(
            serverId = "server-1",
            threadId = "thread-1",
            threadTitle = "图片分析",
            message = "",
            cwd = "/workspace/app",
            options = TurnOptions(),
            attachments = listOf(
                DraftAttachment("attachment-1", UserInputPart.LocalImage("/tmp/screenshot.png")),
            ),
            updatedAtMillis = 1000,
            revision = "revision-1",
        )

        assertEquals(true, draft.hasContent())
        assertEquals("1 个附件", draft.toSummary().preview)
    }

    @Test
    fun attachmentOnlyNewTaskIsStillADraft() {
        val draft = ConversationDraft.NewTask(
            serverId = "server-1",
            draftId = "draft-1",
            message = "",
            cwd = "/workspace/app",
            options = ThreadOptions(),
            attachments = listOf(
                DraftAttachment("attachment-1", UserInputPart.LocalImage("/tmp/screenshot.png")),
            ),
            updatedAtMillis = 1000,
            revision = "revision-1",
        )

        assertEquals(true, draft.hasContent())
        assertEquals("1 个附件", draft.toSummary().preview)
    }

    @Test
    fun newTaskAndThreadTargetsNeverShareAKey() {
        val newTask = DraftKey("server-1", DraftTarget.NewTask("same-id"))
        val thread = DraftKey("server-1", DraftTarget.Thread("same-id"))

        assertNotEquals(newTask, thread)
    }
}
