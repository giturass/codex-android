package com.hebo.codex.ui.screens.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.hebo.codex.domain.ConversationState
import com.hebo.codex.domain.UserFacingError
import com.hebo.codex.domain.RuntimeActivity
import com.hebo.codex.domain.RuntimeActivityStatus
import com.hebo.codex.domain.RuntimeStatus
import com.hebo.codex.domain.ThreadGoal
import com.hebo.codex.domain.ThreadGoalStatus
import com.hebo.codex.domain.TokenUsage
import com.hebo.codex.ui.components.RemainingLinearProgressIndicator
import com.hebo.codex.ui.displayLabelRes
import com.hebo.codex.ui.formatCompactCount
import com.hebo.codex.ui.formatDurationSeconds
import kotlin.math.roundToInt

@Composable
internal fun RuntimeStatusPanel(
    conversation: ConversationState,
    connected: Boolean,
    runtimeStatus: RuntimeStatus = conversation.runtimeStatus,
) {
    val presentation = runtimeStatusPresentation(conversation, runtimeStatus, connected) ?: return
    var expanded by remember(conversation.threadId) { mutableStateOf(false) }
    ComposerAccessoryPanel(
        title = presentation.summary,
        icon = Icons.Rounded.MonitorHeart,
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        if (!connected) RuntimeText("连接已断开，显示最后同步状态")
        conversation.tokenUsage?.let { usage ->
            RuntimeCard("上下文与 Token") {
                if (runtimeStatus.tokenUsageStale && connected) RuntimeText("用量待同步，显示最后收到的数据")
                RuntimeTokenUsage(usage, presentation.contextRemaining)
            }
        }
        presentation.goal?.let { goal ->
            RuntimeCard("会话目标 · ${stringResource(goal.status.displayLabelRes)}") {
                RuntimeText(goal.objective)
                if (runtimeStatus.goalStale && connected) RuntimeText("目标状态待重新同步")
                val remaining = runtimeRemainingTokens(goal.tokensUsed, goal.tokenBudget)
                if (remaining != null) {
                    RuntimeRemaining("目标预算", remaining)
                } else {
                    RuntimeText("已使用 ${formatCompactCount(goal.tokensUsed)} Token · 未设置预算")
                }
                RuntimeText("已运行 ${formatDurationSeconds(goal.timeUsedSeconds)}")
            }
        }
        if (conversation.pendingRequests.isNotEmpty()) {
            RuntimeText("${conversation.pendingRequests.size} 个请求等待处理")
        }
        runtimeStatus.diffs.forEachIndexed { index, diff ->
            key(diff.turnId) {
                RuntimeCard(if (diff.turnId == conversation.activeTurnId) "本轮汇总变更" else "回合汇总变更 ${index + 1}") {
                    if (diff.stale && connected) RuntimeText("变更待同步，显示最后收到的数据")
                    TurnDiffSummary(diff)
                }
            }
        }
        presentation.error?.let { error ->
            RuntimeCard("请求未完成", attention = true) {
                RuntimeText(error.summary, attention = true)
                error.technicalDetails?.let { RuntimeDetail("查看详情", it) }
            }
        }
        presentation.records.asReversed().forEach { activity ->
            key(activity.id) {
                val terminalInput = activity.terminalInput
                RuntimeCard(
                    "${activity.title} · ${if (terminalInput == null) activity.status.label else "已发送"}",
                    attention = activity.status == RuntimeActivityStatus.FAILED || activity.status == RuntimeActivityStatus.WARNING,
                ) {
                    if (terminalInput == null) {
                        if (activity.detail.isNotBlank()) RuntimeText(activity.detail)
                        activity.text?.let { RuntimeDetail("查看详情", it, activity.truncated) }
                        if (activity.truncated && activity.text == null) RuntimeText("内容较长，仅展示保留的片段")
                    } else {
                        TerminalInputDetails(
                            input = terminalInput,
                            command = terminalInputCommand(terminalInput, conversation.items),
                        )
                        if (activity.truncated) RuntimeText("输入较长，仅展示保留的片段")
                    }
                }
            }
        }
        conversation.notices.asReversed().forEach { notice ->
            key(notice.id) {
                RuntimeCard(if (notice.requiresAttention) "需注意" else "通知", attention = notice.requiresAttention) {
                    RuntimeText(notice.message, attention = notice.requiresAttention)
                    notice.technicalDetails?.let { RuntimeDetail("查看详情", it) }
                }
            }
        }
    }
}

