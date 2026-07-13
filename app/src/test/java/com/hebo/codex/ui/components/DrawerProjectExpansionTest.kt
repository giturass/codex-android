package com.hebo.codex.ui.components

import com.hebo.codex.domain.RecentProject
import com.hebo.codex.domain.ThreadRuntimeStatus
import com.hebo.codex.domain.ThreadSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class DrawerProjectExpansionTest {
    @Test
    fun `进入项目分组时只展开当前会话所在项目`() {
        val projects = listOf(
            project("/workspace/api", "api-thread"),
            project("/workspace/android", "android-thread"),
        )

        val expandedPaths = initialExpandedProjectPaths(projects, "android-thread")

        assertEquals(setOf("/workspace/android"), expandedPaths)
    }

    @Test
    fun `没有当前会话时只展开最近项目`() {
        val projects = listOf(
            project("/workspace/api", "api-thread"),
            project("/workspace/android", "android-thread"),
        )

        val expandedPaths = initialExpandedProjectPaths(projects, null)

        assertEquals(setOf("/workspace/api"), expandedPaths)
    }

    @Test
    fun `没有项目时不展开任何项目`() {
        assertEquals(emptySet<String>(), initialExpandedProjectPaths(emptyList(), "thread"))
    }

    private fun project(path: String, threadId: String) = RecentProject(
        path = path,
        name = path.substringAfterLast('/'),
        lastActiveAt = 1,
        threads = listOf(thread(threadId, path)),
    )

    private fun thread(id: String, cwd: String) = ThreadSummary(
        id = id,
        sessionId = "session-$id",
        title = id,
        preview = id,
        cwd = cwd,
        createdAt = 1,
        updatedAt = 1,
        recencyAt = null,
        status = ThreadRuntimeStatus.NOT_LOADED,
    )
}
