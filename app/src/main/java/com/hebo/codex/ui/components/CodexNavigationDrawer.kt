@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.hebo.codex.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DismissibleDrawerSheet
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.hebo.codex.domain.ConnectionStatus
import com.hebo.codex.domain.DraftKey
import com.hebo.codex.domain.DraftSummary
import com.hebo.codex.domain.DraftTarget
import com.hebo.codex.domain.RecentProject
import com.hebo.codex.domain.ServerProfile
import com.hebo.codex.domain.ThreadRuntimeStatus
import com.hebo.codex.domain.ThreadAttention
import com.hebo.codex.domain.ThreadAttentionState
import com.hebo.codex.domain.ThreadSummary
import com.hebo.codex.domain.projectName
import com.hebo.codex.ui.theme.statusColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal val CodexNavigationDrawerWidth = 360.dp

@Composable
fun CodexNavigationDrawer(
    servers: List<ServerProfile>,
    activeServer: ServerProfile?,
    connectionStatuses: Map<String, ConnectionStatus>,
    projects: List<RecentProject>,
    threads: List<ThreadSummary>,
    recentThreads: List<ThreadSummary>,
    drafts: List<DraftSummary>,
    threadAttention: Map<String, ThreadAttentionState>,
    searchTerm: String,
    showArchived: Boolean,
    isLoading: Boolean,
    isRefreshing: Boolean,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    activeTarget: DrawerActiveTarget?,
    isWideLayout: Boolean,
    onNewConversation: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onDeleteDraft: (DraftKey) -> Unit,
    onNewProjectTask: (String) -> Unit,
    onSearch: (String) -> Unit,
    onShowArchived: (Boolean) -> Unit,
    onShowRecent: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onRenameConversation: (threadId: String, name: String) -> Unit,
    onForkConversation: (String) -> Unit,
    onArchiveConversation: (String) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onSelectServer: (String) -> Unit,
    onReconnectServer: (String) -> Unit,
    onEditServer: (String) -> Unit,
    onRemoveServer: (String) -> Unit,
    onAddServer: () -> Unit,
    onOpenSettings: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetModifier = modifier.fillMaxHeight().widthIn(max = CodexNavigationDrawerWidth)
    val content: @Composable ColumnScope.() -> Unit = {
        DrawerContent(
            servers = servers,
            activeServer = activeServer,
            connectionStatuses = connectionStatuses,
            projects = projects,
            threads = threads,
            recentThreads = recentThreads,
            drafts = drafts,
            threadAttention = threadAttention,
            searchTerm = searchTerm,
            showArchived = showArchived,
            isLoading = isLoading,
            isRefreshing = isRefreshing,
            isLoadingMore = isLoadingMore,
            hasMore = hasMore,
            activeTarget = activeTarget,
            onNewConversation = onNewConversation,
            onOpenConversation = onOpenConversation,
            onDeleteDraft = onDeleteDraft,
            onNewProjectTask = onNewProjectTask,
            onSearch = onSearch,
            onShowArchived = onShowArchived,
            onShowRecent = onShowRecent,
            onRefresh = onRefresh,
            onLoadMore = onLoadMore,
            onRenameConversation = onRenameConversation,
            onForkConversation = onForkConversation,
            onArchiveConversation = onArchiveConversation,
            onDeleteConversation = onDeleteConversation,
            onSelectServer = onSelectServer,
            onReconnectServer = onReconnectServer,
            onEditServer = onEditServer,
            onRemoveServer = onRemoveServer,
            onAddServer = onAddServer,
            onOpenSettings = onOpenSettings,
        )
    }

    if (isWideLayout) {
        DismissibleDrawerSheet(
            modifier = sheetModifier,
            drawerContainerColor = MaterialTheme.colorScheme.surface,
            content = content,
        )
    } else {
        ModalDrawerSheet(
            modifier = sheetModifier,
            drawerContainerColor = MaterialTheme.colorScheme.surface,
            content = content,
        )
    }
}

private enum class DrawerPage {
    RECENT,
    SEARCH,
}

sealed interface DrawerActiveTarget {
    val serverId: String

