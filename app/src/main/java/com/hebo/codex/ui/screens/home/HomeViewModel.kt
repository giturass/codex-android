package com.hebo.codex.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.hebo.codex.domain.CodexRepository
import com.hebo.codex.domain.ConnectionStatus
import com.hebo.codex.domain.RecentProject
import com.hebo.codex.domain.ServerProfile
import com.hebo.codex.domain.ThreadSummary
import com.hebo.codex.domain.groupRecentProjects
import com.hebo.codex.util.runSuspendCatching
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class HomeTab {
    PROJECTS,
    CONVERSATIONS,
}

data class HomeUiState(
    val servers: List<ServerProfile> = emptyList(),
    val activeServerId: String? = null,
    val connectionStatuses: Map<String, ConnectionStatus> = emptyMap(),
    val threads: List<ThreadSummary> = emptyList(),
    val projects: List<RecentProject> = emptyList(),
    val selectedTab: HomeTab = HomeTab.PROJECTS,
    val isLoading: Boolean = false,
    val isCreatingConversation: Boolean = false,
    val errorMessage: String? = null,
) {
    val activeServer: ServerProfile?
        get() = servers.singleOrNull { it.id == activeServerId }
}

sealed interface HomeEvent {
    data class OpenConversation(val serverId: String, val threadId: String) : HomeEvent
}

class HomeViewModel(
    private val repository: CodexRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(HomeUiState())
    private val mutableEvents = Channel<HomeEvent>(Channel.BUFFERED)
    private var refreshJob: Job? = null

    val state: StateFlow<HomeUiState> = mutableState
    val events = mutableEvents.receiveAsFlow()

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
                    it.copy(
                        servers = servers,
                        activeServerId = activeServerId,
                        connectionStatuses = statuses,
                    )
                }
            }
        }
        viewModelScope.launch {
            repository.activeServerId.collect { serverId ->
                if (serverId == null) {
                    mutableState.update { it.copy(threads = emptyList(), projects = emptyList()) }
                } else {
                    requestLoad(serverId)
                }
            }
        }
    }

    fun selectTab(tab: HomeTab) {
        mutableState.update { it.copy(selectedTab = tab) }
    }

    fun selectServer(serverId: String) {
        viewModelScope.launch {
            runSuspendCatching { repository.selectServer(serverId) }
                .onFailure(::showError)
        }
    }

    fun reconnectServer(serverId: String) {
        viewModelScope.launch {
            runSuspendCatching { repository.connect(serverId) }
                .onFailure(::showError)
        }
    }

    fun removeServer(serverId: String) {
        viewModelScope.launch {
            runSuspendCatching { repository.removeServer(serverId) }
                .onFailure(::showError)
        }
    }

    fun refresh() {
        val serverId = mutableState.value.activeServerId ?: return
        requestLoad(serverId)
    }

    fun startConversation(cwd: String) {
        if (mutableState.value.isCreatingConversation) return
        val serverId = mutableState.value.activeServerId ?: return
        viewModelScope.launch {
            mutableState.update { it.copy(isCreatingConversation = true, errorMessage = null) }
            val result = runSuspendCatching { repository.startConversation(serverId, cwd) }
            result.exceptionOrNull()?.let(::showError)
            result.getOrNull()?.let { threadId ->
                mutableEvents.send(HomeEvent.OpenConversation(serverId, threadId))
            }
            mutableState.update { it.copy(isCreatingConversation = false) }
        }
    }

    fun clearError() {
        mutableState.update { it.copy(errorMessage = null) }
    }

    private suspend fun loadThreads(serverId: String) {
        mutableState.update { it.copy(isLoading = true, errorMessage = null) }
        runSuspendCatching { repository.recentThreads(serverId) }
            .onSuccess { threads ->
                if (mutableState.value.activeServerId == serverId) {
                    mutableState.update {
                        it.copy(threads = threads, projects = groupRecentProjects(threads))
                    }
                }
            }
            .onFailure(::showError)
        mutableState.update { it.copy(isLoading = false) }
    }

    private fun requestLoad(serverId: String) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch { loadThreads(serverId) }
    }

    private fun showError(error: Throwable) {
        mutableState.update { it.copy(errorMessage = error.message ?: "操作失败") }
    }

    companion object {
        fun factory(repository: CodexRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeViewModel(repository) }
        }
    }
}
