package com.hebo.codex.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.hebo.codex.domain.CodexRepository
import com.hebo.codex.domain.ConnectionStatus
import com.hebo.codex.domain.ConversationNotice
import com.hebo.codex.domain.ModelOption
import com.hebo.codex.domain.RecentProject
import com.hebo.codex.domain.ServerProfile
import com.hebo.codex.domain.SortDirection
import com.hebo.codex.domain.ThreadOptions
import com.hebo.codex.domain.ThreadSortKey
import com.hebo.codex.domain.ThreadSourceKind
import com.hebo.codex.domain.ThreadSummary
import com.hebo.codex.domain.groupRecentProjects
import com.hebo.codex.util.runSuspendCatching
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val servers: List<ServerProfile> = emptyList(),
    val activeServerId: String? = null,
    val connectionStatuses: Map<String, ConnectionStatus> = emptyMap(),
    val threads: List<ThreadSummary> = emptyList(),
    val projects: List<RecentProject> = emptyList(),
    val models: List<ModelOption> = emptyList(),
    val serverNotices: List<ConversationNotice> = emptyList(),
    val searchTerm: String = "",
    val showArchived: Boolean = false,
    val sortKey: ThreadSortKey = ThreadSortKey.RECENCY_AT,
    val sortDirection: SortDirection = SortDirection.DESC,
    val modelProviders: List<String> = emptyList(),
    val sourceKinds: Set<ThreadSourceKind> = emptySet(),
    val nextCursor: String? = null,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
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
    private var searchJob: Job? = null

    val state: StateFlow<HomeUiState> = mutableState
    val events = mutableEvents.receiveAsFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.servers,
                repository.activeServerId,
                repository.connectionStatuses,
                repository.serverNotices,
            ) { servers, activeServerId, connectionStatuses, notices ->
                HomeDependencies(servers, activeServerId, connectionStatuses, notices[activeServerId].orEmpty())
            }.collect { value ->
                mutableState.update {
                    it.copy(
                        servers = value.servers,
                        activeServerId = value.activeServerId,
                        connectionStatuses = value.connectionStatuses,
                        serverNotices = value.notices,
                    )
                }
            }
        }
        viewModelScope.launch {
            repository.activeServerId.collect { serverId ->
                if (serverId == null) {
                    mutableState.update {
                        it.copy(threads = emptyList(), projects = emptyList(), models = emptyList(), nextCursor = null)
                    }
                } else {
                    requestLoad(serverId)
                    loadModels(serverId)
                }
            }
        }
        viewModelScope.launch {
            repository.threadRevisions.collect { revisions ->
                val serverId = mutableState.value.activeServerId ?: return@collect
                if (revisions.containsKey(serverId)) requestLoad(serverId)
            }
        }
    }

    fun selectServer(serverId: String) {
        mutableState.update { it.copy(searchTerm = "") }
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
        mutableState.value.activeServerId?.let(::requestLoad)
    }

    fun search(term: String) {
        mutableState.update { it.copy(searchTerm = term) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            refresh()
        }
    }

    fun setArchived(show: Boolean) {
        mutableState.update { it.copy(showArchived = show) }
        refresh()
    }

    fun setThreadFilters(
        sortKey: ThreadSortKey,
        sortDirection: SortDirection,
        modelProviders: List<String>,
        sourceKinds: Set<ThreadSourceKind>,
    ) {
        mutableState.update {
            it.copy(
                sortKey = sortKey,
                sortDirection = sortDirection,
                modelProviders = modelProviders,
                sourceKinds = sourceKinds,
            )
        }
        refresh()
    }

    fun loadMore() {
        val state = mutableState.value
        val serverId = state.activeServerId ?: return
        val cursor = state.nextCursor ?: return
        if (state.isLoading || state.isLoadingMore) return
        viewModelScope.launch {
            mutableState.update { it.copy(isLoadingMore = true, errorMessage = null) }
            runSuspendCatching {
                repository.threadPage(
                    serverId = serverId,
                    cursor = cursor,
                    archived = state.showArchived,
                    searchTerm = state.searchTerm,
                    sortKey = state.sortKey,
                    sortDirection = state.sortDirection,
                    modelProviders = state.modelProviders,
                    sourceKinds = state.sourceKinds,
                )
            }.onSuccess { page ->
                if (mutableState.value.activeServerId == serverId) {
                    mutableState.update { current ->
                        val threads = (current.threads + page.data).distinctBy(ThreadSummary::id)
                        current.copy(
                            threads = threads,
                            projects = groupRecentProjects(threads),
                            nextCursor = page.nextCursor,
                        )
                    }
                }
            }.onFailure(::showError)
            mutableState.update { it.copy(isLoadingMore = false) }
        }
    }

    fun startConversation(cwd: String, options: ThreadOptions = ThreadOptions()) {
        if (mutableState.value.isCreatingConversation) return
        val serverId = mutableState.value.activeServerId ?: return
        viewModelScope.launch {
            mutableState.update { it.copy(isCreatingConversation = true, errorMessage = null) }
            val result = runSuspendCatching { repository.startConversation(serverId, cwd, options) }
            result.exceptionOrNull()?.let(::showError)
            result.getOrNull()?.let { threadId ->
                mutableEvents.send(HomeEvent.OpenConversation(serverId, threadId))
            }
            mutableState.update { it.copy(isCreatingConversation = false) }
        }
    }

    fun forkConversation(threadId: String) {
        val serverId = mutableState.value.activeServerId ?: return
        viewModelScope.launch {
            runSuspendCatching { repository.forkConversation(serverId, threadId) }
                .onSuccess { mutableEvents.send(HomeEvent.OpenConversation(serverId, it)) }
                .onFailure(::showError)
        }
    }

    fun renameConversation(threadId: String, name: String) {
        val serverId = mutableState.value.activeServerId ?: return
        viewModelScope.launch {
            runSuspendCatching { repository.renameConversation(serverId, threadId, name) }
                .onSuccess { refresh() }
                .onFailure(::showError)
        }
    }

    fun archiveConversation(threadId: String) {
        val serverId = mutableState.value.activeServerId ?: return
        viewModelScope.launch {
            runSuspendCatching {
                if (mutableState.value.showArchived) repository.unarchiveConversation(serverId, threadId)
                else repository.archiveConversation(serverId, threadId)
            }.onSuccess { refresh() }.onFailure(::showError)
        }
    }

    fun deleteConversation(threadId: String) {
        val serverId = mutableState.value.activeServerId ?: return
        viewModelScope.launch {
            runSuspendCatching { repository.deleteConversation(serverId, threadId) }
                .onSuccess { refresh() }
                .onFailure(::showError)
        }
    }

    fun clearError() {
        mutableState.update { it.copy(errorMessage = null) }
    }

    private suspend fun loadThreads(serverId: String) {
        mutableState.update { it.copy(isLoading = true, errorMessage = null, nextCursor = null) }
        val query = mutableState.value
        runSuspendCatching {
            repository.threadPage(
                serverId = serverId,
                archived = query.showArchived,
                searchTerm = query.searchTerm,
                sortKey = query.sortKey,
                sortDirection = query.sortDirection,
                modelProviders = query.modelProviders,
                sourceKinds = query.sourceKinds,
            )
        }.onSuccess { page ->
            if (mutableState.value.activeServerId == serverId) {
                mutableState.update {
                    it.copy(
                        threads = page.data,
                        projects = groupRecentProjects(page.data),
                        nextCursor = page.nextCursor,
                    )
                }
            }
        }.onFailure(::showError)
        mutableState.update { it.copy(isLoading = false) }
    }

    private fun loadModels(serverId: String) {
        viewModelScope.launch {
            runSuspendCatching { repository.models(serverId) }
                .onSuccess { models ->
                    if (mutableState.value.activeServerId == serverId) mutableState.update { it.copy(models = models) }
                }
        }
    }

    private fun requestLoad(serverId: String) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch { loadThreads(serverId) }
    }

    private fun showError(error: Throwable) {
        mutableState.update { it.copy(errorMessage = error.message ?: "操作失败") }
    }

    private data class HomeDependencies(
        val servers: List<ServerProfile>,
        val activeServerId: String?,
        val connectionStatuses: Map<String, ConnectionStatus>,
        val notices: List<ConversationNotice>,
    )

    companion object {
        fun factory(repository: CodexRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeViewModel(repository) }
        }
    }
}
