@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hebo.codex.ui.screens.home

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.hebo.codex.ui.components.TaskPromptBar
import com.hebo.codex.ui.components.ThreadRow

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    newConversationRequest: Int,
    onNewConversationRequestHandled: () -> Unit,
    onOpenNavigation: () -> Unit,
    onAddServer: () -> Unit,
    onOpenProject: (serverId: String, cwd: String) -> Unit,
    onOpenConversation: (serverId: String, threadId: String) -> Unit,
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

    LaunchedEffect(newConversationRequest) {
        if (newConversationRequest > 0) {
            showNewConversation = true
            onNewConversationRequestHandled()
        }
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
                title = {
                    Column {
                        Text("Codex")
                        state.activeServer?.let { server ->
                            Text(
                                server.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onOpenNavigation) {
                        Icon(Icons.Rounded.Menu, contentDescription = "打开导航")
                    }
                },
                actions = {
                    if (state.activeServer != null) {
                        IconButton(onClick = { showNewConversation = true }) {
                            Icon(Icons.Rounded.Add, contentDescription = "新建任务")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (state.activeServer != null) {
                TaskPromptBar(
                    label = "开始一项新任务",
                    isLoading = state.isCreatingConversation,
                    onClick = { showNewConversation = true },
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
                onOpenProject = { cwd -> onOpenProject(state.activeServerId.orEmpty(), cwd) },
                onOpenConversation = { threadId ->
                    onOpenConversation(state.activeServerId.orEmpty(), threadId)
                },
                onClearSearch = { viewModel.search("") },
                onRefresh = viewModel::refresh,
                onArchived = viewModel::setArchived,
                onOpenFilters = { showThreadFilters = true },
                onLoadMore = viewModel::loadMore,
                onThreadMenu = { selectedThread = it },
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }

    if (showNewConversation) {
        NewConversationSheet(
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
    onOpenProject: (String) -> Unit,
    onOpenConversation: (String) -> Unit,
    onClearSearch: () -> Unit,
    onRefresh: () -> Unit,
    onArchived: (Boolean) -> Unit,
    onOpenFilters: () -> Unit,
    onLoadMore: () -> Unit,
    onThreadMenu: (ThreadSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.activeServer == null) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("请从侧边栏选择服务器")
        }
        return
    }
    PullToRefreshBox(
        isRefreshing = state.isLoading,
        onRefresh = onRefresh,
        modifier = modifier,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item("intro") {
                Column(Modifier.fillMaxWidth().padding(vertical = 22.dp)) {
                    Text("今天想处理什么？", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "从最近项目继续，或在下方开始一项新任务",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item("projects-title") { SectionTitle("最近项目") }
            if (!state.isLoading && state.projects.isEmpty()) {
                item("projects-empty") { EmptyContent("还没有项目记录", "新建任务时输入服务端项目路径") }
            }
            items(state.projects.take(6), key = RecentProject::path) { project ->
                ProjectCard(project, onClick = { onOpenProject(project.path) })
            }
            item("threads-header") {
                Row(
                    Modifier.fillMaxWidth().padding(top = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("最近会话", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    if (state.searchTerm.isNotBlank()) {
                        IconButton(onClick = onClearSearch) {
                            Icon(Icons.Rounded.Close, contentDescription = "清除搜索")
                        }
                    }
                    FilterChip(
                        selected = state.showArchived,
                        onClick = { onArchived(!state.showArchived) },
                        label = { Text(if (state.showArchived) "已归档" else "当前") },
                    )
                    IconButton(onClick = onOpenFilters) {
                        Icon(Icons.Rounded.Tune, contentDescription = "筛选与排序")
                    }
                }
            }
            if (!state.isLoading && state.threads.isEmpty()) {
                item("threads-empty") { EmptyContent("还没有历史会话", "从下方开始第一项任务") }
            }
            items(state.threads, key = ThreadSummary::id) { thread ->
                ThreadRow(
                    thread = thread,
                    showProjectPath = true,
                    onClick = { onOpenConversation(thread.id) },
                    onMenu = { onThreadMenu(thread) },
                )
            }
            if (state.nextCursor != null) {
                item("load-more") {
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
private fun NewConversationSheet(
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
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    val selectedModel = selectableModels.firstOrNull { it.model == modelId }
    ModalBottomSheet(
        onDismissRequest = { if (!isSaving) onDismiss() },
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("新建任务", style = MaterialTheme.typography.titleLarge)
            Text(
                "先选择工作目录，其他设置可以沿用服务器默认值",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
                shape = MaterialTheme.shapes.medium,
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
                selectedModel?.description?.takeIf(String::isNotBlank)?.let { description ->
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(Modifier.padding(top = 4.dp))
            TextButton(
                onClick = { showAdvanced = !showAdvanced },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (showAdvanced) "收起高级设置" else "高级设置")
            }
            if (showAdvanced) {
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
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("开始会话")
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
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
