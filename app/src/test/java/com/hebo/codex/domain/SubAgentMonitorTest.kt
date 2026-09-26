package com.hebo.codex.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SubAgentMonitorTest {
    @Test
    fun `快照读取期间的状态通知会重放到旧快照之上`() = runTest {
        val source = ControlledSource()
        val monitor = monitor(source)
        val child = thread("child")
        monitor.setConnected(true)
        runCurrent()
        source.snapshots.single().response.complete(listOf(child))
        runCurrent()

        monitor.refresh()
        runCurrent()
        monitor.accept(SubAgentEvent.StatusChanged(
            "child", ThreadRuntimeStatus.ACTIVE, setOf(ThreadActiveFlag.WAITING_APPROVAL),
        ))
        runCurrent()
        assertEquals(SubAgentRuntimeStatus.WAITING_APPROVAL, monitor.agentStatus("child"))

        source.snapshots.last().response.complete(listOf(child))
        runCurrent()

        assertEquals(SubAgentRuntimeStatus.WAITING_APPROVAL, monitor.agentStatus("child"))
    }

    @Test
    fun `首次快照之前收到的未知代理通知也会被重放`() = runTest {
        val source = ControlledSource()
        val monitor = monitor(source)
        monitor.setConnected(true)
        runCurrent()

        monitor.accept(SubAgentEvent.StatusChanged(
            "child", ThreadRuntimeStatus.ACTIVE, setOf(ThreadActiveFlag.WAITING_INPUT),
        ))
        runCurrent()
        source.snapshots.first().response.complete(listOf(thread("child")))
        runCurrent()

        assertEquals(SubAgentRuntimeStatus.WAITING_INPUT, monitor.agentStatus("child"))
        assertEquals(2, source.snapshots.size)
    }

    @Test
    fun `已返回但尚未应用的旧回合查询不会覆盖重新运行的代理`() = runTest {
        val source = ControlledSource()
        val monitor = monitor(source)
        monitor.setConnected(true)
        runCurrent()
        source.snapshots.single().response.complete(listOf(thread("child", status = ThreadRuntimeStatus.IDLE)))
        runCurrent()
        assertEquals("child", source.turns.single().first)

        // 先让查询恢复执行，再排入状态通知，使旧查询结果在新状态之后进入邮箱
        source.turns.single().second.response.complete(ConversationTurnStatus.COMPLETED)
        monitor.accept(SubAgentEvent.StatusChanged("child", ThreadRuntimeStatus.ACTIVE, emptySet()))
        runCurrent()

        assertEquals(SubAgentRuntimeStatus.RUNNING, monitor.agentStatus("child"))
        assertFalse(source.turns.single().second.cancelled)
        assertNull(monitor.state.value.error)
    }

    @Test
    fun `关闭标记未加载而删除移除代理且快照不能恢复已删除项`() = runTest {
        val source = ControlledSource()
        val monitor = monitor(source)
        val child = thread("child")
        monitor.setConnected(true)
        runCurrent()
        source.snapshots.single().response.complete(listOf(child))
        runCurrent()

        monitor.accept(SubAgentEvent.Removed("child", deleted = false))
        runCurrent()
        assertEquals(SubAgentRuntimeStatus.NOT_LOADED, monitor.agentStatus("child"))

        monitor.refresh()
        runCurrent()
        monitor.accept(SubAgentEvent.Removed("child", deleted = true))
        runCurrent()
        assertTrue(monitor.state.value.agents.isEmpty())

        source.snapshots.last().response.complete(listOf(child))
        runCurrent()
        assertTrue(monitor.state.value.agents.isEmpty())
    }

    @Test
    fun `删除父会话清空代理并阻止后续查询和通知重新填充`() = runTest {
        val source = ControlledSource()
        val monitor = monitor(source)
        monitor.setConnected(true)
        runCurrent()
        source.snapshots.single().response.complete(listOf(thread("child")))
        runCurrent()
        monitor.refresh()
        runCurrent()

        monitor.accept(SubAgentEvent.Removed("parent", deleted = true))
        runCurrent()
        assertTrue(source.snapshots.last().cancelled)
        assertTrue(monitor.state.value.agents.isEmpty())

        monitor.accept(SubAgentEvent.Started(thread("late-child")))
        monitor.refresh()
        monitor.setConnected(true)
        runCurrent()

        assertTrue(monitor.state.value.agents.isEmpty())
        assertEquals(2, source.snapshots.size)
    }

    @Test
    fun `重连保留旧快照直到新快照返回并取消旧连接查询`() = runTest {
        val source = ControlledSource()
        val monitor = monitor(source)
        monitor.setConnected(true)
        runCurrent()
        source.snapshots.single().response.complete(listOf(thread("old-child")))
        runCurrent()
        monitor.refresh()
        runCurrent()
        val oldRequest = source.snapshots.last()

        monitor.setConnected(false)
        runCurrent()
        assertTrue(oldRequest.cancelled)
        assertTrue(monitor.state.value.isStale)
        assertEquals(listOf("old-child"), monitor.state.value.agents.map { it.threadId })

        monitor.setConnected(true)
        runCurrent()
        assertEquals(listOf("old-child"), monitor.state.value.agents.map { it.threadId })
        assertTrue(monitor.state.value.isStale)
        oldRequest.response.complete(listOf(thread("obsolete-child")))
        source.snapshots.last().response.complete(listOf(thread("new-child")))
        runCurrent()

        assertEquals(listOf("new-child"), monitor.state.value.agents.map { it.threadId })
        assertFalse(monitor.state.value.isStale)
        assertNull(monitor.state.value.error)
    }

    @Test
    fun `后代代理启动后立即显示并通过发现请求补全树`() = runTest {
        val source = ControlledSource()
        val monitor = monitor(source)
        val child = thread("child")
        val grandchild = thread("grandchild", parent = "child")
        monitor.setConnected(true)
        runCurrent()
        source.snapshots.single().response.complete(listOf(child))
        runCurrent()

        monitor.accept(SubAgentEvent.Started(grandchild))
        runCurrent()
        assertEquals(listOf("child", "grandchild"), monitor.state.value.agents.map { it.threadId })
        assertEquals(2, source.snapshots.size)
        source.snapshots.last().response.complete(listOf(child, grandchild))
        runCurrent()

        monitor.accept(SubAgentEvent.Discover("grandchild"))
        runCurrent()
        source.snapshots.last().response.complete(listOf(child, grandchild, thread("nested", parent = "grandchild")))
        runCurrent()

        assertEquals(listOf("child", "grandchild", "nested"), monitor.state.value.agents.map { it.threadId })
    }

    @Test
    fun `快照同步失败保留已有状态并可重试清除错误`() = runTest {
        val source = ControlledSource()
        val monitor = monitor(source)
        monitor.setConnected(true)
        runCurrent()
        source.snapshots.single().response.complete(listOf(thread("child")))
        runCurrent()

        monitor.refresh()
        runCurrent()
        source.snapshots.last().response.completeExceptionally(IllegalStateException("snapshot failure"))
        runCurrent()
        assertNotNull(monitor.state.value.error)
        assertEquals(SubAgentRuntimeStatus.RUNNING, monitor.agentStatus("child"))

        monitor.refresh()
        runCurrent()
        source.snapshots.last().response.complete(emptyList())
        runCurrent()

        assertNull(monitor.state.value.error)
        assertTrue(monitor.state.value.agents.isEmpty())
    }

    @Test
    fun `空闲代理的回合读取失败可重试并正确判定已完成`() = runTest {
        val source = ControlledSource()
        val monitor = monitor(source)
        val idleChild = thread("child", status = ThreadRuntimeStatus.IDLE)
        monitor.setConnected(true)
        runCurrent()
        source.snapshots.single().response.complete(listOf(idleChild))
        runCurrent()
        source.turns.single().second.response.completeExceptionally(IllegalStateException("turn failure"))
        runCurrent()
        assertNotNull(monitor.state.value.error)
        assertEquals(SubAgentRuntimeStatus.IDLE, monitor.agentStatus("child"))

        monitor.refresh()
        runCurrent()
        source.snapshots.last().response.complete(listOf(idleChild))
        runCurrent()
        source.turns.last().second.response.complete(ConversationTurnStatus.COMPLETED)
        runCurrent()

        assertEquals(SubAgentRuntimeStatus.COMPLETED, monitor.agentStatus("child"))
        assertNull(monitor.state.value.error)
    }

    @Test
    fun `重复空闲通知沿用已完成回合状态直到新查询返回`() = runTest {
        val source = ControlledSource()
        val monitor = monitor(source)
        monitor.setConnected(true)
        runCurrent()
        source.snapshots.single().response.complete(listOf(thread("child", status = ThreadRuntimeStatus.IDLE)))
        runCurrent()
        source.turns.single().second.response.complete(ConversationTurnStatus.COMPLETED)
        runCurrent()

        monitor.accept(SubAgentEvent.StatusChanged("child", ThreadRuntimeStatus.IDLE, emptySet()))
        runCurrent()

        assertEquals(SubAgentRuntimeStatus.COMPLETED, monitor.agentStatus("child"))
        source.turns.last().second.response.complete(ConversationTurnStatus.INTERRUPTED)
        runCurrent()
        assertEquals(SubAgentRuntimeStatus.INTERRUPTED, monitor.agentStatus("child"))

        monitor.accept(SubAgentEvent.StatusChanged("child", ThreadRuntimeStatus.ACTIVE, emptySet()))
        monitor.accept(SubAgentEvent.StatusChanged("child", ThreadRuntimeStatus.IDLE, emptySet()))
        runCurrent()
        assertEquals(SubAgentRuntimeStatus.IDLE, monitor.agentStatus("child"))
    }

    @Test
    fun `临时代理跨正常刷新保留但断连后不能保留过时运行状态`() = runTest {
        val source = ControlledSource()
        val monitor = monitor(source)
        monitor.setConnected(true)
        runCurrent()
        source.snapshots.single().response.complete(listOf(thread("persistent-child")))
        runCurrent()

        monitor.accept(SubAgentEvent.Started(thread("ephemeral-child").copy(ephemeral = true)))
        runCurrent()
        source.snapshots.last().response.complete(listOf(thread("persistent-child")))
        runCurrent()
        assertEquals(SubAgentRuntimeStatus.RUNNING, monitor.agentStatus("ephemeral-child"))

        monitor.setConnected(false)
        runCurrent()
        assertEquals(listOf("persistent-child"), monitor.state.value.agents.map { it.threadId })

        monitor.setConnected(true)
        runCurrent()
        source.snapshots.last().response.complete(listOf(thread("persistent-child")))
        runCurrent()
        assertEquals(listOf("persistent-child"), monitor.state.value.agents.map { it.threadId })
    }

    @Test
    fun `临时代理空闲时不请求协议不支持的持久回合列表`() = runTest {
        val source = ControlledSource()
        val monitor = monitor(source)
        monitor.setConnected(true)
        runCurrent()
        source.snapshots.single().response.complete(emptyList())
        runCurrent()

        monitor.accept(SubAgentEvent.Started(thread("ephemeral-child", status = ThreadRuntimeStatus.IDLE).copy(ephemeral = true)))
        runCurrent()
        source.snapshots.last().response.complete(emptyList())
        runCurrent()

        assertEquals(SubAgentRuntimeStatus.IDLE, monitor.agentStatus("ephemeral-child"))
        assertTrue(source.turns.isEmpty())
        assertNull(monitor.state.value.error)
    }

    @Test
    fun `关闭监视器取消在途请求且不再接受事件`() = runTest {
        val source = ControlledSource()
        val monitor = monitor(source)
        monitor.setConnected(true)
        runCurrent()

        monitor.close()
        runCurrent()
        monitor.accept(SubAgentEvent.Started(thread("late-child")))
        monitor.refresh()
        runCurrent()

        assertTrue(source.snapshots.single().cancelled)
        assertTrue(monitor.state.value.agents.isEmpty())
    }

    private fun TestScope.monitor(source: SubAgentSource) = SubAgentMonitor(backgroundScope, "parent", source)

    private fun SubAgentMonitor.agentStatus(id: String): SubAgentRuntimeStatus =
        state.value.agents.single { it.threadId == id }.status

    private fun thread(
        id: String,
        parent: String = "parent",
        status: ThreadRuntimeStatus = ThreadRuntimeStatus.ACTIVE,
    ) = ThreadSummary(
        id = id,
        sessionId = "session",
        title = id,
        preview = "",
        cwd = "/workspace",
        createdAt = 0L,
        updatedAt = 0L,
        recencyAt = null,
        status = status,
        historyMode = ThreadHistoryMode.PAGINATED,
        parentThreadId = parent,
    )

    private class ControlledSource : SubAgentSource {
        val snapshots = mutableListOf<Request<List<ThreadSummary>>>()
        val turns = mutableListOf<Pair<String, Request<ConversationTurnStatus?>>>()

        override suspend fun listDescendants(): List<ThreadSummary> =
            Request<List<ThreadSummary>>().also(snapshots::add).await()

        override suspend fun latestTurnStatus(threadId: String): ConversationTurnStatus? =
            Request<ConversationTurnStatus?>().also { turns += threadId to it }.await()
    }

    private class Request<T> {
        val response = CompletableDeferred<T>()
        var cancelled = false

        suspend fun await(): T = try {
            response.await()
        } catch (error: CancellationException) {
            cancelled = true
            throw error
        }
    }
}
