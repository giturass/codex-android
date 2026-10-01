package com.hebo.codex.ui.screens.chat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Picture
import android.graphics.RectF
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntSize
import androidx.core.graphics.createBitmap
import androidx.exifinterface.media.ExifInterface
import com.caverock.androidsvg.SVG
import dev.snipme.highlights.model.SyntaxLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import kotlin.math.roundToInt

internal suspend fun decodeRemotePreview(
    bytes: ByteArray,
    fileName: String,
): RemotePreview = withContext(Dispatchers.Default) {
    val declaredSvg = fileName.extension().equals("svg", ignoreCase = true)
    if (declaredSvg) return@withContext decodeSvgPreview(bytes)

    decodeBitmap(bytes)?.let { return@withContext it }

    val text = decodeRemoteText(bytes) ?: return@withContext RemotePreview.Unsupported
    if (text.looksLikeSvg()) return@withContext decodeSvgPreview(text)

    val source = text.toPreviewText()
    val kind = textPreviewKind(fileName)
    val formatted = if (kind == TextPreviewKind.Json && !source.truncated) {
        formatJson(text)?.toPreviewText()
    } else {
        null
    }
    RemotePreview.Text(
        source = source,
        kind = kind,
        formatted = formatted,
        language = syntaxLanguageForFileName(fileName),
    )
}

internal suspend fun decodeRemoteImageThumbnail(
    bytes: ByteArray,
    maxDimension: Int = MAX_IMAGE_THUMBNAIL_DIMENSION,
): ImageBitmap = withContext(Dispatchers.Default) {
    decodeBitmap(bytes, maxDimension = maxDimension)?.let { return@withContext it.preview }
    val text = decodeRemoteText(bytes)
    require(text != null && text.looksLikeSvg()) { "内容不是支持的图片格式" }
    decodeSvgPreview(text, maxPreviewDimension = maxDimension).preview
}

internal suspend fun decodeRemoteImagePreview(bytes: ByteArray): RemotePreview = withContext(Dispatchers.Default) {
    decodeBitmap(bytes)?.let { return@withContext it }
    val text = decodeRemoteText(bytes)
    require(text != null && text.looksLikeSvg()) { "内容不是支持的图片格式" }
    decodeSvgPreview(text)
}

private fun decodeBitmap(bytes: ByteArray, maxDimension: Int? = null): RemotePreview.Image? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val supportsSubSampling = supportsBitmapRegionDecoding(bytes)
    val sampleSize = if (maxDimension != null) {
        bitmapThumbnailSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
    } else {
        bitmapPreviewSampleSize(
            width = bounds.outWidth,
            height = bounds.outHeight,
            supportsSubSampling = supportsSubSampling,
        )
    }
    val bitmap = BitmapFactory.decodeByteArray(
        bytes,
        0,
        bytes.size,
        BitmapFactory.Options().apply { inSampleSize = sampleSize },
    ) ?: error("无法解码图片")
    return RemotePreview.Image(
        preview = bitmap.applyExifOrientation(bytes).asImageBitmap(),
        encodedBytes = bytes.takeIf { supportsSubSampling && maxDimension == null },
    )
}

private fun Bitmap.applyExifOrientation(bytes: ByteArray): Bitmap {
    val exif = bytes.inputStream().use { ExifInterface(it) }
    if (!exif.isFlipped && exif.rotationDegrees == 0) return this
    val matrix = Matrix().apply {
        if (exif.isFlipped) postScale(-1f, 1f)
        postRotate(exif.rotationDegrees.toFloat())
    }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true).also { oriented ->
        if (oriented !== this) recycle()
    }
}

internal fun bitmapThumbnailSampleSize(width: Int, height: Int, maxDimension: Int = MAX_IMAGE_THUMBNAIL_DIMENSION): Int {
    require(width > 0 && height > 0 && maxDimension > 0)
    var sampleSize = 1
    while ((width - 1) / sampleSize >= maxDimension || (height - 1) / sampleSize >= maxDimension) {
        sampleSize *= 2
    }
    return sampleSize
}

