package com.hebo.codex.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hebo.codex.domain.ConnectionStatus
import com.hebo.codex.ui.theme.statusColors

@Composable
internal fun ConnectionStatusButton(
    status: ConnectionStatus,
    onReconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val statusIcon = when (status) {
        ConnectionStatus.Connected -> null
        ConnectionStatus.Connecting -> Icons.Rounded.Sync
        ConnectionStatus.Disconnected -> Icons.Rounded.CloudOff
        is ConnectionStatus.Failed -> Icons.Rounded.ErrorOutline
    }
    if (statusIcon != null) {
        val iconModifier = if (status == ConnectionStatus.Connecting) {
            val transition = rememberInfiniteTransition(label = "连接图标旋转")
            val rotation by transition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1_000, easing = LinearEasing),
                ),
                label = "连接图标旋转角度",
            )
            Modifier.graphicsLayer { rotationZ = rotation }
        } else {
            Modifier
        }
        IconButton(onClick = { expanded = true }, modifier = modifier.size(48.dp)) {
            Icon(
                imageVector = statusIcon,
                contentDescription = "服务器连接：${status.label()}",
                modifier = iconModifier,
                tint = if (status is ConnectionStatus.Failed) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (expanded) {
        AlertDialog(
            onDismissRequest = { expanded = false },
            title = { Text("服务器连接") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ConnectionIndicator(status)
                        Text(status.label())
                    }
                    if (status is ConnectionStatus.Failed) ExpandableErrorDetails(status.error)
                }
            },
            confirmButton = {
                if (status == ConnectionStatus.Disconnected || status is ConnectionStatus.Failed) {
                    TextButton(onClick = onReconnect) { Text("重连") }
                }
            },
            dismissButton = {
                TextButton(onClick = { expanded = false }) { Text("关闭") }
            },
        )
    }
}

@Composable
fun ConnectionIndicator(status: ConnectionStatus, modifier: Modifier = Modifier) {
    val color = when (status) {
        ConnectionStatus.Connected -> MaterialTheme.statusColors.success
        ConnectionStatus.Connecting -> MaterialTheme.statusColors.warning
        ConnectionStatus.Disconnected -> MaterialTheme.colorScheme.outline
        is ConnectionStatus.Failed -> MaterialTheme.colorScheme.error
    }
    Box(
        modifier
            .size(9.dp)
            .background(color, CircleShape)
            .semantics { contentDescription = status.label() },
    )
}

fun ConnectionStatus.label(): String = when (this) {
    ConnectionStatus.Connected -> "已连接"
    ConnectionStatus.Connecting -> "连接中"
    ConnectionStatus.Disconnected -> "未连接"
    is ConnectionStatus.Failed -> "连接失败"
}
