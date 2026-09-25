package com.hebo.codex.ui.screens.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hebo.codex.domain.AgentState
import com.hebo.codex.domain.AgentStatus
import com.hebo.codex.domain.AgentTool
import com.hebo.codex.domain.ConversationItem
import com.hebo.codex.domain.SubAgentActivityKind
import com.hebo.codex.domain.targetThreadIds

internal fun AgentTool.title(): String = when (this) {
    AgentTool.SPAWN -> "创建子代理"
    AgentTool.SEND_INPUT, AgentTool.SEND_MESSAGE -> "向子代理发送消息"
    AgentTool.RESUME -> "恢复子代理"
    AgentTool.WAIT -> "等待子代理"
    AgentTool.CLOSE -> "关闭子代理"
    AgentTool.FOLLOWUP_TASK -> "分配后续任务"
    AgentTool.INTERRUPT -> "中断子代理"
    AgentTool.LIST -> "查看子代理"
}

internal fun SubAgentActivityKind.title(): String = when (this) {
    SubAgentActivityKind.STARTED -> "子代理已启动"
    SubAgentActivityKind.INTERACTED -> "子代理已交互"
    SubAgentActivityKind.INTERRUPTED -> "子代理已中断"
    SubAgentActivityKind.COMPLETED -> "子代理已完成"
}

internal fun AgentStatus.label(): String = when (this) {
    AgentStatus.PENDING_INIT -> "准备中"
    AgentStatus.RUNNING -> "运行中"
    AgentStatus.INTERRUPTED -> "已中断"
    AgentStatus.COMPLETED -> "已完成"
    AgentStatus.ERRORED -> "出错"
    AgentStatus.SHUTDOWN -> "已关闭"
    AgentStatus.NOT_FOUND -> "未找到"
}

@Composable
internal fun AgentCollaborationDetails(
    item: ConversationItem.AgentCollaboration,
    cwd: String,
    onOpenRemoteFile: (RemoteFileTarget) -> Unit,
    onLinkError: (String) -> Unit,
    onOpenConversation: (String) -> Unit,
) {
    item.prompt?.takeIf(String::isNotBlank)?.let { prompt ->
        Text(
            if (item.tool == AgentTool.SPAWN || item.tool == AgentTool.FOLLOWUP_TASK) "任务" else "消息",
            style = MaterialTheme.typography.labelLarge,
        )
        SelectionContainer {
            ChatMarkdown(prompt, cwd, onOpenRemoteFile, onLinkError)
        }
    }
    val modelDescription = listOfNotNull(item.model, item.reasoningEffort).joinToString(" · ")
    if (modelDescription.isNotBlank()) {
        Text(
            modelDescription,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    item.targetThreadIds.forEach { threadId ->
        AgentTargetCard(
            threadId = threadId,
            label = "子代理 · ${threadId.takeLast(8)}",
            agentState = item.agentsStates[threadId],
            cwd = cwd,
            onOpenRemoteFile = onOpenRemoteFile,
            onLinkError = onLinkError,
            onOpenConversation = onOpenConversation,
        )
    }
}

@Composable
internal fun SubAgentActivityDetails(
    item: ConversationItem.SubAgentActivity,
    onOpenConversation: (String) -> Unit,
) {
    Text(item.agentPath, style = MaterialTheme.typography.bodyMedium)
    TextButton(onClick = { onOpenConversation(item.agentThreadId) }) {
        Text("打开子会话")
    }
}

@Composable
private fun AgentTargetCard(
    threadId: String,
    label: String,
    agentState: AgentState?,
    cwd: String,
    onOpenRemoteFile: (RemoteFileTarget) -> Unit,
    onLinkError: (String) -> Unit,
    onOpenConversation: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = { onOpenConversation(threadId) }) {
                    Text("打开子会话")
                }
            }
            Text(
                text = agentState?.let { "记录状态：${it.status.label()}" } ?: "尚未报告状态",
                style = MaterialTheme.typography.labelMedium,
                color = if (agentState?.status == AgentStatus.ERRORED) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            agentState?.message?.takeIf(String::isNotBlank)?.let { message ->
                SelectionContainer {
                    ChatMarkdown(message, cwd, onOpenRemoteFile, onLinkError)
                }
            }
        }
    }
}