internal fun bitmapPreviewSampleSize(
    width: Int,
    height: Int,
    supportsSubSampling: Boolean,
): Int {
    require(width > 0 && height > 0)
    var sampleSize = 1
    if (supportsSubSampling) {
        while (width / sampleSize > MAX_IMAGE_PREVIEW_DIMENSION ||
            height / sampleSize > MAX_IMAGE_PREVIEW_DIMENSION
        ) {
            sampleSize *= 2
        }
    } else {
        while ((width / sampleSize).toLong() * (height / sampleSize) > MAX_IN_MEMORY_IMAGE_PIXELS) {
            sampleSize *= 2
        }
    }
    return sampleSize
}

internal fun supportsBitmapRegionDecoding(bytes: ByteArray): Boolean =
    bytes.startsWith(JPEG_SIGNATURE) ||
        bytes.startsWith(PNG_SIGNATURE) ||
        bytes.isWebp()

private fun ByteArray.isWebp(): Boolean =
    size >= 12 &&
        copyOfRange(0, 4).contentEquals(RIFF_SIGNATURE) &&
        copyOfRange(8, 12).contentEquals(WEBP_SIGNATURE)

private fun decodeSvgPreview(bytes: ByteArray): RemotePreview.Svg {
    val text = decodeRemoteText(bytes) ?: error("SVG 不是 UTF-8 或带 BOM 的 UTF-16 文本")
    return decodeSvgPreview(text)
}

private fun decodeSvgPreview(text: String, maxPreviewDimension: Int = MAX_IMAGE_PREVIEW_DIMENSION): RemotePreview.Svg {
    require(!UNSAFE_XML_DECLARATION.containsMatchIn(text)) { "SVG 包含不允许的 DTD 或实体声明" }
    val svg = runCatching { SVG.getFromString(text) }
        .getOrElse { error -> throw IllegalArgumentException("无法解析 SVG：${error.message}", error) }
    val rendered = renderSvg(svg, maxPreviewDimension)
    return RemotePreview.Svg(
        picture = rendered.picture,
        imageSize = rendered.imageSize,
        preview = rendered.preview,
        source = text.toPreviewText(),
    )
}

private fun renderSvg(svg: SVG, maxPreviewDimension: Int): RenderedSvg {
    val viewBox = svg.documentViewBox
    val documentWidth = svg.documentWidth.validDimension()
    val documentHeight = svg.documentHeight.validDimension()
    val viewBoxWidth = viewBox?.width()?.validDimension()
    val viewBoxHeight = viewBox?.height()?.validDimension()
    val (sourceWidth, sourceHeight) = when {
        documentWidth != null && documentHeight != null -> documentWidth to documentHeight
        documentWidth != null && viewBoxWidth != null && viewBoxHeight != null ->
            documentWidth to documentWidth * viewBoxHeight / viewBoxWidth

        documentHeight != null && viewBoxWidth != null && viewBoxHeight != null ->
            documentHeight * viewBoxWidth / viewBoxHeight to documentHeight

        viewBoxWidth != null && viewBoxHeight != null -> viewBoxWidth to viewBoxHeight
        documentWidth != null -> documentWidth to documentWidth
        documentHeight != null -> documentHeight to documentHeight
        else -> DEFAULT_SVG_PREVIEW_DIMENSION.toFloat() to DEFAULT_SVG_PREVIEW_DIMENSION.toFloat()
    }
    val sourceLongestDimension = maxOf(sourceWidth, sourceHeight)
    val sourceScale = when {
        sourceLongestDimension > MAX_SVG_LOGICAL_DIMENSION -> MAX_SVG_LOGICAL_DIMENSION / sourceLongestDimension
        sourceLongestDimension < DEFAULT_SVG_PREVIEW_DIMENSION ->
            DEFAULT_SVG_PREVIEW_DIMENSION / sourceLongestDimension

        else -> 1f
    }
    val imageSize = IntSize(
        width = (sourceWidth * sourceScale).roundToInt().coerceIn(1, MAX_SVG_LOGICAL_DIMENSION),
        height = (sourceHeight * sourceScale).roundToInt().coerceIn(1, MAX_SVG_LOGICAL_DIMENSION),
    )
    val picture = svg.renderToPicture(imageSize.width, imageSize.height)
    val previewScale = (maxPreviewDimension / imageSize.maxDimension.toFloat()).coerceAtMost(1f)
    val previewWidth = (imageSize.width * previewScale).roundToInt().coerceAtLeast(1)
    val previewHeight = (imageSize.height * previewScale).roundToInt().coerceAtLeast(1)
    val preview = createBitmap(previewWidth, previewHeight)
    Canvas(preview).drawPicture(
        picture,
        RectF(0f, 0f, previewWidth.toFloat(), previewHeight.toFloat()),
    )
    return RenderedSvg(
        picture = picture,
        imageSize = imageSize,
        preview = preview.asImageBitmap(),
    )
}

