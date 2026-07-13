@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hebo.codex.ui.screens.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FactCheck
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Webhook
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hebo.codex.domain.ActivityKind
import com.hebo.codex.domain.ActivityStatus
import com.hebo.codex.domain.ChangedFile
import com.hebo.codex.domain.ConversationItem
import com.hebo.codex.domain.FileChangeKind
import kotlinx.coroutines.delay

@Composable
internal fun ActivityGroupCard(
    items: List<ConversationItem>,
    isSealed: Boolean,
    onOpenRemotePath: (String) -> Unit,
) {
    if (items.isEmpty()) return
    var showDetails by rememberSaveable(items.first().turnId) { mutableStateOf(false) }
    val status = activityGroupStatus(items, isSealed)
    val contentColor = when (status) {
        ActivityStatus.FAILED, ActivityStatus.DECLINED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button) { showDetails = true }
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            if (isSealed) {
                Icon(
                    Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = contentColor,
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = contentColor,
                    strokeWidth = 2.dp,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = activityGroupLabel(items, isSealed),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = "查看处理过程详情",
            modifier = Modifier.size(22.dp),
            tint = contentColor,
        )
    }

    if (showDetails) {
        ActivityDetailsSheet(
            items = items,
            onOpenRemotePath = { path ->
                showDetails = false
                onOpenRemotePath(path)
            },
            onDismiss = { showDetails = false },
        )
    }
}

internal fun activityGroupLabel(items: List<ConversationItem>, isSealed: Boolean): String {
    if (!isSealed) return activeActivityLabel(items)

    val failedCount = items.count { it.activityStatus() == ActivityStatus.FAILED }
    val declinedCount = items.count { it.activityStatus() == ActivityStatus.DECLINED }
    val commandCount = items.count { it is ConversationItem.CommandExecution }
    val changedFileCount = items
        .filterIsInstance<ConversationItem.FileChange>()
        .flatMap(ConversationItem.FileChange::changes)
        .distinctBy(ChangedFile::path)
        .size
    val activityCount = items.count { it is ConversationItem.Activity }
    return buildList {
        if (failedCount > 0) add("$failedCount 项失败")
        if (declinedCount > 0) add("$declinedCount 项已取消")
        if (items.any { it is ConversationItem.Reasoning }) add("思考")
        if (commandCount > 0) add("执行 $commandCount 条命令")
        if (changedFileCount > 0) add("改动 $changedFileCount 个文件")
        if (activityCount > 0) add("执行 $activityCount 项操作")
    }.joinToString(" · ")
}

private fun activeActivityLabel(items: List<ConversationItem>): String {
    val activeItem = items.lastOrNull { it.activityStatus() == ActivityStatus.IN_PROGRESS }
        ?: items.last()
    return when (activeItem) {
        is ConversationItem.Reasoning -> activeItem.summary
            .lineSequence()
            .lastOrNull(String::isNotBlank)
            ?.trim()
            ?: "思考中"

        is ConversationItem.Plan -> activeItem.text
            .lineSequence()
            .lastOrNull(String::isNotBlank)
            ?.trim()
            ?: "制定计划中"

        is ConversationItem.CommandExecution -> activeItem.command.commandPreview()
        is ConversationItem.FileChange -> activeItem.changes
            .map(::changedFileLabel)
            .map(ChangedFileLabel::path)
            .distinct()
            .joinToString("、")
            .ifBlank { "修改文件" }

        is ConversationItem.Activity -> activeItem.title
        is ConversationItem.UserMessage, is ConversationItem.AgentMessage -> error("活动组包含非活动条目")
    }
}

private fun activityGroupStatus(items: List<ConversationItem>, isSealed: Boolean): ActivityStatus {
    if (!isSealed) return ActivityStatus.IN_PROGRESS
    val statuses = items.map(ConversationItem::activityStatus)
    return when {
        ActivityStatus.FAILED in statuses -> ActivityStatus.FAILED
        ActivityStatus.DECLINED in statuses -> ActivityStatus.DECLINED
        else -> ActivityStatus.COMPLETED
    }
}

internal fun ConversationItem.activityStatus(): ActivityStatus = when (this) {
    is ConversationItem.Reasoning -> if (isStreaming) ActivityStatus.IN_PROGRESS else ActivityStatus.COMPLETED
    is ConversationItem.Plan -> if (isStreaming) ActivityStatus.IN_PROGRESS else ActivityStatus.COMPLETED
    is ConversationItem.CommandExecution -> status
    is ConversationItem.FileChange -> status
    is ConversationItem.Activity -> status
    is ConversationItem.AgentMessage -> if (isStreaming) ActivityStatus.IN_PROGRESS else ActivityStatus.COMPLETED
    is ConversationItem.UserMessage -> ActivityStatus.COMPLETED
}

