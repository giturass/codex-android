package com.hebo.codex.ui.screens.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.hebo.codex.util.runSuspendCatching

@Composable
internal fun ChatImageThumbnail(
    source: ChatImageSource,
    imageLoader: ChatImageLoader,
    modifier: Modifier = Modifier,
) {
    var image by remember(source, imageLoader) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(source, imageLoader) { mutableStateOf(false) }
    LaunchedEffect(source, imageLoader) {
        imageLoader.observeThumbnail(source).collect {
            image = it
            failed = false
        }
    }
    LaunchedEffect(source, imageLoader) {
        runSuspendCatching { imageLoader.loadThumbnail(source) }
            .onSuccess { image = it }
            .onFailure { failed = true }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        val bitmap = image
        when {
            bitmap != null -> Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            failed -> Icon(
                Icons.Rounded.BrokenImage,
                contentDescription = if (source is ChatImageSource.FileReference) {
                    "图片暂不可预览"
                } else {
                    "缩略图加载失败，点击预览重试"
                },
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            else -> CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                strokeWidth = 2.dp,
            )
        }
    }
}

@Composable
internal fun ChatImagePreviewDialog(
    source: ChatImageSource,
    imageLoader: ChatImageLoader,
    onDismiss: () -> Unit,
) {
    var attempt by remember(source) { mutableIntStateOf(0) }
    var preview by remember(source, imageLoader, attempt) { mutableStateOf<RemotePreview?>(null) }
    var error by remember(source, imageLoader, attempt) { mutableStateOf<String?>(null) }
    LaunchedEffect(source, imageLoader, attempt) {
        runSuspendCatching { imageLoader.loadPreview(source) }
            .onSuccess { preview = it }
            .onFailure { error = it.message ?: "无法加载图片" }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val view = LocalView.current
        SideEffect {
            val window = (view.parent as DialogWindowProvider).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
        Surface(Modifier.fillMaxSize(), color = Color.Black, contentColor = Color.White) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "关闭图片预览")
                    }
                    Text("图片预览", style = MaterialTheme.typography.titleMedium)
                }
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    val content = preview
                    val failure = error
                    when {
                        content is RemotePreview.Image -> ZoomableRemoteImage(
                            image = content,
                            contentDescription = "图片预览",
                            onZoomedChange = {},
                        )
                        content is RemotePreview.Svg -> ZoomableRemoteSvg(
                            svg = content,
                            contentDescription = "图片预览",
                            onZoomedChange = {},
                        )
                        failure != null -> Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(failure, textAlign = TextAlign.Center)
                            if (source !is ChatImageSource.FileReference) {
                                TextButton(onClick = { attempt++ }) {
                                    Text("重试", color = Color.White)
                                }
                            }
                        }
                        else -> CircularProgressIndicator(color = Color.White)
                    }
                }
            }
        }
    }
}