    data class Thread(
        override val serverId: String,
        val threadId: String,
    ) : DrawerActiveTarget
}

private data class ThreadActionSelection(
    val threadId: String,
    val title: String,
    val archived: Boolean,
    val draftKey: DraftKey? = null,
)

@Composable
private fun ColumnScope.DrawerContent(
    servers: List<ServerProfile>,
    activeServer: ServerProfile?,
    connectionStatuses: Map<String, ConnectionStatus>,
    projects: List<RecentProject>,
    threads: List<ThreadSummary>,
    recentThreads: List<ThreadSummary>,
    drafts: List<DraftSummary>,
    threadAttention: Map<String, ThreadAttentionState>,
    searchTerm: String,
    showArchived: Boolean,
    isLoading: Boolean,
    isRefreshing: Boolean,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    activeTarget: DrawerActiveTarget?,
    onNewConversation: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onDeleteDraft: (DraftKey) -> Unit,
    onNewProjectTask: (String) -> Unit,
    onSearch: (String) -> Unit,
    onShowArchived: (Boolean) -> Unit,
    onShowRecent: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onRenameConversation: (threadId: String, name: String) -> Unit,
    onForkConversation: (String) -> Unit,
    onArchiveConversation: (String) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onSelectServer: (String) -> Unit,
    onReconnectServer: (String) -> Unit,
    onEditServer: (String) -> Unit,
    onRemoveServer: (String) -> Unit,
    onAddServer: () -> Unit,
    onOpenSettings: (String) -> Unit,
) {
    var drawerPage by rememberSaveable(activeServer?.id) { mutableStateOf(DrawerPage.RECENT) }
    var groupByProject by rememberSaveable { mutableStateOf(false) }
    var expandedProjects by remember(activeServer?.id) { mutableStateOf(emptySet<String>()) }
    var selectedThread by remember(activeServer?.id) { mutableStateOf<ThreadActionSelection?>(null) }
    val recentListState = key(activeServer?.id, DrawerPage.RECENT) { rememberLazyListState() }
    val searchListState = key(activeServer?.id, DrawerPage.SEARCH) { rememberLazyListState() }
    val drawerListState = when (drawerPage) {
        DrawerPage.RECENT -> recentListState
        DrawerPage.SEARCH -> searchListState
    }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    fun leaveSearch() {
        focusManager.clearFocus()
        keyboardController?.hide()
        onShowRecent()
    }

    fun showRecent() {
        if (drawerPage == DrawerPage.SEARCH) leaveSearch()
        drawerPage = DrawerPage.RECENT
    }

    fun leaveTransientPage() {
        showRecent()
    }

    val activeThreadId = (activeTarget as? DrawerActiveTarget.Thread)
        ?.takeIf { it.serverId == activeServer?.id }
        ?.threadId

    val drawerBody: @Composable ColumnScope.(PaddingValues) -> Unit = { listContentPadding ->
        if (servers.isNotEmpty() && drawerPage == DrawerPage.SEARCH) {
            DrawerSearchHeader(
                searchTerm = searchTerm,
                showArchived = showArchived,
                onSearch = onSearch,
                onShowArchived = onShowArchived,
                onBack = ::showRecent,
            )
        }
        if (isLoading && !isRefreshing) LinearProgressIndicator(Modifier.fillMaxWidth())

        RefreshableDrawerList(
            state = drawerListState,
            isRefreshing = isRefreshing,
            enabled = activeServer != null &&
                !isLoading &&
                !isLoadingMore &&
                (drawerPage == DrawerPage.RECENT || searchTerm.isNotBlank() || showArchived),
            onRefresh = onRefresh,
            modifier = Modifier.weight(1f),
            contentPadding = listContentPadding,
        ) {
            val threadDrafts = threadDraftsById(drafts)
            val unlistedDrafts = unlistedThreadDrafts(recentThreads, drafts)
            if (activeServer != null && drawerPage == DrawerPage.RECENT) {
                item("recent-title") {
                    DrawerRecentHeader(
                        groupByProject = groupByProject,
                        refreshEnabled = !isLoading && !isLoadingMore,
                        onNewConversation = onNewConversation,
                        onSearch = { drawerPage = DrawerPage.SEARCH },
                        onGroupByProjectChange = { enabled ->
                            groupByProject = enabled
                            if (enabled) {
                                expandedProjects = emptySet()
                            }
                        },
                        onRefresh = onRefresh,
                    )
                }
                items(unlistedDrafts, key = { "draft-thread:${it.key.target.id}" }) { draft ->
                    val threadId = draft.key.target.id
                    DrawerDraftThreadItem(
                        draft = draft,
                        attention = threadAttention[threadId]?.attention,
                        selected = isThreadSelected(activeServer.id, threadId, activeTarget),
                        onClick = {
                            leaveTransientPage()
                            onOpenConversation(threadId)
                        },
                        onMenu = {
                            selectedThread = ThreadActionSelection(
                                threadId = threadId,
                                title = draft.contextTitle,
                                archived = false,
                                draftKey = draft.key,
                            )
                        },
                    )
                }
            }
            when {
                activeServer == null && servers.isNotEmpty() -> {
                    item("no-active-server") {
                        DrawerEmptyMessage("请选择服务器")
                    }
                }

                activeServer != null && drawerPage == DrawerPage.SEARCH -> {
                    val hasQuery = searchTerm.isNotBlank() || showArchived
                    if (!hasQuery) {
                        item("search-hint") { DrawerEmptyMessage("输入关键词搜索会话") }
                    } else {
                        item("search-title") {
                            val title = when {
                                showArchived && searchTerm.isNotBlank() -> "已归档搜索结果"
                                showArchived -> "已归档会话"
                                else -> "搜索结果"
                            }
                            DrawerSectionTitle(
                                text = title,
                                refreshEnabled = !isLoading && !isLoadingMore,
                                onRefresh = onRefresh,
                            )
                        }
                        if (!isLoading && threads.isEmpty()) {
                            item("search-empty") { DrawerEmptyMessage("没有匹配的会话") }
                        }
                        items(threads, key = ThreadSummary::id) { thread ->
                            val draft = threadDrafts[thread.id]
                            DrawerThreadItem(
                                thread = thread,
                                draft = draft,
                                attention = threadAttention[thread.id]?.attention,
                                selected = isThreadSelected(activeServer.id, thread.id, activeTarget),
                                showProject = true,
                                onClick = {
                                    leaveTransientPage()
                                    onOpenConversation(thread.id)
                                },
                                onMenu = {
                                    selectedThread = ThreadActionSelection(
                                        threadId = thread.id,
                                        title = thread.title,
                                        archived = showArchived,
                                        draftKey = draft?.key,
                                    )
                                },
                            )
                        }
                    }
                }

                activeServer != null && groupByProject -> {
                    if (!isLoading && projects.isEmpty() && unlistedDrafts.isEmpty()) {
                        item("grouped-empty") {
                            DrawerEmptyMessage("还没有会话")
                        }
                    }
                    items(projects, key = RecentProject::path) { project ->
                        val expanded = project.path in expandedProjects
                        val active = project.threads.any { it.id == activeThreadId }
                        Column {
                            ProjectHeader(
                                project = project,
                                expanded = expanded,
                                active = active,
                                onToggle = {
                                    expandedProjects = if (expanded) {
                                        expandedProjects - project.path
                                    } else {
                                        expandedProjects + project.path
                                    }
                                },
                                onNewTask = {
                                    leaveTransientPage()
                                    onNewProjectTask(project.path)
                                },
                            )
                            if (expanded) {
                                project.threads.forEach { thread ->
                                    val draft = threadDrafts[thread.id]
                                    DrawerThreadItem(
                                        thread = thread,
                                        draft = draft,
                                        attention = threadAttention[thread.id]?.attention,
                                        selected = isThreadSelected(activeServer.id, thread.id, activeTarget),
                                        showProject = false,
                                        onClick = {
                                            leaveTransientPage()
                                            onOpenConversation(thread.id)
                                        },
                                        onMenu = {
                                            selectedThread = ThreadActionSelection(
                                                threadId = thread.id,
                                                title = thread.title,
                                                archived = false,
                                                draftKey = draft?.key,
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                activeServer != null -> {
                    if (!isLoading && recentThreads.isEmpty() && unlistedDrafts.isEmpty()) {
                        item("recent-empty") {
                            DrawerEmptyMessage("还没有会话")
                        }
                    }
                    items(recentThreads, key = ThreadSummary::id) { thread ->
                        val draft = threadDrafts[thread.id]
                        DrawerThreadItem(
                            thread = thread,
                            draft = draft,
                            attention = threadAttention[thread.id]?.attention,
                            selected = isThreadSelected(activeServer.id, thread.id, activeTarget),
                            showProject = true,
                            onClick = { onOpenConversation(thread.id) },
                            onMenu = {
                                selectedThread = ThreadActionSelection(
                                    threadId = thread.id,
                                    title = thread.title,
                                    archived = false,
                                    draftKey = draft?.key,
                                )
                            },
                        )
                    }
                }
            }

            val canLoadMore = drawerPage != DrawerPage.SEARCH || searchTerm.isNotBlank() || showArchived
            if (hasMore && canLoadMore) {
                item("load-more") {
                    TextButton(
                        onClick = onLoadMore,
                        enabled = !isLoading && !isLoadingMore,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    ) {
                        if (isLoadingMore) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("加载更多")
                        }
                    }
                }
            }
        }
    }

    Column(Modifier.fillMaxHeight().padding(horizontal = 12.dp)) {
        Spacer(Modifier.height(12.dp))
        if (servers.isEmpty()) {
            Text(
                text = "Codex",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleLarge,
            )
            NavigationDrawerItem(
                label = { Text("添加服务器") },
                selected = false,
                onClick = onAddServer,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
            )
            drawerBody(PaddingValues())
        } else {
            CollapsibleDrawerServerFooter(
                modifier = Modifier.weight(1f),
                resetKey = activeServer?.id,
                forceVisible = !drawerListState.canScrollBackward,
                footer = { footerModifier ->
                    Column(footerModifier) {
                        Spacer(Modifier.height(8.dp))
                        ServerSelector(
                            servers = servers,
                            activeServer = activeServer,
                            statuses = connectionStatuses,
                            onSelect = onSelectServer,
                            onReconnect = onReconnectServer,
                            onEdit = onEditServer,
                            onDelete = onRemoveServer,
                            onOpenSettings = { serverId ->
                                leaveTransientPage()
                                onOpenSettings(serverId)
                            },
                            onAdd = onAddServer,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                },
                content = drawerBody,
            )
        }
    }

    selectedThread?.let { thread ->
        ThreadActionsDialog(
            threadId = thread.threadId,
            title = thread.title,
            archived = thread.archived,
            onDismiss = { selectedThread = null },
            onRename = {
                onRenameConversation(thread.threadId, it)
                selectedThread = null
            },
            onFork = {
                onForkConversation(thread.threadId)
                selectedThread = null
            },
            onArchive = {
                onArchiveConversation(thread.threadId)
                selectedThread = null
            },
            onDelete = {
                onDeleteConversation(thread.threadId)
                selectedThread = null
            },
            onDiscardDraft = thread.draftKey?.let { draftKey ->
                {
                    onDeleteDraft(draftKey)
                    selectedThread = null
                }
            },
        )
    }
}

private const val DRAWER_SERVER_FOOTER_SETTLE_DURATION_MILLIS = 160

internal fun ScrollChromeState.afterDrawerUserScroll(
    deltaY: Float,
    hideDistance: Float,
): ScrollChromeState = afterUserScroll(
    hideDelta = -deltaY,
    hideDistance = hideDistance,
)

@Composable
private fun CollapsibleDrawerServerFooter(
    resetKey: Any?,
    forceVisible: Boolean,
    footer: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.(PaddingValues) -> Unit,
) {
    var footerHeightPx by remember { mutableStateOf(0) }
    var scrollState by remember(resetKey) { mutableStateOf(ScrollChromeState()) }
    var settleJob by remember(resetKey) { mutableStateOf<Job?>(null) }
    val scrollScope = rememberCoroutineScope()
    val density = LocalDensity.current

    LaunchedEffect(forceVisible) {
        if (forceVisible) {
            settleJob?.cancel()
            settleJob = null
            scrollState = ScrollChromeState()
        }
    }
    LaunchedEffect(footerHeightPx) {
        val hideDistance = footerHeightPx.toFloat()
        scrollState = if (scrollState.fullyHidden) {
            scrollState.copy(offset = hideDistance)
        } else {
            scrollState.copy(offset = scrollState.offset.coerceAtMost(hideDistance))
        }
    }

    val scrollConnection = remember(resetKey, footerHeightPx) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source == NestedScrollSource.UserInput && consumed.y != 0f) {
                    settleJob?.cancel()
                    settleJob = null
                    scrollState = scrollState.afterDrawerUserScroll(
                        deltaY = consumed.y,
                        hideDistance = footerHeightPx.toFloat(),
                    )
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val hideDistance = footerHeightPx.toFloat()
                val targetOffset = scrollState.settleTarget(hideDistance)
                val targetHidden = hideDistance > 0f && targetOffset == hideDistance
                scrollState = scrollState.copy(settledHidden = targetHidden)
                if (targetOffset != scrollState.offset) {
                    val initialOffset = scrollState.offset
                    settleJob?.cancel()
                    settleJob = scrollScope.launch {
                        animate(
                            initialValue = initialOffset,
                            targetValue = targetOffset,
                            animationSpec = tween(DRAWER_SERVER_FOOTER_SETTLE_DURATION_MILLIS),
                        ) { value, _ ->
                            scrollState = scrollState.copy(offset = value)
                        }
                        scrollState = if (targetOffset == 0f) {
                            ScrollChromeState()
                        } else {
                            ScrollChromeState(
                                fullyHidden = true,
                                offset = targetOffset,
                                settledHidden = true,
                            )
                        }
                    }
                }
                return Velocity.Zero
            }
        }
    }
    val footerHeight = with(density) { footerHeightPx.toDp() }
    val listContentPadding = PaddingValues(
        bottom = footerHeight.takeUnless { scrollState.fullyHidden } ?: 0.dp,
    )

    Box(modifier.clipToBounds().nestedScroll(scrollConnection)) {
        Column(Modifier.fillMaxSize()) {
            content(listContentPadding)
        }
        footer(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { footerHeightPx = it.height }
                .graphicsLayer { translationY = scrollState.offset },
        )
    }
}

@Composable
private fun RefreshableDrawerList(
    state: LazyListState,
    isRefreshing: Boolean,
    enabled: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    content: LazyListScope.() -> Unit,
) {
    if (enabled || isRefreshing) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = modifier,
        ) {
            LazyColumn(
                state = state,
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding,
                content = content,
            )
        }
    } else {
        LazyColumn(
            state = state,
            modifier = modifier.fillMaxSize(),
            contentPadding = contentPadding,
            content = content,
        )
    }
}

@Composable
private fun DrawerRecentHeader(
    groupByProject: Boolean,
    refreshEnabled: Boolean,
    onNewConversation: () -> Unit,
    onSearch: () -> Unit,
    onGroupByProjectChange: (Boolean) -> Unit,
    onRefresh: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "最近会话",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IconButton(onClick = onNewConversation) {
            Icon(Icons.Rounded.Add, contentDescription = "新建任务")
        }
        if (groupByProject) {
            IconButton(onClick = { onGroupByProjectChange(false) }) {
                Icon(Icons.Rounded.Folder, contentDescription = "关闭按项目分组")
            }
        }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "更多会话操作")
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("搜索会话") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onSearch()
                    },
                )
                if (!groupByProject) {
                    DropdownMenuItem(
                        text = { Text("按项目分组") },
                        leadingIcon = { Icon(Icons.Rounded.Folder, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onGroupByProjectChange(true)
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text("刷新会话列表") },
                    leadingIcon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                    enabled = refreshEnabled,
                    onClick = {
                        menuExpanded = false
                        onRefresh()
                    },
                )
            }
        }
    }
}

