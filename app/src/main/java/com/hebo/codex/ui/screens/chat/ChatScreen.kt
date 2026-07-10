@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hebo.codex.ui.screens.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
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
import androidx.compose.material.icons.rounded.Lock
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hebo.codex.R
import com.hebo.codex.domain.ActivityStatus
import com.hebo.codex.domain.ApprovalDecision
import com.hebo.codex.domain.ApprovalPolicy
import com.hebo.codex.domain.ApprovalReviewer
import com.hebo.codex.domain.ApprovalType
import com.hebo.codex.domain.ChangedFile
import com.hebo.codex.domain.ConnectionStatus
import com.hebo.codex.domain.ConversationItem
import com.hebo.codex.domain.ConversationState
import com.hebo.codex.domain.FileChangeKind
import com.hebo.codex.domain.FeatureEntry
import com.hebo.codex.domain.FeatureKind
import com.hebo.codex.domain.ModelOption
import com.hebo.codex.domain.NetworkPolicyRuleAction
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
import com.hebo.codex.domain.UserInputPart
import com.hebo.codex.domain.UserFacingError
import com.hebo.codex.domain.projectName
import com.hebo.codex.ui.displayDescriptionRes
import com.hebo.codex.ui.displayLabelRes
import com.hebo.codex.ui.formatCount
import com.hebo.codex.ui.formatPercent
import com.hebo.codex.ui.featureScopeLabel
import com.hebo.codex.ui.components.ExpandableErrorDetails
import com.hebo.codex.ui.components.UserFacingErrorDialog
import com.hebo.codex.ui.theme.statusColors
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.highlightedCodeBlock
import com.mikepenz.markdown.compose.elements.highlightedCodeFence
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.model.rememberMarkdownState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

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
    val conversationRows = remember(conversation?.items) {
        buildConversationRows(conversation?.items.orEmpty())
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val scrollScope = rememberCoroutineScope()
    var autoFollow by rememberSaveable(conversation?.threadId) { mutableStateOf(true) }
    var dialog by remember { mutableStateOf<ChatDialog?>(null) }
    var activeRequestKey by rememberSaveable(conversation?.threadId) { mutableStateOf<String?>(null) }
    var technicalError by remember { mutableStateOf<UserFacingError?>(null) }
    val detailsLabel = stringResource(R.string.common_details)
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
            val result = snackbarHostState.showSnackbar(
                message = error.summary,
                actionLabel = error.technicalDetails?.let { detailsLabel },
            )
            if (result == SnackbarResult.ActionPerformed) technicalError = error
            viewModel.clearError()
        }
    }
    LaunchedEffect(conversation?.pendingRequests?.map { it.requestKey }) {
        if (activeRequestKey != null && activeRequest == null) {
            activeRequestKey = conversation?.pendingRequests?.firstOrNull()?.requestKey
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
                            val status = when {
                                state.connectionStatus !is ConnectionStatus.Connected -> "已断开"
                                conversation.pendingRequests.isNotEmpty() -> "需要处理"
                                conversation.isRunning -> "运行中"
                                conversation.errorMessage != null -> "失败"
                                else -> "可继续"
                            }
                            Text(
                                "$status · ${projectName(conversation.cwd)} · ${conversation.serverName}",
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
                    TaskProgressBar(
                        goal = state.goal,
                        plan = conversation.plan?.takeIf { plan ->
                            conversation.isRunning || plan.steps.any { it.status != PlanStepStatus.COMPLETED }
                        },
                        onOpenGoal = { dialog = ChatDialog.Goal },
                        onSetGoalStatus = { status ->
                            state.goal?.let { goal ->
                                viewModel.setGoal(goal.objective, status, goal.tokenBudget)
                            }
                        },
                    )
                    conversation.pendingRequests.firstOrNull()?.let { request ->
                        PendingRequestBanner(
                            request = request,
                            requestCount = conversation.pendingRequests.size,
                            onClick = { activeRequestKey = request.requestKey },
                        )
                    }
                    ChatComposer(
                        conversation = conversation,
                        draft = state.draft,
                        models = state.models,
                        resources = state.inputResources,
                        connectionStatus = state.connectionStatus,
                        isLoadingResources = state.isLoadingResources,
                        isSending = state.isSendingDraft,
                        onLoadResources = viewModel::loadInputResources,
                        onMessageChange = viewModel::updateDraftMessage,
                        onAddAttachment = viewModel::addDraftAttachment,
                        onRemoveAttachment = viewModel::removeDraftAttachment,
                        onOptionsChange = viewModel::updateDraftOptions,
                        onInputError = viewModel::reportInputError,
                        onOpenGoal = { dialog = ChatDialog.Goal },
                        onOpenReview = { dialog = ChatDialog.Review },
                        onCompact = viewModel::compact,
                        onSend = {
                            autoFollow = true
                            viewModel.sendDraft()
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
                conversation.notices.forEach { notice ->
                    item("notice:${notice.id}") {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            if (notice.technicalDetails == null) {
                                Text(notice.message, modifier = Modifier.fillMaxWidth().padding(12.dp))
                            } else {
                                ExpandableErrorDetails(
                                    error = UserFacingError(notice.message, notice.technicalDetails),
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                )
                            }
                        }
                    }
                }
                items(conversationRows, key = ConversationRow::key) { row ->
                    when (row) {
                        is ConversationRow.Item -> ConversationItemView(row.item)
                        is ConversationRow.ActivityGroup -> ActivityGroupCard(row.items)
                    }
                }
                if (conversation.turnDiff.isNotBlank()) {
                    item("turn-diff") {
                        TurnDiffSummaryCard(
                            diff = conversation.turnDiff,
                            isRunning = conversation.isRunning,
                            onOpen = { dialog = ChatDialog.Changes },
                        )
                    }
                }
                conversation.tokenUsage?.let { usage ->
                    item("token-usage") {
                        val contextUsage = usage.modelContextWindow
                            ?.takeIf { it > 0 }
                            ?.let { formatPercent(usage.totalTokens * 100.0 / it) }
                        Text(
                            "已用 ${formatCount(usage.totalTokens)} Token" +
                                " · 输入 ${formatCount(usage.inputTokens)}" +
                                (usage.cachedInputTokens.takeIf { it > 0 }?.let { "（缓存 ${formatCount(it)}）" } ?: "") +
                                " · 输出 ${formatCount(usage.outputTokens)}" +
                                (contextUsage?.let { " · 占上下文 $it" } ?: ""),
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

        ChatDialog.Changes -> ReviewChangesDialog(
            diff = conversation?.turnDiff.orEmpty(),
            onDismiss = { dialog = null },
            onAddComment = viewModel::appendToDraft,
        )

        null -> Unit
    }

    activeRequest?.let { request ->
        val pendingRequests = conversation.pendingRequests
        val requestIndex = pendingRequests.indexOfFirst { it.requestKey == request.requestKey }.coerceAtLeast(0)
        ModalBottomSheet(
            onDismissRequest = { activeRequestKey = null },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.9f)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "待处理 ${requestIndex + 1}/${pendingRequests.size}",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    IconButton(
                        onClick = { activeRequestKey = pendingRequests[requestIndex - 1].requestKey },
                        enabled = requestIndex > 0,
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "上一项")
                    }
                    IconButton(
                        onClick = { activeRequestKey = pendingRequests[requestIndex + 1].requestKey },
                        enabled = requestIndex < pendingRequests.lastIndex,
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "下一项")
                    }
                }
                Box(
                    Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                ) {
                    Column {
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
    }
    technicalError?.let { error ->
        UserFacingErrorDialog(error = error, onDismiss = { technicalError = null })
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
private fun ActivityGroupCard(items: List<ConversationItem>) {
    val commandCount = items.count { it is ConversationItem.CommandExecution }
    val changedFileCount = items.filterIsInstance<ConversationItem.FileChange>().sumOf { it.changes.size }
    val otherCount = items.size - commandCount - items.count { it is ConversationItem.FileChange }
    val summary = buildList {
        if (commandCount > 0) add("$commandCount 条命令")
        if (changedFileCount > 0) add("$changedFileCount 个文件")
        if (otherCount > 0) add("$otherCount 项活动")
    }.joinToString(" · ")
    val statuses = items.map(ConversationItem::activityStatus)
    val status = when {
        ActivityStatus.IN_PROGRESS in statuses -> ActivityStatus.IN_PROGRESS
        ActivityStatus.FAILED in statuses -> ActivityStatus.FAILED
        ActivityStatus.DECLINED in statuses -> ActivityStatus.DECLINED
        else -> ActivityStatus.COMPLETED
    }
    ActivitySurface(
        icon = { Icon(Icons.Rounded.Code, contentDescription = null) },
        title = if (status == ActivityStatus.IN_PROGRESS) "正在执行" else "活动记录",
        summary = summary,
        status = status,
        initiallyExpanded = status == ActivityStatus.IN_PROGRESS,
    ) {
        items.forEach { item -> ConversationItemView(item) }
    }
}

private fun ConversationItem.activityStatus(): ActivityStatus = when (this) {
    is ConversationItem.CommandExecution -> status
    is ConversationItem.FileChange -> status
    is ConversationItem.Activity -> status
    else -> ActivityStatus.COMPLETED
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
        summary = item.summary ?: item.details.lineSequence().firstOrNull()?.take(120),
        status = item.status,
        hasDetails = item.details.isNotBlank() || !item.technicalDetails.isNullOrBlank(),
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
        item.technicalDetails?.takeIf(String::isNotBlank)?.let { ExpandableTechnicalDetails(it) }
    }
}

@Composable
private fun ActivitySurface(
    icon: @Composable () -> Unit,
    title: String,
    status: ActivityStatus,
    summary: String? = null,
    hasDetails: Boolean = true,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit,
) {
    var expanded by rememberSaveable(initiallyExpanded) { mutableStateOf(initiallyExpanded) }
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
    val isCritical = request is PendingServerRequest.Permissions
    val icon = when (request) {
        is PendingServerRequest.Approval -> if (request.type == ApprovalType.COMMAND_EXECUTION) {
            Icons.Rounded.Code
        } else {
            Icons.Rounded.Description
        }
        is PendingServerRequest.Questions -> Icons.AutoMirrored.Rounded.HelpOutline
        is PendingServerRequest.McpElicitation -> Icons.Rounded.Extension
        is PendingServerRequest.Permissions -> Icons.Rounded.Lock
    }
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
                icon,
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
            onPermissions(
                request.requestKey,
                request.requestedPermissions.responseJson,
                grant,
                session,
                strictAutoReview,
            )
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
            if (approval.commandActions.isNotEmpty()) {
                Text("这条命令将执行", style = MaterialTheme.typography.labelLarge)
                approval.commandActions.forEach { CommandActionView(it) }
            }
            approval.proposedExecPolicy?.let { amendment ->
                PolicyProposal(
                    title = "以后允许执行同类命令",
                    description = amendment.command.joinToString(" "),
                    technicalDetails = amendment.responseJson,
                )
                OutlinedButton(
                    onClick = { onExecPolicy(amendment.responseJson) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("保存命令规则并允许本次操作")
                }
            }
            approval.proposedNetworkPolicies.forEach { amendment ->
                PolicyProposal(
                    title = if (amendment.action == NetworkPolicyRuleAction.ALLOW) {
                        "以后允许访问此主机"
                    } else {
                        "以后阻止访问此主机"
                    },
                    description = amendment.host,
                    technicalDetails = amendment.responseJson,
                )
                OutlinedButton(
                    onClick = { onNetworkPolicy(amendment.responseJson) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("保存网络规则并允许本次操作") }
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
    var otherQuestions by remember(request.requestKey) {
        mutableStateOf(request.questions.filter { it.options.isEmpty() }.map { it.id }.toSet())
    }
    var remainingMillis by remember(request.requestKey) {
        mutableStateOf(
            request.autoResolutionMillis?.let {
                max(0L, it - (System.currentTimeMillis() - request.receivedAtMillis))
            },
        )
    }
    LaunchedEffect(request.requestKey, request.autoResolutionMillis) {
        if (request.autoResolutionMillis != null) {
            while ((remainingMillis ?: 0L) > 0L) {
                delay(1_000)
                remainingMillis = max(
                    0L,
                    request.autoResolutionMillis - (System.currentTimeMillis() - request.receivedAtMillis),
                )
            }
        }
    }
    val canSubmit = allQuestionsAnswered(request.questions, answers) && remainingMillis != 0L
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Codex 需要你的回答", style = MaterialTheme.typography.titleMedium)
            request.questions.forEach { question ->
                Text(question.header, style = MaterialTheme.typography.labelLarge)
                Text(question.question)
                if (question.options.isNotEmpty()) {
                    question.options.forEach { option ->
                        val selected = question.id !in otherQuestions &&
                            answers[question.id].orEmpty().singleOrNull() == option.label
                        Surface(
                            onClick = {
                                otherQuestions = otherQuestions - question.id
                                answers = answers + (question.id to listOf(option.label))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = if (selected) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer
                            },
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = selected, onClick = null)
                                Column(Modifier.weight(1f)) {
                                    Text(option.label, style = MaterialTheme.typography.labelLarge)
                                    if (option.description.isNotBlank()) {
                                        Text(
                                            option.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (question.options.isEmpty() || question.allowsOther) {
                    val selected = question.options.isEmpty() || question.id in otherQuestions
                    if (question.options.isNotEmpty()) {
                        Surface(
                            onClick = {
                                otherQuestions = otherQuestions + question.id
                                answers = answers + (question.id to emptyList())
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = if (selected) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer
                            },
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = selected, onClick = null)
                                Text("其他回答", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                    if (selected) {
                        OutlinedTextField(
                            value = answers[question.id].orEmpty().singleOrNull().orEmpty(),
                            onValueChange = { value -> answers = answers + (question.id to listOf(value)) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(if (question.isSecret) "保密回答" else "回答") },
                            visualTransformation = if (question.isSecret) {
                                PasswordVisualTransformation()
                            } else {
                                androidx.compose.ui.text.input.VisualTransformation.None
                            },
                        )
                    }
                }
            }
            remainingMillis?.let {
                Text(
                    if (it > 0L) "将在 ${(it + 999L) / 1_000L} 秒后自动处理" else "该请求正在自动处理",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = { onSubmit(answers) },
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("提交回答") }
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
                Text("该服务需要结构化响应", style = MaterialTheme.typography.labelLarge)
                ExpandableTechnicalDetails(it)
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("响应内容（JSON 格式）") },
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
            PermissionSummary(request.requestedPermissions)
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
    draft: ChatDraft,
    models: List<ModelOption>,
    resources: List<FeatureEntry>,
    connectionStatus: ConnectionStatus,
    isLoadingResources: Boolean,
    isSending: Boolean,
    onLoadResources: (Boolean) -> Unit,
    onMessageChange: (String) -> Unit,
    onAddAttachment: (UserInputPart, String?) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onOptionsChange: (TurnOptions) -> Unit,
    onInputError: (Throwable) -> Unit,
    onOpenGoal: () -> Unit,
    onOpenReview: () -> Unit,
    onCompact: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    var dialog by remember { mutableStateOf<ComposerDialog?>(null) }
    var isAddingImage by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val composerScope = rememberCoroutineScope()
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            composerScope.launch {
                isAddingImage = true
                runCatching { uri.toImageDataUrl(context) }
                    .onSuccess { onAddAttachment(UserInputPart.Image(it), uri.toString()) }
                    .onFailure(onInputError)
                isAddingImage = false
            }
        }
    }
    val selectedModel = models.firstOrNull { it.model == draft.options.model }
    val effortLabel = draft.options.reasoningEffort?.let { effort ->
        selectedModel?.reasoningEfforts?.firstOrNull { it.value == effort }?.label ?: effort
    }
    val modelLabel = listOfNotNull(
        selectedModel?.displayName ?: draft.options.model ?: "默认模型",
        effortLabel,
    ).joinToString(" · ")
    val reviewerLabel = when (draft.options.approvalsReviewer?.wireValue ?: conversation.approvalsReviewer) {
        ApprovalReviewer.AUTO_REVIEW.wireValue -> "自动审查"
        else -> "由我审批"
    }
    val activeToken = activeComposerToken(draft.message)
    val paletteEntries = remember(activeToken, resources) {
        composerPaletteEntries(activeToken, resources)
    }
    LaunchedEffect(activeToken?.trigger) {
        if (activeToken?.trigger == '$' || activeToken?.trigger == '@') onLoadResources(false)
    }
    val connected = connectionStatus is ConnectionStatus.Connected
    val hasInput = draft.message.isNotBlank() || draft.attachments.isNotEmpty()

    Surface(modifier = Modifier.imePadding(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (activeToken != null && (paletteEntries.isNotEmpty() || isLoadingResources)) {
                ComposerPalette(
                    entries = paletteEntries,
                    isLoading = isLoadingResources,
                    onSelect = { entry ->
                        onMessageChange(removeActiveComposerToken(draft.message, activeToken))
                        when (entry) {
                            is ComposerPaletteEntry.Resource -> onAddAttachment(entry.input, null)
                            is ComposerPaletteEntry.Command -> when (entry.command) {
                                ComposerCommand.GOAL -> onOpenGoal()
                                ComposerCommand.REVIEW -> onOpenReview()
                                ComposerCommand.COMPACT -> onCompact()
                                ComposerCommand.SETTINGS -> {
                                    onLoadResources(false)
                                    dialog = ComposerDialog.Options
                                }
                                ComposerCommand.ATTACH -> dialog = ComposerDialog.Attachments
                            }
                        }
                    },
                )
                Spacer(Modifier.height(6.dp))
            }
            if (draft.attachments.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    draft.attachments.forEach { attachment ->
                        DraftAttachmentChip(
                            attachment = attachment,
                            onRemove = { onRemoveAttachment(attachment.id) },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
                    BasicTextField(
                        value = draft.message,
                        onValueChange = onMessageChange,
                        enabled = !isSending,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 9.dp),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        minLines = 1,
                        maxLines = 5,
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (draft.message.isEmpty()) {
                                    Text(
                                        when {
                                            !connected -> "服务器已断开"
                                            conversation.isRunning -> "输入消息以引导当前回合"
                                            else -> "向 Codex 发送消息，/ 执行操作"
                                        },
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = {
                                onLoadResources(false)
                                dialog = ComposerDialog.Attachments
                            },
                            enabled = !isSending,
                        ) { Icon(Icons.Rounded.Add, contentDescription = "添加图片、Skill 或提及") }
                        TextButton(
                            onClick = {
                                onLoadResources(false)
                                dialog = ComposerDialog.Options
                            },
                            enabled = !conversation.isRunning && !isSending,
                            modifier = Modifier.widthIn(max = 148.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp),
                        ) {
                            Text(modelLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        TextButton(
                            onClick = {
                                onLoadResources(false)
                                dialog = ComposerDialog.Options
                            },
                            enabled = !conversation.isRunning && !isSending,
                            modifier = Modifier.widthIn(max = 92.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp),
                        ) {
                            Text(reviewerLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.weight(1f))
                        if (isAddingImage) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        if (conversation.isRunning) {
                            IconButton(onClick = onStop, enabled = !isSending) {
                                Icon(Icons.Rounded.Stop, contentDescription = "停止")
                            }
                            Button(
                                onClick = onSend,
                                enabled = hasInput && connected && !isSending,
                                contentPadding = PaddingValues(horizontal = 12.dp),
                            ) {
                                if (isSending) {
                                    CircularProgressIndicator(
                                        Modifier.size(18.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.Send,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    Text("引导")
                                }
                            }
                        } else {
                            FilledIconButton(
                                onClick = onSend,
                                enabled = hasInput && connected && !isSending,
                            ) {
                                if (isSending) {
                                    CircularProgressIndicator(
                                        Modifier.size(18.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "发送")
                                }
                            }
                        }
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
                onAddAttachment(it, null)
                dialog = null
            },
            onPickImage = {
                imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
        )

        ComposerDialog.Options -> TurnOptionsSheet(
            value = draft.options,
            models = models,
            onDismiss = { dialog = null },
            onSave = {
                onOptionsChange(it)
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
    onPickImage: () -> Unit,
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
                TextButton(onClick = onPickImage) {
                    Icon(Icons.Rounded.Image, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("设备图片")
                }
                TextButton(onClick = { showImageForm = !showImageForm }) {
                    Text(if (showImageForm) "收起地址输入" else "图片地址")
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
    var showAdvanced by rememberSaveable(value) { mutableStateOf(false) }
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
            Text("下一轮设置", style = MaterialTheme.typography.titleLarge)
            Text(
                "设置仅用于下一次发送，服务端确认接收后恢复会话默认值",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            model?.reasoningEfforts?.takeIf(List<*>::isNotEmpty)?.let { efforts ->
                Text("推理强度", style = MaterialTheme.typography.labelLarge)
                OptionRow(
                    values = listOf<String?>(null) + efforts.map { it.value },
                    selected = options.reasoningEffort,
                    label = { effort -> efforts.firstOrNull { it.value == effort }?.label ?: "默认" },
                    onSelect = { options = options.copy(reasoningEffort = it) },
                )
            }
            Text("审批者", style = MaterialTheme.typography.labelLarge)
            OptionRow(
                values = listOf<ApprovalReviewer?>(null) + ApprovalReviewer.entries,
                selected = options.approvalsReviewer,
                label = { it?.let { value -> stringResource(value.displayLabelRes) }
                    ?: stringResource(R.string.common_default) },
                onSelect = { options = options.copy(approvalsReviewer = it) },
            )
            options.approvalsReviewer?.let { SettingDescription(stringResource(it.displayDescriptionRes)) }
            Text("审批策略", style = MaterialTheme.typography.labelLarge)
            OptionRow(
                values = listOf<ApprovalPolicy?>(null) + ApprovalPolicy.entries,
                selected = options.approvalPolicy,
                label = { it?.let { value -> stringResource(value.displayLabelRes) }
                    ?: stringResource(R.string.common_default) },
                onSelect = { options = options.copy(approvalPolicy = it) },
            )
            options.approvalPolicy?.let { SettingDescription(stringResource(it.displayDescriptionRes)) }
            Text("沙箱", style = MaterialTheme.typography.labelLarge)
            OptionRow(
                values = listOf<SandboxMode?>(null) + SandboxMode.entries,
                selected = options.sandboxMode,
                label = { it?.let { value -> stringResource(value.displayLabelRes) }
                    ?: stringResource(R.string.common_default) },
                onSelect = { options = options.copy(sandboxMode = it) },
            )
            options.sandboxMode?.let {
                SettingDescription(
                    stringResource(it.displayDescriptionRes),
                    critical = it == SandboxMode.DANGER_FULL_ACCESS,
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 2.dp))
            TextButton(onClick = { showAdvanced = !showAdvanced }, modifier = Modifier.fillMaxWidth()) {
                Text(if (showAdvanced) "收起高级设置" else "展开高级设置")
            }
            if (showAdvanced) {
                OutlinedTextField(
                    value = options.cwd.orEmpty(),
                    onValueChange = { options = options.copy(cwd = it.ifBlank { null }) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("工作目录覆盖，可留空") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = options.model.orEmpty(),
                    onValueChange = { options = options.copy(model = it.ifBlank { null }) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("模型 ID，可留空") },
                    singleLine = true,
                )
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
                    label = { it?.let { value -> stringResource(value.displayLabelRes) }
                        ?: stringResource(R.string.common_default) },
                    onSelect = { options = options.copy(reasoningSummary = it) },
                )
                if (model == null || model.supportsPersonality) {
                    Text("人格", style = MaterialTheme.typography.labelLarge)
                    OptionRow(
                        values = listOf<Personality?>(null) + Personality.entries,
                        selected = options.personality,
                        label = { it?.let { value -> stringResource(value.displayLabelRes) }
                            ?: stringResource(R.string.common_default) },
                        onSelect = { options = options.copy(personality = it) },
                    )
                }
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
                OutlinedTextField(
                    value = options.outputSchema.orEmpty(),
                    onValueChange = { options = options.copy(outputSchema = it.ifBlank { null }) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("结构化输出 JSON Schema，可留空") },
                    minLines = 3,
                )
            }
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
private fun SettingDescription(text: String, critical: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (critical) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
                    label = { it.label },
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
    var status by rememberSaveable(goal?.status) { mutableStateOf(goal?.status ?: ThreadGoalStatus.ACTIVE) }
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
                    label = { stringResource(it.displayLabelRes) },
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
    data object Changes : ChatDialog
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

private val FeatureEntry.resourceOrigin: String
    get() = when (kind) {
        FeatureKind.SKILL -> metadata["scope"]?.ifBlank { null }?.let(::featureScopeLabel) ?: "Skill"
        FeatureKind.APP -> "App"
        FeatureKind.PLUGIN -> parentId ?: "Plugin"
        else -> "其他资源"
    }
