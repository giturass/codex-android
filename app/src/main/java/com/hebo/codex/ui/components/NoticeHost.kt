package com.hebo.codex.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.platform.LocalDensity
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
import com.hebo.codex.domain.UserFacingError
import kotlinx.coroutines.delay

internal val NoticeMaxWidth = 600.dp

// Each occurrence has its own identity, including repeated messages with identical text.
class Notice(
    val message: String,
    val technicalDetails: String? = null,
    val isError: Boolean = false,
)

@Stable
class NoticeHostState {
    private val pending = ArrayDeque<Notice>()
    var current by mutableStateOf<Notice?>(null)
        private set
    var showingDetails by mutableStateOf(false)
        private set

    val hasContent: Boolean get() = current != null

    fun showMessage(message: String, technicalDetails: String? = null) {
        show(Notice(message, technicalDetails))
    }

    fun showError(error: UserFacingError) {
        show(Notice(error.summary, error.technicalDetails, isError = true))
    }

    private fun show(notice: Notice) {
        if (current == null) {
            current = notice
        } else {
            pending.addLast(notice)
        }
    }

    fun showDetails(notice: Notice) {
        if (current === notice) showingDetails = true
    }

    fun dismiss(notice: Notice) {
        if (current === notice) {
            showingDetails = false
            current = pending.removeFirstOrNull()
        }
    }

    companion object {
        val Saver = mapSaver(
            save = { state: NoticeHostState ->
                mapOf(
                    "message" to state.current?.message,
                    "details" to state.current?.technicalDetails,
                    "error" to state.current?.isError,
                    "showingDetails" to state.showingDetails,
                    "pending" to ArrayList(state.pending.map {
                        arrayListOf(it.message, it.technicalDetails, it.isError)
                    }),
                )
            },
            restore = { saved ->
                NoticeHostState().apply {
                    current = (saved["message"] as? String)?.let {
                        Notice(it, saved["details"] as? String, saved["error"] as Boolean)
                    }
                    showingDetails = saved["showingDetails"] as Boolean
                    (saved["pending"] as List<*>).forEach { value ->
                        val fields = value as List<*>
                        pending.addLast(Notice(fields[0] as String, fields[1] as? String, fields[2] as Boolean))
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
fun NoticeHostEffect(state: NoticeHostState) {
    val notice = state.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val accessibilityManager = LocalAccessibilityManager.current
    val showingDetails = state.showingDetails

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

    if (showingDetails && notice != null) {
        UserFacingErrorDialog(
            error = UserFacingError(notice.message, notice.technicalDetails),
            onDismiss = { state.dismiss(notice) },
        )
    }
}

@Composable
internal fun FloatingNoticeHost(
    state: NoticeHostState,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
) {
    val slideOffset = with(LocalDensity.current) { 8.dp.roundToPx() }
    AnimatedContent(
        targetState = state.current.takeIf { visible },
        modifier = modifier,
        contentAlignment = Alignment.TopCenter,
        transitionSpec = {
            val enter = fadeIn(tween(180, delayMillis = 100)) +
                slideInVertically(tween(180, delayMillis = 100)) { -slideOffset }
            val exit = fadeOut(tween(100)) + slideOutVertically(tween(100)) { -slideOffset }
            (enter togetherWith exit).using(null)
        },
        label = "notice",
    ) { notice ->
        if (notice != null) {
            NoticeSurface(
                message = notice.message,
                isError = notice.isError,
                onReadMessage = { state.showDetails(notice) },
                onDismiss = { state.dismiss(notice) },
                actions = {
                    if (notice.isError || notice.technicalDetails != null) {
                        TextButton(onClick = { state.showDetails(notice) }) { Text(stringResource(R.string.common_details)) }
                    }
                },
            )
        }
    }
}

@Composable
private fun NoticeSurface(
    message: String,
    isError: Boolean,
    onReadMessage: () -> Unit,
    onDismiss: () -> Unit,
    actions: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 4.dp,
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
                        .clickable(onClickLabel = "查看完整提示", onClick = onReadMessage)
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "关闭提示", modifier = Modifier.size(18.dp))
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
