package com.hebo.codex.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectGrouperTest {
    @Test
    fun groupsThreadsByWorkingDirectoryAndSortsByRecency() {
        val threads = listOf(
            thread(id = "1", cwd = "/workspace/mobile", updatedAt = 20),
            thread(id = "2", cwd = "/workspace/api", updatedAt = 30),
            thread(id = "3", cwd = "/workspace/mobile", updatedAt = 10),
        )

        val projects = groupRecentProjects(threads)

        assertEquals(listOf("api", "mobile"), projects.map(RecentProject::name))
        assertEquals(listOf("1", "3"), projects[1].threads.map(ThreadSummary::id))
    }

    @Test
    fun supportsWindowsPaths() {
        assertEquals("codex", projectName("C:\\Users\\me\\codex\\"))
    }

    private fun thread(id: String, cwd: String, updatedAt: Long) = ThreadSummary(
        id = id,
        sessionId = "session-$id",
        title = id,
        preview = id,
        cwd = cwd,
        createdAt = updatedAt,
        updatedAt = updatedAt,
        recencyAt = null,
        status = ThreadRuntimeStatus.NOT_LOADED,
        historyMode = ThreadHistoryMode.PAGINATED,
    )
}
