package com.hebo.codex.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.hebo.codex.domain.CodexRepository
import com.hebo.codex.domain.ConversationDraft
import com.hebo.codex.domain.DraftAttachment
import com.hebo.codex.domain.DraftRepository
import com.hebo.codex.domain.FeatureEntry
import com.hebo.codex.domain.ModelOption
import com.hebo.codex.domain.ThreadOptions
import com.hebo.codex.domain.TurnOptions
import com.hebo.codex.domain.UserInputPart
import com.hebo.codex.domain.UserFacingError
import com.hebo.codex.domain.newDraftRevision
import com.hebo.codex.domain.quickPhraseInput
import com.hebo.codex.domain.loadInputResources
import com.hebo.codex.ui.screens.chat.isShellCommandInput
import com.hebo.codex.ui.screens.chat.shellCommandBlockReason
import com.hebo.codex.ui.screens.chat.shellCommandFromInput
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
    val attachments: List<DraftAttachment> = emptyList(),
    val inputResources: List<FeatureEntry> = emptyList(),
    val isLoadingResources: Boolean = false,
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
    initialCwd: String?,
) : ViewModel() {
    private val mutableState = MutableStateFlow(NewTaskUiState(cwd = initialCwd.orEmpty()))
    private val mutableEvents = Channel<NewTaskEvent>(Channel.BUFFERED)
    private var createdThreadId: String? = null

    val state: StateFlow<NewTaskUiState> = mutableState
    val events = mutableEvents.receiveAsFlow()

    init {
        viewModelScope.launch {
            val serverId = routeServerId ?: repository.activeServerId.filterNotNull().first()
            mutableState.update { it.copy(serverId = serverId) }
        }
    }

    fun updateMessage(message: String) {
        updateEditor { it.copy(message = message) }
    }

    fun updateCwd(cwd: String) {
        updateEditor { it.copy(cwd = cwd, inputResources = emptyList()) }
    }

    fun updateOptions(options: ThreadOptions) {
        updateEditor { it.copy(options = options) }
    }

    fun addAttachment(input: UserInputPart, previewUri: String? = null) {
        updateEditor { state ->
            state.copy(
                attachments = state.attachments + DraftAttachment(
                    id = UUID.randomUUID().toString(),
                    input = input,
                    previewUri = previewUri,
                ),
            )
        }
    }

    fun removeAttachment(id: String) {
        updateEditor { state ->
            state.copy(attachments = state.attachments.filterNot { it.id == id })
        }
    }

    fun reportInputError(error: Throwable) {
        mutableState.update { it.copy(errorMessage = error.toUserFacingError("无法添加输入")) }
    }

    suspend fun readRemoteFilePreview(path: String): ByteArray {
        val serverId = requireNotNull(mutableState.value.serverId) { "请先选择服务器" }
        return repository.readRemoteFilePreview(serverId, path)
    }

    fun loadInputResources(forceRefresh: Boolean = false) {
        val current = mutableState.value
        val serverId = current.serverId ?: return
        if (current.isLoadingResources) return
        viewModelScope.launch {
            mutableState.update { it.copy(isLoadingResources = true) }
            val cwd = current.cwd.takeIf(String::isNotBlank)
            val result = loadInputResources(
                repository = repository,
                serverId = serverId,
                cwd = cwd,
                forceRefresh = forceRefresh,
            )
            mutableState.update {
                it.copy(
                    inputResources = result.entries,
                    isLoadingResources = false,
                    errorMessage = result.failure?.toUserFacingError("无法加载输入资源"),
                )
            }
        }
    }

    fun applyDefaultModel(model: ModelOption) {
        val current = mutableState.value
        if (current.options.model != null) return
        updateOptions(
            current.options.copy(
                model = model.model,
                serviceTier = model.defaultServiceTier,
            ),
        )
    }

    fun startConversation() {
        val current = mutableState.value
        if (current.isCreatingConversation) return
        if (isShellCommandInput(current.message)) {
            val command = shellCommandFromInput(current.message)
            val validationError = shellCommandBlockReason(
                message = current.message,
                attachmentCount = current.attachments.size,
                isRunning = false,
            ) ?: if (command == null) "Shell 命令不能为空" else null
            if (validationError != null) {
                showError(IllegalArgumentException(validationError))
                return
            }
            submitConversation(current, clearsEditor = true) { serverId, threadId ->
                repository.runShellCommand(serverId, threadId, requireNotNull(command))
            }
            return
        }
        val input = buildList {
            current.message.takeIf(String::isNotBlank)?.let { add(UserInputPart.Text(it)) }
            addAll(current.attachments.map(DraftAttachment::input))
        }
        if (input.isEmpty()) {
            showError(IllegalArgumentException("请输入任务内容"))
            return
        }
        submitConversation(current, clearsEditor = true) { serverId, threadId ->
            repository.sendMessage(serverId, threadId, input, current.options.toTurnOptions())
        }
    }

    fun sendQuickPhrase(content: String) {
        val current = mutableState.value
        if (current.isCreatingConversation) return
        val input = quickPhraseInput(content)
        submitConversation(current, clearsEditor = false) { serverId, threadId ->
            repository.sendMessage(serverId, threadId, input, current.options.toTurnOptions())
        }
    }

    private fun submitConversation(
        submission: NewTaskUiState,
        clearsEditor: Boolean,
        submit: suspend (serverId: String, threadId: String) -> Unit,
    ) {
        val serverId = submission.serverId ?: return
        if (submission.cwd.isBlank()) {
            showError(IllegalArgumentException("请选择项目"))
            return
        }

        mutableState.update { it.copy(isCreatingConversation = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val threadId = createdThreadId ?: runSuspendCatching {
                    repository.startConversation(serverId, submission.cwd, submission.options)
                }.onFailure(::showError).getOrNull() ?: return@launch
                createdThreadId = threadId

                runSuspendCatching {
                    submit(serverId, threadId)
                }
                    .onSuccess {
                        createdThreadId = null
                        if (clearsEditor) {
                            mutableState.update {
                                it.copy(message = "", attachments = emptyList(), isCreatingConversation = false)
                            }
                        }
                        mutableEvents.send(NewTaskEvent.OpenConversation(serverId, threadId))
                    }
                    .onFailure { sendError ->
                        if (clearsEditor) {
                            migrateDraftToConversation(
                                submission = submission,
                                threadId = threadId,
                                sendError = sendError,
                            )
                        } else {
                            showError(sendError)
                        }
                    }
            } finally {
                mutableState.update { it.copy(isCreatingConversation = false) }
            }
        }
    }

    private suspend fun migrateDraftToConversation(
        submission: NewTaskUiState,
        threadId: String,
        sendError: Throwable,
    ) {
        val serverId = requireNotNull(submission.serverId)
        val threadDraft = ConversationDraft.Thread(
            serverId = serverId,
            threadId = threadId,
            threadTitle = submission.conversationTitle(),
            message = submission.message,
            cwd = submission.cwd,
            options = submission.options.toTurnOptions(),
            attachments = submission.attachments,
            updatedAtMillis = System.currentTimeMillis(),
            revision = newDraftRevision(),
        )
        runSuspendCatching {
            draftRepository.save(threadDraft)
        }.onSuccess {
            createdThreadId = null
            mutableState.update {
                it.copy(message = "", attachments = emptyList(), isCreatingConversation = false)
            }
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

    fun clearError() {
        mutableState.update { it.copy(errorMessage = null) }
    }

    private fun updateEditor(transform: (NewTaskUiState) -> NewTaskUiState) {
        mutableState.update(transform)
    }

    private fun showError(error: Throwable) {
        mutableState.update { it.copy(errorMessage = error.toUserFacingError()) }
    }

    companion object {
        fun factory(
            repository: CodexRepository,
            draftRepository: DraftRepository,
            serverId: String?,
            initialCwd: String?,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                NewTaskViewModel(repository, draftRepository, serverId, initialCwd)
            }
        }
    }
}

private fun NewTaskUiState.conversationTitle(): String = message.lineSequence()
    .map(String::trim)
    .firstOrNull(String::isNotEmpty)
    ?.take(72)
    ?: "附件任务"

private fun ThreadOptions.toTurnOptions(): TurnOptions = TurnOptions(
    model = model,
    serviceTier = serviceTier,
    approvalPolicy = approvalPolicy,
    approvalsReviewer = approvalsReviewer,
    sandboxMode = sandboxMode,
)
