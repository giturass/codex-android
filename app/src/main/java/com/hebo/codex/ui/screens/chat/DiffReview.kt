@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hebo.codex.ui.screens.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hebo.codex.ui.theme.statusColors

internal enum class ReviewLineKind {
    ADDITION,
    DELETION,
    CONTEXT,
    METADATA,
}

internal data class ReviewLine(
    val text: String,
    val kind: ReviewLineKind,
    val oldLine: Int? = null,
    val newLine: Int? = null,
)

internal data class ReviewFile(
    val path: String,
    val lines: List<ReviewLine>,
    val additions: Int,
    val deletions: Int,
)

internal fun parseUnifiedDiff(diff: String): List<ReviewFile> {
    if (diff.isBlank()) return emptyList()
    val files = mutableListOf<ReviewFile>()
    var path = "本轮差异"
    var lines = mutableListOf<ReviewLine>()
    var additions = 0
    var deletions = 0
    var oldLine: Int? = null
    var newLine: Int? = null
    var fileStarted = false

    fun flushFile() {
        if (!fileStarted && lines.isEmpty()) return
        files += ReviewFile(path, lines.toList(), additions, deletions)
        lines = mutableListOf()
        additions = 0
        deletions = 0
        oldLine = null
        newLine = null
        fileStarted = false
    }

    diff.lineSequence().forEach { line ->
        val fileMatch = DIFF_FILE_REGEX.matchEntire(line)
        if (fileMatch != null) {
            flushFile()
            path = fileMatch.groupValues[2]
            fileStarted = true
            lines += ReviewLine(line, ReviewLineKind.METADATA)
            return@forEach
        }
        val hunkMatch = HUNK_REGEX.find(line)
        if (hunkMatch != null) {
            oldLine = hunkMatch.groupValues[1].toInt()
            newLine = hunkMatch.groupValues[2].toInt()
            lines += ReviewLine(line, ReviewLineKind.METADATA)
            fileStarted = true
            return@forEach
        }
        when {
            line.startsWith("+++ ") -> {
                val candidate = line.removePrefix("+++ ").removePrefix("b/")
                if (candidate != "/dev/null") path = candidate
                lines += ReviewLine(line, ReviewLineKind.METADATA)
            }

            line.startsWith("--- ") || line.startsWith("index ") || line.startsWith("new file ") ||
                line.startsWith("deleted file ") || line.startsWith("\\ No newline") -> {
                lines += ReviewLine(line, ReviewLineKind.METADATA)
            }

            line.startsWith('+') -> {
                lines += ReviewLine(line, ReviewLineKind.ADDITION, newLine = newLine)
                additions += 1
                newLine = newLine?.plus(1)
            }

            line.startsWith('-') -> {
                lines += ReviewLine(line, ReviewLineKind.DELETION, oldLine = oldLine)
                deletions += 1
                oldLine = oldLine?.plus(1)
            }

            else -> {
                lines += ReviewLine(line, ReviewLineKind.CONTEXT, oldLine = oldLine, newLine = newLine)
                oldLine = oldLine?.plus(1)
                newLine = newLine?.plus(1)
            }
        }
        fileStarted = true
    }
    flushFile()
    return files
}

@Composable
internal fun TurnDiffSummaryCard(diff: String, isRunning: Boolean, onOpen: () -> Unit) {
    val files = remember(diff) { parseUnifiedDiff(diff) }
    val additions = files.sumOf(ReviewFile::additions)
    val deletions = files.sumOf(ReviewFile::deletions)
    Surface(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Description, contentDescription = null)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(if (isRunning) "本轮修改中" else "审阅本轮修改", style = MaterialTheme.typography.labelLarge)
                Text(
                    "${files.size} 个文件 · +$additions −$deletions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("查看", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
internal fun ReviewChangesDialog(
    diff: String,
    onDismiss: () -> Unit,
    onAddComment: (String) -> Unit,
) {
    val files = remember(diff) { parseUnifiedDiff(diff) }
    var expandedFiles by remember(diff) {
        mutableStateOf(files.firstOrNull()?.path?.let(::setOf).orEmpty())
    }
    var commentTarget by remember { mutableStateOf<Pair<ReviewFile, ReviewLine>?>(null) }
    var comment by rememberSaveable { mutableStateOf("") }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("本轮修改") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, contentDescription = "关闭审阅")
                        }
                    },
                )
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item("summary") {
                    Text(
                        "${files.size} 个文件 · +${files.sumOf(ReviewFile::additions)} −${files.sumOf(ReviewFile::deletions)}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "点按变更行右侧的评论按钮，可将行级反馈加入输入框",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(files, key = ReviewFile::path) { file ->
                    val expanded = file.path in expandedFiles
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Surface(
                            onClick = {
                                expandedFiles = if (expanded) expandedFiles - file.path else expandedFiles + file.path
                            },
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        file.path,
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "+${file.additions} −${file.deletions}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Icon(
                                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                    contentDescription = if (expanded) "收起差异" else "展开差异",
                                )
                            }
                        }
                        if (expanded) {
                            file.lines.forEach { line ->
                                ReviewLineRow(line) {
                                    comment = ""
                                    commentTarget = file to line
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    commentTarget?.let { (file, line) ->
        AlertDialog(
            onDismissRequest = { commentTarget = null },
            title = { Text("添加行级反馈") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(file.path, style = MaterialTheme.typography.labelLarge)
                    SelectionContainer {
                        Text(line.text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedTextField(
                        value = comment,
                        onValueChange = { comment = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("反馈") },
                        minLines = 3,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddComment(formatReviewComment(file, line, comment.trim()))
                        commentTarget = null
                    },
                    enabled = comment.isNotBlank(),
                ) { Text("加入输入框") }
            },
            dismissButton = { TextButton(onClick = { commentTarget = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun ReviewLineRow(line: ReviewLine, onComment: () -> Unit) {
    val background = when (line.kind) {
        ReviewLineKind.ADDITION -> MaterialTheme.statusColors.success.copy(alpha = 0.10f)
        ReviewLineKind.DELETION -> MaterialTheme.colorScheme.error.copy(alpha = 0.10f)
        ReviewLineKind.METADATA -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        ReviewLineKind.CONTEXT -> Color.Transparent
    }
    Row(
        Modifier.fillMaxWidth().background(background).padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            listOf(line.oldLine, line.newLine).joinToString(" ") { it?.toString()?.padStart(4) ?: "    " },
            modifier = Modifier.width(72.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
        )
        SelectionContainer(Modifier.weight(1f)) {
            Text(
                line.text,
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 5.dp),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                softWrap = false,
            )
        }
        if (line.kind == ReviewLineKind.ADDITION || line.kind == ReviewLineKind.DELETION) {
            IconButton(onClick = onComment, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Rounded.AddComment, contentDescription = "为此行添加反馈", modifier = Modifier.size(18.dp))
            }
        } else {
            Spacer(Modifier.width(38.dp))
        }
    }
}

private fun formatReviewComment(file: ReviewFile, line: ReviewLine, comment: String): String {
    val lineNumber = line.newLine?.let { "+$it" } ?: line.oldLine?.let { "-$it" } ?: "未编号"
    return "审阅意见：`${file.path}` 第 $lineNumber 行\n> ${line.text}\n\n$comment"
}

private val DIFF_FILE_REGEX = Regex("^diff --git a/(.+) b/(.+)$")
private val HUNK_REGEX = Regex("@@ -([0-9]+)(?:,[0-9]+)? \\+([0-9]+)(?:,[0-9]+)? @@")
