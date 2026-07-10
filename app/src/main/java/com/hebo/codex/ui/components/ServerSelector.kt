@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hebo.codex.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
    onDelete: (String) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var deletingServer by remember { mutableStateOf<ServerProfile?>(null) }
    val activeStatus = activeServer?.id?.let(statuses::get) ?: ConnectionStatus.Disconnected

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        Card(
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ConnectionIndicator(activeStatus)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(activeServer?.name ?: "请选择服务器", style = MaterialTheme.typography.titleMedium)
                    Text(
                        activeServer?.webSocketUrl ?: "打开列表选择服务器",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(Icons.Rounded.ExpandMore, contentDescription = "切换服务器")
            }
        }
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            servers.forEach { server ->
                val status = statuses[server.id] ?: ConnectionStatus.Disconnected
                DropdownMenuItem(
                    text = {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    server.name,
                                    modifier = Modifier.weight(1f),
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
                            Text(
                                server.webSocketUrl,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                status.label(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    leadingIcon = { ConnectionIndicator(status) },
                    trailingIcon = {
                        Row {
                            IconButton(
                                onClick = {
                                    expanded = false
                                    onReconnect(server.id)
                                },
                                modifier = Modifier.size(40.dp),
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = "重新连接 ${server.name}")
                            }
                            IconButton(
                                onClick = {
                                    expanded = false
                                    deletingServer = server
                                },
                                modifier = Modifier.size(40.dp),
                            ) {
                                Icon(Icons.Rounded.Delete, contentDescription = "删除 ${server.name}")
                            }
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
            text = { Text("确定删除 ${server.name} 吗？本机保存的连接信息和 Token 将被移除") },
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
