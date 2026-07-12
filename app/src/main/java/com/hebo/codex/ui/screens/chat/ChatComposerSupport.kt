package com.hebo.codex.ui.screens.chat

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.hebo.codex.domain.FeatureEntry
import com.hebo.codex.domain.FeatureKind
import com.hebo.codex.domain.DraftAttachment
import com.hebo.codex.domain.UserInputPart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class ComposerToken(
    val start: Int,
    val end: Int,
    val trigger: Char,
    val query: String,
)

internal enum class ComposerCommand(val label: String, val description: String) {
    GOAL("/goal", "创建或管理持续目标"),
    REVIEW("/review", "审查代码修改"),
    COMPACT("/compact", "压缩当前会话上下文"),
    SETTINGS("/settings", "调整下一轮模型与权限"),
    ATTACH("/attach", "添加图片、Skill 或提及"),
}

internal sealed interface ComposerPaletteEntry {
    val key: String
    val title: String
    val description: String

    data class Command(val command: ComposerCommand) : ComposerPaletteEntry {
        override val key: String = "command:${command.name}"
        override val title: String = command.label
        override val description: String = command.description
    }

    data class Resource(
        override val key: String,
        val input: UserInputPart,
        override val title: String,
        override val description: String,
    ) : ComposerPaletteEntry
}

internal fun activeComposerToken(text: String, cursor: Int = text.length): ComposerToken? {
    val end = cursor.coerceIn(0, text.length)
    if (end == 0) return null
    val start = text.substring(0, end).indexOfLast(Char::isWhitespace) + 1
    if (start >= end) return null
    val trigger = text[start]
    if (trigger != '/' && trigger != '$' && trigger != '@') return null
    return ComposerToken(
        start = start,
        end = end,
        trigger = trigger,
        query = text.substring(start + 1, end),
    )
}

internal fun removeActiveComposerToken(text: String, token: ComposerToken): String =
    text.removeRange(token.start, token.end).let { result ->
        if (token.end == text.length) result.trimEnd() else result
    }

internal fun insertComposerTrigger(value: TextFieldValue, trigger: Char): TextFieldValue {
    require(trigger == '$' || trigger == '@') { "不支持的输入触发符：$trigger" }
    val selectionStart = value.selection.min.coerceIn(0, value.text.length)
    val selectionEnd = value.selection.max.coerceIn(selectionStart, value.text.length)
    val before = value.text.substring(0, selectionStart)
    val after = value.text.substring(selectionEnd)
    val insertion = buildString {
        if (before.lastOrNull()?.isWhitespace() == false) append(' ')
        append(trigger)
    }
    val cursor = before.length + insertion.length
    return TextFieldValue(
        text = before + insertion + after,
        selection = TextRange(cursor),
    )
}

internal fun composerPaletteEntries(
    token: ComposerToken?,
    resources: List<FeatureEntry>,
): List<ComposerPaletteEntry> {
    token ?: return emptyList()
    val query = token.query.trim()
    return when (token.trigger) {
        '/' -> ComposerCommand.entries
            .filter { query.isEmpty() || it.label.removePrefix("/").contains(query, ignoreCase = true) }
            .map(ComposerPaletteEntry::Command)

        '$' -> resources
            .asSequence()
            .filter { it.kind == FeatureKind.SKILL }
            .filter { query.isEmpty() || it.matchesComposerQuery(query) }
            .map {
                ComposerPaletteEntry.Resource(
                    key = "skill:${it.id}",
                    input = UserInputPart.Skill(it.title, it.path ?: it.id),
                    title = it.title,
                    description = it.description.ifBlank { "Skill" },
                )
            }
            .take(PALETTE_RESULT_LIMIT)
            .toList()

        '@' -> resources
            .asSequence()
            .filter { it.kind == FeatureKind.APP || it.kind == FeatureKind.PLUGIN }
            .filter { query.isEmpty() || it.matchesComposerQuery(query) }
            .map {
                ComposerPaletteEntry.Resource(
                    key = "${it.kind.name.lowercase()}:${it.id}",
                    input = UserInputPart.Mention(
                        it.title,
                        it.metadata["mentionPath"] ?: it.path ?: it.id,
                    ),
                    title = it.title,
                    description = it.description.ifBlank {
                        if (it.kind == FeatureKind.APP) "App" else "Plugin"
                    },
                )
            }
            .take(PALETTE_RESULT_LIMIT)
            .toList()

        else -> emptyList()
    }
}

