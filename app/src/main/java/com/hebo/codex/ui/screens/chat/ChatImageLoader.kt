package com.hebo.codex.ui.screens.chat

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.util.Base64
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class ChatImageLoader(
    private val readRemoteFile: suspend (String) -> ByteArray,
) {
    private val thumbnails = LinkedHashMap<String, ImageBitmap>(16, 0.75f, true)
    private val thumbnailSnapshots = MutableStateFlow<Map<String, ImageBitmap>>(emptyMap())
    private val loadPermits = Semaphore(2)
    private var thumbnailBytes = 0

    fun observeThumbnail(source: ChatImageSource): Flow<ImageBitmap> = flow {
        val key = loadPermits.withPermit { source.cacheKey() }
        emitAll(thumbnailSnapshots.mapNotNull { it[key] }.distinctUntilChanged())
    }.flowOn(Dispatchers.Default)

    suspend fun loadThumbnail(source: ChatImageSource): ImageBitmap = loadPermits.withPermit {
        withContext(Dispatchers.Default) {
            val key = source.cacheKey()
            synchronized(thumbnails) { thumbnails[key] }?.let { return@withContext it }
            val thumbnail = decodeRemoteImageThumbnail(loadBytes(source))
            currentCoroutineContext().ensureActive()
            cacheThumbnail(key, thumbnail)
            thumbnail
        }
    }

    suspend fun loadPreview(source: ChatImageSource): RemotePreview = loadPermits.withPermit {
        val bytes = loadBytes(source)
        val preview = decodeRemoteImagePreview(bytes)
        val thumbnail = decodeRemoteImageThumbnail(bytes)
        withContext(Dispatchers.Default) {
            cacheThumbnail(source.cacheKey(), thumbnail)
        }
        preview
    }

    private fun cacheThumbnail(key: String, thumbnail: ImageBitmap) {
        synchronized(thumbnails) {
            thumbnails.put(key, thumbnail)?.let { thumbnailBytes -= it.asAndroidBitmap().allocationByteCount }
            thumbnailBytes += thumbnail.asAndroidBitmap().allocationByteCount
            val entries = thumbnails.entries.iterator()
            while (thumbnailBytes > MAX_THUMBNAIL_CACHE_BYTES && entries.hasNext()) {
                thumbnailBytes -= entries.next().value.asAndroidBitmap().allocationByteCount
                entries.remove()
            }
            thumbnailSnapshots.value = thumbnails.toMap()
        }
    }

    internal suspend fun loadBytes(source: ChatImageSource): ByteArray = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        val bytes = when (source) {
            is ChatImageSource.ServerFile -> readRemoteFile(source.path)
            is ChatImageSource.FileReference -> error("服务端未提供此图片的可读取地址，暂时无法预览")
            is ChatImageSource.Url -> when {
                source.value.startsWith("data:", ignoreCase = true) -> decodeChatImageDataUrl(source.value)
                else -> readHttpImage(source.value)
            }
        }
        currentCoroutineContext().ensureActive()
        require(bytes.size <= MAX_CHAT_IMAGE_BYTES) { IMAGE_SIZE_ERROR }
        bytes
    }
}

private fun ChatImageSource.cacheKey(): String {
    val (kind, value) = when (this) {
        is ChatImageSource.Url -> "url:" to value
        is ChatImageSource.ServerFile -> "file:" to path
        is ChatImageSource.FileReference -> "id:" to fileId
    }
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(kind.toByteArray())
    return Base64.getEncoder().encodeToString(digest.digest(value.toByteArray()))
}

internal fun decodeChatImageDataUrl(
    value: String,
    maxBytes: Int = MAX_CHAT_IMAGE_BYTES,
): ByteArray {
    require(value.startsWith("data:", ignoreCase = true)) { "无效的图片 data URL" }
    val separator = value.indexOf(',')
    require(separator >= 5) { "图片 data URL 缺少内容分隔符" }
    val metadata = value.substring(5, separator).split(';')
    require(metadata.first().startsWith("image/", ignoreCase = true)) { "data URL 内容不是图片" }
    val base64 = metadata.last().equals("base64", ignoreCase = true)
    val maxEncodedBytes = if (base64) ((maxBytes + 2L) / 3 * 4).toInt() else maxBytes
    val encoded = decodeDataUrlPayload(value, separator + 1, maxEncodedBytes)
    val bytes = if (base64) Base64.getDecoder().decode(encoded) else encoded
    require(bytes.size <= maxBytes) { IMAGE_SIZE_ERROR }
    return bytes
}

private fun decodeDataUrlPayload(value: String, start: Int, maxBytes: Int): ByteArray {
    require(value.length.toLong() - start <= maxBytes.toLong() * 3) { IMAGE_SIZE_ERROR }
    val output = ByteArrayOutputStream(minOf(value.length - start, maxBytes, 8_192))
    var index = start
    while (index < value.length) {
        require(output.size() < maxBytes) { IMAGE_SIZE_ERROR }
        val character = value[index]
        if (character == '%') {
            require(index + 2 < value.length) { "图片 data URL 包含不完整的百分号编码" }
            val high = value[index + 1].digitToIntOrNull(16)
            val low = value[index + 2].digitToIntOrNull(16)
            require(high != null && low != null) { "图片 data URL 包含无效的百分号编码" }
            output.write(high * 16 + low)
            index += 3
        } else {
            require(character.code <= 0x7f) { "图片 data URL 中的非 ASCII 字符必须使用百分号编码" }
            output.write(character.code)
            index++
        }
    }
    return output.toByteArray()
}

private suspend fun readHttpImage(value: String): ByteArray {
    val uri = URI(value)
    require(uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true)) {
        "仅支持 HTTP、HTTPS 或 data URL 图片"
    }
    val connection = uri.toURL().openConnection() as HttpURLConnection
    connection.connectTimeout = HTTP_TIMEOUT_MILLIS
    connection.readTimeout = HTTP_TIMEOUT_MILLIS
    val context = currentCoroutineContext()
    return suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { connection.disconnect() }
        try {
            context.ensureActive()
            require(connection.responseCode in 200..299) { "图片请求失败（HTTP ${connection.responseCode}）" }
            require(connection.contentLengthLong <= MAX_CHAT_IMAGE_BYTES) { IMAGE_SIZE_ERROR }
            val bytes = connection.inputStream.use { input ->
                readChatImageBytes(input) { context.ensureActive() }
            }
            continuation.resume(bytes)
        } catch (error: Exception) {
            continuation.resumeWithException(error)
        } finally {
            connection.disconnect()
        }
    }
}

internal fun readChatImageBytes(
    input: InputStream,
    maxBytes: Int = MAX_CHAT_IMAGE_BYTES,
    ensureActive: () -> Unit = {},
): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8_192)
    while (true) {
        ensureActive()
        val count = input.read(buffer, 0, minOf(buffer.size, maxBytes - output.size() + 1))
        if (count < 0) break
        require(output.size() + count <= maxBytes) { IMAGE_SIZE_ERROR }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

private const val MAX_CHAT_IMAGE_BYTES = 16 * 1024 * 1024
private const val MAX_THUMBNAIL_CACHE_BYTES = 8 * 1024 * 1024
private const val HTTP_TIMEOUT_MILLIS = 15_000
private const val IMAGE_SIZE_ERROR = "图片超过 16 MiB 预览限制"
