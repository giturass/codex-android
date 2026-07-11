package com.hebo.codex.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.hebo.codex.domain.CodexRepository
import com.hebo.codex.domain.ConversationDraft
import com.hebo.codex.domain.DraftKey
import com.hebo.codex.domain.DraftRepository
import com.hebo.codex.domain.DraftTarget
import com.hebo.codex.domain.ModelOption
import com.hebo.codex.domain.ThreadOptions
import com.hebo.codex.domain.TurnOptions
import com.hebo.codex.domain.UserFacingError
import com.hebo.codex.domain.newDraftRevision
import com.hebo.codex.util.runSuspendCatching
import com.hebo.codex.util.toUserFacingError
import java.util.UUID
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NewTaskUiState(
    val serverId: String? = null,
    val message: String = "",
    val cwd: String = "",
    val options: ThreadOptions = ThreadOptions(),
    val isLoadingDraft: Boolean = true,
    val isCreatingConversation: Boolean = false,
    val errorMessage: UserFacingError? = null,
)

sealed interface NewTaskEvent {
    data class OpenConversation(val serverId: String, val threadId: String) : NewTaskEvent
}

class NewTaskViewModel(
    private val repository: CodexRepository,
    private val draftRepository: DraftRepository,
    private val routeServerId: String?,
    private val draftId: String,
    private val initialCwd: String?,
) : ViewModel() {
    private val mutableState = MutableStateFlow(NewTaskUiState(cwd = initialCwd.orEmpty()))
    private val mutableEvents = Channel<NewTaskEvent>(Channel.BUFFERED)
    private var createdThreadId: String? = null

    val state: StateFlow<NewTaskUiState> = mutableState
    val events = mutableEvents.receiveAsFlow()

    init {
        viewModelScope.launch {
            val serverId = routeServerId ?: repository.activeServerId.filterNotNull().first()
            val key = DraftKey(serverId, DraftTarget.NewTask(draftId))
            runSuspendCatching { draftRepository.load(key) }
                .onSuccess { restored ->
                    require(restored == null || restored is ConversationDraft.NewTask) { "草稿类型不匹配" }
                    mutableState.update {
                        if (restored is ConversationDraft.NewTask) {
                            it.copy(
                                serverId = serverId,
                                message = restored.message,
                                cwd = restored.cwd,
                                options = restored.options,
                                isLoadingDraft = false,
                            )
                        } else {
                            it.copy(serverId = serverId, isLoadingDraft = false)
                        }
                    }
                }
                .onFailure { error ->
                    mutableState.update {
                        it.copy(
                            serverId = serverId,
                            isLoadingDraft = false,
                            errorMessage = error.toUserFacingError("无法恢复草稿"),
                        )
                    }
                }
        }
        viewModelScope.launch {
            draftRepository.failures.collect { failure ->
                if (failure.key == currentKey()) {
                    mutableState.update {
                        it.copy(errorMessage = failure.error.toUserFacingError("保存草稿失败"))
                    }
                }
            }
        }
    }

    fun updateMessage(message: String) {
        updateEditor { it.copy(message = message) }
    }

    fun updateCwd(cwd: String) {
        updateEditor { it.copy(cwd = cwd) }
    }

    fun updateOptions(options: ThreadOptions) {
        updateEditor { it.copy(options = options) }
    }

    fun applyDefaultModel(model: ModelOption) {
        val current = mutableState.value
        if (current.isLoadingDraft || current.options.model != null) return
        updateOptions(
            current.options.copy(
                model = model.model,
                serviceTier = model.defaultServiceTier,
            ),
        )
    }

    fun startConversation() {
        val current = mutableState.value
        if (current.isCreatingConversation || current.isLoadingDraft) return
        val serverId = current.serverId ?: return
        if (current.message.isBlank()) {
            showError(IllegalArgumentException("请输入任务内容"))
            return
        }
        if (current.cwd.isBlank()) {
            showError(IllegalArgumentException("请选择项目"))
            return
        }

        viewModelScope.launch {
            mutableState.update { it.copy(isCreatingConversation = true, errorMessage = null) }
            val threadId = createdThreadId ?: runSuspendCatching {
                repository.startConversation(serverId, current.cwd, current.options)
            }.onFailure(::showError).getOrNull()
            if (threadId == null) {
                mutableState.update { it.copy(isCreatingConversation = false) }
                return@launch
            }
            createdThreadId = threadId

            runSuspendCatching { repository.sendMessage(serverId, threadId, current.message) }
                .onSuccess {
                    runSuspendCatching { draftRepository.delete(currentDraft(current).key) }
                        .onFailure { error ->
                            mutableState.update {
                                it.copy(errorMessage = error.toUserFacingError("清理草稿失败"))
                            }
                        }
                    createdThreadId = null
                    mutableState.update { it.copy(message = "", isCreatingConversation = false) }
                    mutableEvents.send(NewTaskEvent.OpenConversation(serverId, threadId))
                }
                .onFailure { sendError ->
                    val threadDraft = ConversationDraft.Thread(
                        serverId = serverId,
                        threadId = threadId,
                        threadTitle = current.message.lineSequence()
                            .map(String::trim)
                            .firstOrNull(String::isNotEmpty)
                            ?.take(72)
                            ?: "新会话",
                        message = current.message,
                        cwd = current.cwd,
                        options = current.options.toTurnOptions(),
                        attachments = emptyList(),
                        updatedAtMillis = System.currentTimeMillis(),
                        revision = newDraftRevision(),
                    )
                    runSuspendCatching {
                        draftRepository.save(threadDraft)
                        draftRepository.delete(currentDraft(current).key)
                    }.onSuccess {
                        createdThreadId = null
                        mutableState.update { it.copy(message = "", isCreatingConversation = false) }
                        mutableEvents.send(NewTaskEvent.OpenConversation(serverId, threadId))
                    }.onFailure { draftError ->
                        showError(
                            IllegalStateException(
                                "首条消息发送失败，且无法迁移草稿：${sendError.message.orEmpty()}",
                                draftError,
                            ),
                        )
                    }
                }
            mutableState.update { it.copy(isCreatingConversation = false) }
        }
    }

    fun flushDraft() {
        currentKey()?.let(draftRepository::flush)
    }

    fun clearError() {
        mutableState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        flushDraft()
    }

    private fun updateEditor(transform: (NewTaskUiState) -> NewTaskUiState) {
        mutableState.update(transform)
        val current = mutableState.value
        if (!current.isLoadingDraft && current.serverId != null) {
            draftRepository.schedule(currentDraft(current))
        }
    }

    private fun currentDraft(state: NewTaskUiState): ConversationDraft.NewTask = ConversationDraft.NewTask(
        serverId = requireNotNull(state.serverId),
        draftId = draftId,
        message = state.message,
        cwd = state.cwd,
        options = state.options,
        updatedAtMillis = System.currentTimeMillis(),
        revision = newDraftRevision(),
    )

    private fun currentKey(): DraftKey? = mutableState.value.serverId?.let { serverId ->
        DraftKey(serverId, DraftTarget.NewTask(draftId))
    }

    private fun showError(error: Throwable) {
        mutableState.update { it.copy(errorMessage = error.toUserFacingError()) }
    }

    companion object {
        fun newDraftId(): String = UUID.randomUUID().toString()

        fun factory(
            repository: CodexRepository,
            draftRepository: DraftRepository,
            serverId: String?,
            draftId: String,
            initialCwd: String?,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                NewTaskViewModel(repository, draftRepository, serverId, draftId, initialCwd)
            }
        }
    }
}

private fun ThreadOptions.toTurnOptions(): TurnOptions = TurnOptions(
    model = model,
    serviceTier = serviceTier,
    personality = personality,
    approvalPolicy = approvalPolicy,
    approvalsReviewer = approvalsReviewer,
    sandboxMode = sandboxMode,
)
