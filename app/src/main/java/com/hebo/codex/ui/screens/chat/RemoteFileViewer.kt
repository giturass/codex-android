@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hebo.codex.ui.screens.chat

import android.graphics.BitmapFactory
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hebo.codex.domain.RemoteFileMetadata
import com.hebo.codex.util.runSuspendCatching
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

@Composable
internal fun RemoteFileSheet(
    target: RemoteFileTarget,
    serverName: String,
    onDismiss: () -> Unit,
    getMetadata: suspend (String) -> RemoteFileMetadata,
    readFile: suspend (String) -> ByteArray,
    onSaved: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fileName = remember(target.path) { target.path.remoteFileName() }
    val mimeType = remember(fileName) { fileName.mimeType() }
    var metadataState by remember(target.path) {
        mutableStateOf<RemoteLoadState<RemoteFileMetadata>>(RemoteLoadState.Loading)
    }
    var previewState by remember(target.path) {
        mutableStateOf<RemoteLoadState<RemotePreview>>(RemoteLoadState.Idle)
    }
    var loadedBytes by remember(target.path) { mutableStateOf<ByteArray?>(null) }
    var isSaving by remember(target.path) { mutableStateOf(false) }
    var saveError by remember(target.path) { mutableStateOf<String?>(null) }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(mimeType),
    ) { destination ->
        if (destination != null) {
            scope.launch {
                isSaving = true
                saveError = null
                runSuspendCatching {
                    val bytes = loadedBytes ?: readFile(target.path).also { loadedBytes = it }
                    withContext(Dispatchers.IO) {
                        val output = requireNotNull(context.contentResolver.openOutputStream(destination, "w")) {
                            "无法打开保存位置"
                        }
                        output.use { it.write(bytes) }
                    }
                }.onSuccess {
                    onSaved(fileName)
                }.onFailure { error ->
                    saveError = error.message ?: "保存文件失败"
                }
                isSaving = false
            }
        }
    }

    LaunchedEffect(target.path) {
        metadataState = runSuspendCatching { getMetadata(target.path) }
            .fold(
                onSuccess = { RemoteLoadState.Ready(it) },
                onFailure = { RemoteLoadState.Failed(it.message ?: "无法读取文件信息") },
            )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        fileName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        serverName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "关闭文件查看器")
                }
            }
            SelectionContainer {
                Text(
                    target.path,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))

            when (val metadata = metadataState) {
                RemoteLoadState.Idle, RemoteLoadState.Loading -> LoadingArea("正在读取文件信息")
                is RemoteLoadState.Failed -> ErrorArea(metadata.message)
                is RemoteLoadState.Ready -> {
                    if (!metadata.value.isFile) {
                        ErrorArea(if (metadata.value.isDirectory) "链接目标是目录" else "链接目标不是普通文件")
                    } else {
                        if (metadata.value.isSymlink) {
                            Text(
                                "此路径是符号链接，将读取它指向的文件",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        Box(Modifier.fillMaxWidth().weight(1f)) {
                            when (val preview = previewState) {
                                RemoteLoadState.Idle -> PreviewHint()
                                RemoteLoadState.Loading -> LoadingArea("正在读取文件")
                                is RemoteLoadState.Failed -> ErrorArea(preview.message)
                                is RemoteLoadState.Ready -> RemotePreviewContent(preview.value, target)
                            }
                        }
                        saveError?.let {
                            Text(
                                it,
                                modifier = Modifier.padding(top = 8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        previewState = RemoteLoadState.Loading
                                        previewState = runSuspendCatching {
                                            val bytes = loadedBytes ?: readFile(target.path).also { loadedBytes = it }
                                            decodeRemotePreview(bytes)
                                        }.fold(
                                            onSuccess = { RemoteLoadState.Ready(it) },
                                            onFailure = {
                                                RemoteLoadState.Failed(it.message ?: "无法预览文件")
                                            },
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = previewState !is RemoteLoadState.Loading && !isSaving,
                            ) {
                                Icon(Icons.Rounded.Visibility, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("预览")
                            }
                            Button(
                                onClick = { saveLauncher.launch(fileName) },
                                modifier = Modifier.weight(1f),
                                enabled = !isSaving && previewState !is RemoteLoadState.Loading,
                            ) {
                                if (isSaving) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Rounded.Download, contentDescription = null)
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(if (isSaving) "保存中" else "保存")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScopeLoading(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun LoadingArea(label: String) {
    BoxScopeLoading {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ErrorArea(message: String) {
    BoxScopeLoading {
        Text(message, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun PreviewHint() {
    BoxScopeLoading {
        Text("选择预览读取文件内容，或直接保存到设备", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RemotePreviewContent(preview: RemotePreview, target: RemoteFileTarget) {
    when (preview) {
        is RemotePreview.Image -> Image(
            bitmap = preview.bitmap,
            contentDescription = target.path.remoteFileName(),
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )

        is RemotePreview.Text -> TextFilePreview(preview, target.line)
        RemotePreview.Unsupported -> BoxScopeLoading {
            Text(
                "该文件不是支持的图片或 UTF-8 文本，可保存后使用其他应用打开",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TextFilePreview(preview: RemotePreview.Text, targetLine: Int?) {
    val selectedLine = targetLine?.coerceIn(1, preview.lines.size)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (selectedLine ?: 1) - 1)
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        itemsIndexed(preview.lines) { index, line ->
            val lineNumber = index + 1
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(
                        if (lineNumber == selectedLine) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerLow,
                    )
                    .padding(vertical = 2.dp),
            ) {
                Text(
                    lineNumber.toString(),
                    modifier = Modifier.width(48.dp).padding(end = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SelectionContainer(modifier = Modifier.weight(1f)) {
                    Text(
                        line,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        if (preview.truncated) {
            item {
                Text(
                    "预览内容已截断，保存文件可查看完整内容",
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

private suspend fun decodeRemotePreview(bytes: ByteArray): RemotePreview = withContext(Dispatchers.Default) {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth > 0 && bounds.outHeight > 0) {
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > MAX_IMAGE_PREVIEW_DIMENSION ||
            bounds.outHeight / sampleSize > MAX_IMAGE_PREVIEW_DIMENSION
        ) {
            sampleSize *= 2
        }
        val bitmap = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sampleSize },
        ) ?: error("无法解码图片")
        return@withContext RemotePreview.Image(bitmap.asImageBitmap())
    }
    if (bytes.any { it == 0.toByte() }) return@withContext RemotePreview.Unsupported

    val text = runCatching {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    }.getOrNull() ?: return@withContext RemotePreview.Unsupported
    val truncated = text.length > MAX_TEXT_PREVIEW_CHARACTERS
    val previewText = if (truncated) text.take(MAX_TEXT_PREVIEW_CHARACTERS) else text
    RemotePreview.Text(previewText.split('\n'), truncated)
}

private sealed interface RemoteLoadState<out T> {
    data object Idle : RemoteLoadState<Nothing>

    data object Loading : RemoteLoadState<Nothing>

    data class Ready<T>(val value: T) : RemoteLoadState<T>

    data class Failed(val message: String) : RemoteLoadState<Nothing>
}

private sealed interface RemotePreview {
    data class Image(val bitmap: ImageBitmap) : RemotePreview

    data class Text(val lines: List<String>, val truncated: Boolean) : RemotePreview

    data object Unsupported : RemotePreview
}

private fun String.remoteFileName(): String =
    replace('\\', '/').substringAfterLast('/').ifBlank { "download" }

private fun String.mimeType(): String {
    val extension = substringAfterLast('.', "").lowercase()
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
}

private const val MAX_IMAGE_PREVIEW_DIMENSION = 2_048
private const val MAX_TEXT_PREVIEW_CHARACTERS = 1_000_000
