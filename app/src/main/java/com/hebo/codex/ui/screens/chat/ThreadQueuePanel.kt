package com.hebo.codex.ui.screens.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hebo.codex.domain.RuntimeInputQueue

@Composable
internal fun ThreadQueuePanel(
    threadId: String,
    queue: RuntimeInputQueue?,
    connected: Boolean,
    enabled: Boolean,
    canEdit: Boolean,
    operationId: String?,
    onWithdraw: (String, Boolean) -> Unit,
    onRetry: () -> Unit,
) {
    if (queue == null || (queue.entries.isEmpty() && queue.error == null)) return
    var expanded by remember(threadId) { mutableStateOf(true) }
    val ready = connected && !queue.loading && !queue.stale && queue.error == null && enabled
    ComposerAccessoryPanel(
        title = "待发送队列 · ${queue.entries.size} 条",
        icon = Icons.AutoMirrored.Rounded.List,
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 240.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (!connected || queue.stale) Text("队列状态待同步", style = MaterialTheme.typography.bodySmall)
            if (queue.loading) Text("正在刷新队列", style = MaterialTheme.typography.bodySmall)
            queue.error?.let {
                Text(it.summary, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onRetry, enabled = connected && !queue.loading) { Text("重试") }
            }
            queue.entries.forEachIndexed { index, entry ->
                key(entry.id) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text("${index + 1}. ${entry.description}", maxLines = 3, style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = { onWithdraw(entry.id, true) }, enabled = ready && canEdit) { Text("撤回编辑") }
                            TextButton(onClick = { onWithdraw(entry.id, false) }, enabled = ready) {
                                Text(if (operationId == entry.id) "处理中…" else "撤销")
                            }
                        }
                    }
                }
            }
        }
    }
}
