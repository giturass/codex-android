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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hebo.codex.domain.ConnectionStatus
import com.hebo.codex.domain.RecentProject
import com.hebo.codex.domain.ServerProfile
import com.hebo.codex.domain.ThreadRuntimeStatus
import com.hebo.codex.domain.ThreadSummary
import com.hebo.codex.ui.theme.statusColors

@Composable
fun CodexNavigationDrawer(
    servers: List<ServerProfile>,
    activeServer: ServerProfile?,
    connectionStatuses: Map<String, ConnectionStatus>,
    projects: List<RecentProject>,
    threads: List<ThreadSummary>,
    searchTerm: String,
    showArchived: Boolean,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    activeThreadId: String?,
    newTaskSelected: Boolean,
    isPermanent: Boolean,
    onNewConversation: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onOpenProjectSettings: (String) -> Unit,
    onSearch: (String) -> Unit,
    onShowArchived: (Boolean) -> Unit,
    onLoadMore: () -> Unit,
    onRenameConversation: (threadId: String, name: String) -> Unit,
    onForkConversation: (String) -> Unit,
    onArchiveConversation: (String) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onSelectServer: (String) -> Unit,
    onReconnectServer: (String) -> Unit,
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
            searchTerm = searchTerm,
            showArchived = showArchived,
            isLoading = isLoading,
            isLoadingMore = isLoadingMore,
            hasMore = hasMore,
            activeThreadId = activeThreadId,
            newTaskSelected = newTaskSelected,
            onNewConversation = onNewConversation,
            onOpenConversation = onOpenConversation,
            onOpenProjectSettings = onOpenProjectSettings,
            onSearch = onSearch,
            onShowArchived = onShowArchived,
            onLoadMore = onLoadMore,
            onRenameConversation = onRenameConversation,
            onForkConversation = onForkConversation,
            onArchiveConversation = onArchiveConversation,
            onDeleteConversation = onDeleteConversation,
            onSelectServer = onSelectServer,
            onReconnectServer = onReconnectServer,
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

@Composable
private fun ColumnScope.DrawerContent(
    servers: List<ServerProfile>,
    activeServer: ServerProfile?,
    connectionStatuses: Map<String, ConnectionStatus>,
    projects: List<RecentProject>,
    threads: List<ThreadSummary>,
    searchTerm: String,
    showArchived: Boolean,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    activeThreadId: String?,
    newTaskSelected: Boolean,
    onNewConversation: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onOpenProjectSettings: (String) -> Unit,
    onSearch: (String) -> Unit,
    onShowArchived: (Boolean) -> Unit,
    onLoadMore: () -> Unit,
    onRenameConversation: (threadId: String, name: String) -> Unit,
    onForkConversation: (String) -> Unit,
    onArchiveConversation: (String) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onSelectServer: (String) -> Unit,
    onReconnectServer: (String) -> Unit,
    onRemoveServer: (String) -> Unit,
    onAddServer: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var expandedProjects by remember(activeServer?.id) { mutableStateOf(emptySet<String>()) }
    var expansionInitialized by remember(activeServer?.id) { mutableStateOf(false) }
    var selectedThread by remember { mutableStateOf<ThreadSummary?>(null) }

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
                onDelete = onRemoveServer,
                onAdd = onAddServer,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            NavigationDrawerItem(
                label = { Text("新建任务") },
                selected = newTaskSelected,
                onClick = onNewConversation,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
            )
        }

        if (activeServer != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = searchTerm,
                    onValueChange = onSearch,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(if (showArchived) "搜索已归档会话" else "搜索项目和会话") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
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
                IconButton(onClick = { onShowArchived(!showArchived) }) {
                    Icon(
                        imageVector = if (showArchived) Icons.Rounded.Unarchive else Icons.Rounded.Archive,
                        contentDescription = if (showArchived) "查看当前会话" else "查看已归档会话",
                        tint = if (showArchived) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())

        LazyColumn(modifier = Modifier.weight(1f)) {
            when {
                activeServer == null && servers.isNotEmpty() -> {
                    item("no-active-server") {
                        DrawerEmptyMessage("请选择服务器")
                    }
                }

                activeServer != null && searchTerm.isNotBlank() -> {
                    item("search-title") {
                        DrawerSectionTitle(if (showArchived) "已归档搜索结果" else "搜索结果")
                    }
                    if (!isLoading && threads.isEmpty()) {
                        item("search-empty") { DrawerEmptyMessage("没有匹配的会话") }
                    }
                    items(threads, key = ThreadSummary::id) { thread ->
                        DrawerThreadItem(
                            thread = thread,
                            selected = thread.id == activeThreadId,
                            showProject = true,
                            onClick = { onOpenConversation(thread.id) },
                            onMenu = { selectedThread = thread },
                        )
                    }
                }

                activeServer != null -> {
                    if (showArchived) {
                        item("archived-title") { DrawerSectionTitle("已归档") }
                    }
                    if (!isLoading && projects.isEmpty()) {
                        item("projects-empty") {
                            DrawerEmptyMessage(if (showArchived) "没有已归档会话" else "还没有会话")
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
                                onOpenSettings = { onOpenProjectSettings(project.path) },
                            )
                            if (expanded) {
                                project.threads.forEach { thread ->
                                    DrawerThreadItem(
                                        thread = thread,
                                        selected = thread.id == activeThreadId,
                                        showProject = false,
                                        onClick = { onOpenConversation(thread.id) },
                                        onMenu = { selectedThread = thread },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (hasMore) {
                item("load-more") {
                    TextButton(
                        onClick = onLoadMore,
                        enabled = !isLoadingMore,
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
                onClick = onOpenSettings,
                icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }

    selectedThread?.let { thread ->
        ThreadActionsDialog(
            thread = thread,
            archived = showArchived,
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
}

@Composable
private fun ProjectHeader(
    project: RecentProject,
    expanded: Boolean,
    active: Boolean,
    onToggle: () -> Unit,
    onOpenSettings: () -> Unit,
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

@Composable
private fun DrawerThreadItem(
    thread: ThreadSummary,
    selected: Boolean,
    showProject: Boolean,
    onClick: () -> Unit,
    onMenu: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (showProject) 4.dp else 34.dp, end = 4.dp, bottom = 2.dp)
            .combinedClickable(onClick = onClick, onLongClick = onMenu),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        thread.title,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (thread.status == ThreadRuntimeStatus.ACTIVE) {
                        Text(
                            "运行中",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.statusColors.success,
                        )
                    }
                    Text(
                        relativeTime(thread.recencyAt ?: thread.updatedAt),
                        modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (showProject) {
                    Text(
                        thread.cwd,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (thread.preview.isNotBlank()) {
                    Text(
                        thread.preview,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onMenu, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Rounded.MoreVert,
                    contentDescription = "${thread.title} 会话操作",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun DrawerSectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
                TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                    Text("永久删除", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}
