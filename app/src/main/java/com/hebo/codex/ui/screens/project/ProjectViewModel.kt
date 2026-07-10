package com.hebo.codex.ui.screens.project

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.hebo.codex.domain.CodexRepository
import com.hebo.codex.domain.ThreadSummary
import com.hebo.codex.domain.projectName
import com.hebo.codex.util.runSuspendCatching
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProjectUiState(
    val projectName: String,
    val cwd: String,
    val threads: List<ThreadSummary> = emptyList(),
    val isLoading: Boolean = true,
    val isCreatingConversation: Boolean = false,
    val errorMessage: String? = null,
)

sealed interface ProjectEvent {
    data class OpenConversation(val threadId: String) : ProjectEvent
}

class ProjectViewModel(
    private val repository: CodexRepository,
    private val serverId: String,
    cwd: String,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ProjectUiState(projectName(cwd), cwd))
    private val mutableEvents = Channel<ProjectEvent>(Channel.BUFFERED)
    private var refreshJob: Job? = null

    val state: StateFlow<ProjectUiState> = mutableState
    val events = mutableEvents.receiveAsFlow()

    init {
        refresh()
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            mutableState.update { it.copy(isLoading = true, errorMessage = null) }
            runSuspendCatching { repository.recentThreads(serverId, mutableState.value.cwd) }
                .onSuccess { threads -> mutableState.update { it.copy(threads = threads) } }
                .onFailure(::showError)
            mutableState.update { it.copy(isLoading = false) }
        }
    }

    fun startConversation() {
        if (mutableState.value.isCreatingConversation) return
        viewModelScope.launch {
            mutableState.update { it.copy(isCreatingConversation = true, errorMessage = null) }
            val result = runSuspendCatching {
                repository.startConversation(serverId, mutableState.value.cwd)
            }
            result.exceptionOrNull()?.let(::showError)
            result.getOrNull()?.let { threadId ->
                mutableEvents.send(ProjectEvent.OpenConversation(threadId))
            }
            mutableState.update { it.copy(isCreatingConversation = false) }
        }
    }

    fun clearError() {
        mutableState.update { it.copy(errorMessage = null) }
    }

    private fun showError(error: Throwable) {
        mutableState.update { it.copy(errorMessage = error.message ?: "操作失败") }
    }

    companion object {
        fun factory(
            repository: CodexRepository,
            serverId: String,
            cwd: String,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { ProjectViewModel(repository, serverId, cwd) }
        }
    }
}
