@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hebo.codex.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.ModalBottomSheet
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
fun QuickPhraseSheet(
    phrases: List<QuickPhrase>,
    onDismiss: () -> Unit,
    onSelect: (QuickPhrase) -> Unit,
    onAdd: (String, String) -> Unit,
    onUpdate: (String, String, String) -> Unit,
    onDelete: (String) -> Unit,
    onMoveUp: (String) -> Unit,
    onMoveDown: (String) -> Unit,
) {
    var searchTerm by rememberSaveable { mutableStateOf("") }
    var editedPhrase by remember { mutableStateOf<QuickPhrase?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var deletedPhrase by remember { mutableStateOf<QuickPhrase?>(null) }
    val filteredPhrases = remember(phrases, searchTerm) {
        val query = searchTerm.trim()
        if (query.isEmpty()) {
            phrases
        } else {
            phrases.filter {
                it.name.contains(query, ignoreCase = true) || it.content.contains(query, ignoreCase = true)
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("快捷短语", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(
                onClick = {
                    editedPhrase = null
                    showEditor = true
                },
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Text("新增")
            }
        }
        OutlinedTextField(
            value = searchTerm,
            onValueChange = { searchTerm = it },
            label = { Text("搜索") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
        )
        if (filteredPhrases.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(160.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (phrases.isEmpty()) "还没有快捷短语" else "没有匹配的快捷短语",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                itemsIndexed(filteredPhrases, key = { _, phrase -> phrase.id }) { _, phrase ->
                    val sourceIndex = phrases.indexOfFirst { it.id == phrase.id }
                    QuickPhraseRow(
                        phrase = phrase,
                        canMoveUp = sourceIndex > 0,
                        canMoveDown = sourceIndex in 0 until phrases.lastIndex,
                        onSelect = { onSelect(phrase) },
                        onEdit = {
                            editedPhrase = phrase
                            showEditor = true
                        },
                        onDelete = { deletedPhrase = phrase },
                        onMoveUp = { onMoveUp(phrase.id) },
                        onMoveDown = { onMoveDown(phrase.id) },
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showEditor) {
        QuickPhraseEditorDialog(
            phrase = editedPhrase,
            onDismiss = { showEditor = false },
            onSave = { name, content ->
                val phrase = editedPhrase
                if (phrase == null) onAdd(name, content) else onUpdate(phrase.id, name, content)
                showEditor = false
            },
        )
    }
    deletedPhrase?.let { phrase ->
        AlertDialog(
            onDismissRequest = { deletedPhrase = null },
            title = { Text("删除快捷短语？") },
            text = { Text("将删除“${phrase.name}”") },
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
private fun QuickPhraseRow(
    phrase: QuickPhrase,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(phrase.name, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        phrase.content,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "管理${phrase.name}")
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
            HorizontalDivider()
        }
    }
}

@Composable
private fun QuickPhraseEditorDialog(
    phrase: QuickPhrase?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var name by rememberSaveable(phrase?.id) { mutableStateOf(phrase?.name.orEmpty()) }
    var content by rememberSaveable(phrase?.id) { mutableStateOf(phrase?.content.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (phrase == null) "新增快捷短语" else "编辑快捷短语") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
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
                onClick = { onSave(name.trim(), content) },
                enabled = name.isNotBlank() && content.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
