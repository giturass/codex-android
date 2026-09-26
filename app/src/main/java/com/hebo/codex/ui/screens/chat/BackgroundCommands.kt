package com.hebo.codex.ui.screens.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hebo.codex.domain.BackgroundTerminal
import com.hebo.codex.domain.UserFacingError
import kotlinx.coroutines.delay

private const val BACKGROUND_COMMAND_DISPLAY_DELAY_MILLIS = 3_000L

@Composable
internal fun BackgroundCommandsPanel(
    commands: List<BackgroundTerminal>,
    error: UserFacingError?,
    terminatingProcessIds: Set<String>,
    onTerminate: (String) -> Unit,
    canTerminate: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(commands.isNotEmpty()) {
        while (commands.isNotEmpty()) {
            nowMillis = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    LaunchedEffect(commands.isEmpty()) {
        if (commands.isEmpty()) expanded = false
    }
    val visibleCommands = remember(commands, nowMillis) {
        commands.filter { nowMillis - it.observedAtMillis >= BACKGROUND_COMMAND_DISPLAY_DELAY_MILLIS }
    }
    if (visibleCommands.isEmpty() && error == null) return

    ComposerAccessoryPanel(
        title = if (visibleCommands.isEmpty()) {
            requireNotNull(error).summary
        } else {
            "${visibleCommands.size} 个命令正在后台运行 · ${
                formatBackgroundCommandDuration(
                    visibleCommands.maxOf { nowMillis - it.observedAtMillis },
                )
            }"
        },
        icon = Icons.Rounded.Terminal,
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        visibleCommands.forEach { command ->
            BackgroundCommandRow(
                command = command,
                durationMillis = nowMillis - command.observedAtMillis,
                isTerminating = command.processId in terminatingProcessIds,
                canTerminate = canTerminate,
                onTerminate = { onTerminate(command.processId) },
            )
        }
        error?.let {
            Text(
                it.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun BackgroundCommandRow(
    command: BackgroundTerminal,
    durationMillis: Long,
    isTerminating: Boolean,
    canTerminate: Boolean,
    onTerminate: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(start = 10.dp, top = 8.dp, end = 4.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    shellCommandForDisplay(command.command),
                    modifier = Modifier.weight(1f),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (isTerminating) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                } else {
                    TextButton(onClick = onTerminate, enabled = canTerminate) {
                        Text("终止")
                    }
                }
            }
            Text(
                "${command.cwd} · ${formatBackgroundCommandDuration(durationMillis)}",
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

internal fun formatBackgroundCommandDuration(durationMillis: Long): String {
    val totalSeconds = durationMillis.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = totalSeconds % 3_600L / 60L
    val seconds = totalSeconds % 60L
    return when {
        hours > 0 -> "${hours} 小时 ${minutes} 分"
        minutes > 0 -> "${minutes} 分 ${seconds} 秒"
        else -> "${seconds} 秒"
    }
}
