package com.hebo.codex.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.hebo.codex.domain.CodexRepository
import com.hebo.codex.domain.ConnectionStatus
import com.hebo.codex.domain.ConversationNotice
import com.hebo.codex.domain.DraftKey
import com.hebo.codex.domain.DraftRepository
import com.hebo.codex.domain.DraftSummary
import com.hebo.codex.domain.DraftTarget
import com.hebo.codex.domain.ModelOption
import com.hebo.codex.domain.RecentProject
import com.hebo.codex.domain.ServerProfile
import com.hebo.codex.domain.ThreadAttention
import com.hebo.codex.domain.ThreadSummary
import com.hebo.codex.domain.UserFacingError
import com.hebo.codex.domain.groupRecentProjects
import com.hebo.codex.util.runSuspendCatching
import com.hebo.codex.util.toUserFacingError
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
    val recentThreads: List<ThreadSummary> = emptyList(),
    val projects: List<RecentProject> = emptyList(),
    val recentProjects: List<RecentProject> = emptyList(),
    val models: List<ModelOption> = emptyList(),
    val serverNotices: List<ConversationNotice> = emptyList(),
    val threadAttention: Map<String, ThreadAttention> = emptyMap(),
    val drafts: List<DraftSummary> = emptyList(),
    val searchTerm: String = "",
    val showArchived: Boolean = false,
    val nextCursor: String? = null,
    val recentNextCursor: String? = null,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val errorMessage: UserFacingError? = null,
) {
    val activeServer: ServerProfile?
        get() = servers.singleOrNull { it.id == activeServerId }

    val activeDrafts: List<DraftSummary>
        get() = drafts.filter { it.key.serverId == activeServerId }
}

sealed interface HomeEvent {
    data class OpenConversation(val serverId: String, val threadId: String) : HomeEvent
    data class ConversationRemoved(val serverId: String, val threadId: String) : HomeEvent
}

