package com.hebo.codex.domain

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationTurnDetailsTest {
    @Test
    fun `详情请求完成前保留摘要且重复请求只读取一次`() = runTest {
        val state = state()
        val result = CompletableDeferred<List<ConversationItem>>()
        var reads = 0
        val job = launch {
            state.loadTurnDetails("turn") { reads++; result.await() }
        }
        runCurrent()
        assertTrue(state.turn.detailsState is TurnDetailsState.Loading)
        assertEquals(listOf(answer), state.turn.items)
        state.loadTurnDetails("turn") { reads++; emptyList() }
        result.complete(listOf(command, answer))
        job.join()
        state.loadTurnDetails("turn") { reads++; emptyList() }

        assertEquals(1, reads)
        assertEquals(listOf(command, answer), state.turn.items)
        assertEquals(TurnDetailsState.Loaded, state.turn.detailsState)
    }

    @Test
    fun `加载失败保留摘要并允许重试`() = runTest {
        val state = state()
        state.loadTurnDetails("turn") { error("读取失败") }
        assertTrue(state.turn.detailsState is TurnDetailsState.Failed)
        assertEquals(listOf(answer), state.turn.items)

        state.loadTurnDetails("turn") { listOf(command, answer) }
        assertEquals(TurnDetailsState.Loaded, state.turn.detailsState)
        assertEquals(listOf(command, answer), state.turn.items)
    }

    @Test
    fun `取消后恢复未加载状态并允许再次读取`() = runTest {
        val state = state()
        val result = CompletableDeferred<List<ConversationItem>>()
        val job = launch { state.loadTurnDetails("turn") { result.await() } }
        runCurrent()
        job.cancel()
        job.join()
        assertEquals(TurnDetailsState.Unloaded, state.turn.detailsState)

        state.loadTurnDetails("turn") { emptyList() }
        assertEquals(TurnDetailsState.Loaded, state.turn.detailsState)
        assertTrue(state.turn.items.isEmpty())
    }

    @Test
    fun `详情快照不会覆盖请求期间收到的条目更新`() = runTest {
        val state = state()
        val result = CompletableDeferred<List<ConversationItem>>()
        val job = launch { state.loadTurnDetails("turn") { result.await() } }
        runCurrent()
        val updated = answer.copy(text = "更新后的回答")
        state.value = state.value.withConversationItem(updated, isActive = false)
        result.complete(listOf(command, answer))
        job.join()

        assertEquals(listOf(command, updated), state.turn.items)
    }

    @Test
    fun `摘要刷新保留已经加载的历史详情`() {
        val summary = state().value
        val loaded = summary.copy(history = summary.history.copy(turns = listOf(
            summary.history.turns.single().copy(
                items = listOf(command, answer), detailsState = TurnDetailsState.Loaded,
            ),
        )))

        val restored = loaded.withRestoredConversation(summary)
        assertEquals(loaded.history, restored.history)
    }

    @Test
    fun `摘要内容变化使旧缓存失效`() {
        val summary = state().value
        val loaded = summary.copy(history = summary.history.copy(turns = listOf(
            summary.history.turns.single().copy(
                items = listOf(command, answer), detailsState = TurnDetailsState.Loaded,
            ),
        )))
        val changed = summary.withConversationItem(answer.copy(text = "修改后的摘要"), isActive = false)

        assertEquals(changed.history, loaded.withRestoredConversation(changed).history)
    }

    @Test
    fun `摘要回退到已有消息或变空时也使旧缓存失效`() {
        val summary = state().value
        val latestAnswer = answer.copy(id = "latest", text = "后续回答")
        val loaded = summary.copy(history = summary.history.copy(turns = listOf(
            summary.history.turns.single().copy(
                items = listOf(command, answer, latestAnswer), detailsState = TurnDetailsState.Loaded,
            ),
        )))
        val emptySummary = summary.copy(history = summary.history.copy(turns = listOf(
            summary.history.turns.single().copy(items = emptyList()),
        )))

        assertEquals(summary.history, loaded.withRestoredConversation(summary).history)
        assertEquals(emptySummary.history, loaded.withRestoredConversation(emptySummary).history)
    }

    @Test
    fun `请求期间摘要刷新不会重置加载状态`() = runTest {
        val state = state()
        val summary = state.value
        val result = CompletableDeferred<List<ConversationItem>>()
        val job = launch { state.loadTurnDetails("turn") { result.await() } }
        runCurrent()
        state.value = state.value.withRestoredConversation(summary)
        assertTrue(state.turn.detailsState is TurnDetailsState.Loading)
        result.complete(listOf(command, answer))
        job.join()
        assertEquals(TurnDetailsState.Loaded, state.turn.detailsState)
    }

    @Test
    fun `刷新使摘要失效后丢弃旧详情响应`() = runTest {
        val state = state()
        val summary = state.value
        val result = CompletableDeferred<List<ConversationItem>>()
        val job = launch { state.loadTurnDetails("turn") { result.await() } }
        runCurrent()
        val changed = summary.withConversationItem(answer.copy(text = "修改后的摘要"), isActive = false)
        state.value = state.value.withRestoredConversation(changed)
        result.complete(listOf(command, answer))
        job.join()
        assertEquals(changed.history, state.value.history)
    }

    @Test
    fun `旧请求完成不会覆盖摘要刷新后的新请求`() = runTest {
        val state = state()
        val summary = state.value
        val oldResult = CompletableDeferred<List<ConversationItem>>()
        val oldJob = launch { state.loadTurnDetails("turn") { oldResult.await() } }
        runCurrent()
        val updatedAnswer = answer.copy(text = "更新后的回答")
        state.value = state.value.withRestoredConversation(
            summary.withConversationItem(updatedAnswer, isActive = false),
        )
        val newResult = CompletableDeferred<List<ConversationItem>>()
        val newJob = launch { state.loadTurnDetails("turn") { newResult.await() } }
        runCurrent()
        oldResult.complete(listOf(command, answer))
        oldJob.join()
        assertTrue(state.turn.detailsState is TurnDetailsState.Loading)
        assertEquals(listOf(updatedAnswer), state.turn.items)

        newResult.complete(listOf(command, updatedAnswer))
        newJob.join()
        assertEquals(listOf(command, updatedAnswer), state.turn.items)
        assertEquals(TurnDetailsState.Loaded, state.turn.detailsState)
    }

    @Test
    fun `断线期间结束的回合不能沿用未完成的本地详情`() {
        val summary = state().value
        val running = summary.copy(history = summary.history.copy(turns = listOf(
            summary.history.turns.single().copy(
                status = ConversationTurnStatus.IN_PROGRESS,
                items = listOf(command, answer), detailsState = TurnDetailsState.Loaded,
            ),
        )))

        assertEquals(summary.history, running.withRestoredConversation(summary).history)
    }

    @Test
    fun `回合完成通知不会把摘要标记为完整详情`() {
        val state = state().value
        val updated = state.reduceConversationEvent(
            ConversationEvent.TurnCompleted("turn", ConversationTurnStatus.COMPLETED, null),
        )
        assertEquals(TurnDetailsState.Unloaded, updated.history.turns.single().detailsState)
    }

    private fun state() = MutableStateFlow(ConversationState(
        serverId = "server", serverName = "测试", threadId = "thread", title = "测试", cwd = "/project",
        history = ConversationHistory(turns = listOf(ConversationTurn(
            id = "turn", status = ConversationTurnStatus.COMPLETED,
            items = listOf(answer), detailsState = TurnDetailsState.Unloaded,
        ))),
    ))

    private val MutableStateFlow<ConversationState>.turn get() = value.history.turns.single()

    private val answer = ConversationItem.AgentMessage("answer", "turn", "回答")
    private val command = ConversationItem.CommandExecution(
        id = "command", turnId = "turn", command = "pwd", cwd = "/project", output = "/project",
        exitCode = 0, status = ActivityStatus.COMPLETED,
    )
}
