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
import com.hebo.codex.domain.FeatureArea
import com.hebo.codex.domain.FeatureEntry
import com.hebo.codex.domain.ModelOption
import com.hebo.codex.domain.ReviewDelivery
import com.hebo.codex.domain.ReviewTarget
import com.hebo.codex.domain.ThreadGoal
import com.hebo.codex.domain.ThreadGoalStatus
import com.hebo.codex.domain.TurnOptions
import com.hebo.codex.domain.UserInputPart
import com.hebo.codex.domain.UserFacingError
import com.hebo.codex.util.runSuspendCatching
import com.hebo.codex.util.toUserFacingError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
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
import java.util.UUID

data class ChatDraft(
    val message: String = "",
    val attachments: List<DraftAttachment> = emptyList(),
    val options: TurnOptions = TurnOptions(),
)

data class DraftAttachment(
    val id: String,
    val input: UserInputPart,
    val previewUri: String? = null,
)

data class ChatUiState(
    val conversation: ConversationState? = null,
    val models: List<ModelOption> = emptyList(),
    val inputResources: List<FeatureEntry> = emptyList(),
    val goal: ThreadGoal? = null,
    val draft: ChatDraft = ChatDraft(),
    val connectionStatus: ConnectionStatus = ConnectionStatus.Disconnected,
    val isLoading: Boolean = true,
    val isLoadingResources: Boolean = false,
    val isSendingDraft: Boolean = false,
    val screenError: UserFacingError? = null,
)

sealed interface ChatEvent {
    data class OpenConversation(val threadId: String) : ChatEvent
}

class ChatViewModel(
    private val repository: CodexRepository,
    private val serverId: String,
    private val threadId: String,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ChatUiState())
    private val mutableEvents = Channel<ChatEvent>(Channel.BUFFERED)
    val state: StateFlow<ChatUiState> = mutableState
    val events = mutableEvents.receiveAsFlow()
    private var draftInitialized = false

    init {
        viewModelScope.launch {
            try {
                repository.openConversation(serverId, threadId).collectLatest { value ->
                    val initialDraft = if (draftInitialized) {
                        null
                    } else {
                        draftInitialized = true
                        ChatDraft(options = value.defaultTurnOptions())
                    }
                    mutableState.update {
                        it.copy(
                            conversation = value,
                            goal = if (value.goalLoaded) value.goal else it.goal,
                            draft = initialDraft ?: it.draft,
                            isLoading = false,
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

    fun updateDraftMessage(message: String) {
        mutableState.update { it.copy(draft = it.draft.copy(message = message)) }
    }

    fun updateDraftOptions(options: TurnOptions) {
        mutableState.update { it.copy(draft = it.draft.copy(options = options)) }
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
    }

    fun removeDraftAttachment(id: String) {
        mutableState.update {
            it.copy(draft = it.draft.copy(attachments = it.draft.attachments.filterNot { item -> item.id == id }))
        }
    }

    fun appendToDraft(value: String) {
        mutableState.update {
            val separator = if (it.draft.message.isBlank()) "" else "\n\n"
            it.copy(draft = it.draft.copy(message = it.draft.message + separator + value))
        }
    }

    fun sendDraft() {
        val current = mutableState.value
        if (current.isSendingDraft || current.connectionStatus !is ConnectionStatus.Connected) return
        val draft = current.draft
        val input = buildList {
            draft.message.takeIf(String::isNotBlank)?.let { add(UserInputPart.Text(it)) }
            addAll(draft.attachments.map(DraftAttachment::input))
        }
        if (input.isEmpty()) return
        mutableState.update { it.copy(isSendingDraft = true, screenError = null) }
        viewModelScope.launch {
            runSuspendCatching { repository.sendMessage(serverId, threadId, input, draft.options) }
                .onSuccess {
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

    fun reportInputError(error: Throwable) {
        mutableState.update { it.copy(screenError = error.toUserFacingError("无法添加输入")) }
    }

    fun interrupt() {
        viewModelScope.launch {
            runSuspendCatching { repository.interrupt(serverId, threadId) }
                .onFailure(::showError)
        }
    }

    fun compact() {
        viewModelScope.launch {
            runSuspendCatching { repository.compactConversation(serverId, threadId) }
                .onFailure(::showError)
        }
    }

    fun rename(name: String) {
        viewModelScope.launch {
            runSuspendCatching { repository.renameConversation(serverId, threadId, name) }
                .onFailure(::showError)
        }
    }

    fun fork(lastTurnId: String? = null) {
        viewModelScope.launch {
            runSuspendCatching { repository.forkConversation(serverId, threadId, lastTurnId) }
                .onSuccess { mutableEvents.send(ChatEvent.OpenConversation(it)) }
                .onFailure(::showError)
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
            val modelsDeferred = async { runSuspendCatching { repository.models(serverId) } }
            val skillsDeferred = async {
                runSuspendCatching {
                    repository.featureCatalog(serverId, FeatureArea.SKILLS, conversation.cwd, threadId, forceRefresh)
                }
            }
            val appsDeferred = async {
                runSuspendCatching {
                    repository.featureCatalog(serverId, FeatureArea.APPS, conversation.cwd, threadId, forceRefresh)
                }
            }
            val pluginsDeferred = async {
                runSuspendCatching {
                    repository.featureCatalog(serverId, FeatureArea.PLUGINS, conversation.cwd, threadId, forceRefresh)
                }
            }
            val models = modelsDeferred.await()
            val skills = skillsDeferred.await()
            val apps = appsDeferred.await()
            val plugins = pluginsDeferred.await()
            val failure = models.exceptionOrNull()
                ?: skills.exceptionOrNull()
                ?: apps.exceptionOrNull()
                ?: plugins.exceptionOrNull()
            mutableState.update {
                it.copy(
                    models = models.getOrDefault(emptyList()),
                    inputResources = availableInputResources(
                        skills = skills.getOrNull()?.entries.orEmpty(),
                        apps = apps.getOrNull()?.entries.orEmpty(),
                        plugins = plugins.getOrNull()?.entries.orEmpty(),
                    ),
                    isLoadingResources = false,
                    screenError = failure?.toUserFacingError("无法加载输入资源"),
                )
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

    override fun onCleared() {
        repository.closeConversation(serverId, threadId)
    }

    private fun showError(error: Throwable) {
        mutableState.update { it.copy(screenError = error.toUserFacingError()) }
    }

    companion object {
        fun factory(
            repository: CodexRepository,
            serverId: String,
            threadId: String,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { ChatViewModel(repository, serverId, threadId) }
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

internal fun availableInputResources(
    skills: List<FeatureEntry>,
    apps: List<FeatureEntry>,
    plugins: List<FeatureEntry>,
): List<FeatureEntry> =
    skills.filter { entry -> entry.enabled != false } +
        apps.filter { entry -> entry.enabled != false && entry.metadata["accessible"] == "true" } +
        plugins.filter { entry -> entry.installed == true && entry.enabled != false }
