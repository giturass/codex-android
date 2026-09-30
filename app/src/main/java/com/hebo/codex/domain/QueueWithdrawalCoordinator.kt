package com.hebo.codex.domain

import com.hebo.codex.util.runSuspendCatching
import com.hebo.codex.util.toUserFacingError
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class QueueWithdrawalState(
    val operationId: String? = null,
    val restoredDraft: ConversationDraft.Thread? = null,
    val feedback: String? = null,
    val error: UserFacingError? = null,
)

/** 撤回和草稿恢复属于会话，页面关闭不取消事务，也不释放输入占用 */
class QueueWithdrawalCoordinator(
    private val applicationScope: CoroutineScope,
    private val draftRepository: DraftRepository,
    private val deleteQueuedInput: suspend (String, String, String) -> Boolean,
) {
    private val states = ConcurrentHashMap<DraftKey, MutableStateFlow<QueueWithdrawalState>>()

    fun state(key: DraftKey): StateFlow<QueueWithdrawalState> = mutableState(key)

    suspend fun loadDraft(key: DraftKey): ConversationDraft? {
        val state = mutableState(key)
        while (true) {
            val before = state.first { it.operationId == null }
            val draft = before.restoredDraft ?: draftRepository.load(key)
            if (state.value === before) return draft
        }
    }

    fun withdraw(key: DraftKey, entryId: String, draftToRestore: ConversationDraft.Thread?): Boolean {
        val target = key.target as? DraftTarget.Thread ?: error("只有会话支持撤回输入")
        require(draftToRestore == null || draftToRestore.key == key)
        val state = mutableState(key)
        while (true) {
            val current = state.value
            if (current.operationId != null) return false
            if (state.compareAndSet(current, current.copy(operationId = entryId, feedback = null, error = null))) break
        }
        applicationScope.launch {
            if (draftToRestore != null) {
                val existing = runSuspendCatching {
                    state.value.restoredDraft ?: draftRepository.load(key)
                }
                if (existing.isFailure) {
                    state.update {
                        it.copy(operationId = null, error = operationError("无法读取当前草稿，消息尚未撤回", existing.exceptionOrNull()!!))
                    }
                    return@launch
                }
                if (existing.getOrNull()?.hasContent() == true) {
                    state.update {
                        it.copy(operationId = null, feedback = "请先发送或清空输入框，再移回排队消息")
                    }
                    return@launch
                }
            }

            val deletion = runSuspendCatching { deleteQueuedInput(key.serverId, target.id, entryId) }
            if (deletion.isFailure) {
                state.update {
                    it.copy(
                        operationId = null,
                        error = operationError("撤回结果未确认，请刷新队列后再操作", deletion.exceptionOrNull()!!),
                    )
                }
                return@launch
            }
            if (!deletion.getOrThrow()) {
                state.update {
                    it.copy(operationId = null, feedback = "未撤回：消息已开始执行或已被其他客户端撤回")
                }
                return@launch
            }
            if (draftToRestore == null) {
                state.update { it.copy(operationId = null, feedback = "消息已撤销") }
                return@launch
            }

            val saved = runSuspendCatching { draftRepository.save(draftToRestore) }
            state.update {
                it.copy(
                    operationId = null,
                    restoredDraft = draftToRestore,
                    error = saved.exceptionOrNull()?.let { error ->
                        operationError("消息已撤回，保存草稿失败；内容已保留，请勿清除草稿", error)
                    },
                )
            }
        }
        return true
    }

    // 新编辑已交给草稿仓库后，恢复快照不再覆盖用户的后续改动
    fun draftChanged(key: DraftKey) {
        mutableState(key).update { it.copy(restoredDraft = null) }
    }

    fun clearFeedback(key: DraftKey) {
        mutableState(key).update { it.copy(feedback = null) }
    }

    fun clearError(key: DraftKey) {
        mutableState(key).update { it.copy(error = null) }
    }

    private fun mutableState(key: DraftKey): MutableStateFlow<QueueWithdrawalState> =
        states.computeIfAbsent(key) { MutableStateFlow(QueueWithdrawalState()) }

    private fun operationError(summary: String, error: Throwable): UserFacingError {
        val cause = error.toUserFacingError()
        return UserFacingError(summary, listOfNotNull(cause.summary, cause.technicalDetails).joinToString("\n"))
    }
}