@Composable
private fun ActivityDetailsSheet(
    items: List<ConversationItem>,
    onOpenRemotePath: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.PartiallyExpanded, SheetValue.Expanded),
    )
    var expandedItemKeys by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    val maxSheetHeight = LocalConfiguration.current.screenHeightDp.dp * 0.88f
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .navigationBarsPadding(),
        ) {
            Text(
                text = "处理过程",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp,
                    top = 22.dp,
                    end = 20.dp,
                    bottom = 32.dp,
                ),
            ) {
                items.forEachIndexed { index, item ->
                    val itemKey = "${item.turnId}:${item.id}"
                    val expanded = itemKey in expandedItemKeys
                    item(key = itemKey) {
                        ActivityTimelineHeader(
                            item = item,
                            isLast = index == items.lastIndex,
                            expanded = expanded,
                            onToggle = {
                                expandedItemKeys = ArrayList(expandedItemKeys).apply {
                                    if (!remove(itemKey)) add(itemKey)
                                }
                            },
                        )
                        ActivityTimelineBody(
                            item = item,
                            isLast = index == items.lastIndex,
                            expanded = expanded,
                            onOpenRemotePath = onOpenRemotePath,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityTimelineHeader(
    item: ConversationItem,
    isLast: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val hasDetails = activityItemHasDetails(item)
    val arrowRotation by animateFloatAsState(
        targetValue = if (hasDetails && expanded) 180f else 0f,
        animationSpec = tween(EXPANDABLE_CONTENT_FADE_DURATION_MILLIS),
        label = "处理过程详情展开箭头",
    )
    val statusDescription = buildString {
        append(activityStatusDescription(item.activityStatus()))
        if (hasDetails) append(if (expanded) "，已展开" else "，已收起")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .height(IntrinsicSize.Min)
            .then(
                if (hasDetails) {
                    Modifier
                        .clickable(role = Role.Button, onClick = onToggle)
                } else {
                    Modifier
                },
            )
            .semantics(mergeDescendants = true) {
                stateDescription = statusDescription
            },
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .width(30.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (!isLast) {
                ActivityTimelineConnector(Modifier.padding(top = 30.dp))
            }
            ActivityTimelineIcon(item)
        }
        Spacer(Modifier.width(12.dp))
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = activityItemTitle(item),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                activityItemSubtitle(item)?.let { subtitle ->
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (hasDetails) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp).rotate(arrowRotation),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ActivityTimelineBody(
    item: ConversationItem,
    isLast: Boolean,
    expanded: Boolean,
    onOpenRemotePath: (String) -> Unit,
) {
    val hasDetails = activityItemHasDetails(item)
    val expansionTarget = hasDetails && expanded
    var expansionPhase by remember(item.turnId, item.id) {
        mutableStateOf(ContentExpansionPhase.COLLAPSED)
    }
    val bodyAlpha by animateFloatAsState(
        targetValue = if (expansionPhase.bodyVisible) 1f else 0f,
        animationSpec = tween(EXPANDABLE_CONTENT_FADE_DURATION_MILLIS),
        label = "处理过程详情透明度",
    )

    LaunchedEffect(expansionTarget) {
        expansionPhase = expansionPhase.startTransition(expansionTarget)
        when (expansionPhase) {
            ContentExpansionPhase.EXPANDING -> {
                delay(EXPANDABLE_CONTENT_SIZE_ANIMATION_DURATION_MILLIS.toLong())
                expansionPhase = expansionPhase.settle()
            }

            ContentExpansionPhase.COLLAPSING -> {
                delay(EXPANDABLE_CONTENT_FADE_DURATION_MILLIS.toLong())
                expansionPhase = expansionPhase.settle()
            }

            ContentExpansionPhase.COLLAPSED,
            ContentExpansionPhase.EXPANDED,
            -> Unit
        }
    }

    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(
            modifier = Modifier
                .width(30.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (!isLast) ActivityTimelineConnector()
        }
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .animateContentSize(
                    animationSpec = tween(EXPANDABLE_CONTENT_SIZE_ANIMATION_DURATION_MILLIS),
                ),
        ) {
            if (expansionPhase.bodyIncluded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .graphicsLayer { alpha = bodyAlpha },
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ActivityItemDetails(item, onOpenRemotePath)
                }
            }
            if (!isLast && expansionPhase.bodyIncluded) Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ActivityTimelineConnector(modifier: Modifier = Modifier) {
    Box(
        modifier
            .width(1.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

@Composable
private fun ActivityTimelineIcon(item: ConversationItem) {
    val status = item.activityStatus()
    val icon = activityTypeIcon(item)
    val tint = when (status) {
        ActivityStatus.IN_PROGRESS -> MaterialTheme.colorScheme.onSurfaceVariant
        ActivityStatus.COMPLETED -> MaterialTheme.colorScheme.onSurfaceVariant
        ActivityStatus.FAILED, ActivityStatus.DECLINED -> MaterialTheme.colorScheme.error
    }
    Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.size(26.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = tint,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = activityTypeDescription(item),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        if (status == ActivityStatus.IN_PROGRESS) {
            CircularProgressIndicator(
                modifier = Modifier.size(30.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp,
            )
        }
        if (status == ActivityStatus.FAILED || status == ActivityStatus.DECLINED) {
            Surface(
                modifier = Modifier.align(Alignment.BottomEnd).size(12.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (status == ActivityStatus.FAILED) Icons.Rounded.ErrorOutline else Icons.Rounded.Block,
                        contentDescription = null,
                        modifier = Modifier.size(9.dp),
                    )
                }
            }
        }
    }
}

private fun activityTypeIcon(item: ConversationItem): ImageVector = when (item) {
    is ConversationItem.Reasoning -> Icons.Rounded.Psychology
    is ConversationItem.Plan -> Icons.Rounded.Checklist
    is ConversationItem.CommandExecution -> Icons.Rounded.Terminal
    is ConversationItem.FileChange -> Icons.Rounded.EditNote
    is ConversationItem.Activity -> when (item.kind) {
        ActivityKind.MCP_TOOL, ActivityKind.DYNAMIC_TOOL -> Icons.Rounded.Extension
        ActivityKind.COLLAB_AGENT -> Icons.Rounded.AccountTree
        ActivityKind.WEB_SEARCH -> Icons.Rounded.TravelExplore
        ActivityKind.CONTEXT_COMPACTION -> Icons.Rounded.Compress
        ActivityKind.REVIEW -> Icons.AutoMirrored.Rounded.FactCheck
        ActivityKind.HOOK -> Icons.Rounded.Webhook
        ActivityKind.SUB_AGENT -> Icons.Rounded.SmartToy
        ActivityKind.IMAGE_VIEW -> Icons.Rounded.Image
        ActivityKind.IMAGE_GENERATION -> Icons.Rounded.AutoAwesome
        ActivityKind.WAIT -> Icons.Rounded.HourglassTop
        ActivityKind.UNKNOWN -> Icons.AutoMirrored.Rounded.HelpOutline
    }

    is ConversationItem.UserMessage, is ConversationItem.AgentMessage -> Icons.AutoMirrored.Rounded.HelpOutline
}

private fun activityTypeDescription(item: ConversationItem): String = when (item) {
    is ConversationItem.Reasoning -> "分析推理"
    is ConversationItem.Plan -> "计划"
    is ConversationItem.CommandExecution -> "命令"
    is ConversationItem.FileChange -> "文件修改"
    is ConversationItem.Activity -> when (item.kind) {
        ActivityKind.MCP_TOOL, ActivityKind.DYNAMIC_TOOL -> "工具调用"
        ActivityKind.COLLAB_AGENT -> "协作代理"
        ActivityKind.WEB_SEARCH -> "网页搜索"
        ActivityKind.CONTEXT_COMPACTION -> "上下文压缩"
        ActivityKind.REVIEW -> "审查"
        ActivityKind.HOOK -> "Hook"
        ActivityKind.SUB_AGENT -> "子代理"
        ActivityKind.IMAGE_VIEW -> "图片"
        ActivityKind.IMAGE_GENERATION -> "图片生成"
        ActivityKind.WAIT -> "等待"
        ActivityKind.UNKNOWN -> "未知活动"
    }

    is ConversationItem.UserMessage -> "用户消息"
    is ConversationItem.AgentMessage -> "Codex 回复"
}

private fun activityStatusDescription(status: ActivityStatus): String = when (status) {
    ActivityStatus.IN_PROGRESS -> "进行中"
    ActivityStatus.COMPLETED -> "已完成"
    ActivityStatus.FAILED -> "失败"
    ActivityStatus.DECLINED -> "已取消"
}

internal fun activityItemTitle(item: ConversationItem): String {
    val title = when (item) {
        is ConversationItem.Reasoning -> item.summary
            .lineSequence()
            .lastOrNull(String::isNotBlank)
            ?.trim()
            ?: "分析与推理"

        is ConversationItem.Plan -> "计划"

        is ConversationItem.CommandExecution -> item.command.commandPreview()

        is ConversationItem.FileChange -> item.changes
            .map(::changedFileLabel)
            .map(ChangedFileLabel::path)
            .distinct()
            .let { paths -> paths.singleOrNull() ?: "${paths.size} 个文件" }

        is ConversationItem.Activity -> item.title
        is ConversationItem.UserMessage -> "用户消息"
        is ConversationItem.AgentMessage -> "Codex 回复"
    }
    return when (item.activityStatus()) {
        ActivityStatus.IN_PROGRESS, ActivityStatus.COMPLETED -> title
        ActivityStatus.FAILED -> "$title · 失败"
        ActivityStatus.DECLINED -> "$title · 已取消"
    }
}

internal fun activityItemSubtitle(item: ConversationItem): String? = when (item) {
    is ConversationItem.Activity -> item.summary?.takeIf(String::isNotBlank)
    else -> null
}

internal fun activityItemHasDetails(item: ConversationItem): Boolean = when (item) {
    is ConversationItem.Reasoning -> item.details.isNotBlank()
    is ConversationItem.Plan -> item.text.isNotBlank()
    is ConversationItem.CommandExecution -> item.command.isNotBlank() ||
        item.cwd.isNotBlank() ||
        item.output.isNotBlank() ||
        item.exitCode != null

    is ConversationItem.FileChange -> item.changes.isNotEmpty()
    is ConversationItem.Activity -> !item.summary.isNullOrBlank() ||
        item.details.isNotBlank() ||
        !item.technicalDetails.isNullOrBlank() ||
        item.remotePath != null

    is ConversationItem.UserMessage, is ConversationItem.AgentMessage -> false
}

@Composable
private fun ActivityItemDetails(item: ConversationItem, onOpenRemotePath: (String) -> Unit) {
    when (item) {
        is ConversationItem.Reasoning -> {
            if (item.details.isNotBlank()) {
                SelectionContainer {
                    Text(
                        item.details,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        is ConversationItem.Plan -> {
            if (item.text.isNotBlank()) {
                SelectionContainer {
                    Text(
                        item.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        is ConversationItem.CommandExecution -> {
            ShellCommandBlock(item.command)
            Text(
                item.cwd,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.output.isNotBlank()) TerminalOutputBlock(item.output)
            item.exitCode?.let { exitCode ->
                Text(
                    "退出码 $exitCode",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        is ConversationItem.FileChange -> {
            item.changes.forEach { change ->
                ChangedFileView(change)
                Spacer(Modifier.height(4.dp))
            }
        }

        is ConversationItem.Activity -> {
            val description = listOfNotNull(item.summary, item.details.takeIf(String::isNotBlank))
                .distinct()
                .joinToString("\n\n")
            if (description.isNotBlank()) {
                SelectionContainer {
                    Text(
                        description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item.technicalDetails?.takeIf(String::isNotBlank)?.let { ExpandableTechnicalDetails(it) }
            item.remotePath?.let { path ->
                OutlinedButton(onClick = { onOpenRemotePath(path) }) {
                    Icon(Icons.Rounded.Visibility, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("查看文件")
                }
            }
        }

        is ConversationItem.UserMessage, is ConversationItem.AgentMessage -> Unit
    }
}

@Composable
private fun ChangedFileView(change: ChangedFile) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val label = changedFileLabel(change)
        Row(Modifier.fillMaxWidth()) {
            Text(
                "${label.action} · ",
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                label.path,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.StartEllipsis,
            )
        }
        if (change.diff.isNotBlank()) FileDiffBlock(change)
    }
}

private data class ChangedFileLabel(val action: String, val path: String)

private fun changedFileLabel(change: ChangedFile): ChangedFileLabel = when (val kind = change.kind) {
    FileChangeKind.Add -> ChangedFileLabel(action = "新增", path = change.path)
    FileChangeKind.Delete -> ChangedFileLabel(action = "删除", path = change.path)
    is FileChangeKind.Update -> kind.movePath
        ?.let { destination -> ChangedFileLabel(action = "移动", path = "${change.path} → $destination") }
        ?: ChangedFileLabel(action = "修改", path = change.path)
}

private fun String.commandPreview(): String =
    shellCommandForDisplay(this)
        .lineSequence()
        .firstOrNull()
        ?.trim()
        ?.take(56)
        .orEmpty()
        .ifBlank { "命令" }
