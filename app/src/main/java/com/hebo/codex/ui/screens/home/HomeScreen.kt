@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hebo.codex.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hebo.codex.R
import com.hebo.codex.domain.ApprovalPolicy
import com.hebo.codex.domain.ApprovalReviewer
import com.hebo.codex.domain.ModelOption
import com.hebo.codex.domain.Personality
import com.hebo.codex.domain.RecentProject
import com.hebo.codex.domain.QuickPhrase
import com.hebo.codex.domain.SandboxMode
import com.hebo.codex.domain.ThreadOptions
import com.hebo.codex.domain.UserFacingError
import com.hebo.codex.domain.projectName
import com.hebo.codex.ui.components.MessageComposer
import com.hebo.codex.ui.components.MessageComposerAction
import com.hebo.codex.ui.components.MessageComposerActionButton
import com.hebo.codex.ui.components.MessageComposerAddButton
import com.hebo.codex.ui.components.QuickPhraseManagerDialog
import com.hebo.codex.ui.components.QuickPhraseMenuContent
import com.hebo.codex.ui.components.insertQuickPhrase
import com.hebo.codex.ui.displayDescriptionRes
import com.hebo.codex.ui.displayLabelRes
import com.hebo.codex.ui.ratelimits.RateLimitIndicator
import com.hebo.codex.ui.ratelimits.RateLimitsUiState
import com.hebo.codex.ui.quickphrases.QuickPhraseViewModel

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    newTaskViewModel: NewTaskViewModel,
    quickPhraseViewModel: QuickPhraseViewModel,
    rateLimitsState: RateLimitsUiState,
    showNavigationIcon: Boolean,
    onOpenNavigation: () -> Unit,
    onAddServer: () -> Unit,
    onRefreshRateLimits: () -> Unit,
    onShowError: suspend (UserFacingError) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val newTaskState by newTaskViewModel.state.collectAsStateWithLifecycle()
    val quickPhraseState by quickPhraseViewModel.state.collectAsStateWithLifecycle()
    var showProjectMenu by remember { mutableStateOf(false) }
    var showCustomPathDialog by remember { mutableStateOf(false) }
    var showOptions by remember { mutableStateOf(false) }

    LifecycleResumeEffect(viewModel) {
        viewModel.reloadThreads()
        onPauseOrDispose {}
    }
    LifecycleResumeEffect(newTaskViewModel) {
        onPauseOrDispose { newTaskViewModel.flushDraft() }
    }
    LifecycleResumeEffect(state.activeServerId) {
        onRefreshRateLimits()
        onPauseOrDispose {}
    }

    LaunchedEffect(
        state.activeServerId,
        state.projects,
        newTaskState.isLoadingDraft,
        newTaskState.cwd,
    ) {
        if (!newTaskState.isLoadingDraft && newTaskState.cwd.isBlank()) {
            state.projects.firstOrNull()?.path?.let(newTaskViewModel::updateCwd)
        }
    }
    LaunchedEffect(
        state.activeServerId,
        state.models,
        newTaskState.isLoadingDraft,
        newTaskState.options.model,
    ) {
        state.models.firstOrNull { it.isDefault && !it.hidden }
            ?.let(newTaskViewModel::applyDefaultModel)
    }
    LaunchedEffect(newTaskState.errorMessage) {
        newTaskState.errorMessage?.let { error ->
            onShowError(error)
            newTaskViewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("新建任务")
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
                    if (showNavigationIcon) {
                        IconButton(onClick = onOpenNavigation) {
                            Icon(Icons.Rounded.Menu, contentDescription = "打开导航")
                        }
                    }
                },
                actions = {
                    RateLimitIndicator(
                        state = rateLimitsState,
                        onRefresh = onRefreshRateLimits,
                    )
                },
            )
        },
    ) { padding ->
        when {
            state.servers.isEmpty() -> NoServers(
                modifier = Modifier.fillMaxSize().padding(padding),
                onAddServer = onAddServer,
            )

            state.activeServer == null -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("请从侧边栏选择服务器")
            }

            else -> NewTaskContent(
                message = newTaskState.message,
                modelLabel = state.models
                    .firstOrNull { it.model == newTaskState.options.model }
                    ?.displayName
                    ?: newTaskState.options.model
                    ?: "默认模型",
                cwd = newTaskState.cwd,
                projects = state.recentProjects,
                quickPhrases = quickPhraseState.phrases,
                isCreating = newTaskState.isCreatingConversation || newTaskState.isLoadingDraft,
                projectMenuExpanded = showProjectMenu,
                onMessageChange = newTaskViewModel::updateMessage,
                onToggleProjectMenu = { showProjectMenu = !showProjectMenu },
                onDismissProjectMenu = { showProjectMenu = false },
                onSelectProject = {
                    newTaskViewModel.updateCwd(it)
                    showProjectMenu = false
                },
                onSelectCustomPath = {
                    showProjectMenu = false
                    showCustomPathDialog = true
                },
                onOpenOptions = { showOptions = true },
                onAddQuickPhrase = quickPhraseViewModel::add,
                onUpdateQuickPhrase = quickPhraseViewModel::update,
                onDeleteQuickPhrase = quickPhraseViewModel::delete,
                onMoveQuickPhraseUp = quickPhraseViewModel::moveUp,
                onMoveQuickPhraseDown = quickPhraseViewModel::moveDown,
                onSend = newTaskViewModel::startConversation,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }

    if (showCustomPathDialog) {
        CustomProjectPathDialog(
            currentPath = newTaskState.cwd,
            onDismiss = { showCustomPathDialog = false },
            onConfirm = {
                newTaskViewModel.updateCwd(it)
                showCustomPathDialog = false
            },
        )
    }
    if (showOptions) {
        ThreadOptionsSheet(
            value = newTaskState.options,
            models = state.models,
            onDismiss = { showOptions = false },
            onSave = {
                newTaskViewModel.updateOptions(it)
                showOptions = false
            },
        )
    }
}

