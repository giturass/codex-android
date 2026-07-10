@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hebo.codex.ui.screens.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hebo.codex.domain.ActivityStatus
import com.hebo.codex.domain.ApprovalDecision
import com.hebo.codex.domain.ApprovalPolicy
import com.hebo.codex.domain.ApprovalReviewer
import com.hebo.codex.domain.ApprovalType
import com.hebo.codex.domain.ChangedFile
import com.hebo.codex.domain.ConversationItem
import com.hebo.codex.domain.ConversationState
import com.hebo.codex.domain.FileChangeKind
import com.hebo.codex.domain.FeatureEntry
import com.hebo.codex.domain.FeatureKind
import com.hebo.codex.domain.ModelOption
import com.hebo.codex.domain.PendingServerRequest
import com.hebo.codex.domain.Personality
import com.hebo.codex.domain.PlanStepStatus
import com.hebo.codex.domain.ReasoningSummary
import com.hebo.codex.domain.ReviewDelivery
import com.hebo.codex.domain.ReviewTarget
import com.hebo.codex.domain.SandboxMode
import com.hebo.codex.domain.ThreadGoal
import com.hebo.codex.domain.ThreadGoalStatus
import com.hebo.codex.domain.TurnOptions
import com.hebo.codex.domain.TurnPlan
import com.hebo.codex.domain.UserInputPart
import com.hebo.codex.domain.projectName
import com.hebo.codex.ui.theme.statusColors
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.highlightedCodeBlock
import com.mikepenz.markdown.compose.elements.highlightedCodeFence
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.model.rememberMarkdownState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray

private val highlightedMarkdownComponents = markdownComponents(
    codeBlock = highlightedCodeBlock,
    codeFence = highlightedCodeFence,
)

