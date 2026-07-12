package com.hebo.codex.ui.components

import com.hebo.codex.domain.DraftKey
import com.hebo.codex.domain.DraftSummary
import com.hebo.codex.domain.DraftTarget
import com.hebo.codex.domain.RecentProject
import com.hebo.codex.domain.ThreadRuntimeStatus
import com.hebo.codex.domain.ThreadSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawerDraftsTest {
    @Test
    fun draftedThreadsAreRemovedFromRecentList() {
        val drafted = thread("thread-1")
        val regular = thread("thread-2")

        assertEquals(
            listOf(regular),
            threadsWithoutDrafts(listOf(drafted, regular), listOf(draft(DraftTarget.Thread(drafted.id)))),
        )
    }

    @Test
    fun emptyProjectsDisappearAfterDraftDeduplication() {
        val drafted = thread("thread-1")
        val project = RecentProject("/workspace/app", "app", 1, listOf(drafted))

        assertEquals(
            emptyList<RecentProject>(),
            projectsWithoutDrafts(listOf(project), listOf(draft(DraftTarget.Thread(drafted.id)))),
        )
    }

    @Test
    fun onlyAddedDraftKeysAreDetected() {
        val existing = draft(DraftTarget.Thread("thread-1"))
        val added = draft(DraftTarget.NewTask("draft-1"))

        assertFalse(hasNewDraft(setOf(existing.key), listOf(existing.copy(preview = "已更新"))))
        assertFalse(hasNewDraft(setOf(existing.key, added.key), listOf(existing)))
        assertTrue(hasNewDraft(setOf(existing.key), listOf(existing, added)))
    }

    @Test
    fun draftSelectionMatchesTargetTypeAndServer() {
        val newTaskDraft = draft(DraftTarget.NewTask("draft-1"))
        val threadDraft = draft(DraftTarget.Thread("thread-1"))

        assertTrue(
            isDraftSelected(
                newTaskDraft,
                DrawerActiveTarget.NewTaskDraft("server-1", "draft-1"),
            ),
        )
        assertTrue(
            isDraftSelected(
                threadDraft,
                DrawerActiveTarget.Thread("server-1", "thread-1"),
            ),
        )
        assertFalse(
            isDraftSelected(
                threadDraft,
                DrawerActiveTarget.Thread("server-2", "thread-1"),
            ),
        )
        assertFalse(
            isDraftSelected(
                newTaskDraft,
                DrawerActiveTarget.Thread("server-1", "draft-1"),
            ),
        )
    }

    @Test
    fun threadSelectionMatchesThreadTargetExactly() {
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
                DrawerActiveTarget.NewTaskDraft("server-1", "thread-1"),
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
    )
}
