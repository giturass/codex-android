@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hebo.codex.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
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
import com.hebo.codex.domain.ApprovalPolicy
import com.hebo.codex.domain.ApprovalReviewer
import com.hebo.codex.domain.ModelOption
import com.hebo.codex.domain.Personality
import com.hebo.codex.domain.SandboxMode
import com.hebo.codex.domain.SortDirection
import com.hebo.codex.domain.ThreadOptions
import com.hebo.codex.domain.ThreadSortKey
import com.hebo.codex.domain.ThreadSourceKind
import com.hebo.codex.domain.ThreadSummary
import com.hebo.codex.ui.components.ProjectCard
import com.hebo.codex.ui.components.ServerSelector
import com.hebo.codex.ui.components.ThreadRow

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onAddServer: () -> Unit,
    onOpenProject: (serverId: String, cwd: String) -> Unit,
    onOpenConversation: (serverId: String, threadId: String) -> Unit,
    onOpenServerHub: (serverId: String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showNewConversation by rememberSaveable { mutableStateOf(false) }
    var showThreadFilters by rememberSaveable { mutableStateOf(false) }
    var selectedThread by remember { mutableStateOf<ThreadSummary?>(null) }

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
    LaunchedEffect(state.serverNotices.lastOrNull()?.id) {
        state.serverNotices.lastOrNull()?.let { snackbarHostState.showSnackbar(it.message) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Codex") },
                actions = {
                    if (state.activeServer != null) {
                        IconButton(onClick = { onOpenServerHub(state.activeServerId.orEmpty()) }) {
                            Icon(Icons.Rounded.Settings, contentDescription = "服务器能力")
                        }
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
                onSearch = viewModel::search,
                onArchived = viewModel::setArchived,
                onOpenFilters = { showThreadFilters = true },
                onLoadMore = viewModel::loadMore,
                onThreadMenu = { selectedThread = it },
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }

    if (showNewConversation) {
        NewConversationDialog(
            projects = state.projects,
            models = state.models,
            isSaving = state.isCreatingConversation,
            onDismiss = { if (!state.isCreatingConversation) showNewConversation = false },
            onCreate = { cwd, options ->
                viewModel.startConversation(cwd, options)
            },
        )
    }

    if (showThreadFilters) {
        ThreadFiltersDialog(
            state = state,
            onDismiss = { showThreadFilters = false },
            onApply = { sortKey, direction, providers, sources ->
                viewModel.setThreadFilters(sortKey, direction, providers, sources)
                showThreadFilters = false
            },
        )
    }

    selectedThread?.let { thread ->
        ThreadActionsDialog(
            thread = thread,
            archived = state.showArchived,
            onDismiss = { selectedThread = null },
            onRename = {
                viewModel.renameConversation(thread.id, it)
                selectedThread = null
            },
            onFork = {
                viewModel.forkConversation(thread.id)
                selectedThread = null
            },
            onArchive = {
                viewModel.archiveConversation(thread.id)
                selectedThread = null
            },
            onDelete = {
                viewModel.deleteConversation(thread.id)
                selectedThread = null
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
    onSearch: (String) -> Unit,
    onArchived: (Boolean) -> Unit,
    onOpenFilters: () -> Unit,
    onLoadMore: () -> Unit,
    onThreadMenu: (ThreadSummary) -> Unit,
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
                        item {
                            OutlinedTextField(
                                value = state.searchTerm,
                                onValueChange = onSearch,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("搜索会话") },
                                singleLine = true,
                            )
                        }
                        item {
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FilterChip(
                                    selected = state.showArchived,
                                    onClick = { onArchived(!state.showArchived) },
                                    label = { Text(if (state.showArchived) "已归档" else "当前会话") },
                                )
                                FilterChip(
                                    selected = state.modelProviders.isNotEmpty() || state.sourceKinds.isNotEmpty() ||
                                        state.sortKey != ThreadSortKey.RECENCY_AT ||
                                        state.sortDirection != SortDirection.DESC,
                                    onClick = onOpenFilters,
                                    label = { Text("筛选与排序") },
                                )
                            }
                        }
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
                                        onMenu = { onThreadMenu(thread) },
                                    )
                                }
                            }
                        }
                        if (state.nextCursor != null) {
                            item {
                                OutlinedButton(
                                    onClick = onLoadMore,
                                    enabled = !state.isLoadingMore,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    if (state.isLoadingMore) CircularProgressIndicator(strokeWidth = 2.dp)
                                    else Text("加载更多")
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
private fun ThreadFiltersDialog(
    state: HomeUiState,
    onDismiss: () -> Unit,
    onApply: (ThreadSortKey, SortDirection, List<String>, Set<ThreadSourceKind>) -> Unit,
) {
    var sortKey by remember(state.sortKey) { mutableStateOf(state.sortKey) }
    var direction by remember(state.sortDirection) { mutableStateOf(state.sortDirection) }
    var providers by rememberSaveable(state.modelProviders) {
        mutableStateOf(state.modelProviders.joinToString(", "))
    }
    var sources by remember(state.sourceKinds) { mutableStateOf(state.sourceKinds) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("筛选与排序") },
        text = {
            Column(
                Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("排序字段", style = MaterialTheme.typography.labelLarge)
                ChoiceRow(
                    values = ThreadSortKey.entries,
                    selected = sortKey,
                    label = {
                        when (it) {
                            ThreadSortKey.CREATED_AT -> "创建时间"
                            ThreadSortKey.UPDATED_AT -> "更新时间"
                            ThreadSortKey.RECENCY_AT -> "最近活动"
                        }
                    },
                    onSelect = { sortKey = it },
                )
                Text("排序方向", style = MaterialTheme.typography.labelLarge)
                ChoiceRow(
                    values = SortDirection.entries,
                    selected = direction,
                    label = { if (it == SortDirection.DESC) "降序" else "升序" },
                    onSelect = { direction = it },
                )
                OutlinedTextField(
                    value = providers,
                    onValueChange = { providers = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("模型提供方，逗号或换行分隔") },
                )
                Text("来源", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    ThreadSourceKind.entries.forEach { source ->
                        FilterChip(
                            selected = source in sources,
                            onClick = {
                                sources = if (source in sources) sources - source else sources + source
                            },
                            label = { Text(source.wireValue) },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val values = providers.split(',', '\n').map(String::trim).filter(String::isNotEmpty).distinct()
                    onApply(sortKey, direction, values, sources)
                },
            ) { Text("应用") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun NewConversationDialog(
    projects: List<RecentProject>,
    models: List<ModelOption>,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String, ThreadOptions) -> Unit,
) {
    val selectableModels = models.filterNot(ModelOption::hidden)
    var cwd by rememberSaveable(projects) { mutableStateOf(projects.firstOrNull()?.path.orEmpty()) }
    var modelId by rememberSaveable(selectableModels) {
        mutableStateOf(selectableModels.firstOrNull { it.isDefault }?.model)
    }
    var modelProvider by rememberSaveable { mutableStateOf("") }
    var serviceTier by rememberSaveable(selectableModels) {
        mutableStateOf(selectableModels.firstOrNull { it.isDefault }?.defaultServiceTier.orEmpty())
    }
    var sandbox by rememberSaveable { mutableStateOf<SandboxMode?>(null) }
    var approval by rememberSaveable { mutableStateOf<ApprovalPolicy?>(null) }
    var approvalReviewer by rememberSaveable { mutableStateOf<ApprovalReviewer?>(null) }
    var personality by rememberSaveable { mutableStateOf<Personality?>(null) }
    var ephemeral by rememberSaveable { mutableStateOf(false) }
    var baseInstructions by rememberSaveable { mutableStateOf("") }
    var developerInstructions by rememberSaveable { mutableStateOf("") }
    var configJson by rememberSaveable { mutableStateOf("") }
    val selectedModel = selectableModels.firstOrNull { it.model == modelId }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建会话") },
        text = {
            Column(
                Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
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
                if (selectableModels.isNotEmpty()) {
                    Text("模型", style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        selectableModels.forEach { model ->
                            FilterChip(
                                selected = model.model == modelId,
                                onClick = {
                                    modelId = model.model
                                    serviceTier = model.defaultServiceTier.orEmpty()
                                },
                                label = { Text(model.displayName) },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = modelId.orEmpty(),
                    onValueChange = { modelId = it.ifBlank { null } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("模型 ID，可留空") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = modelProvider,
                    onValueChange = { modelProvider = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("模型提供方，可留空") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = serviceTier,
                    onValueChange = { serviceTier = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("服务层级，可留空") },
                    singleLine = true,
                )
                Text("沙箱", style = MaterialTheme.typography.labelLarge)
                ChoiceRow(
                    values = listOf(null, SandboxMode.READ_ONLY, SandboxMode.WORKSPACE_WRITE, SandboxMode.DANGER_FULL_ACCESS),
                    selected = sandbox,
                    label = { it?.wireValue ?: "服务器默认" },
                    onSelect = { sandbox = it },
                )
                Text("审批", style = MaterialTheme.typography.labelLarge)
                ChoiceRow(
                    values = listOf(null, ApprovalPolicy.UNTRUSTED, ApprovalPolicy.ON_REQUEST, ApprovalPolicy.NEVER),
                    selected = approval,
                    label = { it?.wireValue ?: "服务器默认" },
                    onSelect = { approval = it },
                )
                if (selectedModel?.supportsPersonality == true) {
                    Text("人格", style = MaterialTheme.typography.labelLarge)
                    ChoiceRow(
                        values = listOf(null, Personality.FRIENDLY, Personality.PRAGMATIC, Personality.NONE),
                        selected = personality,
                        label = { it?.wireValue ?: "服务器默认" },
                        onSelect = { personality = it },
                    )
                }
                Text("审批者", style = MaterialTheme.typography.labelLarge)
                ChoiceRow(
                    values = listOf<ApprovalReviewer?>(null) + ApprovalReviewer.entries,
                    selected = approvalReviewer,
                    label = { it?.wireValue ?: "服务器默认" },
                    onSelect = { approvalReviewer = it },
                )
                OutlinedTextField(
                    value = baseInstructions,
                    onValueChange = { baseInstructions = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("基础指令，可留空") },
                    minLines = 2,
                )
                OutlinedTextField(
                    value = developerInstructions,
                    onValueChange = { developerInstructions = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("开发者指令，可留空") },
                    minLines = 2,
                )
                OutlinedTextField(
                    value = configJson,
                    onValueChange = { configJson = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("线程配置 JSON 对象，可留空") },
                    minLines = 3,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("临时会话，不写入磁盘", modifier = Modifier.weight(1f))
                    androidx.compose.material3.Switch(checked = ephemeral, onCheckedChange = { ephemeral = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onCreate(
                        cwd,
                        ThreadOptions(
                            model = modelId,
                            modelProvider = modelProvider.ifBlank { null },
                            serviceTier = serviceTier.ifBlank { null },
                            sandboxMode = sandbox,
                            approvalPolicy = approval,
                            approvalsReviewer = approvalReviewer,
                            configJson = configJson.ifBlank { null },
                            personality = personality,
                            baseInstructions = baseInstructions.ifBlank { null },
                            developerInstructions = developerInstructions.ifBlank { null },
                            ephemeral = ephemeral,
                        ),
                    )
                },
                enabled = cwd.isNotBlank() && !isSaving,
            ) {
                if (isSaving) CircularProgressIndicator(strokeWidth = 2.dp)
                else Text("创建")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun <T> ChoiceRow(
    values: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        values.forEach { value ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(label(value)) },
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
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
                Button(onClick = { onRename(name) }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    Text("保存名称")
                }
                OutlinedButton(onClick = onFork, modifier = Modifier.fillMaxWidth()) { Text("分叉会话") }
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
