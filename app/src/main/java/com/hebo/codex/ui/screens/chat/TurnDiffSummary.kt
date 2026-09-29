package com.hebo.codex.ui.screens.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hebo.codex.domain.RuntimeDiff

@Composable
internal fun TurnDiffSummary(diff: RuntimeDiff) {
    val parsed = remember(diff.diff) { parseTurnDiff(diff.diff) }
    val partial = diff.truncated || parsed.incomplete
    var selectedPath by remember(diff.turnId) { mutableStateOf<String?>(null) }
    val selectedFile = parsed.files.firstOrNull { it.path == selectedPath }
    LaunchedEffect(selectedPath, parsed.files) {
        if (selectedPath != null && selectedFile == null) selectedPath = null
    }
    var showRawDiff by remember(diff.turnId) { mutableStateOf(false) }
    val additions = parsed.files.sumOf { it.additions }
    val deletions = parsed.files.sumOf { it.deletions }

    Text(
        text = when {
            parsed.files.isEmpty() && !partial -> "暂无文件变更"
            else -> "${if (partial) "已识别 " else ""}${parsed.files.size} 个文件 · +$additions / −$deletions 行"
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (partial) {
        Text(
            text = if (diff.truncated) "内容已截断，文件与行数仅统计保留的内容" else "部分变更无法识别，仅统计可识别文件",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    parsed.files.forEach { file ->
        Surface(
            onClick = { selectedPath = file.path },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (file.previousPath == null) file.path else "${file.previousPath} → ${file.path}",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${file.kind.label} · 新增 ${file.additions} 行 · 删除 ${file.deletions} 行",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = "查看文件差异",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (partial) {
        TextButton(onClick = { showRawDiff = true }) {
            Text("查看保留的原始补丁", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    selectedFile?.let { file ->
        FullCodeDialog(
            title = file.path,
            text = remember(file.diff) { AnnotatedString(file.diff) },
            onDismiss = { selectedPath = null },
        )
    }
    if (showRawDiff) {
        FullCodeDialog(
            title = "保留的原始补丁",
            text = remember(diff.diff) { AnnotatedString(diff.diff) },
            onDismiss = { showRawDiff = false },
        )
    }
}

private val TurnDiffKind.label: String
    get() = when (this) {
        TurnDiffKind.ADD -> "新增"
        TurnDiffKind.UPDATE -> "修改"
        TurnDiffKind.DELETE -> "删除"
        TurnDiffKind.RENAME -> "重命名"
    }
