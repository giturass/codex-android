package com.hebo.codex.ui.screens.servers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.hebo.codex.domain.CodexRepository
import com.hebo.codex.domain.ConnectionStatus
import com.hebo.codex.domain.ServerProfile
import com.hebo.codex.util.runSuspendCatching
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ServersUiState(
    val servers: List<ServerProfile> = emptyList(),
    val activeServerId: String? = null,
    val statuses: Map<String, ConnectionStatus> = emptyMap(),
    val errorMessage: String? = null,
)

class ServersViewModel(
    private val repository: CodexRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ServersUiState())
    val state: StateFlow<ServersUiState> = mutableState

    init {
        viewModelScope.launch {
            combine(
                repository.servers,
                repository.activeServerId,
                repository.connectionStatuses,
            ) { servers, activeServerId, statuses ->
                Triple(servers, activeServerId, statuses)
            }.collect { (servers, activeServerId, statuses) ->
                mutableState.update {
                    it.copy(servers = servers, activeServerId = activeServerId, statuses = statuses)
                }
            }
        }
    }

    fun select(serverId: String) {
        viewModelScope.launch {
            runSuspendCatching { repository.selectServer(serverId) }.onFailure(::showError)
        }
    }

    fun reconnect(serverId: String) {
        viewModelScope.launch {
            runSuspendCatching { repository.connect(serverId) }.onFailure(::showError)
        }
    }

    fun remove(serverId: String) {
        viewModelScope.launch {
            runSuspendCatching { repository.removeServer(serverId) }.onFailure(::showError)
        }
    }

    fun clearError() {
        mutableState.update { it.copy(errorMessage = null) }
    }

    private fun showError(error: Throwable) {
        mutableState.update { it.copy(errorMessage = error.message ?: "操作失败") }
    }

    companion object {
        fun factory(repository: CodexRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { ServersViewModel(repository) }
        }
    }
}

data class AddServerUiState(
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)

class AddServerViewModel(
    private val repository: CodexRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AddServerUiState())
    private val mutableEvents = Channel<Unit>(Channel.BUFFERED)

    val state: StateFlow<AddServerUiState> = mutableState
    val savedEvents = mutableEvents.receiveAsFlow()

    fun save(name: String, endpoint: String, token: String) {
        if (mutableState.value.isSaving) return
        viewModelScope.launch {
            mutableState.value = AddServerUiState(isSaving = true)
            val result = runSuspendCatching { repository.addServer(name, endpoint, token) }
            val error = result.exceptionOrNull()
            if (error == null) {
                mutableEvents.send(Unit)
            } else {
                mutableState.value = AddServerUiState(errorMessage = error.message ?: "添加失败")
            }
            mutableState.update { it.copy(isSaving = false) }
        }
    }

    fun clearError() {
        mutableState.update { it.copy(errorMessage = null) }
    }

    companion object {
        fun factory(repository: CodexRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { AddServerViewModel(repository) }
        }
    }
}
