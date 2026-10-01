package com.hebo.codex.ui.screens.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.hebo.codex.util.runSuspendCatching
import com.mikepenz.markdown.annotator.annotatorSettings
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.model.ImageData
import com.mikepenz.markdown.model.ImageTransformer
import com.mikepenz.markdown.model.ImageWidth
import com.mikepenz.markdown.model.PlaceholderConfig
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal val LocalChatImageLoader = staticCompositionLocalOf<ChatImageLoader> {
    error("未提供会话图片加载器")
}

internal class ChatMarkdownImageTransformer(
    private val imageLoader: ChatImageLoader,
    private val cwd: String,
) : ImageTransformer {
    // The renderer first measures inline images, then may move them to a block.
    // Keep their state across those composition paths and deduplicate loads.
    private val images = mutableMapOf<String, ChatMarkdownImageState>()

    @Composable
    fun imageState(link: String): ChatMarkdownImageState {
        val state = images.getOrPut(link) { ChatMarkdownImageState(link, cwd, imageLoader) }
        LaunchedEffect(state, state.attempt) { state.load() }
        return state
    }

    @Composable
    override fun transform(link: String): ImageData? = imageState(link).result?.getOrNull()?.let { bitmap ->
        ImageData(painter = remember(bitmap) { BitmapPainter(bitmap) })
    }

    override fun placeholderConfig(
        link: String,
        density: Density,
        containerSize: Size,
        imageWidth: ImageWidth,
        imageSize: Size,
        imageSizeChanged: ((String, Size) -> Unit)?,
    ): PlaceholderConfig {
        if (!imageSize.isUnspecified) {
            return super.placeholderConfig(link, density, containerSize, imageWidth, imageSize, imageSizeChanged)
        }
        val width = with(density) {
            if (containerSize.isUnspecified) 240f else containerSize.width.toDp().value
        }
        return PlaceholderConfig(Size(width, 64f * density.fontScale))
    }
}

internal class ChatMarkdownImageState(
    private val link: String,
    private val cwd: String,
    private val imageLoader: ChatImageLoader,
) {
    private val loadMutex = Mutex()
    var result by mutableStateOf<Result<ImageBitmap>?>(null)
        private set
    var attempt by mutableIntStateOf(0)
        private set

    fun source(): ChatImageSource = resolveChatMarkdownImageSource(link, cwd)

    suspend fun load() = loadMutex.withLock {
        if (result == null) result = runSuspendCatching {
            imageLoader.loadThumbnail(source(), maxDimension = MARKDOWN_IMAGE_MAX_DIMENSION)
        }
    }

    fun retry() {
        result = null
        attempt++
    }
}

private const val MARKDOWN_IMAGE_MAX_DIMENSION = 1_024

@Composable
internal fun ChatMarkdownBlockImage(
    model: MarkdownComponentModel,
    transformer: ChatMarkdownImageTransformer,
    onPreview: (ChatImageSource) -> Unit,
) {
    val image = resolveChatMarkdownImageLink(model.content, model.node, annotatorSettings()) ?: return
    ChatMarkdownImage(
        state = transformer.imageState(image.link),
        alt = image.alt,
        onPreview = onPreview,
    )
}

@Composable
internal fun ChatMarkdownInlineImage(
    model: MarkdownComponentModel,
    transformer: ChatMarkdownImageTransformer,
    onPreview: (ChatImageSource) -> Unit,
) {
    ChatMarkdownImage(
        state = transformer.imageState(model.content),
        alt = null,
        onPreview = onPreview,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun ChatMarkdownImage(
    state: ChatMarkdownImageState,
    alt: String?,
    onPreview: (ChatImageSource) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bitmap = state.result?.getOrNull()
    val error = state.result?.exceptionOrNull()
    Surface(
        onClick = {
            if (bitmap != null) onPreview(state.source()) else state.retry()
        },
        enabled = bitmap != null || error != null,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = alt ?: "图片，点击查看大图",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().aspectRatio(bitmap.width.toFloat() / bitmap.height),
            )
        } else {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (error == null) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        Icons.Rounded.BrokenImage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column {
                    Text(
                        if (error == null) "正在加载图片" else "图片加载失败，点击重试",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (error != null) {
                        Text(
                            text = error.message ?: "无法读取图片",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
