package com.hebo.codex.ui.components

import com.hebo.codex.domain.DraftKey
import com.hebo.codex.domain.DraftSummary
import com.hebo.codex.domain.DraftTarget
import com.hebo.codex.domain.ThreadHistoryMode
import com.hebo.codex.domain.ThreadRuntimeStatus
import com.hebo.codex.domain.ThreadSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawerDraftsTest {
    @Test
    fun threadDraftsAreIndexedByConversation() {
        val threadDraft = draft(DraftTarget.Thread("thread-1"))
        val newTaskDraft = draft(DraftTarget.NewTask("draft-1"))

        assertEquals(
            mapOf("thread-1" to threadDraft),
            threadDraftsById(listOf(threadDraft, newTaskDraft)),
        )
    }

    @Test
    fun onlyDraftsMissingFromRecentConversationsAreListedSeparately() {
        val listed = draft(DraftTarget.Thread("thread-1"))
        val unlisted = draft(DraftTarget.Thread("thread-2"))

        assertEquals(
            listOf(unlisted),
            unlistedThreadDrafts(listOf(thread("thread-1")), listOf(listed, unlisted)),
        )
    }

    @Test
    fun threadSelectionMatchesThreadAndServer() {
        assertTrue(
            isThreadSelected(
                "server-1",
                "thread-1",
                DrawerActiveTarget.Thread("server-1", "thread-1"),
            ),
        )
        assertFalse(
            isThreadSelected(
                "server-1",
                "thread-1",
                DrawerActiveTarget.Thread("server-2", "thread-1"),
            ),
        )
    }

    private fun draft(target: DraftTarget) = DraftSummary(
        key = DraftKey("server-1", target),
        preview = "待处理内容",
        contextTitle = "会话",
        cwd = "/workspace/app",
        attachmentCount = 0,
        updatedAtMillis = 1,
        revision = "revision-${target.id}",
    )

    private fun thread(id: String) = ThreadSummary(
        id = id,
        sessionId = id,
        title = id,
        preview = "",
        cwd = "/workspace/app",
        createdAt = 1,
        updatedAt = 1,
        recencyAt = 1,
        status = ThreadRuntimeStatus.IDLE,
        historyMode = ThreadHistoryMode.PAGINATED,
    )
}
