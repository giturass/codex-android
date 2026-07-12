@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.hebo.codex.ui.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PermanentDrawerSheet
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hebo.codex.domain.ConnectionStatus
import com.hebo.codex.domain.DraftKey
import com.hebo.codex.domain.DraftSummary
import com.hebo.codex.domain.DraftTarget
import com.hebo.codex.domain.RecentProject
import com.hebo.codex.domain.ServerProfile
import com.hebo.codex.domain.ThreadRuntimeStatus
import com.hebo.codex.domain.ThreadAttention
import com.hebo.codex.domain.ThreadSummary
import com.hebo.codex.domain.projectName
import com.hebo.codex.ui.theme.statusColors

@Composable
fun CodexNavigationDrawer(
    servers: List<ServerProfile>,
    activeServer: ServerProfile?,
    connectionStatuses: Map<String, ConnectionStatus>,
    projects: List<RecentProject>,
    threads: List<ThreadSummary>,
    recentThreads: List<ThreadSummary>,
    drafts: List<DraftSummary>,
    threadAttention: Map<String, ThreadAttention>,
    searchTerm: String,
    showArchived: Boolean,
    isLoading: Boolean,
    isRefreshing: Boolean,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    activeThreadId: String?,
    newTaskSelected: Boolean,
    isPermanent: Boolean,
    onNewConversation: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onOpenDraft: (DraftSummary) -> Unit,
    onDeleteDraft: (DraftKey) -> Unit,
    onOpenProjectSettings: (String) -> Unit,
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
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetModifier = modifier.fillMaxHeight().widthIn(max = 360.dp)
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
            activeThreadId = activeThreadId,
            newTaskSelected = newTaskSelected,
            onNewConversation = onNewConversation,
            onOpenConversation = onOpenConversation,
            onOpenDraft = onOpenDraft,
            onDeleteDraft = onDeleteDraft,
            onOpenProjectSettings = onOpenProjectSettings,
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

    if (isPermanent) {
        PermanentDrawerSheet(
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

@Composable
private fun ColumnScope.DrawerContent(
    servers: List<ServerProfile>,
    activeServer: ServerProfile?,
    connectionStatuses: Map<String, ConnectionStatus>,
    projects: List<RecentProject>,
    threads: List<ThreadSummary>,
    recentThreads: List<ThreadSummary>,
    drafts: List<DraftSummary>,
    threadAttention: Map<String, ThreadAttention>,
    searchTerm: String,
    showArchived: Boolean,
    isLoading: Boolean,
    isRefreshing: Boolean,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    activeThreadId: String?,
    newTaskSelected: Boolean,
    onNewConversation: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onOpenDraft: (DraftSummary) -> Unit,
    onDeleteDraft: (DraftKey) -> Unit,
    onOpenProjectSettings: (String) -> Unit,
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
    onOpenSettings: () -> Unit,
) {
    var drawerPage by rememberSaveable(activeServer?.id) { mutableStateOf(DrawerPage.RECENT) }
    var groupByProject by rememberSaveable { mutableStateOf(false) }
    var expandedProjects by remember(activeServer?.id) { mutableStateOf(emptySet<String>()) }
    var expansionInitialized by remember(activeServer?.id) { mutableStateOf(false) }
    var selectedThread by remember(activeServer?.id) { mutableStateOf<ThreadSummary?>(null) }
    var selectedDraft by remember(activeServer?.id) { mutableStateOf<DraftSummary?>(null) }
    val recentListState = key(activeServer?.id, DrawerPage.RECENT) { rememberLazyListState() }
    val searchListState = key(activeServer?.id, DrawerPage.SEARCH) { rememberLazyListState() }
    val currentDraftKeys: Set<DraftKey> = remember(drafts) {
        drafts.mapTo(mutableSetOf()) { it.key }
    }
    var previousDraftKeys by remember(activeServer?.id) { mutableStateOf(currentDraftKeys) }
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

    LaunchedEffect(currentDraftKeys) {
        if (hasNewDraft(previousDraftKeys, drafts)) {
            recentListState.requestScrollToItem(0)
        }
        previousDraftKeys = currentDraftKeys
    }

    LaunchedEffect(projects, activeThreadId) {
        val activeProject = projects.firstOrNull { project ->
            project.threads.any { it.id == activeThreadId }
        }
        when {
            activeProject != null -> {
                expandedProjects = expandedProjects + activeProject.path
                expansionInitialized = true
            }

            !expansionInitialized && projects.isNotEmpty() -> {
                expandedProjects = setOf(projects.first().path)
                expansionInitialized = true
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
        } else {
            ServerSelector(
                servers = servers,
                activeServer = activeServer,
                statuses = connectionStatuses,
                onSelect = onSelectServer,
                onReconnect = onReconnectServer,
                onEdit = onEditServer,
                onDelete = onRemoveServer,
                onAdd = onAddServer,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            when (drawerPage) {
                DrawerPage.RECENT -> DrawerMainHeader(
                    newTaskSelected = newTaskSelected,
                    groupByProject = groupByProject,
                    onNewConversation = {
                        leaveTransientPage()
                        onNewConversation()
                    },
                    onSearch = { drawerPage = DrawerPage.SEARCH },
                    onGroupByProjectChange = { groupByProject = it },
                )

                DrawerPage.SEARCH -> DrawerSearchHeader(
                    searchTerm = searchTerm,
                    showArchived = showArchived,
                    onSearch = onSearch,
                    onShowArchived = onShowArchived,
                    onBack = ::showRecent,
                )
            }
        }
        if (isLoading && !isRefreshing) LinearProgressIndicator(Modifier.fillMaxWidth())

        RefreshableDrawerList(
            state = when (drawerPage) {
                DrawerPage.RECENT -> recentListState
                DrawerPage.SEARCH -> searchListState
            },
            isRefreshing = isRefreshing,
            enabled = activeServer != null &&
                !isLoading &&
                !isLoadingMore &&
                (drawerPage == DrawerPage.RECENT || searchTerm.isNotBlank() || showArchived),
            onRefresh = onRefresh,
            modifier = Modifier.weight(1f),
        ) {
            val visibleDrafts = drafts
            val visibleRecentThreads = threadsWithoutDrafts(recentThreads, visibleDrafts)
            val visibleProjects = projectsWithoutDrafts(projects, visibleDrafts)
            if (activeServer != null && drawerPage == DrawerPage.RECENT) {
                if (visibleDrafts.isNotEmpty()) {
                    item("drafts-title") { DrawerSectionTitle("草稿") }
                    items(visibleDrafts, key = { it.drawerKey }) { draft ->
                        DrawerDraftItem(
                            draft = draft,
                            onClick = {
                                leaveTransientPage()
                                onOpenDraft(draft)
                            },
                            onDelete = { selectedDraft = draft },
                        )
                    }
                }
                item("recent-title") {
                    DrawerSectionTitle(
                        text = "最近会话",
                        refreshEnabled = !isLoading && !isLoadingMore,
                        onRefresh = onRefresh,
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
                            DrawerThreadItem(
                                thread = thread,
                                attention = threadAttention[thread.id],
                                selected = thread.id == activeThreadId,
                                showProject = true,
                                onClick = {
                                    leaveTransientPage()
                                    onOpenConversation(thread.id)
                                },
                                onMenu = { selectedThread = thread },
                            )
                        }
                    }
                }

                activeServer != null && groupByProject -> {
                    if (!isLoading && visibleProjects.isEmpty()) {
                        item("grouped-empty") {
                            DrawerEmptyMessage(if (visibleDrafts.isEmpty()) "还没有会话" else "没有其他最近会话")
                        }
                    }
                    items(visibleProjects, key = RecentProject::path) { project ->
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
                                onOpenSettings = {
                                    leaveTransientPage()
                                    onOpenProjectSettings(project.path)
                                },
                                onNewTask = {
                                    leaveTransientPage()
                                    onNewProjectTask(project.path)
                                },
                            )
                            if (expanded) {
                                project.threads.forEach { thread ->
                                    DrawerThreadItem(
                                        thread = thread,
                                        attention = threadAttention[thread.id],
                                        selected = thread.id == activeThreadId,
                                        showProject = false,
                                        onClick = {
                                            leaveTransientPage()
                                            onOpenConversation(thread.id)
                                        },
                                        onMenu = { selectedThread = thread },
                                    )
                                }
                            }
                        }
                    }
                }

                activeServer != null -> {
                    if (!isLoading && visibleRecentThreads.isEmpty()) {
                        item("recent-empty") {
                            DrawerEmptyMessage(if (visibleDrafts.isEmpty()) "还没有会话" else "没有其他最近会话")
                        }
                    }
                    items(visibleRecentThreads, key = ThreadSummary::id) { thread ->
                        DrawerThreadItem(
                            thread = thread,
                            attention = threadAttention[thread.id],
                            selected = thread.id == activeThreadId,
                            showProject = true,
                            onClick = { onOpenConversation(thread.id) },
                            onMenu = { selectedThread = thread },
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

        if (activeServer != null) {
            HorizontalDivider()
            NavigationDrawerItem(
                label = { Text("服务器设置") },
                selected = false,
                onClick = {
                    leaveTransientPage()
                    onOpenSettings()
                },
                icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }

    selectedThread?.let { thread ->
        ThreadActionsDialog(
            thread = thread,
            archived = drawerPage == DrawerPage.SEARCH && showArchived,
            onDismiss = { selectedThread = null },
            onRename = {
                onRenameConversation(thread.id, it)
                selectedThread = null
            },
            onFork = {
                onForkConversation(thread.id)
                selectedThread = null
            },
            onArchive = {
                onArchiveConversation(thread.id)
                selectedThread = null
            },
            onDelete = {
                onDeleteConversation(thread.id)
                selectedThread = null
            },
        )
    }
    selectedDraft?.let { draft ->
        AlertDialog(
            onDismissRequest = { selectedDraft = null },
            title = { Text("删除草稿？") },
            text = { Text("删除后无法恢复：${draft.preview}") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteDraft(draft.key)
                        selectedDraft = null
                    },
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedDraft = null }) { Text("取消") }
            },
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
    content: LazyListScope.() -> Unit,
) {
    if (enabled || isRefreshing) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = modifier,
        ) {
            LazyColumn(state = state, modifier = Modifier.fillMaxSize(), content = content)
        }
    } else {
        LazyColumn(state = state, modifier = modifier.fillMaxSize(), content = content)
    }
}

@Composable
private fun DrawerMainHeader(
    newTaskSelected: Boolean,
    groupByProject: Boolean,
    onNewConversation: () -> Unit,
    onSearch: () -> Unit,
    onGroupByProjectChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "会话",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (newTaskSelected) {
            FilledTonalIconButton(onClick = onNewConversation) {
                Icon(Icons.Rounded.Add, contentDescription = "新建任务")
            }
        } else {
            IconButton(onClick = onNewConversation) {
                Icon(Icons.Rounded.Add, contentDescription = "新建任务")
            }
        }
        IconButton(onClick = onSearch) {
            Icon(Icons.Rounded.Search, contentDescription = "搜索会话")
        }
        IconToggleButton(
            checked = groupByProject,
            onCheckedChange = onGroupByProjectChange,
        ) {
            Icon(
                Icons.Rounded.Folder,
                contentDescription = "按项目分组",
            )
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
    onOpenSettings: () -> Unit,
    onNewTask: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
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
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "${project.name} 项目操作")
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("项目设置") },
                    leadingIcon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onOpenSettings()
                    },
                )
            }
        }
    }
}