private val IntSize.maxDimension: Int get() = maxOf(width, height)

private fun Float.validDimension(): Float? = takeIf { it.isFinite() && it > 0f }

internal fun decodeRemoteText(bytes: ByteArray): String? {
    val (charset, offset) = when {
        bytes.startsWith(UTF_8_BOM) -> Charsets.UTF_8 to UTF_8_BOM.size
        bytes.startsWith(UTF_16_LE_BOM) -> Charsets.UTF_16LE to UTF_16_LE_BOM.size
        bytes.startsWith(UTF_16_BE_BOM) -> Charsets.UTF_16BE to UTF_16_BE_BOM.size
        bytes.any { it == 0.toByte() } -> return null
        else -> Charsets.UTF_8 to 0
    }
    return charset.decodeStrictly(bytes, offset)
}

private fun Charset.decodeStrictly(bytes: ByteArray, offset: Int): String? = runCatching {
    newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes, offset, bytes.size - offset))
        .toString()
}.getOrNull()

private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
    size >= prefix.size && prefix.indices.all { index -> this[index] == prefix[index] }

private fun String.toPreviewText(): PreviewText {
    val truncated = length > MAX_TEXT_PREVIEW_CHARACTERS
    return PreviewText(
        content = if (truncated) take(MAX_TEXT_PREVIEW_CHARACTERS) else this,
        truncated = truncated,
    )
}

private fun String.looksLikeSvg(): Boolean = SVG_ROOT.containsMatchIn(take(SVG_PREAMBLE_LIMIT))

private fun formatJson(text: String): String? = runCatching {
    val element = Json.parseToJsonElement(text)
    PRETTY_JSON.encodeToString(JsonElement.serializer(), element)
}.getOrNull()

internal fun textPreviewKind(fileName: String): TextPreviewKind = when (fileName.extension().lowercase()) {
    "md", "markdown", "mdown", "mkdn" -> TextPreviewKind.Markdown
    "json" -> TextPreviewKind.Json
    else -> TextPreviewKind.Plain
}

internal fun shouldAutoPreviewRemoteFile(fileName: String): Boolean {
    val normalizedName = fileName.lowercase()
    return normalizedName in AUTO_PREVIEW_FILE_NAMES || normalizedName.extension() in AUTO_PREVIEW_EXTENSIONS
}

internal fun syntaxLanguageForFileName(fileName: String): SyntaxLanguage? {
    val normalizedName = fileName.lowercase()
    return when (normalizedName.extension()) {
        "c", "h" -> SyntaxLanguage.C
        "cc", "cpp", "cxx", "hh", "hpp", "hxx" -> SyntaxLanguage.CPP
        "dart" -> SyntaxLanguage.DART
        "java" -> SyntaxLanguage.JAVA
        "kt", "kts" -> SyntaxLanguage.KOTLIN
        "rs" -> SyntaxLanguage.RUST
        "cs" -> SyntaxLanguage.CSHARP
        "coffee" -> SyntaxLanguage.COFFEESCRIPT
        "js", "jsx", "mjs", "cjs" -> SyntaxLanguage.JAVASCRIPT
        "pl", "pm" -> SyntaxLanguage.PERL
        "py", "pyw" -> SyntaxLanguage.PYTHON
        "rb" -> SyntaxLanguage.RUBY
        "sh", "bash", "zsh" -> SyntaxLanguage.SHELL
        "swift" -> SyntaxLanguage.SWIFT
        "ts", "tsx", "mts", "cts" -> SyntaxLanguage.TYPESCRIPT
        "go" -> SyntaxLanguage.GO
        "php" -> SyntaxLanguage.PHP
        else -> when (normalizedName) {
            ".bashrc", ".zshrc", "gradlew" -> SyntaxLanguage.SHELL
            else -> null
        }
    }
}

