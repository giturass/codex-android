package com.hebo.codex.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hebo.codex.domain.QuickPhrase

internal fun insertQuickPhrase(value: TextFieldValue, content: String): TextFieldValue {
    val selectionStart = value.selection.min.coerceIn(0, value.text.length)
    val selectionEnd = value.selection.max.coerceIn(selectionStart, value.text.length)
    return TextFieldValue(
        text = value.text.replaceRange(selectionStart, selectionEnd, content),
        selection = TextRange(selectionStart + content.length),
    )
}

@Composable
fun QuickPhraseMenuContent(
    phrases: List<QuickPhrase>,
    onBack: () -> Unit,
    onManage: () -> Unit,
    onSelect: (QuickPhrase) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "返回添加菜单")
        }
        Text(
            text = "快捷短语",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
        )
        TextButton(onClick = onManage) {
            Text("管理")
        }
    }
    HorizontalDivider()
    if (phrases.isEmpty()) {
        DropdownMenuItem(
            text = {
                Text(
                    text = "还没有快捷短语",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            enabled = false,
            onClick = {},
        )
    } else {
        phrases.forEach { phrase ->
            DropdownMenuItem(
                text = {
                    Text(
                        text = phrase.content,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                onClick = { onSelect(phrase) },
            )
        }
    }
}

@Composable
fun QuickPhraseManagerDialog(
    phrases: List<QuickPhrase>,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
    onUpdate: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onMoveUp: (String) -> Unit,
    onMoveDown: (String) -> Unit,
) {
    var showEditor by remember { mutableStateOf(false) }
    var editedPhrase by remember { mutableStateOf<QuickPhrase?>(null) }
    var deletedPhrase by remember { mutableStateOf<QuickPhrase?>(null) }

    if (!showEditor && deletedPhrase == null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("管理快捷短语", modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = {
                            editedPhrase = null
                            showEditor = true
                        },
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "新增快捷短语")
                    }
                }
            },
            text = {
                if (phrases.isEmpty()) {
                    Text(
                        text = "还没有快捷短语",
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                        itemsIndexed(phrases, key = { _, phrase -> phrase.id }) { index, phrase ->
                            QuickPhraseManageRow(
                                phrase = phrase,
                                canMoveUp = index > 0,
                                canMoveDown = index < phrases.lastIndex,
                                onEdit = {
                                    editedPhrase = phrase
                                    showEditor = true
                                },
                                onDelete = { deletedPhrase = phrase },
                                onMoveUp = { onMoveUp(phrase.id) },
                                onMoveDown = { onMoveDown(phrase.id) },
                            )
                            if (index < phrases.lastIndex) HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("完成") }
            },
        )
    }

    if (showEditor) {
        QuickPhraseEditorDialog(
            phrase = editedPhrase,
            onDismiss = { showEditor = false },
            onSave = { content ->
                val phrase = editedPhrase
                if (phrase == null) onAdd(content) else onUpdate(phrase.id, content)
                showEditor = false
            },
        )
    }

    deletedPhrase?.let { phrase ->
        AlertDialog(
            onDismissRequest = { deletedPhrase = null },
            title = { Text("删除快捷短语？") },
            text = {
                Text(
                    text = phrase.content,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(phrase.id)
                        deletedPhrase = null
                    },
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deletedPhrase = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun QuickPhraseManageRow(
    phrase: QuickPhrase,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onEdit)
                .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = phrase.content,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Column {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "管理快捷短语")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("编辑") },
                        leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onEdit()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("上移") },
                        leadingIcon = { Icon(Icons.Rounded.ArrowUpward, contentDescription = null) },
                        enabled = canMoveUp,
                        onClick = {
                            menuExpanded = false
                            onMoveUp()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("下移") },
                        leadingIcon = { Icon(Icons.Rounded.ArrowDownward, contentDescription = null) },
                        enabled = canMoveDown,
                        onClick = {
                            menuExpanded = false
                            onMoveDown()
                        },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("删除") },
                        leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickPhraseEditorDialog(
    phrase: QuickPhrase?,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var content by rememberSaveable(phrase?.id) { mutableStateOf(phrase?.content.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (phrase == null) "新增快捷短语" else "编辑快捷短语") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("内容") },
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(content) },
                enabled = content.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
