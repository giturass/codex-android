package com.hebo.codex.ui.screens.servers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.hebo.codex.domain.CodexRepository
import com.hebo.codex.domain.ProxyProfile
import com.hebo.codex.domain.UserFacingError
import com.hebo.codex.util.runSuspendCatching
import com.hebo.codex.util.toUserFacingError
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ServerEditorUiState(
    val isEditing: Boolean,
    val name: String = "",
    val endpoint: String = "",
    val token: String = "",
    val proxies: List<ProxyProfile> = emptyList(),
    val proxyId: String? = null,
    val isReady: Boolean = !isEditing,
    val isLoading: Boolean = isEditing,
    val isSaving: Boolean = false,
    val isTesting: Boolean = false,
    val errorMessage: UserFacingError? = null,
) {
    val isBusy: Boolean
        get() = isSaving || isTesting

    val canSave: Boolean
        get() = isReady && !isBusy && name.isNotBlank() && endpoint.isNotBlank() && token.isNotBlank()

    val canTest: Boolean
        get() = isReady && !isBusy && endpoint.isNotBlank() && token.isNotBlank()
}

class ServerEditorViewModel(
    private val repository: CodexRepository,
    private val serverId: String?,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ServerEditorUiState(isEditing = serverId != null))
    private val mutableEvents = Channel<Unit>(Channel.BUFFERED)
    private val mutableTestSucceededEvents = Channel<Unit>(Channel.BUFFERED)

    val state: StateFlow<ServerEditorUiState> = mutableState
    val savedEvents = mutableEvents.receiveAsFlow()
    val testSucceededEvents = mutableTestSucceededEvents.receiveAsFlow()

    init {
        viewModelScope.launch {
            repository.proxies.collect { proxies ->
                mutableState.update { state ->
                    state.copy(
                        proxies = proxies,
                        proxyId = state.proxyId?.takeIf { id -> proxies.any { it.id == id } },
                    )
                }
            }
        }
        serverId?.let(::loadServer)
    }

    fun updateName(name: String) {
        mutableState.update { it.copy(name = name) }
    }

    fun updateEndpoint(endpoint: String) {
        mutableState.update { it.copy(endpoint = endpoint) }
    }

    fun updateToken(token: String) {
        mutableState.update { it.copy(token = token) }
    }

    fun updateProxy(proxyId: String?) {
        mutableState.update { it.copy(proxyId = proxyId) }
    }

    fun save() {
        val snapshot = mutableState.value
        if (!snapshot.canSave) return
        viewModelScope.launch {
            mutableState.update { it.copy(isSaving = true, errorMessage = null) }
            val result = runSuspendCatching {
                if (serverId == null) {
                    repository.addServer(snapshot.name, snapshot.endpoint, snapshot.token, snapshot.proxyId)
                } else {
                    repository.updateServer(serverId, snapshot.name, snapshot.endpoint, snapshot.token, snapshot.proxyId)
                }
            }
            val error = result.exceptionOrNull()
            if (error == null) {
                mutableEvents.send(Unit)
            } else {
                val summary = if (serverId == null) "添加服务器失败" else "编辑服务器失败"
                mutableState.update { it.copy(errorMessage = error.toUserFacingError(summary)) }
            }
            mutableState.update { it.copy(isSaving = false) }
        }
    }

    fun testConnection() {
        val snapshot = mutableState.value
        if (!snapshot.canTest) return
        viewModelScope.launch {
            mutableState.update { it.copy(isTesting = true, errorMessage = null) }
            runSuspendCatching {
                repository.testServerConnection(snapshot.endpoint, snapshot.token, snapshot.proxyId)
            }.onSuccess {
                mutableTestSucceededEvents.send(Unit)
            }.onFailure { error ->
                mutableState.update {
                    it.copy(errorMessage = error.toUserFacingError("测试服务器连接失败"))
                }
            }
            mutableState.update { it.copy(isTesting = false) }
        }
    }

    fun clearError() {
        mutableState.update { it.copy(errorMessage = null) }
    }

    private fun loadServer(id: String) {
        viewModelScope.launch {
            runSuspendCatching { repository.serverConnectionDetails(id) }
                .onSuccess { details ->
                    mutableState.update {
                        it.copy(
                            name = details.profile.name,
                            endpoint = details.profile.webSocketUrl,
                            token = details.token,
                            proxyId = details.profile.proxyId,
                            isReady = true,
                            isLoading = false,
                        )
                    }
                }
                .onFailure { error ->
                    mutableState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.toUserFacingError("加载服务器失败"),
                        )
                    }
                }
        }
    }

    companion object {
        fun factory(repository: CodexRepository, serverId: String?): ViewModelProvider.Factory = viewModelFactory {
            initializer { ServerEditorViewModel(repository, serverId) }
        }
    }
}
