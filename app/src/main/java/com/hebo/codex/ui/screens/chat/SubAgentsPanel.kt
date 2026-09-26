package com.hebo.codex.ui.screens.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hebo.codex.domain.SubAgentRuntimeStatus
import com.hebo.codex.domain.SubAgentSummary
import com.hebo.codex.domain.UserFacingError
import com.hebo.codex.ui.components.ExpandableErrorDetails

@Composable
internal fun SubAgentsPanel(
    agents: List<SubAgentSummary>,
    error: UserFacingError?,
    connected: Boolean,
    onRetry: () -> Unit,
    onOpenConversation: (String) -> Unit,
    stale: Boolean = false,
) {
    val visibleAgents = visibleSubAgents(agents)
    if (visibleAgents.isEmpty() && error == null) return
    var expanded by remember { mutableStateOf(false) }
    ComposerAccessoryPanel(
        title = if (!connected || stale) {
            "子 Agent · 状态待同步" + if (error != null) " · 同步失败" else ""
        } else {
            subAgentsPanelSummary(visibleAgents, error != null)
        },
        icon = Icons.Rounded.Group,
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        if (!connected) {
            Text("连接后可更新状态并打开子会话", style = MaterialTheme.typography.bodySmall)
        } else if (stale) {
            Text("状态待同步，显示最后收到的数据", style = MaterialTheme.typography.bodySmall)
        }
        visibleAgents.forEach { agent ->
            SubAgentRow(
                agent = agent,
                connected = connected,
                onOpenConversation = { onOpenConversation(agent.threadId) },
            )
        }
        error?.let {
            Column {
                ExpandableErrorDetails(it)
                TextButton(onClick = onRetry, enabled = connected) {
                    Text("重试")
                }
            }
        }
    }
}

@Composable
private fun SubAgentRow(
    agent: SubAgentSummary,
    connected: Boolean,
    onOpenConversation: () -> Unit,
) {
    Surface(
        onClick = onOpenConversation,
        enabled = connected,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    agent.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                agent.role?.let { role ->
                    Text(
                        role,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                agent.status.label(),
                style = MaterialTheme.typography.labelSmall,
                color = when (agent.status) {
                    SubAgentRuntimeStatus.ERRORED -> MaterialTheme.colorScheme.error
                    SubAgentRuntimeStatus.WAITING_APPROVAL,
                    SubAgentRuntimeStatus.WAITING_INPUT,
                    -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = "打开子会话",
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

internal fun visibleSubAgents(agents: List<SubAgentSummary>): List<SubAgentSummary> =
    agents.filter {
        it.status != SubAgentRuntimeStatus.COMPLETED && it.status != SubAgentRuntimeStatus.NOT_LOADED
    }

internal fun subAgentsPanelSummary(agents: List<SubAgentSummary>, hasError: Boolean): String = buildList {
    add("子 Agent")
    if (hasError) add("状态同步失败")
    SubAgentRuntimeStatus.entries.forEach { status ->
        val count = agents.count { it.status == status }
        if (count > 0) add("$count 个${status.label()}")
    }
}.joinToString(" · ")

private fun SubAgentRuntimeStatus.label(): String = when (this) {
    SubAgentRuntimeStatus.WAITING_APPROVAL -> "等待审批"
    SubAgentRuntimeStatus.WAITING_INPUT -> "等待输入"
    SubAgentRuntimeStatus.ERRORED -> "出错"
    SubAgentRuntimeStatus.RUNNING -> "运行中"
    SubAgentRuntimeStatus.IDLE -> "空闲"
    SubAgentRuntimeStatus.COMPLETED -> "已完成"
    SubAgentRuntimeStatus.INTERRUPTED -> "已中断"
    SubAgentRuntimeStatus.NOT_LOADED -> "未加载"
}