private const val CHAT_LIST_END_KEY = "chat-list-end"

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    showNavigationIcon: Boolean,
    onOpenNavigation: () -> Unit,
    onOpenConversation: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val conversation = state.conversation
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val scrollScope = rememberCoroutineScope()
    var autoFollow by rememberSaveable(conversation?.threadId) { mutableStateOf(true) }
    var dialog by remember { mutableStateOf<ChatDialog?>(null) }
    var activeRequestKey by rememberSaveable(conversation?.threadId) { mutableStateOf<String?>(null) }
    val activeRequest = conversation?.pendingRequests?.firstOrNull { it.requestKey == activeRequestKey }
    val userScrollConnection = remember(conversation?.threadId) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source == NestedScrollSource.UserInput && consumed.y != 0f) {
                    autoFollow = false
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ChatEvent.OpenConversation -> onOpenConversation(event.threadId)
            }
        }
    }
    LaunchedEffect(state.screenError, conversation?.errorMessage) {
        val error = state.screenError ?: conversation?.errorMessage
        if (error != null) {
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }
    LaunchedEffect(conversation?.pendingRequests?.map { it.requestKey }) {
        if (activeRequestKey != null && activeRequest == null) activeRequestKey = null
    }
    LaunchedEffect(listState, autoFollow) {
        if (autoFollow) {
            snapshotFlow {
                val lastIndex = listState.layoutInfo.totalItemsCount - 1
                lastIndex.takeIf { autoFollow && it >= 0 && listState.canScrollForward }
            }.filterNotNull().collectLatest { lastIndex ->
                listState.scrollToItem(lastIndex)
            }
        } else {
            snapshotFlow {
                !listState.isScrollInProgress && !listState.canScrollForward
            }.first { it }
            autoFollow = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            conversation?.title ?: "会话",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (conversation != null) {
                            Text(
                                "${projectName(conversation.cwd)} · ${conversation.serverName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
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
                    if (conversation != null) {
                        IconButton(onClick = { dialog = ChatDialog.Actions }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "会话操作")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (conversation != null && !autoFollow) {
                SmallFloatingActionButton(
                    onClick = {
                        scrollScope.launch {
                            val lastIndex = listState.layoutInfo.totalItemsCount - 1
                            if (lastIndex >= 0) {
                                listState.animateScrollToItem(lastIndex)
                                autoFollow = true
                            }
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "回到底部")
                }
            }
        },
        bottomBar = {
            if (conversation != null) {
                Column {
                    conversation.pendingRequests.firstOrNull()?.let { request ->
                        PendingRequestBanner(
                            request = request,
                            requestCount = conversation.pendingRequests.size,
                            onClick = { activeRequestKey = request.requestKey },
                        )
                    }
                    ChatComposer(
                        conversation = conversation,
                        models = state.models,
                        resources = state.inputResources,
                        isLoadingResources = state.isLoadingResources,
                        onLoadResources = viewModel::loadInputResources,
                        onSend = { input, options ->
                            autoFollow = true
                            viewModel.send(input, options)
                        },
                        onStop = viewModel::interrupt,
                    )
                }
            }
        },
    ) { padding ->
        when {
            state.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            conversation == null -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("无法打开会话")
            }

            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding).nestedScroll(userScrollConnection),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                state.goal?.let { goal -> item("goal") { GoalCard(goal) } }
                conversation.notices.forEach { notice ->
                    item("notice:${notice.id}") {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Text(notice.message, modifier = Modifier.fillMaxWidth().padding(12.dp))
                        }
                    }
                }
                conversation.plan?.let { plan -> item("plan") { PlanCard(plan) } }
                items(conversation.items, key = ConversationItem::id) { item ->
                    ConversationItemView(item)
                }
                if (conversation.turnDiff.isNotBlank()) {
                    item("turn-diff") {
                        ActivitySurface(
                            icon = { Icon(Icons.Rounded.Description, contentDescription = null) },
                            title = "本轮差异",
                            status = if (conversation.isRunning) ActivityStatus.IN_PROGRESS else ActivityStatus.COMPLETED,
                        ) { TerminalOutputBlock(conversation.turnDiff) }
                    }
                }
                conversation.tokenUsage?.let { usage ->
                    item("token-usage") {
                        Text(
                            "Token ${usage.totalTokens} · 输入 ${usage.inputTokens} · 输出 ${usage.outputTokens}" +
                                (usage.modelContextWindow?.let { " · 上下文 $it" } ?: ""),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (conversation.isRunning) {
                    item("running") { RunningIndicator() }
                }
                item(CHAT_LIST_END_KEY) {
                    Spacer(Modifier.height(1.dp))
                }
            }
        }
    }

    when (dialog) {
        ChatDialog.Actions -> ChatActionsSheet(
            title = conversation?.title.orEmpty(),
            turns = conversation?.items.orEmpty()
                .filter { it.turnId != conversation?.activeTurnId && it.turnId != "pending" }
                .distinctBy(ConversationItem::turnId)
                .map { it.turnId },
            canForkLatest = conversation?.isRunning != true,
            onDismiss = { dialog = null },
            onRename = {
                viewModel.rename(it)
                dialog = null
            },
            onFork = { lastTurnId ->
                viewModel.fork(lastTurnId)
                dialog = null
            },
            onCompact = {
                viewModel.compact()
                dialog = null
            },
            onReview = { dialog = ChatDialog.Review },
            onGoal = { dialog = ChatDialog.Goal },
        )

        ChatDialog.Review -> ReviewDialog(
            onDismiss = { dialog = null },
            onSubmit = { target, delivery ->
                viewModel.review(target, delivery)
                dialog = null
            },
        )

        ChatDialog.Goal -> GoalDialog(
            goal = state.goal,
            onDismiss = { dialog = null },
            onSave = { objective, status, budget ->
                viewModel.setGoal(objective, status, budget)
                dialog = null
            },
            onClear = {
                viewModel.clearGoal()
                dialog = null
            },
        )

        null -> Unit
    }

    activeRequest?.let { request ->
        ModalBottomSheet(
            onDismissRequest = { activeRequestKey = null },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.9f)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                PendingRequestCard(
                    request = request,
                    onApproval = viewModel::respondToApproval,
                    onExecPolicy = viewModel::respondToApprovalWithExecPolicy,
                    onNetworkPolicy = viewModel::respondToApprovalWithNetworkPolicy,
                    onQuestions = viewModel::respondToQuestions,
                    onMcp = viewModel::respondToMcpElicitation,
                    onPermissions = viewModel::respondToPermissions,
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ConversationItemView(item: ConversationItem) {
    when (item) {
        is ConversationItem.UserMessage -> UserMessage(item)
        is ConversationItem.AgentMessage -> AgentMessage(item)
        is ConversationItem.Reasoning -> ReasoningCard(item)
        is ConversationItem.CommandExecution -> CommandCard(item)
        is ConversationItem.FileChange -> FileChangeCard(item)
        is ConversationItem.Activity -> ActivityCard(item)
    }
}

@Composable
private fun UserMessage(item: ConversationItem.UserMessage) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.88f),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            SelectionContainer {
                Text(
                    item.text,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun AgentMessage(item: ConversationItem.AgentMessage) {
    val markdownState = rememberMarkdownState(
        content = item.text.ifEmpty { "…" },
        retainState = true,
    )

    Column(Modifier.fillMaxWidth().padding(horizontal = 2.dp)) {
        SelectionContainer {
            Markdown(
                markdownState = markdownState,
                components = highlightedMarkdownComponents,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ReasoningCard(item: ConversationItem.Reasoning) {
    ActivitySurface(
        icon = { Icon(Icons.Rounded.Psychology, contentDescription = null) },
        title = if (item.isStreaming) "正在思考" else "思考完成",
        summary = item.summary.ifBlank { if (item.isStreaming) "正在分析…" else "查看分析过程" },
        status = if (item.isStreaming) ActivityStatus.IN_PROGRESS else ActivityStatus.COMPLETED,
        hasDetails = item.details.isNotBlank(),
    ) {
        SelectionContainer { Text(item.details, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun CommandCard(item: ConversationItem.CommandExecution) {
    ActivitySurface(
        icon = { Icon(Icons.Rounded.Code, contentDescription = null) },
        title = "运行命令",
        summary = item.command.lineSequence().firstOrNull()?.take(120),
        status = item.status,
    ) {
        ShellCommandBlock(item.command)
        Text(
            item.cwd,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (item.output.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            TerminalOutputBlock(item.output)
        }
        if (item.exitCode != null) {
            Text(
                "退出码 ${item.exitCode}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FileChangeCard(item: ConversationItem.FileChange) {
    ActivitySurface(
        icon = { Icon(Icons.Rounded.Description, contentDescription = null) },
        title = "修改文件",
        summary = "${item.changes.size} 个文件",
        status = item.status,
    ) {
        item.changes.forEach { change -> ChangedFileView(change) }
    }
}

@Composable
private fun ChangedFileView(change: ChangedFile) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            changedFileTitle(change),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (change.diff.isNotBlank()) FileDiffBlock(change)
    }
}

private fun changedFileTitle(change: ChangedFile): String = when (val kind = change.kind) {
    FileChangeKind.Add -> "新增 · ${change.path}"
    FileChangeKind.Delete -> "删除 · ${change.path}"
    is FileChangeKind.Update -> kind.movePath
        ?.let { destination -> "移动 · ${change.path} → $destination" }
        ?: "修改 · ${change.path}"
}

@Composable
private fun ActivityCard(item: ConversationItem.Activity) {
    ActivitySurface(
        icon = { Icon(Icons.Rounded.HourglassTop, contentDescription = null) },
        title = item.title,
        summary = item.details.lineSequence().firstOrNull()?.take(120),
        status = item.status,
        hasDetails = item.details.isNotBlank(),
    ) {
        if (item.details.isNotBlank()) {
            SelectionContainer {
                Text(
                    item.details,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ActivitySurface(
    icon: @Composable () -> Unit,
    title: String,
    status: ActivityStatus,
    summary: String? = null,
    hasDetails: Boolean = true,
    content: @Composable () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = hasDetails) { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) { icon() }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.labelLarge)
                    summary?.takeIf(String::isNotBlank)?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                ActivityStatusIcon(status)
                if (hasDetails) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (expanded) "收起详情" else "展开详情",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            if (expanded && hasDetails) {
                HorizontalDivider()
                Column(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun ActivityStatusIcon(status: ActivityStatus) {
    val icon = when (status) {
        ActivityStatus.IN_PROGRESS -> Icons.Rounded.HourglassTop
        ActivityStatus.COMPLETED -> Icons.Rounded.CheckCircle
        ActivityStatus.FAILED -> Icons.Rounded.Error
        ActivityStatus.DECLINED -> Icons.Rounded.Block
    }
    val tint = when (status) {
        ActivityStatus.IN_PROGRESS -> MaterialTheme.colorScheme.primary
        ActivityStatus.COMPLETED -> MaterialTheme.statusColors.success
        ActivityStatus.FAILED, ActivityStatus.DECLINED -> MaterialTheme.colorScheme.error
    }
    val description = when (status) {
        ActivityStatus.IN_PROGRESS -> "进行中"
        ActivityStatus.COMPLETED -> "已完成"
        ActivityStatus.FAILED -> "失败"
        ActivityStatus.DECLINED -> "已拒绝"
    }
    Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(19.dp))
}

@Composable
private fun PendingRequestBanner(
    request: PendingServerRequest,
    requestCount: Int,
    onClick: () -> Unit,
) {
    val isCritical = request is PendingServerRequest.Approval || request is PendingServerRequest.Permissions
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            1.dp,
            if (isCritical) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Error,
                contentDescription = null,
                tint = if (isCritical) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(pendingRequestTitle(request), style = MaterialTheme.typography.labelLarge)
                Text(
                    if (requestCount > 1) "还有 ${requestCount - 1} 项等待处理" else pendingRequestSummary(request),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text("查看", style = MaterialTheme.typography.labelLarge)
        }
    }
}

private fun pendingRequestTitle(request: PendingServerRequest): String = when (request) {
    is PendingServerRequest.Approval -> if (request.type == ApprovalType.COMMAND_EXECUTION) {
        "命令等待确认"
    } else {
        "文件修改等待确认"
    }

    is PendingServerRequest.Questions -> "Codex 需要你的回答"
    is PendingServerRequest.McpElicitation -> "${request.serverName} 请求确认"
    is PendingServerRequest.Permissions -> "权限请求"
}

private fun pendingRequestSummary(request: PendingServerRequest): String = when (request) {
    is PendingServerRequest.Approval -> request.reason ?: request.command ?: request.cwd ?: "查看详情后决定"
    is PendingServerRequest.Questions -> request.questions.firstOrNull()?.question ?: "请补充信息"
    is PendingServerRequest.McpElicitation -> request.message
    is PendingServerRequest.Permissions -> request.reason ?: request.cwd
}

@Composable
private fun PendingRequestCard(
    request: PendingServerRequest,
    onApproval: (String, ApprovalDecision) -> Unit,
    onExecPolicy: (String, String) -> Unit,
    onNetworkPolicy: (String, String) -> Unit,
    onQuestions: (String, Map<String, List<String>>) -> Unit,
    onMcp: (String, String, String?) -> Unit,
    onPermissions: (String, String, Boolean, Boolean, Boolean) -> Unit,
) {
    when (request) {
        is PendingServerRequest.Approval -> ApprovalCard(
            approval = request,
            onDecision = { decision -> onApproval(request.requestKey, decision) },
            onExecPolicy = { amendment -> onExecPolicy(request.requestKey, amendment) },
            onNetworkPolicy = { amendment -> onNetworkPolicy(request.requestKey, amendment) },
        )

        is PendingServerRequest.Questions -> QuestionsCard(request) { answers ->
            onQuestions(request.requestKey, answers)
        }

        is PendingServerRequest.McpElicitation -> McpElicitationCard(request) { action, content ->
            onMcp(request.requestKey, action, content)
        }

        is PendingServerRequest.Permissions -> PermissionsCard(request) { grant, session, strictAutoReview ->
            onPermissions(request.requestKey, request.requestedPermissions, grant, session, strictAutoReview)
        }
    }
}

@Composable
private fun ApprovalCard(
    approval: PendingServerRequest.Approval,
    onDecision: (ApprovalDecision) -> Unit,
    onExecPolicy: (String) -> Unit,
    onNetworkPolicy: (String) -> Unit,
) {
    val networkAmendments = remember(approval.proposedNetworkPolicies) {
        runCatching {
            Json.parseToJsonElement(approval.proposedNetworkPolicies.orEmpty()) as? JsonArray
        }.getOrNull().orEmpty()
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                if (approval.type == ApprovalType.COMMAND_EXECUTION) "命令等待审批" else "文件修改等待审批",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
            )
            approval.reason?.takeIf(String::isNotBlank)?.let { Text(it) }
            approval.command?.takeIf(String::isNotBlank)?.let { ShellCommandBlock(it) }
            approval.cwd?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            approval.networkHost?.let { Text("网络主机：$it") }
            approval.grantRoot?.let { Text("授权目录：$it") }
            approval.commandActions?.let { JsonText(it) }
            approval.proposedExecPolicy?.let { amendment ->
                JsonText(amendment)
                OutlinedButton(onClick = { onExecPolicy(amendment) }, modifier = Modifier.fillMaxWidth()) {
                    Text("应用命令规则并允许")
                }
            }
            networkAmendments.forEach { amendment ->
                JsonText(amendment.toString())
                OutlinedButton(
                    onClick = { onNetworkPolicy(amendment.toString()) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("应用此网络规则并允许") }
            }
            Button(
                onClick = { onDecision(ApprovalDecision.ACCEPT) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("允许一次") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onDecision(ApprovalDecision.ACCEPT_FOR_SESSION) },
                    modifier = Modifier.weight(1f),
                ) { Text("本会话允许") }
                TextButton(
                    onClick = { onDecision(ApprovalDecision.DECLINE) },
                    modifier = Modifier.weight(1f),
                ) { Text("拒绝") }
            }
        }
    }
}

@Composable
private fun QuestionsCard(
    request: PendingServerRequest.Questions,
    onSubmit: (Map<String, List<String>>) -> Unit,
) {
    var answers by remember(request.requestKey) { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Codex 需要你的回答", style = MaterialTheme.typography.titleMedium)
            request.questions.forEach { question ->
                Text(question.header, style = MaterialTheme.typography.labelLarge)
                Text(question.question)
                if (question.options.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        question.options.forEach { option ->
                            val selected = option.label in answers[question.id].orEmpty()
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    val current = answers[question.id].orEmpty()
                                    answers = answers + (
                                        question.id to if (selected) current - option.label else current + option.label
                                    )
                                },
                                label = { Text(option.label) },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                    }
                    question.options.forEach { option ->
                        if (option.description.isNotBlank()) {
                            Text(
                                "${option.label}：${option.description}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (question.options.isEmpty() || question.allowsOther) {
                    val current = answers[question.id].orEmpty()
                    val optionLabels = question.options.map { it.label }.toSet()
                    val other = current.firstOrNull { it !in optionLabels }.orEmpty()
                    OutlinedTextField(
                        value = other,
                        onValueChange = { value ->
                            answers = answers + (question.id to (current.filter { it in optionLabels } + value).filter(String::isNotBlank))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(if (question.isSecret) "保密回答" else "其它回答") },
                        visualTransformation = if (question.isSecret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                    )
                }
            }
            request.autoResolutionMillis?.let { Text("将在 ${it / 1000} 秒后自动处理", style = MaterialTheme.typography.labelSmall) }
            Button(onClick = { onSubmit(answers) }, modifier = Modifier.fillMaxWidth()) { Text("提交回答") }
        }
    }
}

@Composable
private fun McpElicitationCard(
    request: PendingServerRequest.McpElicitation,
    onDecision: (String, String?) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    var content by rememberSaveable(request.requestKey) { mutableStateOf("") }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${request.serverName} 请求确认", style = MaterialTheme.typography.titleMedium)
            Text(request.message)
            request.requestedSchema?.let {
                Text("响应结构", style = MaterialTheme.typography.labelLarge)
                JsonText(it)
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("响应 JSON") },
                    minLines = 3,
                )
            }
            request.url?.let { url ->
                OutlinedButton(onClick = { uriHandler.openUri(url) }, modifier = Modifier.fillMaxWidth()) {
                    Text("打开授权页面")
                }
            }
            Button(
                onClick = { onDecision("accept", content.ifBlank { null }) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("接受") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onDecision("decline", null) }, modifier = Modifier.weight(1f)) {
                    Text("拒绝")
                }
                TextButton(onClick = { onDecision("cancel", null) }, modifier = Modifier.weight(1f)) {
                    Text("取消")
                }
            }
        }
    }
}

@Composable
private fun PermissionsCard(
    request: PendingServerRequest.Permissions,
    onDecision: (grant: Boolean, session: Boolean, strictAutoReview: Boolean) -> Unit,
) {
    var strictAutoReview by rememberSaveable(request.requestKey) { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("权限请求", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
            request.reason?.let { Text(it) }
            Text(request.cwd, style = MaterialTheme.typography.labelSmall)
            JsonText(request.requestedPermissions)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("本回合后续命令严格自动审查", modifier = Modifier.weight(1f))
                Switch(checked = strictAutoReview, onCheckedChange = { strictAutoReview = it })
            }
            Button(
                onClick = { onDecision(true, false, strictAutoReview) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("允许一次") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onDecision(true, true, false) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("本会话允许")
                }
                TextButton(onClick = { onDecision(false, false, false) }, modifier = Modifier.weight(1f)) {
                    Text("拒绝")
                }
            }
        }
    }
}

@Composable
private fun JsonText(value: String) {
    SelectionContainer {
        Text(
            value,
            modifier = Modifier.fillMaxWidth().background(
                MaterialTheme.colorScheme.inverseSurface,
                RoundedCornerShape(8.dp),
            ).padding(10.dp),
            color = MaterialTheme.colorScheme.inverseOnSurface,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun PlanCard(plan: TurnPlan) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("执行计划", style = MaterialTheme.typography.titleMedium)
            plan.explanation?.takeIf(String::isNotBlank)?.let { Text(it) }
            plan.steps.forEach { step ->
                val mark = when (step.status) {
                    PlanStepStatus.PENDING -> "○"
                    PlanStepStatus.IN_PROGRESS -> "◉"
                    PlanStepStatus.COMPLETED -> "✓"
                }
                Text("$mark ${step.text}")
            }
        }
    }
}

@Composable
private fun GoalCard(goal: ThreadGoal) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("目标 · ${goal.status}", style = MaterialTheme.typography.titleMedium)
            Text(goal.objective)
            Text(
                "已用 ${goal.tokensUsed} Token · ${goal.timeUsedSeconds} 秒" +
                    (goal.tokenBudget?.let { " · 预算 $it" } ?: ""),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun RunningIndicator() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(8.dp))
        Text("正在处理", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ChatComposer(
    conversation: ConversationState,
    models: List<ModelOption>,
    resources: List<FeatureEntry>,
    isLoadingResources: Boolean,
    onLoadResources: (Boolean) -> Unit,
    onSend: (List<UserInputPart>, TurnOptions) -> Unit,
    onStop: () -> Unit,
) {
    var message by rememberSaveable(conversation.threadId) { mutableStateOf("") }
    var attachments by remember(conversation.threadId) { mutableStateOf<List<UserInputPart>>(emptyList()) }
    var options by remember(conversation.threadId) {
        mutableStateOf(TurnOptions(model = conversation.model, serviceTier = conversation.serviceTier))
    }
    var dialog by remember { mutableStateOf<ComposerDialog?>(null) }
    val selectedModel = models.firstOrNull { it.model == options.model }
    val modeLabel = options.reasoningEffort
        ?.let { effort -> selectedModel?.reasoningEfforts?.firstOrNull { it.value == effort }?.label ?: effort }
        ?: selectedModel?.displayName
        ?: options.model
        ?: "默认"
    Surface(modifier = Modifier.imePadding(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (attachments.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    attachments.forEachIndexed { index, part ->
                        FilterChip(
                            selected = true,
                            onClick = { attachments = attachments.filterIndexed { itemIndex, _ -> itemIndex != index } },
                            label = { Text(part.label) },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(6.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    IconButton(
                        onClick = {
                            onLoadResources(false)
                            dialog = ComposerDialog.Attachments
                        },
                    ) { Icon(Icons.Rounded.Add, contentDescription = "添加图片、Skill 或提及") }
                    BasicTextField(
                        value = message,
                        onValueChange = { message = it },
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp, vertical = 11.dp),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        minLines = 1,
                        maxLines = 5,
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (message.isEmpty()) {
                                    Text(
                                        if (conversation.isRunning) "输入消息以引导当前回合" else "向 Codex 发送消息",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )
                    TextButton(
                        onClick = {
                            onLoadResources(false)
                            dialog = ComposerDialog.Options
                        },
                        enabled = !conversation.isRunning,
                        modifier = Modifier.widthIn(max = 88.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) {
                        Text(modeLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (conversation.isRunning) {
                        IconButton(onClick = onStop) {
                            Icon(Icons.Rounded.Stop, contentDescription = "停止")
                        }
                    }
                    FilledIconButton(
                        onClick = {
                            val input = buildList {
                                message.takeIf(String::isNotBlank)?.let { add(UserInputPart.Text(it)) }
                                addAll(attachments)
                            }
                            if (input.isNotEmpty()) {
                                onSend(input, options)
                                message = ""
                                attachments = emptyList()
                            }
                        },
                        enabled = message.isNotBlank() || attachments.isNotEmpty(),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Send,
                            contentDescription = if (conversation.isRunning) "引导当前回合" else "发送",
                        )
                    }
                }
            }
        }
    }

    when (dialog) {
        ComposerDialog.Attachments -> AttachmentSheet(
            resources = resources,
            isLoading = isLoadingResources,
            onRefresh = { onLoadResources(true) },
            onDismiss = { dialog = null },
            onAdd = {
                attachments = attachments + it
                dialog = null
            },
        )

        ComposerDialog.Options -> TurnOptionsSheet(
            value = options,
            models = models,
            onDismiss = { dialog = null },
            onSave = {
                options = it
                dialog = null
            },
        )

        null -> Unit
    }
}

@Composable
private fun AttachmentSheet(
    resources: List<FeatureEntry>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
    onAdd: (UserInputPart) -> Unit,
) {
    var kind by rememberSaveable { mutableStateOf(ManualAttachment.IMAGE_URL) }
    var value by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var resourceFilter by rememberSaveable { mutableStateOf(ResourceFilter.ALL) }
    var showImageForm by rememberSaveable { mutableStateOf(false) }
    val filteredResources = remember(resources, query, resourceFilter) {
        resources.filter { entry ->
            resourceFilter.accepts(entry.kind) &&
                (query.isBlank() || listOf(entry.title, entry.description, entry.parentId, entry.path)
                    .filterNotNull()
                    .joinToString(" ")
                    .contains(query, ignoreCase = true))
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("添加输入", style = MaterialTheme.typography.titleLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { showImageForm = !showImageForm }) {
                    Icon(Icons.Rounded.Image, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (showImageForm) "收起图片输入" else "添加图片")
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onRefresh) { Text("刷新") }
            }
            if (showImageForm) {
                OptionRow(
                    values = ManualAttachment.entries,
                    selected = kind,
                    label = { if (it == ManualAttachment.IMAGE_URL) "图片 URL" else "服务端本地图片" },
                    onSelect = { kind = it },
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (kind == ManualAttachment.IMAGE_URL) "URL 或 data URL" else "服务端绝对路径") },
                )
                Button(
                    onClick = {
                        onAdd(
                            if (kind == ManualAttachment.IMAGE_URL) UserInputPart.Image(value.trim())
                            else UserInputPart.LocalImage(value.trim()),
                        )
                    },
                    enabled = value.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("添加图片") }
                HorizontalDivider()
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("搜索 Skills、Apps 与 Plugins") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "清除搜索")
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
            )
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                ResourceFilter.entries.forEachIndexed { index, filter ->
                    if (index > 0) Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = resourceFilter == filter,
                        onClick = { resourceFilter = filter },
                        label = { Text(filter.label) },
                    )
                }
            }
            if (isLoading) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                if (!isLoading && filteredResources.isEmpty()) {
                    item("resources-empty") {
                        Box(
                            Modifier.fillMaxWidth().padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (query.isBlank()) "当前没有可用内容" else "没有匹配的内容",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                items(filteredResources, key = { "${it.kind}:${it.id}" }) { entry ->
                    ListItem(
                        headlineContent = { Text(entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            if (entry.description.isNotBlank()) {
                                Text(entry.description, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        },
                        leadingContent = {
                            Icon(
                                when (entry.kind) {
                                    FeatureKind.SKILL -> Icons.Rounded.AutoAwesome
                                    FeatureKind.APP -> Icons.Rounded.Apps
                                    else -> Icons.Rounded.Extension
                                },
                                contentDescription = null,
                            )
                        },
                        trailingContent = {
                            Text(entry.resourceOrigin, style = MaterialTheme.typography.labelSmall)
                        },
                        modifier = Modifier.clickable {
                            val input = if (entry.kind == FeatureKind.SKILL) {
                                UserInputPart.Skill(entry.title, entry.path ?: entry.id)
                            } else {
                                UserInputPart.Mention(entry.title, entry.metadata["mentionPath"] ?: entry.path ?: entry.id)
                            }
                            onAdd(input)
                        },
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp))
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("关闭") }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun TurnOptionsSheet(
    value: TurnOptions,
    models: List<ModelOption>,
    onDismiss: () -> Unit,
    onSave: (TurnOptions) -> Unit,
) {
    var options by remember(value) { mutableStateOf(value) }
    var writableRoots by remember(value) { mutableStateOf(value.sandboxWritableRoots.joinToString("\n")) }
    val selectableModels = models.filterNot(ModelOption::hidden)
    val model = selectableModels.firstOrNull { it.model == options.model }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
                Text("本轮设置", style = MaterialTheme.typography.titleLarge)
                Text(
                    "模型与推理强度只影响下一轮，其他选项沿用会话默认值",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = options.cwd.orEmpty(),
                    onValueChange = { options = options.copy(cwd = it.ifBlank { null }) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("工作目录覆盖，可留空") },
                    singleLine = true,
                )
                if (selectableModels.isNotEmpty()) {
                    Text("模型", style = MaterialTheme.typography.labelLarge)
                    OptionRow(
                        values = listOf<ModelOption?>(null) + selectableModels,
                        selected = model,
                        label = { it?.displayName ?: "会话默认" },
                        onSelect = { selected ->
                            options = options.copy(
                                model = selected?.model,
                                serviceTier = selected?.defaultServiceTier,
                                reasoningEffort = selected?.defaultReasoningEffort,
                            )
                        },
                    )
                }
                OutlinedTextField(
                    value = options.model.orEmpty(),
                    onValueChange = { options = options.copy(model = it.ifBlank { null }) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("模型 ID，可留空") },
                    singleLine = true,
                )
                model?.reasoningEfforts?.takeIf(List<*>::isNotEmpty)?.let { efforts ->
                    Text("推理强度", style = MaterialTheme.typography.labelLarge)
                    OptionRow(
                        values = listOf<String?>(null) + efforts.map { it.value },
                        selected = options.reasoningEffort,
                        label = { effort -> efforts.firstOrNull { it.value == effort }?.label ?: "默认" },
                        onSelect = { options = options.copy(reasoningEffort = it) },
                    )
                }
                model?.serviceTiers?.takeIf(List<*>::isNotEmpty)?.let { tiers ->
                    Text("服务层级", style = MaterialTheme.typography.labelLarge)
                    OptionRow(
                        values = listOf<String?>(null) + tiers.map { it.value },
                        selected = options.serviceTier,
                        label = { tier -> tiers.firstOrNull { it.value == tier }?.label ?: "默认" },
                        onSelect = { options = options.copy(serviceTier = it) },
                    )
                }
                OutlinedTextField(
                    value = options.serviceTier.orEmpty(),
                    onValueChange = { options = options.copy(serviceTier = it.ifBlank { null }) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("服务层级，可留空") },
                    singleLine = true,
                )
                Text("推理摘要", style = MaterialTheme.typography.labelLarge)
                OptionRow(
                    values = listOf<ReasoningSummary?>(null) + ReasoningSummary.entries,
                    selected = options.reasoningSummary,
                    label = { it?.wireValue ?: "默认" },
                    onSelect = { options = options.copy(reasoningSummary = it) },
                )
                if (model == null || model.supportsPersonality) {
                    Text("人格", style = MaterialTheme.typography.labelLarge)
                    OptionRow(
                        values = listOf<Personality?>(null) + Personality.entries,
                        selected = options.personality,
                        label = { it?.wireValue ?: "默认" },
                        onSelect = { options = options.copy(personality = it) },
                    )
                }
                Text("沙箱", style = MaterialTheme.typography.labelLarge)
                OptionRow(
                    values = listOf<SandboxMode?>(null) + SandboxMode.entries,
                    selected = options.sandboxMode,
                    label = { it?.wireValue ?: "默认" },
                    onSelect = { options = options.copy(sandboxMode = it) },
                )
                if (options.sandboxMode == SandboxMode.READ_ONLY || options.sandboxMode == SandboxMode.WORKSPACE_WRITE) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("允许网络访问", modifier = Modifier.weight(1f))
                        Switch(
                            checked = options.sandboxNetworkAccess,
                            onCheckedChange = { options = options.copy(sandboxNetworkAccess = it) },
                        )
                    }
                }
                if (options.sandboxMode == SandboxMode.WORKSPACE_WRITE) {
                    OutlinedTextField(
                        value = writableRoots,
                        onValueChange = { writableRoots = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("额外可写绝对路径，每行一个") },
                        minLines = 2,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("排除 TMPDIR", modifier = Modifier.weight(1f))
                        Switch(
                            checked = options.sandboxExcludeTmpdirEnvVar,
                            onCheckedChange = { options = options.copy(sandboxExcludeTmpdirEnvVar = it) },
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("排除 /tmp", modifier = Modifier.weight(1f))
                        Switch(
                            checked = options.sandboxExcludeSlashTmp,
                            onCheckedChange = { options = options.copy(sandboxExcludeSlashTmp = it) },
                        )
                    }
                }
                Text("审批策略", style = MaterialTheme.typography.labelLarge)
                OptionRow(
                    values = listOf<ApprovalPolicy?>(null) + ApprovalPolicy.entries,
                    selected = options.approvalPolicy,
                    label = { it?.wireValue ?: "默认" },
                    onSelect = { options = options.copy(approvalPolicy = it) },
                )
                Text("审批者", style = MaterialTheme.typography.labelLarge)
                OptionRow(
                    values = listOf<ApprovalReviewer?>(null) + ApprovalReviewer.entries,
                    selected = options.approvalsReviewer,
                    label = { it?.wireValue ?: "默认" },
                    onSelect = { options = options.copy(approvalsReviewer = it) },
                )
                OutlinedTextField(
                    value = options.outputSchema.orEmpty(),
                    onValueChange = { options = options.copy(outputSchema = it.ifBlank { null }) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("结构化输出 JSON Schema，可留空") },
                    minLines = 3,
                )
            Button(
                onClick = {
                    onSave(
                        options.copy(
                            sandboxWritableRoots = writableRoots.lineSequence()
                                .map(String::trim)
                                .filter(String::isNotEmpty)
                                .toList(),
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("应用设置") }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun <T> OptionRow(
    values: List<T>,
    selected: T,
    label: (T) -> String,
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
private fun ChatActionsSheet(
    title: String,
    turns: List<String>,
    canForkLatest: Boolean,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onFork: (String?) -> Unit,
    onCompact: () -> Unit,
    onReview: () -> Unit,
    onGoal: () -> Unit,
) {
    var name by rememberSaveable(title) { mutableStateOf(title) }
    var lastTurnId by rememberSaveable(turns) { mutableStateOf<String?>(null) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
                Text("会话操作", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("会话名称") },
                    singleLine = true,
                )
                Button(onClick = { onRename(name) }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    Text("保存名称")
                }
                if (turns.isNotEmpty()) {
                    Text("分叉截止回合", style = MaterialTheme.typography.labelLarge)
                    OptionRow(
                        values = listOf<String?>(null) + turns,
                        selected = lastTurnId,
                        label = { turnId ->
                            turnId?.let { "第 ${turns.indexOf(it) + 1} 轮" } ?: "最新回合"
                        },
                        onSelect = { lastTurnId = it },
                    )
                }
                OutlinedButton(
                    onClick = { onFork(lastTurnId) },
                    enabled = lastTurnId != null || canForkLatest,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("分叉会话")
                }
                OutlinedButton(onClick = onCompact, modifier = Modifier.fillMaxWidth()) { Text("压缩上下文") }
                OutlinedButton(onClick = onReview, modifier = Modifier.fillMaxWidth()) { Text("代码审查") }
                OutlinedButton(onClick = onGoal, modifier = Modifier.fillMaxWidth()) { Text("目标管理") }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("关闭") }
                Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ReviewDialog(
    onDismiss: () -> Unit,
    onSubmit: (ReviewTarget, ReviewDelivery) -> Unit,
) {
    var type by rememberSaveable { mutableStateOf(ReviewType.UNCOMMITTED) }
    var value by rememberSaveable { mutableStateOf("") }
    var detached by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("代码审查") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OptionRow(
                    values = ReviewType.entries,
                    selected = type,
                    label = ReviewType::label,
                    onSelect = { type = it },
                )
                if (type != ReviewType.UNCOMMITTED) {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(type.inputLabel) },
                        minLines = if (type == ReviewType.CUSTOM) 3 else 1,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("在独立会话中审查", modifier = Modifier.weight(1f))
                    Switch(checked = detached, onCheckedChange = { detached = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val target = when (type) {
                        ReviewType.UNCOMMITTED -> ReviewTarget.UncommittedChanges
                        ReviewType.BASE_BRANCH -> ReviewTarget.BaseBranch(value.trim())
                        ReviewType.COMMIT -> ReviewTarget.Commit(value.trim())
                        ReviewType.CUSTOM -> ReviewTarget.Custom(value.trim())
                    }
                    onSubmit(target, if (detached) ReviewDelivery.DETACHED else ReviewDelivery.INLINE)
                },
                enabled = type == ReviewType.UNCOMMITTED || value.isNotBlank(),
            ) { Text("开始") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun GoalDialog(
    goal: ThreadGoal?,
    onDismiss: () -> Unit,
    onSave: (String, ThreadGoalStatus, Long?) -> Unit,
    onClear: () -> Unit,
) {
    var objective by rememberSaveable(goal?.objective) { mutableStateOf(goal?.objective.orEmpty()) }
    var budget by rememberSaveable(goal?.tokenBudget) { mutableStateOf(goal?.tokenBudget?.toString().orEmpty()) }
    var status by rememberSaveable(goal?.status) {
        mutableStateOf(ThreadGoalStatus.entries.firstOrNull { it.wireValue == goal?.status } ?: ThreadGoalStatus.ACTIVE)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("目标管理") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = objective,
                    onValueChange = { objective = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("目标") },
                    minLines = 3,
                )
                Text("状态", style = MaterialTheme.typography.labelLarge)
                OptionRow(
                    values = ThreadGoalStatus.entries,
                    selected = status,
                    label = { it.wireValue },
                    onSelect = { status = it },
                )
                OutlinedTextField(
                    value = budget,
                    onValueChange = { budget = it.filter(Char::isDigit) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Token 预算，可留空") },
                    singleLine = true,
                )
                if (goal != null) TextButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) { Text("清除目标") }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(objective.trim(), status, budget.toLongOrNull()) }, enabled = objective.isNotBlank()) {
                Text("保存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private sealed interface ChatDialog {
    data object Actions : ChatDialog
    data object Review : ChatDialog
    data object Goal : ChatDialog
}

private enum class ComposerDialog {
    Attachments,
    Options,
}

private enum class ManualAttachment {
    IMAGE_URL,
    LOCAL_IMAGE,
}

private enum class ResourceFilter(val label: String) {
    ALL("全部"),
    SKILLS("Skills"),
    APPS("Apps"),
    PLUGINS("Plugins"),
    ;

    fun accepts(kind: FeatureKind): Boolean = when (this) {
        ALL -> kind == FeatureKind.SKILL || kind == FeatureKind.APP || kind == FeatureKind.PLUGIN
        SKILLS -> kind == FeatureKind.SKILL
        APPS -> kind == FeatureKind.APP
        PLUGINS -> kind == FeatureKind.PLUGIN
    }
}

private enum class ReviewType(val label: String, val inputLabel: String) {
    UNCOMMITTED("未提交修改", ""),
    BASE_BRANCH("相对分支", "基础分支"),
    COMMIT("提交", "提交 SHA"),
    CUSTOM("自定义", "审查说明"),
}

private val UserInputPart.label: String
    get() = when (this) {
        is UserInputPart.Text -> text.take(24)
        is UserInputPart.Image -> "图片 URL"
        is UserInputPart.LocalImage -> "本地图片"
        is UserInputPart.Skill -> "Skill · $name"
        is UserInputPart.Mention -> "提及 · $name"
    }

private val FeatureEntry.resourceOrigin: String
    get() = when (kind) {
        FeatureKind.SKILL -> metadata["scope"]?.ifBlank { null } ?: "Skill"
        FeatureKind.APP -> "App"
        FeatureKind.PLUGIN -> parentId ?: "Plugin"
        else -> kind.name
    }
