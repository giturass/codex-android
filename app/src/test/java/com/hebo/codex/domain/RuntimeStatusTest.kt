package com.hebo.codex.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeStatusTest {
    @Test
    fun `重复进度替换原记录且完成后保持单一终态`() {
        val running = RuntimeActivity("review-1", "审查", "正在审查", RuntimeActivityStatus.RUNNING)
        val state = RuntimeStatus()
            .reduce(RuntimeStatusEvent.ActivityChanged(running))
            .reduce(RuntimeStatusEvent.ActivityChanged(running.copy(detail = "正在检查权限")))
            .reduce(RuntimeStatusEvent.ActivityChanged(running.copy(status = RuntimeActivityStatus.COMPLETED)))

        assertEquals(1, state.records.size)
        assertEquals(RuntimeActivityStatus.COMPLETED, state.records.single().status)
    }

    @Test
    fun `断线保留最后快照且不能推断运行中的活动已完成`() {
        val state = RuntimeStatus(
            records = listOf(RuntimeActivity("hook", "Hook", "", RuntimeActivityStatus.RUNNING)),
            diffs = listOf(RuntimeDiff("turn", "diff")),
            queue = RuntimeInputQueue(listOf(RuntimeQueuedInput("input", "继续")), loading = true),
        ).markStale()

        assertEquals(RuntimeActivityStatus.UNKNOWN, state.records.single().status)
        assertEquals("diff", state.diffs.single().diff)
        assertTrue(state.diffs.single().stale)
        assertEquals("继续", state.queue?.entries?.single()?.description)
        assertTrue(state.queue!!.stale)
        assertFalse(state.queue.loading)
        assertTrue(state.tokenUsageStale)
        assertTrue(state.goalStale)
        assertFalse(state.reduce(RuntimeStatusEvent.TokenUsageSynced).tokenUsageStale)
        assertTrue(state.reduce(RuntimeStatusEvent.TokenUsageSynced).goalStale)
    }

    @Test
    fun `回合结束只使该回合未收到终态的活动状态失效`() {
        val state = RuntimeStatus(
            records = listOf(
                RuntimeActivity("a", "Hook", "", RuntimeActivityStatus.RUNNING, turnId = "first"),
                RuntimeActivity("b", "Hook", "", RuntimeActivityStatus.RUNNING, turnId = "second"),
                RuntimeActivity("c", "审查", "", RuntimeActivityStatus.FAILED, turnId = "first"),
            ),
        ).reduce(RuntimeStatusEvent.TurnCompleted("first"))

        assertEquals(
            listOf(RuntimeActivityStatus.UNKNOWN, RuntimeActivityStatus.RUNNING, RuntimeActivityStatus.FAILED),
            state.records.map { it.status },
        )
    }

    @Test
    fun `汇总变更通知替换对应回合而非追加补丁文本`() {
        val state = RuntimeStatus(diffs = listOf(RuntimeDiff("first", "old"), RuntimeDiff("second", "other")))
            .reduce(RuntimeStatusEvent.DiffChanged(RuntimeDiff("first", "new")))

        assertEquals(2, state.diffs.size)
        assertEquals("new", state.diffs.single { it.turnId == "first" }.diff)
        assertEquals("other", state.diffs.single { it.turnId == "second" }.diff)
    }

    @Test
    fun `队列刷新期间保留原列表直到完整结果到达`() {
        val state = RuntimeStatus(queue = RuntimeInputQueue(listOf(RuntimeQueuedInput("first", "第一条"))))
            .reduce(RuntimeStatusEvent.QueueChanged)

        assertTrue(state.queue!!.loading)
        assertEquals("first", state.queue.entries.single().id)
        val loaded = state.reduce(RuntimeStatusEvent.QueueLoaded(RuntimeInputQueue()))
        assertTrue(loaded.queue!!.entries.isEmpty())
        assertFalse(loaded.queue.loading)
    }
}