@Composable
private fun NewTaskContent(
    message: String,
    modelLabel: String,
    cwd: String,
    projects: List<RecentProject>,
    quickPhrases: List<QuickPhrase>,
    isCreating: Boolean,
    projectMenuExpanded: Boolean,
    onMessageChange: (String) -> Unit,
    onToggleProjectMenu: () -> Unit,
    onDismissProjectMenu: () -> Unit,
    onSelectProject: (String) -> Unit,
    onSelectCustomPath: () -> Unit,
    onOpenOptions: () -> Unit,
    onAddQuickPhrase: (String) -> Unit,
    onUpdateQuickPhrase: (String, String) -> Unit,
    onDeleteQuickPhrase: (String) -> Unit,
    onMoveQuickPhraseUp: (String) -> Unit,
    onMoveQuickPhraseDown: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var addMenuExpanded by remember { mutableStateOf(false) }
    var showQuickPhrases by remember { mutableStateOf(false) }
    var showQuickPhraseManager by remember { mutableStateOf(false) }
    var composerValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = message,
                selection = TextRange(message.length),
            ),
        )
    }
    LaunchedEffect(message) {
        if (message != composerValue.text) {
            composerValue = TextFieldValue(
                text = message,
                selection = TextRange(message.length),
            )
        }
    }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    Column(
        modifier = modifier.imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("今天想处理什么？", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text(
                    "选择项目并描述任务，Codex 会立即开始处理",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(28.dp))
                ProjectSelector(
                    cwd = cwd,
                    projects = projects,
                    expanded = projectMenuExpanded,
                    enabled = !isCreating,
                    onToggle = onToggleProjectMenu,
                    onDismiss = onDismissProjectMenu,
                    onSelect = onSelectProject,
                    onSelectCustomPath = onSelectCustomPath,
                )
            }
        }
        MessageComposer(
            value = composerValue,
            onValueChange = {
                composerValue = it
                onMessageChange(it.text)
            },
            placeholder = "描述要完成的任务……",
            enabled = !isCreating,
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth()
                .padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 10.dp),
            leadingContent = {
                Box {
                    MessageComposerAddButton(
                        onClick = {
                            showQuickPhrases = false
                            addMenuExpanded = true
                        },
                        enabled = !isCreating,
                        contentDescription = "添加内容",
                    )
                    DropdownMenu(
                        expanded = addMenuExpanded,
                        onDismissRequest = {
                            addMenuExpanded = false
                            showQuickPhrases = false
                        },
                        modifier = Modifier.widthIn(min = 280.dp, max = 400.dp),
                    ) {
                        if (showQuickPhrases) {
                            QuickPhraseMenuContent(
                                phrases = quickPhrases,
                                onBack = { showQuickPhrases = false },
                                onManage = {
                                    addMenuExpanded = false
                                    showQuickPhrases = false
                                    showQuickPhraseManager = true
                                },
                                onSelect = { phrase ->
                                    val nextValue = insertQuickPhrase(composerValue, phrase.content)
                                    composerValue = nextValue
                                    onMessageChange(nextValue.text)
                                    addMenuExpanded = false
                                    showQuickPhrases = false
                                    focusRequester.requestFocus()
                                    keyboardController?.show()
                                },
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("快捷短语") },
                                leadingIcon = { Icon(Icons.Rounded.Description, contentDescription = null) },
                                onClick = { showQuickPhrases = true },
                            )
                        }
                    }
                }
            },
            expandedContent = {
                TextButton(
                    onClick = onOpenOptions,
                    enabled = !isCreating,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(modelLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            trailingContent = {
                MessageComposerActionButton(
                    onClick = onSend,
                    enabled = cwd.isNotBlank() && message.isNotBlank() && !isCreating,
                    action = MessageComposerAction.SEND,
                    isLoading = isCreating,
                    contentDescription = "开始任务",
                )
            },
            focusRequester = focusRequester,
        )
    }
    if (showQuickPhraseManager) {
        QuickPhraseManagerDialog(
            phrases = quickPhrases,
            onDismiss = { showQuickPhraseManager = false },
            onAdd = onAddQuickPhrase,
            onUpdate = onUpdateQuickPhrase,
            onDelete = onDeleteQuickPhrase,
            onMoveUp = onMoveQuickPhraseUp,
            onMoveDown = onMoveQuickPhraseDown,
        )
    }
}

