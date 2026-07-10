package com.hebo.codex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hebo.codex.domain.ConnectionStatus
import com.hebo.codex.ui.theme.Danger
import com.hebo.codex.ui.theme.Success

@Composable
fun ConnectionIndicator(status: ConnectionStatus, modifier: Modifier = Modifier) {
    val color = when (status) {
        ConnectionStatus.Connected -> Success
        ConnectionStatus.Connecting -> Color(0xFFF0A202)
        ConnectionStatus.Disconnected -> Color(0xFF737B97)
        is ConnectionStatus.Failed -> Danger
    }
    Box(modifier.size(9.dp).background(color, CircleShape))
}

fun ConnectionStatus.label(): String = when (this) {
    ConnectionStatus.Connected -> "已连接"
    ConnectionStatus.Connecting -> "连接中"
    ConnectionStatus.Disconnected -> "未连接"
    is ConnectionStatus.Failed -> "连接失败"
}