internal fun threadsWithoutDrafts(
    threads: List<ThreadSummary>,
    drafts: List<DraftSummary>,
): List<ThreadSummary> {
    val draftedThreadIds = drafts.mapNotNullTo(mutableSetOf()) { draft ->
        (draft.key.target as? DraftTarget.Thread)?.id
    }
    return threads.filterNot { it.id in draftedThreadIds }
}

internal fun projectsWithoutDrafts(
    projects: List<RecentProject>,
    drafts: List<DraftSummary>,
): List<RecentProject> = projects.mapNotNull { project ->
    project.copy(threads = threadsWithoutDrafts(project.threads, drafts))
        .takeIf { it.threads.isNotEmpty() }
}

internal fun hasNewDraft(previousKeys: Set<DraftKey>, drafts: List<DraftSummary>): Boolean =
    drafts.any { it.key !in previousKeys }

private val DraftSummary.drawerKey: String
    get() = when (val target = key.target) {
        is DraftTarget.NewTask -> "draft:${key.serverId}:new:${target.id}"
        is DraftTarget.Thread -> "draft:${key.serverId}:thread:${target.id}"
    }

@Composable
private fun DrawerDraftItem(
    draft: DraftSummary,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, bottom = 4.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClickLabel = "删除草稿",
                onLongClick = onDelete,
            ),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.EditNote,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(
                    draft.preview,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = buildList {
                        add(draft.contextTitle)
                        draft.cwd.takeIf(String::isNotBlank)?.let { add(projectName(it)) }
                        add(relativeTime(draft.updatedAtMillis / 1_000))
                    }.joinToString(" · "),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.76f),
                )
            }
        }
    }
}

@Composable
private fun DrawerThreadItem(
    thread: ThreadSummary,
    attention: ThreadAttention?,
    selected: Boolean,
    showProject: Boolean,
    onClick: () -> Unit,
    onMenu: () -> Unit,
) {
    val effectiveAttention = effectiveThreadAttention(thread, attention)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (showProject) 4.dp else 34.dp, end = 4.dp, bottom = 2.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClickLabel = "会话操作",
                onLongClick = onMenu,
            ),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    thread.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = buildString {
                        if (showProject) {
                            append(projectName(thread.cwd))
                            append(" · ")
                        }
                        append(relativeTime(thread.recencyAt ?: thread.updatedAt))
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ThreadStatusIndicator(effectiveAttention)
        }
    }
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
                color = MaterialTheme.statusColors.success,
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
    thread: ThreadSummary,
    archived: Boolean,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onFork: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    var name by rememberSaveable(thread.id) { mutableStateOf(thread.title) }
    var confirmDelete by rememberSaveable(thread.id) { mutableStateOf(false) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("永久删除会话") },
            text = { Text("确定删除“${thread.title}”吗？删除后无法恢复") },
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
