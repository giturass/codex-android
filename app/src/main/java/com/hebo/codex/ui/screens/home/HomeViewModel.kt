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
import com.hebo.codex.domain.ThreadAttentionState
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

data class ThreadFilter(
    val searchTerm: String = "",
    val showArchived: Boolean = false,
) {
    val isRecent: Boolean
        get() = searchTerm.isBlank() && !showArchived
}

enum class ThreadFeedLoadState {
    IDLE,
    LOADING,
    REFRESHING,
    APPENDING,
}

data class ThreadFeed(
    val threads: List<ThreadSummary> = emptyList(),
    val nextCursor: String? = null,
    val loadState: ThreadFeedLoadState = ThreadFeedLoadState.IDLE,
) {
    val projects: List<RecentProject>
        get() = groupRecentProjects(threads)
}

data class ThreadQuery(
    val serverId: String,
    val filter: ThreadFilter = ThreadFilter(),
)

data class HomeUiState(
    val servers: List<ServerProfile> = emptyList(),
    val activeServerId: String? = null,
    val connectionStatuses: Map<String, ConnectionStatus> = emptyMap(),
    val filter: ThreadFilter = ThreadFilter(),
    val recentFeed: ThreadFeed = ThreadFeed(),
    val filteredFeed: ThreadFeed = ThreadFeed(),
    val models: List<ModelOption> = emptyList(),
    val isLoadingModels: Boolean = false,
    val modelsError: UserFacingError? = null,
    val serverNotices: List<ConversationNotice> = emptyList(),
    val threadAttention: Map<String, ThreadAttentionState> = emptyMap(),
    val drafts: List<DraftSummary> = emptyList(),
    val errorMessage: UserFacingError? = null,
) {
    val visibleFeed: ThreadFeed
        get() = if (filter.isRecent) recentFeed else filteredFeed

    val threads: List<ThreadSummary>
        get() = visibleFeed.threads

    val recentThreads: List<ThreadSummary>
        get() = recentFeed.threads

    val projects: List<RecentProject>
        get() = visibleFeed.projects

    val recentProjects: List<RecentProject>
        get() = recentFeed.projects

    val searchTerm: String
        get() = filter.searchTerm

    val showArchived: Boolean
        get() = filter.showArchived

    val nextCursor: String?
        get() = visibleFeed.nextCursor

    val recentNextCursor: String?
        get() = recentFeed.nextCursor

    val isLoading: Boolean
        get() = visibleFeed.loadState == ThreadFeedLoadState.LOADING

    val isRefreshing: Boolean
        get() = visibleFeed.loadState == ThreadFeedLoadState.REFRESHING

    val isLoadingMore: Boolean
        get() = visibleFeed.loadState == ThreadFeedLoadState.APPENDING

    val isLoadingMoreRecentProjects: Boolean
        get() = recentFeed.loadState == ThreadFeedLoadState.APPENDING

    val activeServer: ServerProfile?
        get() = servers.singleOrNull { it.id == activeServerId }

    val activeDrafts: List<DraftSummary>
        get() = drafts.filter {
            it.key.serverId == activeServerId && it.key.target is DraftTarget.Thread
        }
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
    private var appendJob: Job? = null
    private var recentProjectsJob: Job? = null
    private var modelListJob: Job? = null

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
                appendJob?.cancel()
                recentProjectsJob?.cancel()
                modelListJob?.cancel()
                modelListJob = null
                if (serverId == null) {
                    mutableState.update {
                        it.copy(
                            activeServerId = null,
                            filter = ThreadFilter(),
                            recentFeed = ThreadFeed(),
                            filteredFeed = ThreadFeed(),
                            models = emptyList(),
                            isLoadingModels = false,
                            modelsError = null,
                        )
                    }
                } else {
                    mutableState.update {
                        it.copy(
                            activeServerId = serverId,
                            filter = ThreadFilter(),
                            recentFeed = ThreadFeed(),
                            filteredFeed = ThreadFeed(),
                            models = emptyList(),
                            isLoadingModels = false,
                            modelsError = null,
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
        mutableState.update { it.copy(filter = ThreadFilter()) }
        viewModelScope.launch {
            runSuspendCatching { repository.selectServer(serverId) }
                .onFailure(::showError)
        }
    }

    fun reconnectServer(serverId: String) {
        viewModelScope.launch {
            // ConnectionCoordinator publishes failures to connectionStatuses before rethrowing them.
            runSuspendCatching { repository.connect(serverId) }
                .onSuccess {
                    if (mutableState.value.activeServerId == serverId) loadModels(serverId)
                }
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

    fun reloadThreads() {
        mutableState.value.activeServerId?.let(::requestLoad)
    }

    fun refresh() {
        val state = mutableState.value
        val serverId = state.activeServerId ?: return
        if (state.isLoading || state.isRefreshing || state.isLoadingMore) return
        requestLoad(serverId, isRefresh = true)
    }

    fun search(term: String) {
        refreshJob?.cancel()
        searchJob?.cancel()
        appendJob?.cancel()
        mutableState.update {
            val filter = it.filter.copy(searchTerm = term)
            it.copy(
                filter = filter,
                filteredFeed = if (filter.isRecent) {
                    it.filteredFeed
                } else {
                    ThreadFeed(loadState = ThreadFeedLoadState.LOADING)
                },
            )
        }
        if (mutableState.value.filter.isRecent) return
        val serverId = mutableState.value.activeServerId ?: return
        searchJob = viewModelScope.launch {
            delay(300)
            requestLoad(serverId)
        }
    }

    fun setArchived(show: Boolean) {
        refreshJob?.cancel()
        searchJob?.cancel()
        appendJob?.cancel()
        mutableState.update {
            val filter = it.filter.copy(showArchived = show)
            it.copy(
                filter = filter,
                filteredFeed = if (filter.isRecent) {
                    it.filteredFeed
                } else {
                    ThreadFeed(loadState = ThreadFeedLoadState.LOADING)
                },
            )
        }
        if (!mutableState.value.filter.isRecent) reloadThreads()
    }

    fun showRecent() {
        searchJob?.cancel()
        refreshJob?.cancel()
        appendJob?.cancel()
        mutableState.update {
            it.copy(filter = ThreadFilter())
        }
        mutableState.value.activeServerId?.let(::requestLoad)
    }

    fun loadMore() {
        val state = mutableState.value
        val serverId = state.activeServerId ?: return
        val cursor = state.nextCursor ?: return
        if (state.isLoading || state.isRefreshing || state.isLoadingMore) return
        val query = ThreadQuery(serverId, state.filter)
        appendJob = viewModelScope.launch {
            updateFeed(query) { it.copy(loadState = ThreadFeedLoadState.APPENDING) }
            mutableState.update { it.copy(errorMessage = null) }
            try {
                runSuspendCatching {
                    repository.threadPage(
                        serverId = serverId,
                        cursor = cursor,
                        archived = query.filter.showArchived,
                        searchTerm = query.filter.searchTerm,
                    )
                }.onSuccess { page ->
                    updateFeed(query) { feed ->
                        feed.copy(
                            threads = (feed.threads + page.data).distinctBy(ThreadSummary::id),
                            nextCursor = page.nextCursor,
                        )
                    }
                }.onFailure { error ->
                    if (mutableState.value.isVisible(query)) showError(error)
                }
            } finally {
                updateFeed(query) { feed ->
                    if (feed.loadState == ThreadFeedLoadState.APPENDING) {
                        feed.copy(loadState = ThreadFeedLoadState.IDLE)
                    } else {
                        feed
                    }
                }
            }
        }
    }

    fun loadMoreRecentProjects() {
        val state = mutableState.value
        val serverId = state.activeServerId ?: return
        val cursor = state.recentNextCursor ?: return
        if (state.recentFeed.loadState != ThreadFeedLoadState.IDLE) return
        val query = ThreadQuery(serverId)
        recentProjectsJob = viewModelScope.launch {
            updateFeed(query) { it.copy(loadState = ThreadFeedLoadState.APPENDING) }
            mutableState.update { it.copy(errorMessage = null) }
            try {
                runSuspendCatching {
                    repository.threadPage(serverId = serverId, cursor = cursor)
                }.onSuccess { page ->
                    updateFeed(query) { feed ->
                        if (feed.nextCursor != cursor) return@updateFeed feed
                        feed.copy(
                            threads = (feed.threads + page.data).distinctBy(ThreadSummary::id),
                            nextCursor = page.nextCursor,
                        )
                    }
                }.onFailure { error ->
                    if (mutableState.value.activeServerId == serverId) showError(error)
                }
            } finally {
                updateFeed(query) { feed ->
                    if (feed.loadState == ThreadFeedLoadState.APPENDING) {
                        feed.copy(loadState = ThreadFeedLoadState.IDLE)
                    } else {
                        feed
                    }
                }
            }
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
                .onSuccess { reloadThreads() }
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
                reloadThreads()
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
                    reloadThreads()
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

    private suspend fun loadThreads(query: ThreadQuery, isRefresh: Boolean) {
        updateFeed(query) {
            it.copy(
                loadState = if (isRefresh) ThreadFeedLoadState.REFRESHING else ThreadFeedLoadState.LOADING,
            )
        }
        mutableState.update { it.copy(errorMessage = null) }
        try {
            runSuspendCatching {
                repository.threadPage(
                    serverId = query.serverId,
                    archived = query.filter.showArchived,
                    searchTerm = query.filter.searchTerm,
                )
            }.onSuccess { page ->
                updateFeed(query) {
                    ThreadFeed(
                        threads = page.data,
                        nextCursor = page.nextCursor,
                        loadState = ThreadFeedLoadState.IDLE,
                    )
                }
            }.onFailure { error ->
                if (mutableState.value.isVisible(query)) showError(error)
            }
        } finally {
            updateFeed(query) { feed ->
                if (
                    feed.loadState == ThreadFeedLoadState.LOADING ||
                    feed.loadState == ThreadFeedLoadState.REFRESHING
                ) {
                    feed.copy(loadState = ThreadFeedLoadState.IDLE)
                } else {
                    feed
                }
            }
        }
    }

    fun retryLoadModels() {
        mutableState.value.activeServerId?.let(::loadModels)
    }

    private fun loadModels(serverId: String) {
        if (modelListJob?.isActive == true) return
        modelListJob = viewModelScope.launch {
            mutableState.update { it.copy(isLoadingModels = true, modelsError = null) }
            runSuspendCatching { repository.models(serverId) }
                .onSuccess { models ->
                    mutableState.update {
                        if (it.activeServerId == serverId) {
                            it.copy(models = models, isLoadingModels = false)
                        } else {
                            it
                        }
                    }
                }
                .onFailure { error ->
                    mutableState.update {
                        if (it.activeServerId == serverId) {
                            it.copy(
                                models = emptyList(),
                                isLoadingModels = false,
                                modelsError = error.toUserFacingError("无法加载模型和思考深度"),
                            )
                        } else {
                            it
                        }
                    }
                }
        }
    }

    private fun requestLoad(serverId: String, isRefresh: Boolean = false) {
        val state = mutableState.value
        if (state.activeServerId != serverId) return
        val query = ThreadQuery(serverId, state.filter)
        refreshJob?.cancel()
        appendJob?.cancel()
        if (query.filter.isRecent) recentProjectsJob?.cancel()
        refreshJob = viewModelScope.launch { loadThreads(query, isRefresh) }
    }

    private fun updateFeed(query: ThreadQuery, transform: (ThreadFeed) -> ThreadFeed) {
        mutableState.update { it.withFeed(query, transform) }
    }

    private fun showError(error: Throwable) {
        mutableState.update { it.copy(errorMessage = error.toUserFacingError()) }
    }

    private data class HomeDependencies(
        val servers: List<ServerProfile>,
        val activeServerId: String?,
        val connectionStatuses: Map<String, ConnectionStatus>,
        val notices: List<ConversationNotice>,
        val threadAttention: Map<String, ThreadAttentionState>,
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

internal fun HomeUiState.isVisible(query: ThreadQuery): Boolean =
    activeServerId == query.serverId && filter == query.filter

internal fun HomeUiState.withFeed(
    query: ThreadQuery,
    transform: (ThreadFeed) -> ThreadFeed,
): HomeUiState {
    if (activeServerId != query.serverId) return this
    return if (query.filter.isRecent) {
        copy(recentFeed = transform(recentFeed))
    } else if (filter == query.filter) {
        copy(filteredFeed = transform(filteredFeed))
    } else {
        this
    }
}
