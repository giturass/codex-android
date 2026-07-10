@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hebo.codex.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.hebo.codex.domain.RecentProject
import com.hebo.codex.ui.components.ProjectCard
import com.hebo.codex.ui.components.ServerSelector
import com.hebo.codex.ui.components.ThreadRow

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onAddServer: () -> Unit,
    onOpenProject: (serverId: String, cwd: String) -> Unit,
    onOpenConversation: (serverId: String, threadId: String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showNewConversation by rememberSaveable { mutableStateOf(false) }

    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose {}
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeEvent.OpenConversation -> {
                    showNewConversation = false
                    onOpenConversation(event.serverId, event.threadId)
                }
            }
        }
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Codex") },
                actions = {
                    if (state.activeServer != null) {
                        IconButton(onClick = viewModel::refresh) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "刷新")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (state.activeServer != null) {
                ExtendedFloatingActionButton(
                    onClick = { showNewConversation = true },
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text("新建会话") },
                    expanded = !state.isCreatingConversation,
                )
            }
        },
    ) { padding ->
        when {
            state.servers.isEmpty() -> NoServers(
                modifier = Modifier.fillMaxSize().padding(padding),
                onAddServer = onAddServer,
            )

            else -> HomeContent(
                state = state,
                onSelectServer = viewModel::selectServer,
                onReconnectServer = viewModel::reconnectServer,
                onRemoveServer = viewModel::removeServer,
                onAddServer = onAddServer,
                onSelectTab = viewModel::selectTab,
                onOpenProject = { cwd -> onOpenProject(state.activeServerId.orEmpty(), cwd) },
                onOpenConversation = { threadId ->
                    onOpenConversation(state.activeServerId.orEmpty(), threadId)
                },
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }

    if (showNewConversation) {
        NewConversationDialog(
            projects = state.projects,
            isSaving = state.isCreatingConversation,
            onDismiss = { if (!state.isCreatingConversation) showNewConversation = false },
            onCreate = { cwd ->
                viewModel.startConversation(cwd)
            },
        )
    }
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    onSelectServer: (String) -> Unit,
    onReconnectServer: (String) -> Unit,
    onRemoveServer: (String) -> Unit,
    onAddServer: () -> Unit,
    onSelectTab: (HomeTab) -> Unit,
    onOpenProject: (String) -> Unit,
    onOpenConversation: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        ServerSelector(
            servers = state.servers,
            activeServer = state.activeServer,
            statuses = state.connectionStatuses,
            onSelect = onSelectServer,
            onReconnect = onReconnectServer,
            onDelete = onRemoveServer,
            onAdd = onAddServer,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(16.dp))
        if (state.activeServer == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("请选择服务器")
            }
        } else {
            HomeTabs(
                selected = state.selectedTab,
                onSelect = onSelectTab,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            if (state.isLoading) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (state.selectedTab) {
                    HomeTab.PROJECTS -> {
                        item { SectionTitle("最近项目") }
                        if (!state.isLoading && state.projects.isEmpty()) {
                            item { EmptyContent("还没有项目记录", "新建会话时输入服务端项目路径") }
                        }
                        items(state.projects, key = RecentProject::path) { project ->
                            ProjectCard(project, onClick = { onOpenProject(project.path) })
                        }
                    }

                    HomeTab.CONVERSATIONS -> {
                        item { SectionTitle("最近会话") }
                        if (!state.isLoading && state.threads.isEmpty()) {
                            item { EmptyContent("还没有历史会话", "点击下方按钮开始第一个会话") }
                        }
                        item {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                ),
                            ) {
                                state.threads.forEach { thread ->
                                    ThreadRow(
                                        thread = thread,
                                        showProjectPath = true,
                                        onClick = { onOpenConversation(thread.id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeTabs(
    selected: HomeTab,
    onSelect: (HomeTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TabButton(
            text = "最近项目",
            selected = selected == HomeTab.PROJECTS,
            onClick = { onSelect(HomeTab.PROJECTS) },
            modifier = Modifier.weight(1f),
        )
        TabButton(
            text = "最近会话",
            selected = selected == HomeTab.CONVERSATIONS,
            onClick = { onSelect(HomeTab.CONVERSATIONS) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TabButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
    ) {
        Text(
            text,
            modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp),
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun NoServers(modifier: Modifier, onAddServer: () -> Unit) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Rounded.Dns,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text("连接 Codex 服务端", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "添加服务端地址和 capability token 后即可查看项目与会话",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAddServer) { Text("添加服务器") }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun EmptyContent(title: String, subtitle: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NewConversationDialog(
    projects: List<RecentProject>,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var cwd by rememberSaveable(projects) { mutableStateOf(projects.firstOrNull()?.path.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建会话") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (projects.isNotEmpty()) {
                    Text("最近项目", style = MaterialTheme.typography.labelLarge)
                    projects.take(4).forEach { project ->
                        OutlinedButton(
                            onClick = { cwd = project.path },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(project.name, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                            if (cwd == project.path) Text("已选择")
                        }
                    }
                }
                OutlinedTextField(
                    value = cwd,
                    onValueChange = { cwd = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("服务端项目路径") },
                    placeholder = { Text("/home/user/project") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(cwd) },
                enabled = cwd.isNotBlank() && !isSaving,
            ) {
                if (isSaving) CircularProgressIndicator(strokeWidth = 2.dp)
                else Text("创建")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