@Composable
internal fun ComposerPalette(
    entries: List<ComposerPaletteEntry>,
    isLoading: Boolean,
    onSelect: (ComposerPaletteEntry) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 3.dp,
    ) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
            if (isLoading && entries.isEmpty()) {
                item("loading") {
                    Box(
                        Modifier.fillMaxWidth().padding(18.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    }
                }
            }
            items(entries, key = ComposerPaletteEntry::key) { entry ->
                Surface(onClick = { onSelect(entry) }, color = MaterialTheme.colorScheme.surfaceContainer) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            when (entry) {
                                is ComposerPaletteEntry.Command -> Icons.Rounded.Description
                                is ComposerPaletteEntry.Resource -> when (entry.input) {
                                    is UserInputPart.Skill -> Icons.Rounded.AutoAwesome
                                    else -> Icons.Rounded.Apps
                                }
                            },
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(entry.title, style = MaterialTheme.typography.labelLarge)
                            Text(
                                entry.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
internal fun DraftAttachmentChip(
    attachment: DraftAttachment,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            Modifier.padding(start = 6.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (attachment.previewUri != null) {
                AttachmentThumbnail(attachment.previewUri)
            } else {
                Icon(
                    when (attachment.input) {
                        is UserInputPart.Image, is UserInputPart.LocalImage -> Icons.Rounded.Image
                        is UserInputPart.Skill -> Icons.Rounded.AutoAwesome
                        is UserInputPart.Mention -> Icons.Rounded.Apps
                        is UserInputPart.Text -> Icons.Rounded.Description
                    },
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                attachment.input.composerLabel,
                modifier = Modifier.padding(horizontal = 7.dp),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = "移除 ${attachment.input.composerLabel}")
            }
        }
    }
}

@Composable
private fun AttachmentThumbnail(uri: String) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, uri, context) {
        value = withContext(Dispatchers.IO) { loadThumbnail(context, uri.toUri()) }
    }
    if (bitmap == null) {
        Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(34.dp))
    } else {
        Image(
            bitmap = bitmap!!,
            contentDescription = "图片预览",
            modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
        )
    }
}

private fun loadThumbnail(context: Context, uri: Uri): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sampleSize = 1
    while (bounds.outWidth / sampleSize > THUMBNAIL_MAX_SIZE ||
        bounds.outHeight / sampleSize > THUMBNAIL_MAX_SIZE
    ) {
        sampleSize *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, options)?.asImageBitmap()
    }
}

internal suspend fun Uri.toImageDataUrl(context: Context): String = withContext(Dispatchers.IO) {
    val mimeType = requireNotNull(context.contentResolver.getType(this@toImageDataUrl)) {
        "无法识别图片类型"
    }
    require(mimeType.startsWith("image/")) { "选择的内容不是图片" }
    val bytes = context.contentResolver.openInputStream(this@toImageDataUrl)?.use { it.readBytes() }
        ?: error("无法读取图片")
    "data:$mimeType;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}"
}

private fun FeatureEntry.matchesComposerQuery(query: String): Boolean =
    listOfNotNull(title, description, parentId, path).any { it.contains(query, ignoreCase = true) }

private val UserInputPart.composerLabel: String
    get() = when (this) {
        is UserInputPart.Text -> text.take(24)
        is UserInputPart.Image -> "设备图片"
        is UserInputPart.LocalImage -> "服务端图片"
        is UserInputPart.Skill -> "Skill · $name"
        is UserInputPart.Mention -> "提及 · $name"
    }

private const val PALETTE_RESULT_LIMIT = 8
private const val THUMBNAIL_MAX_SIZE = 256