@Composable
private fun RuntimeTokenUsage(usage: TokenUsage, remaining: RuntimeRemainingTokens?) {
    if (remaining == null) {
        RuntimeText("服务器未提供上下文窗口大小")
    } else {
        RuntimeRemaining("上下文", remaining)
    }
    RuntimeMetric("最近一次输入", usage.last.inputTokens)
    RuntimeMetric("最近一次输出", usage.last.outputTokens)
    RuntimeMetric("其中缓存输入", usage.last.cachedInputTokens)
    RuntimeMetric("其中推理输出", usage.last.reasoningOutputTokens)
    RuntimeMetric("累计 Token", usage.total.totalTokens)
}

@Composable
private fun RuntimeMetric(label: String, value: Long) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        RuntimeText(formatCompactCount(value))
    }
}

@Composable
private fun RuntimeRemaining(label: String, remaining: RuntimeRemainingTokens) {
    RuntimeText(
        "${label}剩余 ${formatCompactCount(remaining.tokens)} / ${formatCompactCount(remaining.total)} Token · ${remaining.percent}%",
    )
    RemainingLinearProgressIndicator(
        remainingRatio = remaining.ratio,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    )
}

@Composable
private fun RuntimeCard(
    title: String,
    attention: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = if (attention) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            content()
        }
    }
}

@Composable
private fun RuntimeText(text: String, attention: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (attention) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun RuntimeDetail(title: String, text: String, truncated: Boolean = false, monospace: Boolean = false) {
    var expanded by remember(title) { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }) {
        Text(
            if (expanded) "收起$title" else title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (expanded) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (truncated) RuntimeText("内容较长，仅展示保留的片段")
    }
}

internal data class RuntimeStatusPresentation(
    val summary: String,
    val contextRemaining: RuntimeRemainingTokens?,
    val goal: ThreadGoal?,
    val records: List<RuntimeActivity>,
    val error: UserFacingError?,
)

internal data class RuntimeRemainingTokens(val tokens: Long, val total: Long) {
    val ratio: Float get() = (tokens.toDouble() / total).toFloat()
    val percent: Int get() = (tokens.toDouble() / total * 100).roundToInt()
}

internal fun runtimeRemainingTokens(used: Long, total: Long?): RuntimeRemainingTokens? {
    if (total == null || total <= 0L) return null
    return RuntimeRemainingTokens(total - used.coerceIn(0L, total), total)
}

internal fun runtimeStatusPresentation(
    conversation: ConversationState,
    runtimeStatus: RuntimeStatus,
    connected: Boolean,
): RuntimeStatusPresentation? {
    val records = runtimeStatus.records.filter { it.status != RuntimeActivityStatus.COMPLETED }
    val goal = conversation.goal?.takeUnless { it.status == ThreadGoalStatus.COMPLETE }
    if (conversation.tokenUsage == null && goal == null && records.isEmpty() &&
        runtimeStatus.diffs.isEmpty() && conversation.errorMessage == null &&
        conversation.notices.isEmpty() && conversation.pendingRequests.isEmpty()
    ) return null

    val remaining = conversation.tokenUsage?.let { runtimeRemainingTokens(it.last.totalTokens, it.modelContextWindow) }
    val error = conversation.errorMessage?.takeUnless { error ->
        conversation.notices.any { it.message == error.summary && it.technicalDetails == error.technicalDetails }
    }
    val attentionCount = records.count { it.status == RuntimeActivityStatus.FAILED || it.status == RuntimeActivityStatus.WARNING } +
        conversation.pendingRequests.size +
        conversation.notices.count { it.requiresAttention } +
        (if (error != null) 1 else 0)
    val summary = buildList {
        add("运行状态")
        when {
            !connected -> add("待同步")
            runtimeStatus.tokenUsageStale && conversation.tokenUsage != null -> add("上下文待同步")
            remaining != null -> add("上下文剩余 ${remaining.percent}%")
        }
        if (attentionCount > 0) add("$attentionCount 项需注意")
    }.joinToString(" · ")
    return RuntimeStatusPresentation(summary, remaining, goal, records, error)
}

private val RuntimeActivityStatus.label: String
    get() = when (this) {
        RuntimeActivityStatus.RUNNING -> "进行中"
        RuntimeActivityStatus.COMPLETED -> "已完成"
        RuntimeActivityStatus.FAILED -> "失败"
        RuntimeActivityStatus.WARNING -> "需注意"
        RuntimeActivityStatus.UNKNOWN -> "状态待同步"
        RuntimeActivityStatus.INFO -> "已更新"
    }
