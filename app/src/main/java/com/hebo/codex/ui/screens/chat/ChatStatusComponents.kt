package com.hebo.codex.ui.screens.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hebo.codex.domain.PlanStepStatus
import com.hebo.codex.domain.ThreadGoal
import com.hebo.codex.domain.ThreadGoalStatus
import com.hebo.codex.domain.TurnPlan
import com.hebo.codex.ui.displayLabelRes
import com.hebo.codex.ui.formatDurationSeconds

@Composable
internal fun TaskProgressBar(
    goal: ThreadGoal?,
    plan: TurnPlan?,
    onOpenGoal: () -> Unit,
    onSetGoalStatus: (ThreadGoalStatus) -> Unit,
) {
    if (goal == null && plan == null) return
    val activeStep = plan?.steps?.firstOrNull { it.status == PlanStepStatus.IN_PROGRESS }
        ?: plan?.steps?.firstOrNull { it.status == PlanStepStatus.PENDING }
    val completedSteps = plan?.steps?.count { it.status == PlanStepStatus.COMPLETED } ?: 0
    val totalSteps = plan?.steps?.size ?: 0
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, top = 9.dp, bottom = 9.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Flag, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        goal != null -> "目标 · ${stringResource(goal.status.displayLabelRes)}"
                        else -> "执行计划 · $completedSteps/$totalSteps"
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    when {
                        goal != null && activeStep != null -> "${goal.objective} · 当前：${activeStep.text}"
                        goal != null -> goal.objective
                        else -> activeStep?.text ?: "计划已完成"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (totalSteps > 0) {
                    LinearProgressIndicator(
                        progress = { completedSteps.toFloat() / totalSteps },
                        modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                    )
                }
                if (goal != null) {
                    Text(
                        formatDurationSeconds(goal.timeUsedSeconds),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (goal != null) {
                when (goal.status) {
                    ThreadGoalStatus.ACTIVE -> IconButton(
                        onClick = { onSetGoalStatus(ThreadGoalStatus.PAUSED) },
                    ) {
                        Icon(Icons.Rounded.Pause, contentDescription = "暂停目标")
                    }

                    ThreadGoalStatus.PAUSED, ThreadGoalStatus.BLOCKED -> IconButton(
                        onClick = { onSetGoalStatus(ThreadGoalStatus.ACTIVE) },
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = "继续目标")
                    }

                    else -> Unit
                }
            }
            IconButton(onClick = onOpenGoal) {
                Icon(Icons.Rounded.Edit, contentDescription = if (goal == null) "创建目标" else "编辑目标")
            }
        }
    }
}
