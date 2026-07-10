package com.hebo.codex.ui.screens.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.hebo.codex.domain.AccountOverview
import com.hebo.codex.domain.CodexRepository
import com.hebo.codex.domain.ConnectionStatus
import com.hebo.codex.domain.FeatureAction
import com.hebo.codex.domain.FeatureActionResult
import com.hebo.codex.domain.FeatureArea
import com.hebo.codex.domain.FeatureCatalog
import com.hebo.codex.domain.LoginChallenge
import com.hebo.codex.domain.ModelOption
import com.hebo.codex.domain.ServerConfiguration
import com.hebo.codex.domain.ServerProfile
import com.hebo.codex.domain.UserFacingError
import com.hebo.codex.util.runSuspendCatching
import com.hebo.codex.util.toUserFacingError
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class HubSection {
    MODELS,
    PERMISSIONS,
    SKILLS,
    HOOKS,
    APPS,
    PLUGINS,
    MCP,
    SHARED_PLUGINS,
    ACCOUNT,
    CONFIG,
}

sealed interface HubData {
    data class Models(val value: List<ModelOption>) : HubData

    data class Catalog(val value: FeatureCatalog) : HubData

    data class Account(val value: AccountOverview) : HubData

    data class Configuration(val value: ServerConfiguration) : HubData
}

data class HubSectionUiState(
    val data: HubData? = null,
    val isLoading: Boolean = false,
    val errorMessage: UserFacingError? = null,
)

enum class ActionResultPresentation {
    MESSAGE,
    DETAILS,
}

data class ServerHubUiState(
    val selectedSection: HubSection? = null,
    val server: ServerProfile? = null,
    val connectionStatus: ConnectionStatus = ConnectionStatus.Disconnected,
    val cwd: String? = null,
    val sections: Map<HubSection, HubSectionUiState> = emptyMap(),
    val pendingActions: Set<String> = emptySet(),
    val loginChallenge: LoginChallenge? = null,
    val actionResult: FeatureActionResult? = null,
    val detailResult: FeatureActionResult? = null,
    val errorMessage: UserFacingError? = null,
)

