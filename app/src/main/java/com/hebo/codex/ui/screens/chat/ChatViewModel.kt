package com.hebo.codex.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.hebo.codex.domain.ApprovalDecision
import com.hebo.codex.domain.CodexRepository
import com.hebo.codex.domain.ConnectionStatus
import com.hebo.codex.domain.ConversationState
import com.hebo.codex.domain.ConversationDraft
import com.hebo.codex.domain.DraftAttachment
import com.hebo.codex.domain.DraftKey
import com.hebo.codex.domain.DraftRepository
import com.hebo.codex.domain.DraftTarget
import com.hebo.codex.domain.FeatureEntry
import com.hebo.codex.domain.ModelOption
import com.hebo.codex.domain.ReviewDelivery
import com.hebo.codex.domain.ReviewTarget
import com.hebo.codex.domain.RemoteFileMetadata
import com.hebo.codex.domain.ThreadGoal
import com.hebo.codex.domain.ThreadGoalStatus
import com.hebo.codex.domain.TurnOptions
import com.hebo.codex.domain.UserInputPart
import com.hebo.codex.domain.UserFacingError
import com.hebo.codex.domain.newDraftRevision
import com.hebo.codex.domain.quickPhraseInput
import com.hebo.codex.domain.loadInputResources
import com.hebo.codex.util.runSuspendCatching
import com.hebo.codex.util.toUserFacingError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import java.io.OutputStream
import java.util.UUID

data class ChatDraft(
    val message: String = "",
    val attachments: List<DraftAttachment> = emptyList(),
    val options: TurnOptions = TurnOptions(),
)

data class ForkRequest(val lastTurnId: String?)

sealed interface ModelListState {
    data object Idle : ModelListState

    data object Loading : ModelListState

    data class Ready(val models: List<ModelOption>) : ModelListState

    data class Failed(val error: UserFacingError) : ModelListState
}

data class ChatUiState(
    val conversation: ConversationState? = null,
    val modelListState: ModelListState = ModelListState.Idle,
    val inputResources: List<FeatureEntry> = emptyList(),
    val goal: ThreadGoal? = null,
    val draft: ChatDraft = ChatDraft(),
    val connectionStatus: ConnectionStatus = ConnectionStatus.Disconnected,
    val isLoading: Boolean = true,
    val isLoadingResources: Boolean = false,
    val isSendingDraft: Boolean = false,
    val terminatingBackgroundProcessIds: Set<String> = emptySet(),
    val forkRequest: ForkRequest? = null,
    val screenError: UserFacingError? = null,
)

sealed interface ChatEvent {
    data class OpenConversation(
        val threadId: String,
        val announcement: String? = null,
    ) : ChatEvent
}

