@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hebo.codex.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.hebo.codex.domain.ConnectionStatus
import com.hebo.codex.domain.ServerProfile

@Composable
fun ServerSelector(
    servers: List<ServerProfile>,
    activeServer: ServerProfile?,
    statuses: Map<String, ConnectionStatus>,
    onSelect: (String) -> Unit,
    onReconnect: (String) -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onOpenSettings: (String) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var actionsServer by remember { mutableStateOf<ServerProfile?>(null) }
    var deletingServer by remember { mutableStateOf<ServerProfile?>(null) }
    val activeStatus = activeServer?.id?.let(statuses::get) ?: ConnectionStatus.Disconnected

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = {
            expanded = it
            if (!it) actionsServer = null
        },
        modifier = modifier,
    ) {
        Surface(
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (activeStatus !is ConnectionStatus.Connected) {
                    ConnectionIndicator(activeStatus)
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = activeServer?.name ?: "请选择服务器",
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = activeServer?.webSocketUrl ?: "打开列表选择服务器",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Rounded.ExpandMore, contentDescription = "切换服务器")
            }
        }
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                actionsServer = null
            },
        ) {
            servers.forEach { server ->
                val status = statuses[server.id] ?: ConnectionStatus.Disconnected
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = server.name,
                                modifier = Modifier.widthIn(max = ServerNameMaxWidth),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = server.webSocketUrl,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (server.id == activeServer?.id) {
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = "当前服务器",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    },
                    leadingIcon = { ConnectionIndicator(status) },
                    trailingIcon = {
                        IconButton(
                            onClick = { actionsServer = server },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "管理 ${server.name}")
                            ServerActionsMenu(
                                expanded = actionsServer?.id == server.id,
                                status = status,
                                onDismiss = { actionsServer = null },
                                onOpenSettings = {
                                    actionsServer = null
                                    expanded = false
                                    onOpenSettings(server.id)
                                },
                                onReconnect = {
                                    actionsServer = null
                                    expanded = false
                                    onReconnect(server.id)
                                },
                                onEdit = {
                                    actionsServer = null
                                    expanded = false
                                    onEdit(server.id)
                                },
                                onDelete = {
                                    actionsServer = null
                                    expanded = false
                                    deletingServer = server
                                },
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(server.id)
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("添加服务器") },
                leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                onClick = {
                    expanded = false
                    onAdd()
                },
            )
        }
    }

    deletingServer?.let { server ->
        AlertDialog(
            onDismissRequest = { deletingServer = null },
            title = { Text("删除服务器") },
            text = { Text("确定删除 ${server.name} 吗？本机保存的连接信息和能力令牌将被移除") },
            confirmButton = {
                Button(
                    onClick = {
                        deletingServer = null
                        onDelete(server.id)
                    },
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deletingServer = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun ServerActionsMenu(
    expanded: Boolean,
    status: ConnectionStatus,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    onReconnect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val settingsEnabled = isServerSettingsEnabled(status)

    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = {
                Column {
                    Text("服务器设置")
                    if (!settingsEnabled) {
                        Text(
                            text = "连接后可用",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            },
            leadingIcon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
            enabled = settingsEnabled,
            onClick = onOpenSettings,
        )
        DropdownMenuItem(
            text = { Text("重新连接") },
            leadingIcon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
            onClick = onReconnect,
        )
        DropdownMenuItem(
            text = { Text("编辑连接") },
            leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
            onClick = onEdit,
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("删除服务器", color = MaterialTheme.colorScheme.error) },
            leadingIcon = {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            onClick = onDelete,
        )
    }
}

internal fun isServerSettingsEnabled(status: ConnectionStatus): Boolean =
    status is ConnectionStatus.Connected

private val ServerNameMaxWidth = 112.dp
