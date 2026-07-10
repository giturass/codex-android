package com.hebo.codex.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.hebo.codex.R
import com.hebo.codex.domain.CommandAction
import com.hebo.codex.domain.ConversationItem
import com.hebo.codex.domain.FileSystemAccessMode
import com.hebo.codex.domain.FileSystemPermissionTarget
import com.hebo.codex.domain.RequestedPermissions
import com.hebo.codex.domain.UserMessagePart

@Composable
internal fun UserMessage(item: ConversationItem.UserMessage) {
    val text = item.content.filterIsInstance<UserMessagePart.Text>().joinToString("\n") { it.text }
    val attachments = item.content.filterNot { it is UserMessagePart.Text }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.88f),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (text.isNotBlank()) {
                    SelectionContainer {
                        Text(text, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                if (attachments.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        attachments.forEach { MessagePartLabel(it) }
                    }
                }
                if (item.isPending) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp)
                        Text(
                            "等待服务器确认",
                            modifier = Modifier.padding(start = 6.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessagePartLabel(part: UserMessagePart) {
    val (label, icon) = when (part) {
        is UserMessagePart.Image -> stringResource(R.string.message_attachment_image) to Icons.Rounded.Image
        is UserMessagePart.LocalImage -> stringResource(
            R.string.message_attachment_server_image,
            part.path.fileName(),
        ) to Icons.Rounded.Image

        is UserMessagePart.Skill -> stringResource(
            R.string.message_attachment_skill,
            part.name,
        ) to Icons.Rounded.AutoAwesome

        is UserMessagePart.Mention -> stringResource(
            R.string.message_attachment_mention,
            part.name,
        ) to Icons.Rounded.Apps

        is UserMessagePart.Text -> return
    }
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
internal fun CommandActionView(action: CommandAction) {
    val title = when (action) {
        is CommandAction.Read -> "读取 ${action.name}"
        is CommandAction.ListFiles -> "列出目录内容"
        is CommandAction.Search -> action.query?.let { "搜索“$it”" } ?: "搜索内容"
        is CommandAction.Unknown -> "运行未识别的命令"
    }
    val detail = when (action) {
        is CommandAction.Read -> action.path
        is CommandAction.ListFiles -> action.path
        is CommandAction.Search -> action.path
        is CommandAction.Unknown -> action.command
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            detail?.takeIf(String::isNotBlank)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun PolicyProposal(title: String, description: String, technicalDetails: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(description, style = MaterialTheme.typography.bodySmall)
            ExpandableTechnicalDetails(technicalDetails)
        }
    }
}

@Composable
internal fun PermissionSummary(permissions: RequestedPermissions) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        permissions.networkEnabled?.let { enabled ->
            PermissionLine(
                title = if (enabled) "访问网络" else "保持网络隔离",
                detail = if (enabled) "允许命令连接外部网络" else "不授予网络访问权限",
            )
        }
        permissions.fileSystem?.entries.orEmpty().forEach { entry ->
            PermissionLine(
                title = when (entry.access) {
                    FileSystemAccessMode.READ -> "读取文件"
                    FileSystemAccessMode.WRITE -> "修改文件"
                    FileSystemAccessMode.DENY -> "禁止访问"
                },
                detail = entry.target.label(),
            )
        }
        permissions.fileSystem?.globScanMaxDepth?.let {
            PermissionLine("目录扫描深度", "$it 层")
        }
        ExpandableTechnicalDetails(permissions.responseJson)
    }
}

@Composable
private fun PermissionLine(title: String, detail: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun FileSystemPermissionTarget.label(): String = when (this) {
    is FileSystemPermissionTarget.Path -> path
    is FileSystemPermissionTarget.GlobPattern -> "匹配 $pattern"
    FileSystemPermissionTarget.Root -> "整个文件系统"
    FileSystemPermissionTarget.Minimal -> "最小运行目录"
    is FileSystemPermissionTarget.ProjectRoots -> subpath?.let { "所有项目的 $it" } ?: "所有项目目录"
    FileSystemPermissionTarget.TempDirectory -> "系统临时目录"
    FileSystemPermissionTarget.SlashTmp -> "/tmp"
    is FileSystemPermissionTarget.Unknown -> listOfNotNull(path, subpath).joinToString("/")
}

@Composable
internal fun ExpandableTechnicalDetails(value: String) {
    var expanded by rememberSaveable(value) { mutableStateOf(false) }
    Column {
        TextButton(onClick = { expanded = !expanded }) {
            Text(
                stringResource(
                    if (expanded) R.string.common_hide_technical_details
                    else R.string.common_show_technical_details,
                ),
            )
        }
        if (expanded) TechnicalText(value)
    }
}

@Composable
private fun TechnicalText(value: String) {
    SelectionContainer {
        Text(
            value,
            modifier = Modifier.fillMaxWidth().background(
                MaterialTheme.colorScheme.inverseSurface,
                RoundedCornerShape(8.dp),
            ).padding(10.dp),
            color = MaterialTheme.colorScheme.inverseOnSurface,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun String.fileName(): String = substringAfterLast('/').ifBlank { this }
