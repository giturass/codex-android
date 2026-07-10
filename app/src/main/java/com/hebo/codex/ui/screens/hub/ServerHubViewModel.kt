package com.hebo.codex.ui.screens.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.hebo.codex.domain.AccountOverview
import com.hebo.codex.domain.CodexRepository
import com.hebo.codex.domain.FeatureAction
import com.hebo.codex.domain.FeatureActionResult
import com.hebo.codex.domain.FeatureArea
import com.hebo.codex.domain.FeatureCatalog
import com.hebo.codex.domain.LoginChallenge
import com.hebo.codex.domain.ModelOption
import com.hebo.codex.domain.ServerConfiguration
import com.hebo.codex.util.runSuspendCatching
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

data class ServerHubUiState(
    val section: HubSection = HubSection.MODELS,
    val models: List<ModelOption> = emptyList(),
    val catalog: FeatureCatalog = FeatureCatalog(emptyList()),
    val account: AccountOverview? = null,
    val configuration: ServerConfiguration? = null,
    val loginChallenge: LoginChallenge? = null,
    val actionResult: FeatureActionResult? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

class ServerHubViewModel(
    private val repository: CodexRepository,
    private val serverId: String,
    private val cwd: String?,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ServerHubUiState())
    val state: StateFlow<ServerHubUiState> = mutableState

    init {
        refresh()
        viewModelScope.launch {
            repository.featureRevisions.collect { revisions ->
                if ((revisions[serverId] ?: 0L) > 0L) refresh()
            }
        }
    }

    fun selectSection(section: HubSection) {
        if (mutableState.value.section == section) return
        mutableState.update { it.copy(section = section, actionResult = null, errorMessage = null) }
        refresh()
    }

    fun refresh(forceRefresh: Boolean = false) {
        if (mutableState.value.isLoading) return
        viewModelScope.launch {
            mutableState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = runSuspendCatching {
                when (val section = mutableState.value.section) {
                    HubSection.MODELS -> HubData.Models(repository.models(serverId))
                    HubSection.ACCOUNT -> HubData.Account(repository.account(serverId))
                    HubSection.CONFIG -> HubData.Configuration(repository.configuration(serverId, cwd))
                    else -> HubData.Catalog(
                        repository.featureCatalog(
                            serverId = serverId,
                            area = section.toFeatureArea(),
                            cwd = cwd,
                            forceRefresh = forceRefresh,
                        ),
                    )
                }
            }
            result.onSuccess { data ->
                mutableState.update { current ->
                    when (data) {
                        is HubData.Models -> current.copy(models = data.value)
                        is HubData.Catalog -> current.copy(catalog = data.value)
                        is HubData.Account -> current.copy(account = data.value)
                        is HubData.Configuration -> current.copy(configuration = data.value)
                    }
                }
            }.onFailure(::showError)
            mutableState.update { it.copy(isLoading = false) }
        }
    }

    fun execute(action: FeatureAction) {
        viewModelScope.launch {
            mutableState.update { it.copy(isLoading = true, actionResult = null, errorMessage = null) }
            runSuspendCatching { repository.executeFeatureAction(serverId, action) }
                .onSuccess { result ->
                    mutableState.update { it.copy(actionResult = result) }
                    refreshAfterAction()
                }
                .onFailure(::showError)
            mutableState.update { it.copy(isLoading = false) }
        }
    }

    fun loadModelCapabilities() {
        viewModelScope.launch {
            runSuspendCatching { repository.modelProviderCapabilities(serverId) }
                .onSuccess { result -> mutableState.update { it.copy(actionResult = result) } }
                .onFailure(::showError)
        }
    }

    fun loginWithApiKey(apiKey: String) {
        launchLogin { repository.loginWithApiKey(serverId, apiKey) }
    }

    fun loginWithChatGpt(deviceCode: Boolean) {
        launchLogin { repository.loginWithChatGpt(serverId, deviceCode) }
    }

    fun cancelLogin() {
        val loginId = mutableState.value.loginChallenge?.loginId ?: return
        viewModelScope.launch {
            runSuspendCatching { repository.cancelLogin(serverId, loginId) }
                .onSuccess { mutableState.update { it.copy(loginChallenge = null) } }
                .onFailure(::showError)
        }
    }

    fun logout() {
        viewModelScope.launch {
            mutableState.update { it.copy(isLoading = true) }
            runSuspendCatching { repository.logout(serverId) }
                .onSuccess {
                    mutableState.update { it.copy(loginChallenge = null) }
                    refreshAfterAction()
                }
                .onFailure(::showError)
            mutableState.update { it.copy(isLoading = false) }
        }
    }

    fun consumeRateLimitReset(creditId: String?) {
        viewModelScope.launch {
            runSuspendCatching { repository.consumeRateLimitReset(serverId, creditId) }
                .onSuccess { mutableState.update { state -> state.copy(actionResult = it) } }
                .onFailure(::showError)
        }
    }

    fun sendCreditsNudge(creditType: String) {
        viewModelScope.launch {
            runSuspendCatching { repository.sendCreditsNudge(serverId, creditType) }
                .onSuccess { mutableState.update { state -> state.copy(actionResult = it) } }
                .onFailure(::showError)
        }
    }

    fun clearMessage() {
        mutableState.update { it.copy(errorMessage = null, actionResult = null) }
    }

    private fun launchLogin(block: suspend () -> LoginChallenge) {
        viewModelScope.launch {
            mutableState.update { it.copy(isLoading = true, errorMessage = null) }
            runSuspendCatching(block)
                .onSuccess { challenge -> mutableState.update { it.copy(loginChallenge = challenge) } }
                .onFailure(::showError)
            mutableState.update { it.copy(isLoading = false) }
        }
    }

    private fun refreshAfterAction() {
        viewModelScope.launch {
            val section = mutableState.value.section
            val result = runSuspendCatching {
                when (section) {
                    HubSection.ACCOUNT -> HubData.Account(repository.account(serverId))
                    HubSection.CONFIG -> HubData.Configuration(repository.configuration(serverId, cwd))
                    HubSection.MODELS -> HubData.Models(repository.models(serverId))
                    else -> HubData.Catalog(
                        repository.featureCatalog(serverId, section.toFeatureArea(), cwd, forceRefresh = true),
                    )
                }
            }
            result.onSuccess { data ->
                mutableState.update { current ->
                    when (data) {
                        is HubData.Models -> current.copy(models = data.value)
                        is HubData.Catalog -> current.copy(catalog = data.value)
                        is HubData.Account -> current.copy(account = data.value)
                        is HubData.Configuration -> current.copy(configuration = data.value)
                    }
                }
            }
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
        mutableState.update { it.copy(errorMessage = error.message ?: "操作失败") }
    }

    private sealed interface HubData {
        data class Models(val value: List<ModelOption>) : HubData
        data class Catalog(val value: FeatureCatalog) : HubData
        data class Account(val value: AccountOverview) : HubData
        data class Configuration(val value: ServerConfiguration) : HubData
    }

    companion object {
        fun factory(
            repository: CodexRepository,
            serverId: String,
            cwd: String?,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { ServerHubViewModel(repository, serverId, cwd) }
        }
    }
}
