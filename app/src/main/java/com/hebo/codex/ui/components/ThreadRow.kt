package com.hebo.codex.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hebo.codex.domain.ThreadRuntimeStatus
import com.hebo.codex.domain.ThreadSummary
import com.hebo.codex.ui.theme.statusColors

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ThreadRow(
    thread: ThreadSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showProjectPath: Boolean = false,
    onMenu: (() -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onMenu,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        thread.title,
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (thread.status == ThreadRuntimeStatus.ACTIVE) {
                        Text(
                            "  运行中",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.statusColors.success,
                        )
                    }
                    Text(
                        "  ${relativeTime(thread.recencyAt ?: thread.updatedAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (showProjectPath) {
                    Text(
                        thread.cwd,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (thread.preview.isNotBlank()) {
                    Text(
                        thread.preview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    }
}
