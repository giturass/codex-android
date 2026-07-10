package com.hebo.codex.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hebo.codex.domain.ConnectionStatus
import com.hebo.codex.domain.RecentProject
import com.hebo.codex.domain.ServerProfile
import com.hebo.codex.domain.ThreadSummary

@Composable
fun CodexNavigationDrawer(
    servers: List<ServerProfile>,
    activeServer: ServerProfile?,
    connectionStatuses: Map<String, ConnectionStatus>,
    projects: List<RecentProject>,
    threads: List<ThreadSummary>,
    searchTerm: String,
    isLoading: Boolean,
    onOpenHome: () -> Unit,
    onNewConversation: () -> Unit,
    onOpenProject: (String) -> Unit,
    onOpenConversation: (String) -> Unit,
    onSearch: (String) -> Unit,
    onSelectServer: (String) -> Unit,
    onReconnectServer: (String) -> Unit,
    onRemoveServer: (String) -> Unit,
    onAddServer: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalDrawerSheet(
        modifier = modifier.fillMaxHeight().widthIn(max = 360.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxHeight().padding(horizontal = 12.dp)) {
            Text(
                text = "Codex",
                modifier = Modifier.padding(start = 12.dp, top = 20.dp, bottom = 14.dp),
                style = MaterialTheme.typography.titleLarge,
            )
            if (servers.isEmpty()) {
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
                Spacer(Modifier.height(10.dp))
                NavigationDrawerItem(
                    label = { Text("新建任务") },
                    selected = false,
                    onClick = onNewConversation,
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                )
            }
            NavigationDrawerItem(
                label = { Text("首页") },
                selected = false,
                onClick = onOpenHome,
                icon = { Icon(Icons.Rounded.Home, contentDescription = null) },
            )
            if (activeServer != null) {
                OutlinedTextField(
                    value = searchTerm,
                    onValueChange = onSearch,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    placeholder = { Text("搜索会话") },
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
            }
            if (isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                if (projects.isNotEmpty()) {
                    item("projects-title") { DrawerSectionTitle("项目") }
                    items(projects.take(8), key = RecentProject::path) { project ->
                        NavigationDrawerItem(
                            label = {
                                Column {
                                    Text(project.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${project.threads.size} 个会话",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            selected = false,
                            onClick = { onOpenProject(project.path) },
                            icon = { Icon(Icons.Rounded.Folder, contentDescription = null) },
                        )
                    }
                }
                if (threads.isNotEmpty()) {
                    item("threads-title") { DrawerSectionTitle("最近会话") }
                    items(threads.take(12), key = ThreadSummary::id) { thread ->
                        NavigationDrawerItem(
                            label = {
                                Column {
                                    Text(thread.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        thread.preview.ifBlank { thread.cwd },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            selected = false,
                            onClick = { onOpenConversation(thread.id) },
                        )
                    }
                }
            }
            HorizontalDivider()
            if (activeServer != null) {
                NavigationDrawerItem(
                    label = { Text("服务器与功能") },
                    selected = false,
                    onClick = onOpenSettings,
                    icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun DrawerSectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
