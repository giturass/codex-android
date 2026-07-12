package com.hebo.codex.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.hebo.codex.domain.CodexRepository
import com.hebo.codex.domain.ConversationDraft
import com.hebo.codex.domain.DraftAttachment
import com.hebo.codex.domain.DraftKey
import com.hebo.codex.domain.DraftRepository
import com.hebo.codex.domain.DraftTarget
import com.hebo.codex.domain.FeatureArea
import com.hebo.codex.domain.FeatureEntry
import com.hebo.codex.domain.ModelOption
import com.hebo.codex.domain.ThreadOptions
import com.hebo.codex.domain.TurnOptions
import com.hebo.codex.domain.UserInputPart
import com.hebo.codex.domain.UserFacingError
import com.hebo.codex.domain.newDraftRevision
import com.hebo.codex.ui.screens.chat.availableInputResources
import com.hebo.codex.util.runSuspendCatching
import com.hebo.codex.util.toUserFacingError
import java.util.UUID
import kotlinx.coroutines.async
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
    val isLoadingDraft: Boolean = true,
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
                                attachments = restored.attachments,
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

    fun loadInputResources(forceRefresh: Boolean = false) {
        val current = mutableState.value
        val serverId = current.serverId ?: return
        if (current.isLoadingResources) return
        viewModelScope.launch {
            mutableState.update { it.copy(isLoadingResources = true) }
            val cwd = current.cwd.takeIf(String::isNotBlank)
            val skillsDeferred = async {
                runSuspendCatching {
                    repository.featureCatalog(serverId, FeatureArea.SKILLS, cwd, forceRefresh = forceRefresh)
                }
            }
            val appsDeferred = async {
                runSuspendCatching {
                    repository.featureCatalog(serverId, FeatureArea.APPS, cwd, forceRefresh = forceRefresh)
                }
            }
            val pluginsDeferred = async {
                runSuspendCatching {
                    repository.featureCatalog(serverId, FeatureArea.PLUGINS, cwd, forceRefresh = forceRefresh)
                }
            }
            val skills = skillsDeferred.await()
            val apps = appsDeferred.await()
            val plugins = pluginsDeferred.await()
            val failure = skills.exceptionOrNull()
                ?: apps.exceptionOrNull()
                ?: plugins.exceptionOrNull()
            mutableState.update {
                it.copy(
                    inputResources = availableInputResources(
                        skills = skills.getOrNull()?.entries.orEmpty(),
                        apps = apps.getOrNull()?.entries.orEmpty(),
                        plugins = plugins.getOrNull()?.entries.orEmpty(),
                    ),
                    isLoadingResources = false,
                    errorMessage = failure?.toUserFacingError("无法加载输入资源"),
                )
            }
        }
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

    fun startConversation(message: String) {
        val current = mutableState.value
        if (current.isCreatingConversation || current.isLoadingDraft) return
        updateMessage(message)
        val submission = mutableState.value
        val serverId = submission.serverId ?: return
        if (submission.message.isBlank() && submission.attachments.isEmpty()) {
            showError(IllegalArgumentException("请输入任务内容"))
            return
        }
        if (submission.cwd.isBlank()) {
            showError(IllegalArgumentException("请选择项目"))
            return
        }

        mutableState.update { it.copy(isCreatingConversation = true, errorMessage = null) }
        viewModelScope.launch {
            val threadId = createdThreadId ?: runSuspendCatching {
                repository.startConversation(serverId, submission.cwd, submission.options)
            }.onFailure(::showError).getOrNull()
            if (threadId == null) {
                mutableState.update { it.copy(isCreatingConversation = false) }
                return@launch
            }
            createdThreadId = threadId

            val input = buildList {
                submission.message.takeIf(String::isNotBlank)?.let { add(UserInputPart.Text(it)) }
                addAll(submission.attachments.map(DraftAttachment::input))
            }
            runSuspendCatching {
                repository.sendMessage(serverId, threadId, input, submission.options.toTurnOptions())
            }
                .onSuccess {
                    runSuspendCatching { draftRepository.delete(currentDraft(submission).key) }
                        .onFailure { error ->
                            mutableState.update {
                                it.copy(errorMessage = error.toUserFacingError("清理草稿失败"))
                            }
                        }
                    createdThreadId = null
                    mutableState.update {
                        it.copy(message = "", attachments = emptyList(), isCreatingConversation = false)
                    }
                    mutableEvents.send(NewTaskEvent.OpenConversation(serverId, threadId))
                }
                .onFailure { sendError ->
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
                        draftRepository.delete(currentDraft(submission).key)
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
        attachments = state.attachments,
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

private fun NewTaskUiState.conversationTitle(): String = message.lineSequence()
    .map(String::trim)
    .firstOrNull(String::isNotEmpty)
    ?.take(72)
    ?: "附件任务"

private fun ThreadOptions.toTurnOptions(): TurnOptions = TurnOptions(
    model = model,
    serviceTier = serviceTier,
    personality = personality,
    approvalPolicy = approvalPolicy,
    approvalsReviewer = approvalsReviewer,
    sandboxMode = sandboxMode,
)