class HomeViewModel(
    private val repository: CodexRepository,
    private val draftRepository: DraftRepository,
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
                repository.threadAttention,
            ) { servers, activeServerId, connectionStatuses, notices, attention ->
                HomeDependencies(
                    servers,
                    activeServerId,
                    connectionStatuses,
                    notices[activeServerId].orEmpty(),
                    attention[activeServerId].orEmpty(),
                )
            }.collect { value ->
                mutableState.update {
                    it.copy(
                        servers = value.servers,
                        activeServerId = value.activeServerId,
                        connectionStatuses = value.connectionStatuses,
                        serverNotices = value.notices,
                        threadAttention = value.threadAttention,
                    )
                }
            }
        }
        viewModelScope.launch {
            draftRepository.summaries.collect { drafts ->
                mutableState.update { it.copy(drafts = drafts) }
            }
        }
        viewModelScope.launch {
            repository.activeServerId.collect { serverId ->
                refreshJob?.cancel()
                searchJob?.cancel()
                if (serverId == null) {
                    mutableState.update {
                        it.copy(
                            threads = emptyList(),
                            recentThreads = emptyList(),
                            projects = emptyList(),
                            recentProjects = emptyList(),
                            models = emptyList(),
                            nextCursor = null,
                            recentNextCursor = null,
                            isLoading = false,
                            isLoadingMore = false,
                        )
                    }
                } else {
                    mutableState.update {
                        it.copy(
                            threads = emptyList(),
                            recentThreads = emptyList(),
                            projects = emptyList(),
                            recentProjects = emptyList(),
                            models = emptyList(),
                            searchTerm = "",
                            showArchived = false,
                            nextCursor = null,
                            recentNextCursor = null,
                            isLoadingMore = false,
                        )
                    }
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
            runSuspendCatching {
                repository.removeServer(serverId)
                draftRepository.deleteServer(serverId)
            }
                .onFailure(::showError)
        }
    }

    fun refresh() {
        mutableState.value.activeServerId?.let(::requestLoad)
    }

    fun search(term: String) {
        refreshJob?.cancel()
        searchJob?.cancel()
        mutableState.update {
            val enteringSearch = it.searchTerm.isBlank() && term.isNotBlank() && !it.showArchived
            it.copy(
                threads = if (enteringSearch) emptyList() else it.threads,
                projects = if (enteringSearch) emptyList() else it.projects,
                searchTerm = term,
                nextCursor = null,
                isLoading = term.isNotBlank() || it.showArchived,
            )
        }
        if (term.isBlank() && !mutableState.value.showArchived) {
            restoreRecentState()
            return
        }
        val serverId = mutableState.value.activeServerId ?: return
        searchJob = viewModelScope.launch {
            delay(300)
            requestLoad(serverId)
        }
    }

    fun setArchived(show: Boolean) {
        refreshJob?.cancel()
        searchJob?.cancel()
        mutableState.update {
            val changingArchive = it.showArchived != show
            it.copy(
                threads = if (changingArchive) emptyList() else it.threads,
                projects = if (changingArchive) emptyList() else it.projects,
                showArchived = show,
                nextCursor = null,
                isLoading = show || it.searchTerm.isNotBlank(),
            )
        }
        if (!show && mutableState.value.searchTerm.isBlank()) {
            restoreRecentState()
        } else {
            refresh()
        }
    }

    fun showRecent() {
        searchJob?.cancel()
        refreshJob?.cancel()
        mutableState.update {
            it.copy(
                threads = it.recentThreads,
                projects = it.recentProjects,
                searchTerm = "",
                showArchived = false,
                nextCursor = it.recentNextCursor,
                isLoading = false,
                isLoadingMore = false,
            )
        }
        mutableState.value.activeServerId?.let(::requestLoad)
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
                )
            }.onSuccess { page ->
                val current = mutableState.value
                if (
                    current.activeServerId == serverId &&
                    current.searchTerm == state.searchTerm &&
                    current.showArchived == state.showArchived
                ) {
                    mutableState.update { current ->
                        val threads = (current.threads + page.data).distinctBy(ThreadSummary::id)
                        val projects = groupRecentProjects(threads)
                        val recentQuery = !state.showArchived && state.searchTerm.isBlank()
                        current.copy(
                            threads = threads,
                            recentThreads = if (recentQuery) threads else current.recentThreads,
                            projects = projects,
                            recentProjects = if (recentQuery) projects else current.recentProjects,
                            nextCursor = page.nextCursor,
                            recentNextCursor = if (recentQuery) page.nextCursor else current.recentNextCursor,
                        )
                    }
                }
            }.onFailure(::showError)
            mutableState.update { it.copy(isLoadingMore = false) }
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
            runSuspendCatching {
                repository.renameConversation(serverId, threadId, name)
                draftRepository.renameThread(serverId, threadId, name.trim())
            }
                .onSuccess { refresh() }
                .onFailure(::showError)
        }
    }

    fun archiveConversation(threadId: String) {
        val serverId = mutableState.value.activeServerId ?: return
        val archived = mutableState.value.showArchived
        viewModelScope.launch {
            runSuspendCatching {
                if (archived) repository.unarchiveConversation(serverId, threadId)
                else repository.archiveConversation(serverId, threadId)
            }.onSuccess {
                refresh()
                if (!archived) mutableEvents.send(HomeEvent.ConversationRemoved(serverId, threadId))
            }.onFailure(::showError)
        }
    }

    fun deleteConversation(threadId: String) {
        val serverId = mutableState.value.activeServerId ?: return
        viewModelScope.launch {
            runSuspendCatching { repository.deleteConversation(serverId, threadId) }
                .onSuccess {
                    runSuspendCatching {
                        draftRepository.delete(DraftKey(serverId, DraftTarget.Thread(threadId)))
                    }.onFailure(::showError)
                    refresh()
                    mutableEvents.send(HomeEvent.ConversationRemoved(serverId, threadId))
                }
                .onFailure(::showError)
        }
    }

    fun deleteDraft(key: DraftKey) {
        viewModelScope.launch {
            runSuspendCatching { draftRepository.delete(key) }
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
            )
        }.onSuccess { page ->
            val current = mutableState.value
            if (
                current.activeServerId == serverId &&
                current.searchTerm == query.searchTerm &&
                current.showArchived == query.showArchived
            ) {
                mutableState.update {
                    val projects = groupRecentProjects(page.data)
                    val recentQuery = !query.showArchived && query.searchTerm.isBlank()
                    it.copy(
                        threads = page.data,
                        recentThreads = if (recentQuery) page.data else it.recentThreads,
                        projects = projects,
                        recentProjects = if (recentQuery) projects else it.recentProjects,
                        nextCursor = page.nextCursor,
                        recentNextCursor = if (recentQuery) page.nextCursor else it.recentNextCursor,
                    )
                }
            }
        }.onFailure(::showError)
        mutableState.update {
            if (
                it.activeServerId == serverId &&
                it.searchTerm == query.searchTerm &&
                it.showArchived == query.showArchived
            ) {
                it.copy(isLoading = false)
            } else {
                it
            }
        }
    }

    private fun restoreRecentState() {
        mutableState.update {
            it.copy(
                threads = it.recentThreads,
                projects = it.recentProjects,
                nextCursor = it.recentNextCursor,
                isLoading = false,
                isLoadingMore = false,
            )
        }
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
        mutableState.update { it.copy(errorMessage = error.toUserFacingError()) }
    }

    private data class HomeDependencies(
        val servers: List<ServerProfile>,
        val activeServerId: String?,
        val connectionStatuses: Map<String, ConnectionStatus>,
        val notices: List<ConversationNotice>,
        val threadAttention: Map<String, ThreadAttention>,
    )

    companion object {
        fun factory(
            repository: CodexRepository,
            draftRepository: DraftRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeViewModel(repository, draftRepository) }
        }
    }
}
