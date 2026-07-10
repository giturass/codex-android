@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hebo.codex.ui.screens.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hebo.codex.domain.ActivityStatus
import com.hebo.codex.domain.ApprovalDecision
import com.hebo.codex.domain.ApprovalType
import com.hebo.codex.domain.ChangedFile
import com.hebo.codex.domain.ConversationItem
import com.hebo.codex.domain.FileChangeKind
import com.hebo.codex.domain.PendingApproval
import com.hebo.codex.ui.theme.statusColors
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.highlightedCodeBlock
import com.mikepenz.markdown.compose.elements.highlightedCodeFence
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.model.rememberMarkdownState

private val highlightedMarkdownComponents = markdownComponents(
    codeBlock = highlightedCodeBlock,
    codeFence = highlightedCodeFence,
)

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val conversation = state.conversation
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    LaunchedEffect(state.screenError, conversation?.errorMessage) {
        val error = state.screenError ?: conversation?.errorMessage
        if (error != null) {
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }
    LaunchedEffect(
        conversation?.items?.size,
        conversation?.items?.lastOrNull(),
        conversation?.approvals?.size,
    ) {
        if (!conversation?.items.isNullOrEmpty()) {
            listState.animateScrollToItem(conversation!!.items.lastIndex + conversation.approvals.size)
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
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (conversation != null) {
                ChatComposer(
                    isRunning = conversation.isRunning,
                    onSend = viewModel::send,
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
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(conversation.items, key = ConversationItem::id) { item ->
                    ConversationItemView(item)
                }
                items(conversation.approvals, key = PendingApproval::requestKey) { approval ->
                    ApprovalCard(
                        approval = approval,
                        onDecision = { decision ->
                            viewModel.respondToApproval(approval.requestKey, decision)
                        },
                    )
                }
                if (conversation.isRunning && conversation.items.isEmpty()) {
                    item { RunningIndicator() }
                }
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
private fun ApprovalCard(
    approval: PendingApproval,
    onDecision: (ApprovalDecision) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                if (approval.type == ApprovalType.COMMAND_EXECUTION) "命令等待审批" else "文件修改等待审批",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            approval.reason?.takeIf(String::isNotBlank)?.let { Text(it) }
            approval.command?.takeIf(String::isNotBlank)?.let { ShellCommandBlock(it) }
            approval.cwd?.takeIf(String::isNotBlank)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = { onDecision(ApprovalDecision.ACCEPT) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("允许一次") }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
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
    isRunning: Boolean,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
) {
    var message by rememberSaveable { mutableStateOf("") }
    Surface(
        modifier = Modifier.imePadding(),
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("输入消息") },
                minLines = 1,
                maxLines = 5,
                enabled = !isRunning,
                shape = RoundedCornerShape(22.dp),
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = {
                    if (isRunning) {
                        onStop()
                    } else if (message.isNotBlank()) {
                        onSend(message)
                        message = ""
                    }
                },
                enabled = isRunning || message.isNotBlank(),
            ) {
                Icon(
                    if (isRunning) Icons.Rounded.Stop else Icons.Rounded.Send,
                    contentDescription = if (isRunning) "停止" else "发送",
                )
            }
        }
    }
}