@Composable
private fun ProjectSelector(
    cwd: String,
    projects: List<RecentProject>,
    expanded: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onSelectCustomPath: () -> Unit,
) {
    Box(Modifier.fillMaxWidth()) {
        Surface(
            onClick = onToggle,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(
                        if (cwd.isBlank()) "选择项目" else projectName(cwd),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (cwd.isBlank()) "选择最近项目或输入服务端路径" else cwd,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(Icons.Rounded.ExpandMore, contentDescription = "选择项目")
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismiss,
            modifier = Modifier.widthIn(min = 280.dp, max = 600.dp),
        ) {
            projects.take(8).forEach { project ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(project.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                project.path,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    trailingIcon = {
                        if (cwd == project.path) Icon(Icons.Rounded.Check, contentDescription = "已选择")
                    },
                    onClick = { onSelect(project.path) },
                )
            }
            if (projects.isNotEmpty()) HorizontalDivider()
            DropdownMenuItem(
                text = { Text("输入其他服务端路径") },
                leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                onClick = onSelectCustomPath,
            )
        }
    }
}

@Composable
private fun CustomProjectPathDialog(
    currentPath: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var path by rememberSaveable(currentPath) { mutableStateOf(currentPath) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择其他项目") },
        text = {
            OutlinedTextField(
                value = path,
                onValueChange = { path = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("服务端项目路径") },
                placeholder = { Text("/home/user/project") },
                singleLine = true,
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(path.trim()) }, enabled = path.isNotBlank()) {
                Text("选择")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ThreadOptionsSheet(
    value: ThreadOptions,
    models: List<ModelOption>,
    onDismiss: () -> Unit,
    onSave: (ThreadOptions) -> Unit,
) {
    val selectableModels = models.filterNot(ModelOption::hidden)
    var modelId by remember(value) { mutableStateOf(value.model) }
    var modelProvider by remember(value) { mutableStateOf(value.modelProvider.orEmpty()) }
    var serviceTier by remember(value) { mutableStateOf(value.serviceTier.orEmpty()) }
    var sandbox by remember(value) { mutableStateOf(value.sandboxMode) }
    var approval by remember(value) { mutableStateOf(value.approvalPolicy) }
    var approvalReviewer by remember(value) { mutableStateOf(value.approvalsReviewer) }
    var personality by remember(value) { mutableStateOf(value.personality) }
    var ephemeral by remember(value) { mutableStateOf(value.ephemeral) }
    var baseInstructions by remember(value) { mutableStateOf(value.baseInstructions.orEmpty()) }
    var developerInstructions by remember(value) { mutableStateOf(value.developerInstructions.orEmpty()) }
    var configJson by remember(value) { mutableStateOf(value.configJson.orEmpty()) }
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    val selectedModel = selectableModels.firstOrNull { it.model == modelId }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("任务选项", style = MaterialTheme.typography.titleLarge)
            Text(
                "未修改的选项将沿用服务器默认值",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    values = listOf(
                        null,
                        SandboxMode.READ_ONLY,
                        SandboxMode.WORKSPACE_WRITE,
                        SandboxMode.DANGER_FULL_ACCESS,
                    ),
                    selected = sandbox,
                    label = { it?.let { value -> stringResource(value.displayLabelRes) }
                        ?: stringResource(R.string.common_server_default) },
                    onSelect = { sandbox = it },
                )
                sandbox?.let {
                    OptionDescription(
                        stringResource(it.displayDescriptionRes),
                        critical = it == SandboxMode.DANGER_FULL_ACCESS,
                    )
                }
                Text("审批", style = MaterialTheme.typography.labelLarge)
                ChoiceRow(
                    values = listOf(null, ApprovalPolicy.UNTRUSTED, ApprovalPolicy.ON_REQUEST, ApprovalPolicy.NEVER),
                    selected = approval,
                    label = { it?.let { value -> stringResource(value.displayLabelRes) }
                        ?: stringResource(R.string.common_server_default) },
                    onSelect = { approval = it },
                )
                approval?.let { OptionDescription(stringResource(it.displayDescriptionRes)) }
                if (selectedModel?.supportsPersonality == true) {
                    Text("人格", style = MaterialTheme.typography.labelLarge)
                    ChoiceRow(
                        values = listOf(null, Personality.FRIENDLY, Personality.PRAGMATIC, Personality.NONE),
                        selected = personality,
                        label = { it?.let { value -> stringResource(value.displayLabelRes) }
                            ?: stringResource(R.string.common_server_default) },
                        onSelect = { personality = it },
                    )
                }
                Text("审批者", style = MaterialTheme.typography.labelLarge)
                ChoiceRow(
                    values = listOf<ApprovalReviewer?>(null) + ApprovalReviewer.entries,
                    selected = approvalReviewer,
                    label = { it?.let { value -> stringResource(value.displayLabelRes) }
                        ?: stringResource(R.string.common_server_default) },
                    onSelect = { approvalReviewer = it },
                )
                approvalReviewer?.let { OptionDescription(stringResource(it.displayDescriptionRes)) }
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
                    Switch(checked = ephemeral, onCheckedChange = { ephemeral = it })
                }
            }
            Button(
                onClick = {
                    onSave(
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
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("保存")
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun OptionDescription(text: String, critical: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (critical) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun <T> ChoiceRow(
    values: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        values.forEach { item ->
            FilterChip(
                selected = item == selected,
                onClick = { onSelect(item) },
                label = { Text(label(item)) },
                modifier = Modifier.padding(end = 8.dp),
            )
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
        Icon(Icons.Rounded.Dns, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("连接 Codex 服务端", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "添加服务端地址和能力令牌后即可开始任务",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAddServer) { Text("添加服务器") }
    }
}
