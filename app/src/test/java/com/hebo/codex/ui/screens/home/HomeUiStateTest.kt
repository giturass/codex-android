package com.hebo.codex.ui.screens.home

import com.hebo.codex.domain.ThreadHistoryMode
import com.hebo.codex.domain.ThreadRuntimeStatus
import com.hebo.codex.domain.ThreadSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class HomeUiStateTest {
    @Test
    fun `最近与筛选结果使用独立数据源`() {
        val recent = thread("recent")
        val filtered = thread("filtered")
        val state = HomeUiState(
            activeServerId = "server-1",
            filter = ThreadFilter(searchTerm = "关键字"),
            recentFeed = ThreadFeed(threads = listOf(recent)),
            filteredFeed = ThreadFeed(threads = listOf(filtered)),
        )

        assertEquals(listOf(filtered), state.threads)
        assertEquals(listOf(recent), state.recentThreads)
    }

    @Test
    fun `过期筛选请求不会覆盖当前结果`() {
        val state = HomeUiState(
            activeServerId = "server-1",
            filter = ThreadFilter(searchTerm = "新条件"),
        )
        val staleQuery = ThreadQuery(
            serverId = "server-1",
            filter = ThreadFilter(searchTerm = "旧条件"),
        )

        val result = state.withFeed(staleQuery) {
            ThreadFeed(threads = listOf(thread("stale")))
        }

        assertSame(state, result)
    }

    @Test
    fun `最近请求可在筛选页面后台更新`() {
        val state = HomeUiState(
            activeServerId = "server-1",
            filter = ThreadFilter(showArchived = true),
        )
        val recent = thread("recent")

        val result = state.withFeed(ThreadQuery("server-1")) {
            ThreadFeed(threads = listOf(recent))
        }

        assertEquals(listOf(recent), result.recentThreads)
        assertEquals(emptyList<ThreadSummary>(), result.threads)
    }

    private fun thread(id: String) = ThreadSummary(
        id = id,
        sessionId = "session-$id",
        title = id,
        preview = id,
        cwd = "/workspace/$id",
        createdAt = 1,
        updatedAt = 1,
        recencyAt = 1,
        status = ThreadRuntimeStatus.IDLE,
        historyMode = ThreadHistoryMode.PAGINATED,
    )
}
