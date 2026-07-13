@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hebo.codex.ui.screens.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hebo.codex.domain.RemoteFileMetadata
import com.hebo.codex.util.runSuspendCatching
import dev.snipme.highlights.model.SyntaxLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream

@Composable
internal fun RemoteFileSheet(
    target: RemoteFileTarget,
    serverName: String,
    onDismiss: () -> Unit,
    getMetadata: suspend (String) -> RemoteFileMetadata,
    readPreview: suspend (String) -> ByteArray,
    downloadFile: suspend (String, OutputStream) -> Long,
    onOpenRemoteFile: (RemoteFileTarget) -> Unit,
    onLinkError: (String) -> Unit,
    onSaved: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val fileName = remember(target.path) { target.path.remoteFileName() }
    val mimeType = remember(fileName) { remoteFileMimeType(fileName) }
    var metadataState by remember(target.path) {
        mutableStateOf<RemoteLoadState<RemoteFileMetadata>>(RemoteLoadState.Loading)
    }
    var previewState by remember(target.path) {
        mutableStateOf<RemoteLoadState<RemotePreview>>(RemoteLoadState.Idle)
    }
    var loadedBytes by remember(target.path) { mutableStateOf<ByteArray?>(null) }
    var isSaving by remember(target.path) { mutableStateOf(false) }
    var isOpening by remember(target.path) { mutableStateOf(false) }
    var actionError by remember(target.path) { mutableStateOf<String?>(null) }
    var expansionPhase by remember(target.path) { mutableStateOf(ContentExpansionPhase.COLLAPSED) }
    var isPreviewZoomed by remember(target.path) { mutableStateOf(false) }
    val previewContentTarget = when (val preview = previewState) {
        is RemoteLoadState.Ready -> preview.value != RemotePreview.Unsupported
        RemoteLoadState.Idle, RemoteLoadState.Loading, is RemoteLoadState.Failed -> false
    }
    val previewAlpha by animateFloatAsState(
        targetValue = if (expansionPhase.bodyVisible) 1f else 0f,
        animationSpec = tween(EXPANDABLE_CONTENT_FADE_DURATION_MILLIS),
        label = "远程文件预览透明度",
    )

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(mimeType),
    ) { destination ->
        if (destination != null) {
            scope.launch {
                isSaving = true
                actionError = null
                runSuspendCatching {
                    val bytes = loadedBytes
                    withContext(Dispatchers.IO) {
                        val output = requireNotNull(context.contentResolver.openOutputStream(destination, "w")) {
                            "无法打开保存位置"
                        }
                        output.use {
                            if (bytes != null) it.write(bytes) else downloadFile(target.path, it)
                        }
                    }
                }.onSuccess {
                    onSaved(fileName)
                }.onFailure { error ->
                    actionError = error.message ?: "保存文件失败"
                }
                isSaving = false
            }
        }
    }

    fun openWithAnotherApp() {
        scope.launch {
            isOpening = true
            actionError = null
            runSuspendCatching {
                val bytes = loadedBytes
                cacheRemoteFileForOpening(context, fileName) { output ->
                    if (bytes != null) output.write(bytes) else downloadFile(target.path, output)
                }
            }.onSuccess { uri ->
                runCatching { openCachedRemoteFile(context, uri, fileName, mimeType) }
                    .onFailure { error -> actionError = error.message ?: "没有可打开该文件的应用" }
            }.onFailure { error ->
                actionError = error.message ?: "无法准备临时文件"
            }
            isOpening = false
        }
    }

    suspend fun loadPreview() {
        previewState = RemoteLoadState.Loading
        previewState = runSuspendCatching {
            val bytes = loadedBytes ?: readPreview(target.path).also { loadedBytes = it }
            decodeRemotePreview(bytes, fileName)
        }.fold(
            onSuccess = { RemoteLoadState.Ready(it) },
            onFailure = { RemoteLoadState.Failed(it.message ?: "无法预览文件") },
        )
    }

    LaunchedEffect(target.path) {
        val result = runSuspendCatching { getMetadata(target.path) }
            .fold(
                onSuccess = { RemoteLoadState.Ready(it) },
                onFailure = { RemoteLoadState.Failed(it.message ?: "无法读取文件信息") },
            )
        metadataState = result
        if (result is RemoteLoadState.Ready && result.value.isFile && shouldAutoPreviewRemoteFile(fileName)) {
            loadPreview()
        }
    }

    LaunchedEffect(previewContentTarget) {
        expansionPhase = expansionPhase.startTransition(previewContentTarget)
        when (expansionPhase) {
            ContentExpansionPhase.EXPANDING -> {
                delay(EXPANDABLE_CONTENT_SIZE_ANIMATION_DURATION_MILLIS.toLong())
                expansionPhase = expansionPhase.settle()
            }

            ContentExpansionPhase.COLLAPSING -> {
                delay(EXPANDABLE_CONTENT_FADE_DURATION_MILLIS.toLong())
                expansionPhase = expansionPhase.settle()
            }

            ContentExpansionPhase.COLLAPSED,
            ContentExpansionPhase.EXPANDED,
            -> Unit
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = !isPreviewZoomed,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .animateContentSize(
                    animationSpec = tween(EXPANDABLE_CONTENT_SIZE_ANIMATION_DURATION_MILLIS),
                )
                .then(if (expansionPhase.bodyIncluded) Modifier.fillMaxHeight(0.9f) else Modifier)
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
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(12.dp))

            when (val metadata = metadataState) {
                RemoteLoadState.Idle, RemoteLoadState.Loading -> CompactLoadingArea("正在读取文件信息")
                is RemoteLoadState.Failed -> CompactMessage(metadata.message, isError = true)
                is RemoteLoadState.Ready -> {
                    if (!metadata.value.isFile) {
                        CompactMessage(
                            if (metadata.value.isDirectory) "链接目标是目录" else "链接目标不是普通文件",
                            isError = true,
                        )
                    } else {
                        if (metadata.value.isSymlink) {
                            Text(
                                "此路径是符号链接，将读取它指向的文件",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        if (expansionPhase.bodyIncluded) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .graphicsLayer { alpha = previewAlpha },
                            ) {
                                val preview = previewState
                                if (preview is RemoteLoadState.Ready) {
                                    RemotePreviewContent(
                                        preview = preview.value,
                                        target = target,
                                        onOpenRemoteFile = onOpenRemoteFile,
                                        onLinkError = onLinkError,
                                        onZoomedChange = { isPreviewZoomed = it },
                                    )
                                }
                            }
                        } else {
                            when (val preview = previewState) {
                                RemoteLoadState.Idle -> Unit
                                RemoteLoadState.Loading -> CompactLoadingArea("正在读取文件")
                                is RemoteLoadState.Failed -> CompactMessage(preview.message, isError = true)
                                is RemoteLoadState.Ready -> if (preview.value == RemotePreview.Unsupported) {
                                    CompactMessage("该文件不支持内置预览，可使用其他应用打开或保存到设备")
                                }
                            }
                        }
                        actionError?.let {
                            Text(
                                it,
                                modifier = Modifier.padding(top = 8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            val showPreviewAction = when (val preview = previewState) {
                                is RemoteLoadState.Ready -> preview.value == RemotePreview.Unsupported
                                RemoteLoadState.Idle,
                                RemoteLoadState.Loading,
                                is RemoteLoadState.Failed,
                                -> true
                            }
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                if (showPreviewAction) {
                                    OutlinedButton(
                                        onClick = { scope.launch { loadPreview() } },
                                        modifier = Modifier.weight(1f),
                                        enabled = previewState !is RemoteLoadState.Loading &&
                                            !isSaving &&
                                            !isOpening,
                                    ) {
                                        if (previewState is RemoteLoadState.Loading) {
                                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                        } else {
                                            Icon(Icons.Rounded.Visibility, contentDescription = null)
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            when (previewState) {
                                                RemoteLoadState.Loading -> "预览中"
                                                is RemoteLoadState.Failed -> "重试"
                                                else -> "预览"
                                            },
                                        )
                                    }
                                }
                                Button(
                                    onClick = { saveLauncher.launch(fileName) },
                                    modifier = Modifier.weight(1f),
                                    enabled = !isSaving && !isOpening,
                                ) {
                                    if (isSaving) {
                                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Rounded.Download, contentDescription = null)
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (isSaving) "保存中" else "保存")
                                }
                                if (!showPreviewAction) {
                                    OutlinedButton(
                                        onClick = ::openWithAnotherApp,
                                        modifier = Modifier.weight(1f),
                                        enabled = !isOpening && !isSaving,
                                    ) {
                                        if (isOpening) {
                                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                        } else {
                                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null)
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Text(if (isOpening) "准备中" else "其他应用")
                                    }
                                }
                            }
                            if (showPreviewAction) {
                                OutlinedButton(
                                    onClick = ::openWithAnotherApp,
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !isOpening && !isSaving,
                                ) {
                                    if (isOpening) {
                                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null)
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (isOpening) "正在准备文件" else "使用其他应用打开")
                                }
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
private fun CompactLoadingArea(label: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(12.dp))
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CompactMessage(message: String, isError: Boolean = false) {
    Text(
        message,
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun RemotePreviewContent(
    preview: RemotePreview,
    target: RemoteFileTarget,
    onOpenRemoteFile: (RemoteFileTarget) -> Unit,
    onLinkError: (String) -> Unit,
    onZoomedChange: (Boolean) -> Unit,
) {
    when (preview) {
        is RemotePreview.Image -> ZoomableRemoteImage(
            image = preview,
            contentDescription = target.path.remoteFileName(),
            onZoomedChange = onZoomedChange,
        )

        is RemotePreview.Svg -> SvgFilePreview(preview, target.line, onZoomedChange)
        is RemotePreview.Text -> TextFilePreview(
            preview = preview,
            target = target,
            onOpenRemoteFile = onOpenRemoteFile,
            onLinkError = onLinkError,
        )

        RemotePreview.Unsupported -> BoxScopeLoading {
            Text(
                "该文件不支持内置预览，可使用其他应用打开或保存到设备",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SvgFilePreview(
    preview: RemotePreview.Svg,
    targetLine: Int?,
    onZoomedChange: (Boolean) -> Unit,
) {
    var showSource by remember(preview, targetLine) { mutableStateOf(targetLine != null) }
    Column(Modifier.fillMaxSize()) {
        PreviewModeSelector(
            options = listOf("图形", "源码"),
            selectedIndex = if (showSource) 1 else 0,
            onSelected = { showSource = it == 1 },
        )
        if (showSource) {
            SourceTextPreview(preview.source, targetLine, language = null)
        } else {
            ZoomableRemoteSvg(
                svg = preview,
                contentDescription = "SVG 预览",
                onZoomedChange = onZoomedChange,
            )
        }
    }
}

@Composable
private fun TextFilePreview(
    preview: RemotePreview.Text,
    target: RemoteFileTarget,
    onOpenRemoteFile: (RemoteFileTarget) -> Unit,
    onLinkError: (String) -> Unit,
) {
    when {
        preview.kind == TextPreviewKind.Markdown -> MarkdownFilePreview(
            preview = preview,
            target = target,
            onOpenRemoteFile = onOpenRemoteFile,
            onLinkError = onLinkError,
        )

        preview.formatted != null -> FormattedTextFilePreview(preview, target.line)
        else -> SourceTextPreview(preview.source, target.line, preview.language)
    }
}

@Composable
private fun MarkdownFilePreview(
    preview: RemotePreview.Text,
    target: RemoteFileTarget,
    onOpenRemoteFile: (RemoteFileTarget) -> Unit,
    onLinkError: (String) -> Unit,
) {
    var showSource by remember(preview, target.line) { mutableStateOf(target.line != null) }
    Column(Modifier.fillMaxSize()) {
        PreviewModeSelector(
            options = listOf("渲染", "源码"),
            selectedIndex = if (showSource) 1 else 0,
            onSelected = { showSource = it == 1 },
        )
        if (showSource) {
            SourceTextPreview(preview.source, target.line, preview.language)
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                if (preview.source.truncated) PreviewTruncatedNotice()
                ChatMarkdown(
                    content = preview.source.content,
                    cwd = remoteFileParent(target.path),
                    onOpenRemoteFile = onOpenRemoteFile,
                    onLinkError = onLinkError,
                )
            }
        }
    }
}

@Composable
private fun FormattedTextFilePreview(preview: RemotePreview.Text, targetLine: Int?) {
    val formatted = requireNotNull(preview.formatted)
    var showOriginal by remember(preview, targetLine) { mutableStateOf(targetLine != null) }
    Column(Modifier.fillMaxSize()) {
        PreviewModeSelector(
            options = listOf("格式化", "原文"),
            selectedIndex = if (showOriginal) 1 else 0,
            onSelected = { showOriginal = it == 1 },
        )
        SourceTextPreview(
            preview = if (showOriginal) preview.source else formatted,
            targetLine = targetLine.takeIf { showOriginal },
            language = if (showOriginal) preview.language else null,
        )
    }
}

@Composable
private fun PreviewModeSelector(
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
    ) {
        options.forEachIndexed { index, option ->
            FilterChip(
                selected = selectedIndex == index,
                onClick = { onSelected(index) },
                label = { Text(option) },
            )
        }
    }
}

@Composable
private fun SourceTextPreview(
    preview: PreviewText,
    targetLine: Int?,
    language: SyntaxLanguage?,
) {
    val darkTheme = isSystemInDarkTheme()
    val plainLines = remember(preview.content) { AnnotatedString(preview.content).previewLines() }
    val highlightedLines by produceState(
        initialValue = plainLines,
        key1 = preview.content,
        key2 = language,
        key3 = darkTheme,
    ) {
        value = if (language == null) {
            plainLines
        } else {
            withContext(Dispatchers.Default) {
                buildHighlightedText(preview.content, language, darkTheme).previewLines()
            }
        }
    }
    val selectedLine = targetLine?.coerceIn(1, highlightedLines.size)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (selectedLine ?: 1) - 1)
    LaunchedEffect(selectedLine) {
        if (selectedLine != null) listState.scrollToItem(selectedLine - 1)
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        itemsIndexed(highlightedLines) { index, line ->
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
                        text = line,
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

private fun AnnotatedString.previewLines(): List<AnnotatedString> {
    val result = mutableListOf<AnnotatedString>()
    var start = 0
    while (start <= length) {
        val newline = text.indexOf('\n', start)
        val end = if (newline == -1) length else newline
        result += subSequence(start, end).let { line ->
            if (line.text.endsWith('\r')) line.subSequence(0, line.length - 1) else line
        }
        if (newline == -1) break
        start = newline + 1
    }
    return result
}

@Composable
private fun PreviewTruncatedNotice() {
    Text(
        "预览内容已截断，保存文件可查看完整内容",
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        color = MaterialTheme.colorScheme.tertiary,
        style = MaterialTheme.typography.labelMedium,
    )
}

private sealed interface RemoteLoadState<out T> {
    data object Idle : RemoteLoadState<Nothing>

    data object Loading : RemoteLoadState<Nothing>

    data class Ready<T>(val value: T) : RemoteLoadState<T>

    data class Failed(val message: String) : RemoteLoadState<Nothing>
}

private fun String.remoteFileName(): String =
    replace('\\', '/').substringAfterLast('/').ifBlank { "download" }