class ServerHubViewModel(
    private val repository: CodexRepository,
    private val serverId: String,
    cwd: String?,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ServerHubUiState(cwd = cwd))
    val state: StateFlow<ServerHubUiState> = mutableState
    private val sectionJobs = mutableMapOf<HubSection, Job>()

    init {
        viewModelScope.launch {
            combine(repository.servers, repository.connectionStatuses) { servers, statuses ->
                servers.firstOrNull { it.id == serverId } to
                    (statuses[serverId] ?: ConnectionStatus.Disconnected)
            }.collect { (server, status) ->
                mutableState.update { it.copy(server = server, connectionStatus = status) }
            }
        }
        viewModelScope.launch {
            repository.featureRevisions
                .map { it[serverId] ?: 0L }
                .distinctUntilChanged()
                .drop(1)
                .collect { revision ->
                    if (revision > 0L) mutableState.value.selectedSection?.let(::refresh)
                }
        }
    }

    fun selectSection(section: HubSection?) {
        if (mutableState.value.selectedSection == section) return
        mutableState.update { it.copy(selectedSection = section, detailResult = null) }
        section?.let(::load)
    }

    fun load(section: HubSection, forceRefresh: Boolean = false) {
        val current = mutableState.value.sections[section]
        if (!forceRefresh && (current?.data != null || current?.isLoading == true)) return

        sectionJobs.remove(section)?.cancel()
        updateSection(section) { it.copy(isLoading = true, errorMessage = null) }
        sectionJobs[section] = viewModelScope.launch {
            runSuspendCatching { loadData(section, forceRefresh) }
                .onSuccess { data ->
                    updateSection(section) { it.copy(data = data, isLoading = false, errorMessage = null) }
                }
                .onFailure { error ->
                    updateSection(section) {
                        it.copy(isLoading = false, errorMessage = error.toUserFacingError("加载失败"))
                    }
                }
        }
    }

    fun refresh(section: HubSection? = mutableState.value.selectedSection) {
        section?.let { load(it, forceRefresh = true) }
    }

    fun reconnect() {
        launchAction(RECONNECT_ACTION_KEY) {
            repository.connect(serverId)
            FeatureActionResult("服务器已重新连接", "{}")
        }
    }

    fun execute(
        action: FeatureAction,
        actionKey: String,
        refreshSection: HubSection? = mutableState.value.selectedSection,
        presentation: ActionResultPresentation = ActionResultPresentation.MESSAGE,
    ) {
        launchAction(
            actionKey = actionKey,
            refreshSection = refreshSection,
            presentation = presentation,
        ) {
            repository.executeFeatureAction(serverId, action)
        }
    }

    fun loadModelCapabilities() {
        launchAction(
            actionKey = MODEL_CAPABILITIES_ACTION_KEY,
            presentation = ActionResultPresentation.DETAILS,
        ) {
            repository.modelProviderCapabilities(serverId)
        }
    }

    fun loginWithApiKey(apiKey: String) {
        launchLogin(ACCOUNT_LOGIN_ACTION_KEY) { repository.loginWithApiKey(serverId, apiKey) }
    }

    fun loginWithChatGpt(deviceCode: Boolean) {
        val actionKey = if (deviceCode) DEVICE_LOGIN_ACTION_KEY else ACCOUNT_LOGIN_ACTION_KEY
        launchLogin(actionKey) { repository.loginWithChatGpt(serverId, deviceCode) }
    }

    fun cancelLogin() {
        val loginId = mutableState.value.loginChallenge?.loginId ?: return
        if (!startAction(CANCEL_LOGIN_ACTION_KEY)) return
        viewModelScope.launch {
            runSuspendCatching { repository.cancelLogin(serverId, loginId) }
                .onSuccess { mutableState.update { it.copy(loginChallenge = null) } }
                .onFailure(::showError)
            finishAction(CANCEL_LOGIN_ACTION_KEY)
        }
    }

    fun logout() {
        if (!startAction(ACCOUNT_LOGOUT_ACTION_KEY)) return
        viewModelScope.launch {
            runSuspendCatching { repository.logout(serverId) }
                .onSuccess {
                    mutableState.update {
                        it.copy(
                            loginChallenge = null,
                            actionResult = FeatureActionResult("已退出登录", "{}"),
                        )
                    }
                    refresh(HubSection.ACCOUNT)
                }
                .onFailure(::showError)
            finishAction(ACCOUNT_LOGOUT_ACTION_KEY)
        }
    }

    fun consumeRateLimitReset(creditId: String?) {
        launchAction(
            actionKey = RATE_LIMIT_ACTION_KEY,
            refreshSection = HubSection.ACCOUNT,
        ) {
            repository.consumeRateLimitReset(serverId, creditId)
        }
    }

    fun sendCreditsNudge(creditType: String) {
        launchAction(
            actionKey = CREDITS_NUDGE_ACTION_KEY,
            refreshSection = HubSection.ACCOUNT,
        ) {
            repository.sendCreditsNudge(serverId, creditType)
        }
    }

    fun clearError() {
        mutableState.update { it.copy(errorMessage = null) }
    }

    fun clearActionResult() {
        mutableState.update { it.copy(actionResult = null) }
    }

    fun clearDetailResult() {
        mutableState.update { it.copy(detailResult = null) }
    }

    private suspend fun loadData(section: HubSection, forceRefresh: Boolean): HubData = when (section) {
        HubSection.MODELS -> HubData.Models(repository.models(serverId))
        HubSection.ACCOUNT -> HubData.Account(repository.account(serverId))
        HubSection.CONFIG -> HubData.Configuration(repository.configuration(serverId, mutableState.value.cwd))
        else -> HubData.Catalog(
            repository.featureCatalog(
                serverId = serverId,
                area = section.toFeatureArea(),
                cwd = mutableState.value.cwd,
                forceRefresh = forceRefresh,
            ),
        )
    }

    private fun launchLogin(actionKey: String, block: suspend () -> LoginChallenge) {
        if (!startAction(actionKey)) return
        viewModelScope.launch {
            runSuspendCatching(block)
                .onSuccess { challenge -> mutableState.update { it.copy(loginChallenge = challenge) } }
                .onFailure(::showError)
            finishAction(actionKey)
        }
    }

    private fun launchAction(
        actionKey: String,
        refreshSection: HubSection? = null,
        presentation: ActionResultPresentation = ActionResultPresentation.MESSAGE,
        block: suspend () -> FeatureActionResult,
    ) {
        if (!startAction(actionKey)) return
        viewModelScope.launch {
            runSuspendCatching(block)
                .onSuccess { result ->
                    mutableState.update {
                        when (presentation) {
                            ActionResultPresentation.MESSAGE -> it.copy(actionResult = result)
                            ActionResultPresentation.DETAILS -> it.copy(detailResult = result)
                        }
                    }
                    refreshSection?.let(::refresh)
                }
                .onFailure(::showError)
            finishAction(actionKey)
        }
    }

    private fun startAction(actionKey: String): Boolean {
        if (actionKey in mutableState.value.pendingActions) return false
        mutableState.update {
            it.copy(
                pendingActions = it.pendingActions + actionKey,
                errorMessage = null,
            )
        }
        return true
    }

    private fun finishAction(actionKey: String) {
        mutableState.update { it.copy(pendingActions = it.pendingActions - actionKey) }
    }

    private fun updateSection(section: HubSection, transform: (HubSectionUiState) -> HubSectionUiState) {
        mutableState.update { state ->
            val current = state.sections[section] ?: HubSectionUiState()
            state.copy(sections = state.sections + (section to transform(current)))
        }
    }

    private fun HubSection.toFeatureArea(): FeatureArea = when (this) {
        HubSection.PERMISSIONS -> FeatureArea.PERMISSIONS
        HubSection.SKILLS -> FeatureArea.SKILLS
        HubSection.HOOKS -> FeatureArea.HOOKS
        HubSection.APPS -> FeatureArea.APPS
        HubSection.PLUGINS -> FeatureArea.PLUGINS
        HubSection.MCP -> FeatureArea.MCP
        HubSection.SHARED_PLUGINS -> FeatureArea.SHARED_PLUGINS
        HubSection.MODELS, HubSection.ACCOUNT, HubSection.CONFIG -> error("当前分区不是目录")
    }

    private fun showError(error: Throwable) {
        mutableState.update { it.copy(errorMessage = error.toUserFacingError()) }
    }

    companion object {
        const val RECONNECT_ACTION_KEY = "server:reconnect"
        const val MODEL_CAPABILITIES_ACTION_KEY = "models:capabilities"
        const val ACCOUNT_LOGIN_ACTION_KEY = "account:login"
        const val DEVICE_LOGIN_ACTION_KEY = "account:device-login"
        const val CANCEL_LOGIN_ACTION_KEY = "account:cancel-login"
        const val ACCOUNT_LOGOUT_ACTION_KEY = "account:logout"
        const val RATE_LIMIT_ACTION_KEY = "account:rate-limit-reset"
        const val CREDITS_NUDGE_ACTION_KEY = "account:credits-nudge"

        fun factory(
            repository: CodexRepository,
            serverId: String,
            cwd: String?,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { ServerHubViewModel(repository, serverId, cwd) }
        }
    }
}