class ChatViewModel(
    private val repository: CodexRepository,
    private val draftRepository: DraftRepository,
    private val serverId: String,
    private val threadId: String,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ChatUiState())
    private val mutableEvents = Channel<ChatEvent>(Channel.BUFFERED)
    val state: StateFlow<ChatUiState> = mutableState
    val events = mutableEvents.receiveAsFlow()
    private var draftInitialized = false
    private var conversationJob: Job? = null
    private var modelListJob: Job? = null
    private var historyPagingJob: Job? = null
    private val draftKey = DraftKey(serverId, DraftTarget.Thread(threadId))

    init {
        viewModelScope.launch {
            draftRepository.failures.collectLatest { failure ->
                if (failure.key == draftKey) {
                    mutableState.update {
                        it.copy(screenError = failure.error.toUserFacingError("保存草稿失败"))
                    }
                }
            }
        }
        viewModelScope.launch {
            runSuspendCatching { repository.getGoal(serverId, threadId) }
                .onSuccess { goal -> mutableState.update { it.copy(goal = goal) } }
        }
        viewModelScope.launch {
            repository.connectionStatuses.collectLatest { statuses ->
                mutableState.update {
                    it.copy(connectionStatus = statuses[serverId] ?: ConnectionStatus.Disconnected)
                }
            }
        }
    }

    fun resumeConversation() {
        if (conversationJob != null) return
        loadModels()
        conversationJob = viewModelScope.launch {
            val restoredResult = if (draftInitialized) {
                Result.success<ConversationDraft?>(null)
            } else {
                runSuspendCatching { draftRepository.load(draftKey) }
            }
            var conversationOpened = false
            try {
                val conversation = repository.openConversation(serverId, threadId)
                conversationOpened = true
                conversation.collectLatest { value ->
                    val initialDraft = if (draftInitialized) {
                        null
                    } else {
                        draftInitialized = true
                        (restoredResult.getOrNull() as? ConversationDraft.Thread)?.toChatDraft()
                            ?: ChatDraft(options = value.defaultTurnOptions())
                    }
                    mutableState.update {
                        it.copy(
                            conversation = value,
                            goal = if (value.goalLoaded) value.goal else it.goal,
                            draft = initialDraft ?: it.draft,
                            isLoading = false,
                            screenError = restoredResult.exceptionOrNull()
                                ?.toUserFacingError("无法恢复草稿")
                                ?: it.screenError,
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                mutableState.value = ChatUiState(
                    isLoading = false,
                    screenError = error.toUserFacingError("无法打开会话"),
                )
            } finally {
                if (conversationOpened) repository.releaseConversation(serverId, threadId)
            }
        }
    }

    fun pauseConversation() {
        conversationJob?.cancel()
        conversationJob = null
        historyPagingJob?.cancel()
    }

    fun loadOlderTurns() = requestOlderTurns(retry = false)

    fun retryOlderTurns() = requestOlderTurns(retry = true)

    private fun requestOlderTurns(retry: Boolean) {
        if (historyPagingJob != null) return
        val history = mutableState.value.conversation?.history ?: return
        if (
            history.olderTurnsCursor == null ||
            history.isLoadingOlderTurns ||
            (!retry && history.olderTurnsError != null)
        ) {
            return
        }
        historyPagingJob = viewModelScope.launch {
            try {
                repository.loadOlderConversationTurns(serverId, threadId)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                showError(error)
            } finally {
                historyPagingJob = null
            }
        }
    }

    fun updateDraftMessage(message: String) {
        updateDraft { it.copy(message = message) }
    }

    fun updateDraftOptions(options: TurnOptions) {
        updateDraft { it.copy(options = options) }
    }

    fun addDraftAttachment(input: UserInputPart, previewUri: String? = null) {
        mutableState.update {
            it.copy(
                draft = it.draft.copy(
                    attachments = it.draft.attachments + DraftAttachment(
                        id = UUID.randomUUID().toString(),
                        input = input,
                        previewUri = previewUri,
                    ),
                ),
            )
        }
        scheduleDraft()
    }

    fun removeDraftAttachment(id: String) {
        mutableState.update {
            it.copy(draft = it.draft.copy(attachments = it.draft.attachments.filterNot { item -> item.id == id }))
        }
        scheduleDraft()
    }

    fun sendDraft() {
        val current = mutableState.value
        if (current.isSendingDraft || current.connectionStatus !is ConnectionStatus.Connected) return
        val draft = current.draft
        if (isShellCommandInput(draft.message)) {
            sendShellDraft(current, draft)
            return
        }
        val input = buildList {
            draft.message.takeIf(String::isNotBlank)?.let { add(UserInputPart.Text(it)) }
            addAll(draft.attachments.map(DraftAttachment::input))
        }
        if (input.isEmpty()) return
        submitDraft(draft) {
            repository.sendMessage(serverId, threadId, input, draft.options)
        }
    }

    private fun sendShellDraft(current: ChatUiState, draft: ChatDraft) {
        val blockReason = shellCommandBlockReason(
            message = draft.message,
            attachmentCount = draft.attachments.size,
            isRunning = current.conversation?.isRunning == true,
        )
        val command = shellCommandFromInput(draft.message)
        val validationError = blockReason ?: if (command == null) "Shell 命令不能为空" else null
        if (validationError != null) {
            reportInputError(IllegalArgumentException(validationError))
            return
        }
        submitDraft(draft) {
            repository.runShellCommand(serverId, threadId, requireNotNull(command))
        }
    }

    fun sendQuickPhrase(content: String) {
        val current = mutableState.value
        if (current.isSendingDraft || current.connectionStatus !is ConnectionStatus.Connected) return
        val input = quickPhraseInput(content)
        mutableState.update { it.copy(isSendingDraft = true, screenError = null) }
        viewModelScope.launch {
            runSuspendCatching {
                repository.sendMessage(serverId, threadId, input, current.draft.options)
            }.onFailure(::showError)
            mutableState.update { it.copy(isSendingDraft = false) }
        }
    }

    fun reportInputError(error: Throwable) {
        mutableState.update { it.copy(screenError = error.toUserFacingError("无法添加输入")) }
    }

    fun interrupt() {
        viewModelScope.launch {
            runSuspendCatching { repository.interrupt(serverId, threadId) }
                .onFailure(::showError)
        }
    }

    fun terminateBackgroundCommand(processId: String) {
        val current = mutableState.value
        if (processId in current.terminatingBackgroundProcessIds) return
        mutableState.update {
            it.copy(
                terminatingBackgroundProcessIds = it.terminatingBackgroundProcessIds + processId,
                screenError = null,
            )
        }
        viewModelScope.launch {
            runSuspendCatching {
                repository.terminateBackgroundTerminal(serverId, threadId, processId)
            }.onFailure(::showError)
            mutableState.update {
                it.copy(
                    terminatingBackgroundProcessIds =
                        it.terminatingBackgroundProcessIds - processId,
                )
            }
        }
    }

    suspend fun getRemoteFileMetadata(path: String): RemoteFileMetadata =
        repository.getRemoteFileMetadata(serverId, path)

    suspend fun readRemoteFilePreview(path: String): ByteArray =
        repository.readRemoteFilePreview(serverId, path)

    suspend fun downloadRemoteFile(path: String, output: OutputStream): Long =
        repository.downloadRemoteFile(serverId, path, output)

    fun compact() {
        viewModelScope.launch {
            runSuspendCatching { repository.compactConversation(serverId, threadId) }
                .onFailure(::showError)
        }
    }

    fun rename(name: String) {
        viewModelScope.launch {
            runSuspendCatching {
                repository.renameConversation(serverId, threadId, name)
                draftRepository.renameThread(serverId, threadId, name.trim())
            }
                .onFailure(::showError)
        }
    }

    fun fork(lastTurnId: String? = null) {
        val current = mutableState.value
        if (current.forkRequest != null) return
        val conversation = current.conversation ?: return
        val turn = lastTurnId?.let { requestedTurnId ->
            requireNotNull(
                buildForkTurns(conversation.history.turns, conversation.activeTurnId)
                    .firstOrNull { it.turnId == requestedTurnId },
            ) { "无法分叉未完成或不存在的回合：$requestedTurnId" }
        }
        val announcement = forkAnnouncement(conversation.title, turn)
        mutableState.update { it.copy(forkRequest = ForkRequest(lastTurnId)) }
        viewModelScope.launch {
            runSuspendCatching { repository.forkConversation(serverId, threadId, lastTurnId) }
                .onSuccess { forkedThreadId ->
                    mutableState.update { it.copy(forkRequest = null) }
                    mutableEvents.send(ChatEvent.OpenConversation(forkedThreadId, announcement))
                }
                .onFailure { error ->
                    mutableState.update { it.copy(forkRequest = null) }
                    showError(error)
                }
        }
    }

    fun review(target: ReviewTarget, delivery: ReviewDelivery) {
        viewModelScope.launch {
            runSuspendCatching { repository.reviewConversation(serverId, threadId, target, delivery) }
                .onSuccess { reviewThreadId ->
                    if (reviewThreadId != threadId) mutableEvents.send(ChatEvent.OpenConversation(reviewThreadId))
                }
                .onFailure(::showError)
        }
    }

    fun setGoal(objective: String, status: ThreadGoalStatus, tokenBudget: Long?) {
        viewModelScope.launch {
            runSuspendCatching { repository.setGoal(serverId, threadId, objective, status, tokenBudget) }
                .onSuccess { goal -> mutableState.update { it.copy(goal = goal) } }
                .onFailure(::showError)
        }
    }

    fun clearGoal() {
        viewModelScope.launch {
            runSuspendCatching { repository.clearGoal(serverId, threadId) }
                .onSuccess { mutableState.update { it.copy(goal = null) } }
                .onFailure(::showError)
        }
    }

    fun loadInputResources(forceRefresh: Boolean = false) {
        val conversation = mutableState.value.conversation ?: return
        if (mutableState.value.isLoadingResources) return
        viewModelScope.launch {
            mutableState.update { it.copy(isLoadingResources = true) }
            val result = loadInputResources(
                repository = repository,
                serverId = serverId,
                cwd = conversation.cwd,
                threadId = threadId,
                forceRefresh = forceRefresh,
            )
            mutableState.update {
                it.copy(
                    inputResources = result.entries,
                    isLoadingResources = false,
                    screenError = result.failure?.toUserFacingError("无法加载输入资源"),
                )
            }
        }
    }

    fun loadModels(forceRefresh: Boolean = false) {
        if (modelListJob?.isActive == true) return
        val current = mutableState.value.modelListState
        if (!forceRefresh && current is ModelListState.Ready) return
        modelListJob = viewModelScope.launch {
            val previous = (current as? ModelListState.Ready)?.models
            if (previous == null) {
                mutableState.update { it.copy(modelListState = ModelListState.Loading) }
            }
            runSuspendCatching { repository.models(serverId) }
                .onSuccess { models ->
                    mutableState.update { it.copy(modelListState = ModelListState.Ready(models)) }
                }
                .onFailure { error ->
                    mutableState.update {
                        it.copy(
                            modelListState = previous
                                ?.let(ModelListState::Ready)
                                ?: ModelListState.Failed(error.toUserFacingError("无法加载模型")),
                        )
                    }
                }
        }
    }

    fun respondToApproval(requestKey: String, decision: ApprovalDecision) {
        runCatching {
            repository.respondToApproval(serverId, threadId, requestKey, decision)
        }.onFailure(::showError)
    }

    fun respondToApprovalWithExecPolicy(requestKey: String, amendmentJson: String) {
        runCatching {
            repository.respondToApprovalWithExecPolicy(
                serverId,
                threadId,
                requestKey,
                Json.parseToJsonElement(amendmentJson),
            )
        }.onFailure(::showError)
    }

    fun respondToApprovalWithNetworkPolicy(requestKey: String, amendmentJson: String) {
        runCatching {
            repository.respondToApprovalWithNetworkPolicy(
                serverId,
                threadId,
                requestKey,
                Json.parseToJsonElement(amendmentJson),
            )
        }.onFailure(::showError)
    }

    fun respondToQuestions(requestKey: String, answers: Map<String, List<String>>) {
        runCatching {
            repository.respondToQuestions(serverId, threadId, requestKey, answers)
        }.onFailure(::showError)
    }

    fun respondToMcpElicitation(requestKey: String, action: String, contentJson: String?) {
        runCatching {
            val content = contentJson?.takeIf(String::isNotBlank)?.let { Json.parseToJsonElement(it) }
            repository.respondToMcpElicitation(serverId, threadId, requestKey, action, content)
        }.onFailure(::showError)
    }

    fun respondToPermissions(
        requestKey: String,
        requestedPermissions: String,
        grant: Boolean,
        session: Boolean,
        strictAutoReview: Boolean,
    ) {
        runCatching {
            val permissions = if (grant) {
                Json.parseToJsonElement(requestedPermissions) as JsonObject
            } else {
                buildJsonObject {}
            }
            repository.respondToPermissions(
                serverId,
                threadId,
                requestKey,
                permissions,
                session,
                strictAutoReview,
            )
        }.onFailure(::showError)
    }

    fun clearError() {
        repository.clearConversationError(serverId, threadId)
        mutableState.update { it.copy(screenError = null) }
    }

    fun flushDraft() {
        draftRepository.flush(draftKey)
    }

    override fun onCleared() {
        pauseConversation()
        flushDraft()
    }

    private fun showError(error: Throwable) {
        mutableState.update { it.copy(screenError = error.toUserFacingError()) }
    }

    private fun submitDraft(draft: ChatDraft, send: suspend () -> Unit) {
        mutableState.update { it.copy(isSendingDraft = true, screenError = null) }
        viewModelScope.launch {
            runSuspendCatching(send)
                .onSuccess {
                    runSuspendCatching { draftRepository.delete(draftKey) }
                        .onFailure { error ->
                            mutableState.update {
                                it.copy(screenError = error.toUserFacingError("清理草稿失败"))
                            }
                        }
                    mutableState.update { state ->
                        state.copy(
                            draft = if (state.draft == draft) {
                                ChatDraft(options = state.conversation.defaultTurnOptions())
                            } else {
                                state.draft
                            },
                            isSendingDraft = false,
                        )
                    }
                }
                .onFailure { error ->
                    mutableState.update { it.copy(isSendingDraft = false) }
                    showError(error)
                }
        }
    }

    private fun updateDraft(transform: (ChatDraft) -> ChatDraft) {
        mutableState.update { it.copy(draft = transform(it.draft)) }
        scheduleDraft()
    }

    private fun scheduleDraft() {
        val state = mutableState.value
        val conversation = state.conversation ?: return
        draftRepository.schedule(
            ConversationDraft.Thread(
                serverId = serverId,
                threadId = threadId,
                threadTitle = conversation.title,
                message = state.draft.message,
                cwd = conversation.cwd,
                options = state.draft.options,
                attachments = state.draft.attachments,
                updatedAtMillis = System.currentTimeMillis(),
                revision = newDraftRevision(),
            ),
        )
    }

    companion object {
        fun factory(
            repository: CodexRepository,
            draftRepository: DraftRepository,
            serverId: String,
            threadId: String,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { ChatViewModel(repository, draftRepository, serverId, threadId) }
        }
    }
}

private fun ConversationState?.defaultTurnOptions(): TurnOptions = this?.let {
    TurnOptions(
        model = it.model,
        serviceTier = it.serviceTier,
        reasoningEffort = it.reasoningEffort,
    )
} ?: TurnOptions()

private fun ConversationDraft.Thread.toChatDraft(): ChatDraft = ChatDraft(
    message = message,
    attachments = attachments,
    options = options,
)
