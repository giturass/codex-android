package com.hebo.codex.ui.screens.servers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.hebo.codex.domain.CodexRepository
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
    val isReady: Boolean = !isEditing,
    val isLoading: Boolean = isEditing,
    val isSaving: Boolean = false,
    val errorMessage: UserFacingError? = null,
) {
    val canSave: Boolean
        get() = isReady && !isSaving && name.isNotBlank() && endpoint.isNotBlank() &&
            (isEditing || token.isNotBlank())
}

class ServerEditorViewModel(
    private val repository: CodexRepository,
    private val serverId: String?,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ServerEditorUiState(isEditing = serverId != null))
    private val mutableEvents = Channel<Unit>(Channel.BUFFERED)

    val state: StateFlow<ServerEditorUiState> = mutableState
    val savedEvents = mutableEvents.receiveAsFlow()

    init {
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

    fun save() {
        val snapshot = mutableState.value
        if (!snapshot.canSave) return
        viewModelScope.launch {
            mutableState.update { it.copy(isSaving = true, errorMessage = null) }
            val result = runSuspendCatching {
                if (serverId == null) {
                    repository.addServer(snapshot.name, snapshot.endpoint, snapshot.token)
                } else {
                    repository.updateServer(serverId, snapshot.name, snapshot.endpoint, snapshot.token)
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

    fun clearError() {
        mutableState.update { it.copy(errorMessage = null) }
    }

    private fun loadServer(id: String) {
        viewModelScope.launch {
            runSuspendCatching { repository.server(id) }
                .onSuccess { server ->
                    mutableState.update {
                        it.copy(
                            name = server.name,
                            endpoint = server.webSocketUrl,
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
