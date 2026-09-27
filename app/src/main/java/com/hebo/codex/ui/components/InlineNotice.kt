package com.hebo.codex.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.hebo.codex.R
import com.hebo.codex.domain.ConnectionStatus
import com.hebo.codex.domain.UserFacingError
import kotlinx.coroutines.delay

// Each occurrence has its own identity, including repeated messages with identical text.
class InlineNotice(
    val message: String,
    val technicalDetails: String? = null,
    val isError: Boolean = false,
)

@Stable
class NoticeHostState {
    private val pending = ArrayDeque<InlineNotice>()
    var current by mutableStateOf<InlineNotice?>(null)
        private set

    val hasContent: Boolean get() = current != null

    fun showMessage(message: String, technicalDetails: String? = null) {
        show(InlineNotice(message, technicalDetails))
    }

    fun showError(error: UserFacingError) {
        show(InlineNotice(error.summary, error.technicalDetails, isError = true))
    }

    private fun show(notice: InlineNotice) {
        if (current == null) {
            current = notice
        } else {
            pending.addLast(notice)
        }
    }

    fun dismiss(notice: InlineNotice) {
        if (current === notice) current = pending.removeFirstOrNull()
    }

    companion object {
        val Saver = mapSaver(
            save = { state: NoticeHostState ->
                mapOf(
                    "message" to state.current?.message,
                    "details" to state.current?.technicalDetails,
                    "error" to state.current?.isError,
                    "pending" to ArrayList(state.pending.map {
                        arrayListOf(it.message, it.technicalDetails, it.isError)
                    }),
                )
            },
            restore = { saved ->
                NoticeHostState().apply {
                    current = (saved["message"] as? String)?.let {
                        InlineNotice(it, saved["details"] as? String, saved["error"] as Boolean)
                    }
                    (saved["pending"] as List<*>).forEach { value ->
                        val fields = value as List<*>
                        pending.addLast(InlineNotice(fields[0] as String, fields[1] as? String, fields[2] as Boolean))
                    }
                }
            },
        )
    }
}

@Composable
fun rememberNoticeHostState(vararg inputs: Any?): NoticeHostState =
    rememberSaveable(*inputs, saver = NoticeHostState.Saver) { NoticeHostState() }

@Composable
fun InlineNoticeHost(
    state: NoticeHostState,
    connectionStatus: ConnectionStatus?,
    onReconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val notice = state.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val accessibilityManager = LocalAccessibilityManager.current
    var showingDetails by rememberSaveable(notice) { mutableStateOf(false) }

    LaunchedEffect(notice, lifecycleOwner, accessibilityManager, showingDetails) {
        if (notice == null || showingDetails) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val timeout = if (notice.isError || notice.technicalDetails != null) 8_000L else 4_000L
            delay(
                accessibilityManager?.calculateRecommendedTimeoutMillis(
                    originalTimeoutMillis = timeout,
                    containsIcons = true,
                    containsText = true,
                    containsControls = true,
                ) ?: timeout,
            )
            state.dismiss(notice)
        }
    }

    Column(modifier) {
        if (notice != null) {
            NoticeSurface(
                message = notice.message,
                isError = notice.isError,
                onReadMessage = { showingDetails = true },
                dismissLabel = "关闭提示",
                onDismiss = { state.dismiss(notice) },
                actions = {
                    if (notice.isError || notice.technicalDetails != null) {
                        TextButton(onClick = { showingDetails = true }) { Text(stringResource(R.string.common_details)) }
                    }
                    if (connectionStatus == ConnectionStatus.Disconnected || connectionStatus is ConnectionStatus.Failed) {
                        TextButton(onClick = onReconnect) { Text("重连") }
                    }
                },
            )
        } else if (connectionStatus != null && connectionStatus != ConnectionStatus.Connected) {
            ConnectionNotice(connectionStatus, onReconnect)
        }
    }
    if (showingDetails && notice != null) {
        UserFacingErrorDialog(
            error = UserFacingError(notice.message, notice.technicalDetails),
            onDismiss = {
                showingDetails = false
                state.dismiss(notice)
            },
        )
    }
}

@Composable
private fun ConnectionNotice(
    status: ConnectionStatus,
    onReconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val message = when (status) {
        ConnectionStatus.Disconnected -> "服务器未连接"
        ConnectionStatus.Connecting -> "正在连接服务器"
        is ConnectionStatus.Failed -> status.error.summary
        ConnectionStatus.Connected -> return
    }
    var collapsed by rememberSaveable(status) { mutableStateOf(false) }
    var showingDetails by rememberSaveable(status) { mutableStateOf(false) }
    val error = (status as? ConnectionStatus.Failed)?.error

    Column(modifier) {
        if (collapsed) {
            TextButton(onClick = { collapsed = false }, modifier = Modifier.padding(horizontal = 12.dp)) {
                ConnectionIndicator(status)
                Text(status.label(), Modifier.padding(start = 8.dp))
            }
        } else {
            NoticeSurface(
                message = message,
                isError = status is ConnectionStatus.Failed,
                onReadMessage = if (error != null) ({ showingDetails = true }) else null,
                dismissLabel = "收起提示",
                onDismiss = { collapsed = true },
                actions = {
                    if (error?.technicalDetails != null) {
                        TextButton(onClick = { showingDetails = true }) { Text(stringResource(R.string.common_details)) }
                    }
                    if (status != ConnectionStatus.Connecting) {
                        TextButton(onClick = onReconnect) { Text("重连") }
                    }
                },
            )
        }
    }
    if (showingDetails && error != null) {
        UserFacingErrorDialog(error, onDismiss = { showingDetails = false })
    }
}

@Composable
private fun NoticeSurface(
    message: String,
    isError: Boolean,
    onReadMessage: (() -> Unit)?,
    dismissLabel: String,
    onDismiss: () -> Unit,
    actions: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            Row(
                modifier = Modifier.padding(start = 12.dp).semantics { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (isError) Icons.Rounded.ErrorOutline else Icons.Rounded.Info,
                    contentDescription = if (isError) "错误" else null,
                    modifier = Modifier.size(20.dp),
                    tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    message,
                    modifier = Modifier.weight(1f)
                        .then(
                            if (onReadMessage != null) Modifier.clickable(onClickLabel = "查看完整提示", onClick = onReadMessage)
                            else Modifier,
                        )
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = dismissLabel, modifier = Modifier.size(18.dp))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                actions()
            }
        }
    }
}