@Composable
private fun DrawerSearchHeader(
    searchTerm: String,
    showArchived: Boolean,
    onSearch: (String) -> Unit,
    onShowArchived: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "退出搜索")
            }
            OutlinedTextField(
                value = searchTerm,
                onValueChange = onSearch,
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                placeholder = { Text("搜索会话") },
                trailingIcon = {
                    if (searchTerm.isNotBlank()) {
                        IconButton(onClick = { onSearch("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = "清除搜索")
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
            )
        }
        Row(
            modifier = Modifier.padding(start = 52.dp, top = 4.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = !showArchived,
                onClick = { if (showArchived) onShowArchived(false) },
                label = { Text("当前") },
            )
            FilterChip(
                selected = showArchived,
                onClick = { if (!showArchived) onShowArchived(true) },
                label = { Text("已归档") },
            )
        }
    }
}

@Composable
private fun ProjectHeader(
    project: RecentProject,
    expanded: Boolean,
    active: Boolean,
    onToggle: () -> Unit,
    onNewTask: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(start = 8.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
            contentDescription = if (expanded) "收起项目" else "展开项目",
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            Icons.Rounded.Folder,
            contentDescription = null,
            modifier = Modifier.padding(horizontal = 8.dp),
            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.weight(1f)) {
            Text(
                project.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                project.path,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onNewTask) {
            Icon(
                Icons.Rounded.Add,
                contentDescription = "在「${project.name}」中新建会话",
            )
        }
    }
}

internal fun threadDraftsById(
    drafts: List<DraftSummary>,
): Map<String, DraftSummary> = buildMap {
    drafts.forEach { draft ->
        val threadId = (draft.key.target as? DraftTarget.Thread)?.id ?: return@forEach
        put(threadId, draft)
    }
}

internal fun unlistedThreadDrafts(
    threads: List<ThreadSummary>,
    drafts: List<DraftSummary>,
): List<DraftSummary> {
    val listedThreadIds = threads.mapTo(mutableSetOf(), ThreadSummary::id)
    return drafts.filter { draft ->
        val threadId = (draft.key.target as? DraftTarget.Thread)?.id
        threadId != null && threadId !in listedThreadIds
    }
}

internal fun isThreadSelected(
    serverId: String,
    threadId: String,
    activeTarget: DrawerActiveTarget?,
): Boolean = activeTarget is DrawerActiveTarget.Thread &&
    activeTarget.serverId == serverId &&
    activeTarget.threadId == threadId

@Composable
private fun DrawerDraftThreadItem(
    draft: DraftSummary,
    attention: ThreadAttention?,
    selected: Boolean,
    onClick: () -> Unit,
    onMenu: () -> Unit,
) {
    DrawerConversationItem(
        title = draft.contextTitle,
        subtitle = buildList {
            draft.cwd.takeIf(String::isNotBlank)?.let { add(projectName(it)) }
            draft.preview.takeIf(String::isNotBlank)?.let { add("草稿：$it") }
            add(relativeTime(draft.updatedAtMillis / 1_000))
        }.joinToString(" · "),
        selected = selected,
        nested = false,
        onClick = onClick,
        onLongClickLabel = "会话操作",
        onLongClick = onMenu,
        leadingContent = {
            Icon(
                Icons.Rounded.EditNote,
                contentDescription = "有未发送草稿",
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = { ThreadStatusIndicator(attention) },
    )
}

@Composable
private fun DrawerThreadItem(
    thread: ThreadSummary,
    draft: DraftSummary? = null,
    attention: ThreadAttention?,
    selected: Boolean,
    showProject: Boolean,
    onClick: () -> Unit,
    onMenu: () -> Unit,
) {
    val effectiveAttention = effectiveThreadAttention(thread, attention)
    DrawerConversationItem(
        title = thread.title,
        subtitle = buildString {
            if (showProject) {
                append(projectName(thread.cwd))
                append(" · ")
            }
            append(relativeTime(thread.recencyAt ?: thread.updatedAt))
        },
        selected = selected,
        nested = !showProject,
        onClick = onClick,
        onLongClickLabel = "会话操作",
        onLongClick = onMenu,
        leadingContent = draft?.let {
            {
                Icon(
                    Icons.Rounded.EditNote,
                    contentDescription = "有未发送草稿",
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        trailingContent = { ThreadStatusIndicator(effectiveAttention) },
    )
}

internal fun effectiveThreadAttention(
    thread: ThreadSummary,
    attention: ThreadAttention?,
): ThreadAttention? = attention ?: when (thread.status) {
    ThreadRuntimeStatus.ACTIVE -> ThreadAttention.RUNNING
    ThreadRuntimeStatus.SYSTEM_ERROR -> ThreadAttention.FAILED
    else -> null
}

@Composable
private fun ThreadStatusIndicator(attention: ThreadAttention?) {
    val label = when (attention) {
        ThreadAttention.RUNNING -> "运行中"
        ThreadAttention.NEEDS_INPUT -> "待处理"
        ThreadAttention.READY -> "可审阅"
        ThreadAttention.FAILED -> "失败"
        null -> null
    }
    val modifier = if (label == null) {
        Modifier.size(32.dp)
    } else {
        Modifier.size(32.dp).semantics { contentDescription = label }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when (attention) {
            ThreadAttention.RUNNING -> CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = MaterialTheme.colorScheme.onSurface,
                strokeWidth = 2.dp,
            )

            ThreadAttention.NEEDS_INPUT -> Icon(
                imageVector = Icons.Rounded.PriorityHigh,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.statusColors.warning,
            )

            ThreadAttention.READY -> Icon(
                imageVector = Icons.Rounded.Circle,
                contentDescription = null,
                modifier = Modifier.size(10.dp),
                tint = MaterialTheme.statusColors.info,
            )

            ThreadAttention.FAILED -> Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.error,
            )

            null -> Unit
        }
    }
}

@Composable
private fun DrawerSectionTitle(
    text: String,
    refreshEnabled: Boolean = false,
    onRefresh: (() -> Unit)? = null,
) {
    if (onRefresh == null) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = onRefresh, enabled = refreshEnabled) {
                Icon(Icons.Rounded.Refresh, contentDescription = "刷新会话列表")
            }
        }
    }
}

@Composable
private fun DrawerEmptyMessage(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 28.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ThreadActionsDialog(
    threadId: String,
    title: String,
    archived: Boolean,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onFork: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onDiscardDraft: (() -> Unit)? = null,
) {
    var name by rememberSaveable(threadId) { mutableStateOf(title) }
    var confirmDelete by rememberSaveable(threadId) { mutableStateOf(false) }
    var confirmDiscardDraft by rememberSaveable(threadId) { mutableStateOf(false) }

    if (confirmDiscardDraft) {
        AlertDialog(
            onDismissRequest = { confirmDiscardDraft = false },
            title = { Text("放弃草稿？") },
            text = { Text("草稿内容将被删除，会话本身会保留") },
            confirmButton = {
                TextButton(onClick = requireNotNull(onDiscardDraft)) {
                    Text("放弃草稿", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscardDraft = false }) { Text("取消") }
            },
        )
    } else if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("永久删除会话") },
            text = { Text("确定删除“$title”吗？删除后无法恢复") },
            confirmButton = {
                TextButton(onClick = onDelete) {
                    Text("永久删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            },
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("会话操作") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("名称") },
                        singleLine = true,
                    )
                    Button(
                        onClick = { onRename(name) },
                        enabled = name.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("保存名称")
                    }
                    OutlinedButton(onClick = onFork, modifier = Modifier.fillMaxWidth()) {
                        Text("分叉会话")
                    }
                    OutlinedButton(onClick = onArchive, modifier = Modifier.fillMaxWidth()) {
                        Text(if (archived) "恢复归档" else "归档")
                    }
                    if (onDiscardDraft != null) {
                        TextButton(
                            onClick = { confirmDiscardDraft = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("放弃草稿", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    TextButton(
                        onClick = { confirmDelete = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("永久删除", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
        )
    }
}
