package com.hebo.codex.domain

import com.hebo.codex.data.remote.ConnectionException
import com.hebo.codex.data.remote.RpcException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QueueWithdrawalCoordinatorTest {
    @Test
    fun `切走重入共享占用并等待保存完成后恢复原会话草稿`() = runTest {
        val deleteResult = CompletableDeferred<Boolean>()
        val saveGate = CompletableDeferred<Unit>()
        val drafts = FakeDraftRepository().apply { beforeSave = { saveGate.await() } }
        val coordinator = QueueWithdrawalCoordinator(backgroundScope, drafts) { _, _, _ -> deleteResult.await() }
        val originalScreen = backgroundScope.launch { coordinator.state(KEY).collect() }
        val recovered = draft("撤回内容").copy(
            attachments = listOf(DraftAttachment("image", UserInputPart.UploadedImage("file-42", "high"))),
        )

        assertTrue(coordinator.withdraw(KEY, "entry", recovered))
        assertEquals("entry", coordinator.state(KEY).value.operationId)
        runCurrent()
        originalScreen.cancel()
        val reopenedState = coordinator.state(KEY)
        val reopenedDraft = async { coordinator.loadDraft(KEY) }
        runCurrent()

        assertSame(coordinator.state(KEY), reopenedState)
        assertEquals("entry", reopenedState.value.operationId)
        assertFalse(reopenedDraft.isCompleted)
        assertNull(reopenedState.value.restoredDraft)
        assertEquals(0, drafts.saveCalls)

        deleteResult.complete(true)
        runCurrent()
        assertEquals("entry", reopenedState.value.operationId)
        assertFalse(reopenedDraft.isCompleted)

        saveGate.complete(Unit)
        runCurrent()
        assertNull(reopenedState.value.operationId)
        assertEquals(recovered, reopenedDraft.await())
        assertEquals(recovered, drafts.load(KEY))
        assertEquals(recovered, reopenedState.value.restoredDraft)
        assertNull(reopenedState.value.feedback)
    }

    @Test
    fun `同一会话重复撤回只发送一次请求`() = runTest {
        val deleteResult = CompletableDeferred<Boolean>()
        val drafts = FakeDraftRepository()
        var requests = 0
        val coordinator = QueueWithdrawalCoordinator(backgroundScope, drafts) { _, _, _ ->
            requests++
            deleteResult.await()
        }

        assertTrue(coordinator.withdraw(KEY, "first", draft("第一条")))
        assertFalse(coordinator.withdraw(KEY, "second", draft("第二条")))
        runCurrent()
        assertEquals(1, requests)

        deleteResult.complete(true)
        runCurrent()
        assertEquals("第一条", coordinator.loadDraft(KEY)?.message)
        assertEquals(1, drafts.saveCalls)
    }

    @Test
    fun `已经开始执行的消息不会回填或保存草稿`() = runTest {
        val drafts = FakeDraftRepository()
        val coordinator = QueueWithdrawalCoordinator(backgroundScope, drafts) { _, _, _ -> false }

        coordinator.withdraw(KEY, "entry", draft("不应恢复"))
        runCurrent()

        assertNull(coordinator.state(KEY).value.operationId)
        assertNull(coordinator.state(KEY).value.restoredDraft)
        assertNull(coordinator.loadDraft(KEY))
        assertEquals(0, drafts.saveCalls)
        assertEquals("未撤回：消息已开始执行或已被其他客户端撤回", coordinator.state(KEY).value.feedback)
    }

    @Test
    fun `RPC 错误保留撤回结果未知语义且不回填`() = runTest {
        val drafts = FakeDraftRepository()
        val coordinator = QueueWithdrawalCoordinator(backgroundScope, drafts) { _, _, _ ->
            throw RpcException(-32001, "服务器繁忙", null)
        }

        coordinator.withdraw(KEY, "entry", draft("不应恢复"))
        runCurrent()

        val state = coordinator.state(KEY).value
        assertNull(state.operationId)
        assertNull(state.restoredDraft)
        assertEquals("撤回结果未确认，请刷新队列后再操作", state.error!!.summary)
        assertTrue(state.error.technicalDetails!!.contains("RPC -32001"))
        assertEquals(0, drafts.saveCalls)
    }

    @Test
    fun `保存失败仍向重入页面提供可恢复原文并明确已经撤回`() = runTest {
        val drafts = FakeDraftRepository().apply { saveError = ConnectionException("存储暂不可用") }
        val recovered = draft("必须保留的内容")
        val coordinator = QueueWithdrawalCoordinator(backgroundScope, drafts) { _, _, _ -> true }

        coordinator.withdraw(KEY, "entry", recovered)
        runCurrent()
        assertNull(coordinator.state(KEY).value.feedback)
        coordinator.clearFeedback(KEY)

        assertNull(drafts.load(KEY))
        assertEquals(recovered, coordinator.loadDraft(KEY))
        assertEquals(recovered, coordinator.state(KEY).value.restoredDraft)
        assertEquals("消息已撤回，保存草稿失败；内容已保留，请勿清除草稿", coordinator.state(KEY).value.error!!.summary)
        assertTrue(coordinator.state(KEY).value.error!!.technicalDetails!!.contains("存储暂不可用"))
    }

    @Test
    fun `共享仓库已有新草稿时不发出删除请求且不覆盖新内容`() = runTest {
        val drafts = FakeDraftRepository()
        val newer = draft("其他页面刚输入的新内容")
        drafts.schedule(newer)
        var requests = 0
        val coordinator = QueueWithdrawalCoordinator(backgroundScope, drafts) { _, _, _ -> requests++; true }

        coordinator.withdraw(KEY, "entry", draft("旧页面准备恢复的内容"))
        runCurrent()

        assertEquals(0, requests)
        assertEquals(newer, coordinator.loadDraft(KEY))
        assertNull(coordinator.state(KEY).value.restoredDraft)
        assertEquals("请先发送或清空输入框，再移回排队消息", coordinator.state(KEY).value.feedback)
    }

    @Test
    fun `恢复后的新编辑不会被协调器的旧快照覆盖`() = runTest {
        val drafts = FakeDraftRepository()
        val coordinator = QueueWithdrawalCoordinator(backgroundScope, drafts) { _, _, _ -> true }
        coordinator.withdraw(KEY, "entry", draft("撤回原文"))
        runCurrent()

        val edited = draft("后来编辑的内容", revision = "new-revision")
        drafts.schedule(edited)
        coordinator.draftChanged(KEY)

        assertNull(coordinator.state(KEY).value.restoredDraft)
        assertEquals(edited, coordinator.loadDraft(KEY))
    }

    @Test
    fun `仅撤销消息不修改现有输入框草稿`() = runTest {
        val drafts = FakeDraftRepository()
        val current = draft("尚未发送的草稿")
        drafts.schedule(current)
        val coordinator = QueueWithdrawalCoordinator(backgroundScope, drafts) { _, _, _ -> true }

        coordinator.withdraw(KEY, "entry", null)
        runCurrent()

        assertEquals(current, coordinator.loadDraft(KEY))
        assertEquals(0, drafts.saveCalls)
        assertEquals("消息已撤销", coordinator.state(KEY).value.feedback)
    }

    @Test
    fun `不同会话的撤回互不阻塞`() = runTest {
        val firstResult = CompletableDeferred<Boolean>()
        val drafts = FakeDraftRepository()
        val coordinator = QueueWithdrawalCoordinator(backgroundScope, drafts) { _, threadId, _ ->
            if (threadId == "thread") firstResult.await() else true
        }
        val second = draft("另一个会话", threadId = "second")

        coordinator.withdraw(KEY, "first-entry", draft("第一个会话"))
        coordinator.withdraw(second.key, "second-entry", second)
        runCurrent()

        assertEquals("first-entry", coordinator.state(KEY).value.operationId)
        assertNull(coordinator.state(second.key).value.operationId)
        assertEquals(second, coordinator.loadDraft(second.key))
        firstResult.complete(true)
        runCurrent()
    }

    private fun draft(message: String, revision: String = "revision", threadId: String = "thread") = ConversationDraft.Thread(
        serverId = "server",
        threadId = threadId,
        threadTitle = "会话",
        message = message,
        cwd = "/workspace",
        options = TurnOptions(),
        attachments = emptyList(),
        updatedAtMillis = 1L,
        revision = revision,
    )

    private class FakeDraftRepository : DraftRepository {
        private val drafts = mutableMapOf<DraftKey, ConversationDraft>()
        override val summaries: Flow<List<DraftSummary>> = MutableStateFlow(emptyList())
        override val failures: Flow<DraftFailure> = emptyFlow()
        var saveError: Throwable? = null
        var saveCalls = 0
        var beforeSave: suspend () -> Unit = {}

        override suspend fun load(key: DraftKey): ConversationDraft? = drafts[key]
        override fun schedule(draft: ConversationDraft) { drafts[draft.key] = draft }
        override fun flush(key: DraftKey) = Unit
        override suspend fun save(draft: ConversationDraft) {
            saveCalls++
            beforeSave()
            saveError?.let { throw it }
            drafts[draft.key] = draft
        }
        override suspend fun delete(key: DraftKey) { drafts.remove(key) }
        override suspend fun deleteServer(serverId: String) { drafts.keys.removeAll { it.serverId == serverId } }
        override suspend fun renameThread(serverId: String, threadId: String, title: String) = Unit
    }

    private companion object {
        val KEY = DraftKey("server", DraftTarget.Thread("thread"))
    }
}
