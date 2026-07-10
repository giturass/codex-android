@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hebo.codex.ui.screens.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
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
    onBack: () -> Unit,
    onOpenConversation: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val conversation = state.conversation
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val scrollScope = rememberCoroutineScope()
    var autoFollow by rememberSaveable(conversation?.threadId) { mutableStateOf(true) }
    var dialog by remember { mutableStateOf<ChatDialog?>(null) }
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
                                "${conversation.serverName} · ${conversation.cwd}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
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
                ExtendedFloatingActionButton(
                    onClick = {
                        scrollScope.launch {
                            val lastIndex = listState.layoutInfo.totalItemsCount - 1
                            if (lastIndex >= 0) {
                                listState.animateScrollToItem(lastIndex)
                                autoFollow = true
                            }
                        }
                    },
                    icon = {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null)
                    },
                    text = { Text("回到底部") },
                )
            }
        },
        bottomBar = {
            if (conversation != null) {
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
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
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
                items(conversation.pendingRequests, key = PendingServerRequest::requestKey) { request ->
                    PendingRequestCard(
                        request = request,
                        onApproval = viewModel::respondToApproval,
                        onExecPolicy = viewModel::respondToApprovalWithExecPolicy,
                        onNetworkPolicy = viewModel::respondToApprovalWithNetworkPolicy,
                        onQuestions = viewModel::respondToQuestions,
                        onMcp = viewModel::respondToMcpElicitation,
                        onPermissions = viewModel::respondToPermissions,
                    )
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
        ChatDialog.Actions -> ChatActionsDialog(
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
            modifier = Modifier.fillMaxWidth(0.84f),
            shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            SelectionContainer {
                Text(
                    item.text,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
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

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier.size(30.dp).background(MaterialTheme.colorScheme.inverseSurface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "C",
                color = MaterialTheme.colorScheme.inverseOnSurface,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("Codex", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            SelectionContainer {
                Markdown(
                    markdownState = markdownState,
                    components = highlightedMarkdownComponents,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ReasoningCard(item: ConversationItem.Reasoning) {
    var expanded by rememberSaveable(item.id) { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Psychology,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("思考", style = MaterialTheme.typography.labelLarge)
                    Text(
                        item.summary.ifBlank { if (item.isStreaming) "正在分析…" else "分析完成" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (expanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (item.details.isNotBlank()) {
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = if (expanded) "收起" else "展开",
                        )
                    }
                }
            }
            if (expanded && item.details.isNotBlank()) {
                HorizontalDivider(Modifier.padding(vertical = 10.dp))
                SelectionContainer { Text(item.details, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun CommandCard(item: ConversationItem.CommandExecution) {
    ActivitySurface(
        icon = { Icon(Icons.Rounded.Code, contentDescription = null) },
        title = "运行命令",
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
        status = item.status,
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
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) { icon() }
                Spacer(Modifier.width(10.dp))
                Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                ActivityStatusIcon(status)
            }
            content()
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
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
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
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                if (approval.type == ApprovalType.COMMAND_EXECUTION) "命令等待审批" else "文件修改等待审批",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
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
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
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
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
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
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("权限请求", style = MaterialTheme.typography.titleMedium)
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
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
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
        Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(10.dp))
        Text("Codex 正在处理")
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
    Surface(
        modifier = Modifier.imePadding(),
        shadowElevation = 8.dp,
    ) {
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
            Row(verticalAlignment = Alignment.Bottom) {
                IconButton(
                    onClick = {
                        onLoadResources(false)
                        dialog = ComposerDialog.Attachments
                    },
                ) { Icon(Icons.Rounded.AttachFile, contentDescription = "添加图片、Skill 或提及") }
                IconButton(
                    onClick = {
                        onLoadResources(false)
                        dialog = ComposerDialog.Options
                    },
                    enabled = !conversation.isRunning,
                ) { Icon(Icons.Rounded.Tune, contentDescription = "本轮设置") }
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(if (conversation.isRunning) "输入消息以引导当前回合" else "输入消息") },
                    minLines = 1,
                    maxLines = 5,
                    shape = RoundedCornerShape(22.dp),
                )
                Spacer(Modifier.width(8.dp))
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

    when (dialog) {
        ComposerDialog.Attachments -> AttachmentDialog(
            resources = resources,
            isLoading = isLoadingResources,
            onRefresh = { onLoadResources(true) },
            onDismiss = { dialog = null },
            onAdd = {
                attachments = attachments + it
                dialog = null
            },
        )

        ComposerDialog.Options -> TurnOptionsDialog(
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
private fun AttachmentDialog(
    resources: List<FeatureEntry>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
    onAdd: (UserInputPart) -> Unit,
) {
    var kind by rememberSaveable { mutableStateOf(ManualAttachment.IMAGE_URL) }
    var value by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加输入") },
        text = {
            Column(
                Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("图片", style = MaterialTheme.typography.labelLarge)
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Skills、Apps 与插件", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                    TextButton(onClick = onRefresh) { Text("刷新") }
                }
                if (isLoading) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                resources.forEach { entry ->
                    OutlinedButton(
                        onClick = {
                            val input = if (entry.kind == FeatureKind.SKILL) {
                                UserInputPart.Skill(entry.title, entry.path ?: entry.id)
                            } else {
                                UserInputPart.Mention(entry.title, entry.metadata["mentionPath"] ?: entry.path ?: entry.id)
                            }
                            onAdd(input)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(entry.title, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(entry.kind.name, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun TurnOptionsDialog(
    value: TurnOptions,
    models: List<ModelOption>,
    onDismiss: () -> Unit,
    onSave: (TurnOptions) -> Unit,
) {
    var options by remember(value) { mutableStateOf(value) }
    var writableRoots by remember(value) { mutableStateOf(value.sandboxWritableRoots.joinToString("\n")) }
    val selectableModels = models.filterNot(ModelOption::hidden)
    val model = selectableModels.firstOrNull { it.model == options.model }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("本轮设置") },
        text = {
            Column(
                Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
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
            }
        },
        confirmButton = {
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
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
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
private fun ChatActionsDialog(
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("会话操作") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
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
