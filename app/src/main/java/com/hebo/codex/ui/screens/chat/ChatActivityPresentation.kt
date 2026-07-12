@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hebo.codex.ui.screens.chat

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hebo.codex.domain.ActivityStatus
import com.hebo.codex.domain.ChangedFile
import com.hebo.codex.domain.ConversationItem
import com.hebo.codex.domain.FileChangeKind

@Composable
internal fun ActivityGroupCard(items: List<ConversationItem>) {
    if (items.isEmpty()) return
    var showDetails by rememberSaveable(items.first().turnId) { mutableStateOf(false) }
    val status = activityGroupStatus(items)
    val icon = if (items.any { it is ConversationItem.Reasoning }) {
        Icons.Rounded.AutoAwesome
    } else {
        Icons.Rounded.Code
    }
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
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = contentColor)
        Spacer(Modifier.width(10.dp))
        Text(
            text = activityGroupLabel(items),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = "查看思考与操作详情",
            modifier = Modifier.size(22.dp),
            tint = contentColor,
        )
    }

    if (showDetails) {
        ActivityDetailsSheet(items = items, onDismiss = { showDetails = false })
    }
}

internal fun activityGroupLabel(items: List<ConversationItem>): String {
    val status = activityGroupStatus(items)
    val activeItem = items.lastOrNull { it.activityStatus() == ActivityStatus.IN_PROGRESS }
    if (activeItem != null) {
        return when (activeItem) {
            is ConversationItem.Reasoning -> "正在思考"
            is ConversationItem.Plan -> "正在制定计划"
            is ConversationItem.CommandExecution -> "正在执行 ${activeItem.command.commandPreview()}"
            is ConversationItem.FileChange -> "正在修改 ${activeItem.changes.size} 个文件"
            is ConversationItem.Activity -> activeItem.title
            is ConversationItem.UserMessage, is ConversationItem.AgentMessage -> "正在处理"
        }
    }
    if (status == ActivityStatus.FAILED) return "操作失败，查看详情"
    if (status == ActivityStatus.DECLINED) return "操作已取消，查看详情"
    if (items.any { it is ConversationItem.Reasoning }) return "思考过程"

    val commandCount = items.count { it is ConversationItem.CommandExecution }
    val changedFileCount = items.filterIsInstance<ConversationItem.FileChange>().sumOf { it.changes.size }
    val activityCount = items.size - commandCount - items.count { it is ConversationItem.FileChange }
    return buildList {
        if (commandCount > 0) add("$commandCount 条命令")
        if (changedFileCount > 0) add("$changedFileCount 个文件")
        if (activityCount > 0) add("$activityCount 项操作")
    }.joinToString(" · ").ifBlank { "活动详情" }
}

private fun activityGroupStatus(items: List<ConversationItem>): ActivityStatus {
    val statuses = items.map(ConversationItem::activityStatus)
    return when {
        ActivityStatus.IN_PROGRESS in statuses -> ActivityStatus.IN_PROGRESS
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
private fun ActivityDetailsSheet(items: List<ConversationItem>, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val hasReasoning = items.any { it is ConversationItem.Reasoning }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .navigationBarsPadding(),
        ) {
            Text(
                text = if (hasReasoning) "思考过程" else "活动详情",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp,
                    top = 22.dp,
                    end = 20.dp,
                    bottom = 32.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                itemsIndexed(items, key = { _, item -> "${item.turnId}:${item.id}" }) { index, item ->
                    ActivityTimelineItem(item = item, isLast = index == items.lastIndex)
                }
            }
        }
    }
}

@Composable
private fun ActivityTimelineItem(item: ConversationItem, isLast: Boolean) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(
            modifier = Modifier
                .width(28.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (!isLast) {
                Box(
                    Modifier
                        .padding(top = 26.dp)
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }
            ActivityTimelineIcon(item)
        }
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(activityItemTitle(item), style = MaterialTheme.typography.bodyLarge)
            ActivityItemDetails(item)
        }
    }
}

@Composable
private fun ActivityTimelineIcon(item: ConversationItem) {
    val status = item.activityStatus()
    val icon: ImageVector = when (status) {
        ActivityStatus.IN_PROGRESS -> Icons.Rounded.HourglassTop
        ActivityStatus.COMPLETED -> Icons.Rounded.Check
        ActivityStatus.FAILED -> Icons.Rounded.ErrorOutline
        ActivityStatus.DECLINED -> Icons.Rounded.Block
    }
    val tint = when (status) {
        ActivityStatus.IN_PROGRESS -> MaterialTheme.colorScheme.onSurfaceVariant
        ActivityStatus.COMPLETED -> MaterialTheme.colorScheme.onSurfaceVariant
        ActivityStatus.FAILED, ActivityStatus.DECLINED -> MaterialTheme.colorScheme.error
    }
    Surface(
        modifier = Modifier.size(26.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = tint,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        }
    }
}

private fun activityItemTitle(item: ConversationItem): String = when (item) {
    is ConversationItem.Reasoning -> when {
        item.isStreaming -> "正在分析"
        item.summary.isNotBlank() -> item.summary
        else -> "分析与推理"
    }

    is ConversationItem.Plan -> if (item.isStreaming) "正在制定计划" else "执行计划"

    is ConversationItem.CommandExecution -> when (item.status) {
        ActivityStatus.IN_PROGRESS -> "正在执行 ${item.command.commandPreview()}"
        ActivityStatus.COMPLETED -> "执行 ${item.command.commandPreview()}"
        ActivityStatus.FAILED -> "命令执行失败"
        ActivityStatus.DECLINED -> "命令未执行"
    }

    is ConversationItem.FileChange -> when (item.status) {
        ActivityStatus.IN_PROGRESS -> "正在修改 ${item.changes.size} 个文件"
        ActivityStatus.COMPLETED -> "修改 ${item.changes.size} 个文件"
        ActivityStatus.FAILED -> "文件修改失败"
        ActivityStatus.DECLINED -> "文件修改已取消"
    }

    is ConversationItem.Activity -> item.title
    is ConversationItem.UserMessage -> "用户消息"
    is ConversationItem.AgentMessage -> "Codex 回复"
}

@Composable
private fun ActivityItemDetails(item: ConversationItem) {
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
        }

        is ConversationItem.UserMessage, is ConversationItem.AgentMessage -> Unit
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

private fun String.commandPreview(): String =
    lineSequence().firstOrNull()?.trim()?.take(56).orEmpty().ifBlank { "命令" }