internal fun remoteFileParent(path: String): String {
    val normalized = path.replace('\\', '/')
    val separator = normalized.lastIndexOf('/')
    return when {
        separator < 0 -> normalized
        separator == 0 -> "/"
        separator == 2 && normalized.getOrNull(1) == ':' -> normalized.take(3)
        else -> normalized.substring(0, separator)
    }
}

private fun String.extension(): String = substringAfterLast('.', "")

internal data class PreviewText(
    val content: String,
    val truncated: Boolean,
)

internal enum class TextPreviewKind {
    Plain,
    Markdown,
    Json,
}

internal sealed interface RemotePreview {
    data class Image(
        val preview: ImageBitmap,
        val encodedBytes: ByteArray?,
    ) : RemotePreview

    data class Svg(
        val picture: Picture,
        val imageSize: IntSize,
        val preview: ImageBitmap,
        val source: PreviewText,
    ) : RemotePreview

    data class Text(
        val source: PreviewText,
        val kind: TextPreviewKind,
        val formatted: PreviewText?,
        val language: SyntaxLanguage?,
    ) : RemotePreview

    data object Unsupported : RemotePreview
}

private data class RenderedSvg(
    val picture: Picture,
    val imageSize: IntSize,
    val preview: ImageBitmap,
)

private val PRETTY_JSON = Json { prettyPrint = true }
private val AUTO_PREVIEW_FILE_NAMES = setOf(
    ".bashrc",
    ".editorconfig",
    ".env",
    ".gitattributes",
    ".gitignore",
    ".zshrc",
    "dockerfile",
    "gradlew",
    "license",
    "makefile",
    "readme",
)
private val AUTO_PREVIEW_EXTENSIONS = setOf(
    "bash",
    "bmp",
    "c",
    "cc",
    "cjs",
    "coffee",
    "conf",
    "config",
    "cpp",
    "cs",
    "css",
    "csv",
    "cts",
    "cxx",
    "dart",
    "gif",
    "go",
    "gradle",
    "graphql",
    "gql",
    "h",
    "hh",
    "hpp",
    "htm",
    "html",
    "hxx",
    "ini",
    "java",
    "jpeg",
    "jpg",
    "js",
    "json",
    "jsonl",
    "jsx",
    "kt",
    "kts",
    "less",
    "log",
    "markdown",
    "md",
    "mdown",
    "mjs",
    "mkdn",
    "mts",
    "php",
    "pl",
    "pm",
    "png",
    "properties",
    "pro",
    "py",
    "pyw",
    "rb",
    "rs",
    "scss",
    "sh",
    "sql",
    "svg",
    "swift",
    "toml",
    "ts",
    "tsv",
    "tsx",
    "txt",
    "webp",
    "xml",
    "yaml",
    "yml",
    "zsh",
)
private val UTF_8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
private val UTF_16_LE_BOM = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
private val UTF_16_BE_BOM = byteArrayOf(0xFE.toByte(), 0xFF.toByte())
private val JPEG_SIGNATURE = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
private val PNG_SIGNATURE = byteArrayOf(
    0x89.toByte(),
    0x50,
    0x4E,
    0x47,
    0x0D,
    0x0A,
    0x1A,
    0x0A,
)
private val RIFF_SIGNATURE = "RIFF".toByteArray(Charsets.US_ASCII)
private val WEBP_SIGNATURE = "WEBP".toByteArray(Charsets.US_ASCII)
private val SVG_ROOT = Regex("^(?:\\s|<\\?xml.*?\\?>|<!--.*?-->)*<svg(?:\\s|>)", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private val UNSAFE_XML_DECLARATION = Regex("<!\\s*(?:DOCTYPE|ENTITY)\\b", RegexOption.IGNORE_CASE)
private const val SVG_PREAMBLE_LIMIT = 4_096
private const val DEFAULT_SVG_PREVIEW_DIMENSION = 512
private const val MAX_IMAGE_PREVIEW_DIMENSION = 2_048
internal const val MAX_IMAGE_THUMBNAIL_DIMENSION = 256
private const val MAX_SVG_LOGICAL_DIMENSION = 32_768
private const val MAX_IN_MEMORY_IMAGE_PIXELS = 8_000_000L
private const val MAX_TEXT_PREVIEW_CHARACTERS = 1_000_000
