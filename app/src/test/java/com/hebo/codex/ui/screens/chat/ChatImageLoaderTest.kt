package com.hebo.codex.ui.screens.chat

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ChatImageLoaderTest {
    @Test
    fun decodesBase64AndEscapedBase64WithoutReplacingPlusSigns() {
        val expected = byteArrayOf(0xfb.toByte(), 0xff.toByte())
        assertArrayEquals(expected, decodeChatImageDataUrl("data:image/png;base64,+/8="))
        assertArrayEquals(expected, decodeChatImageDataUrl("data:image/png;base64,%2B%2F8%3D"))
    }

    @Test
    fun decodesPercentEncodedUtf8SvgAndPreservesLiteralPlus() {
        assertEquals(
            "<svg>中+</svg>",
            decodeChatImageDataUrl("data:image/svg+xml;charset=utf-8,%3Csvg%3E%E4%B8%AD+%3C/svg%3E")
                .toString(Charsets.UTF_8),
        )
    }

    @Test
    fun rejectsMalformedAndNonImageDataUrls() {
        listOf(
            "data:image/png;base64",
            "data:text/plain;base64,AQID",
            "data:image/png;base64,%%%",
            "data:image/png,%0",
            "data:image/png,%GG",
        ).forEach { value ->
            assertThrows(value, IllegalArgumentException::class.java) { decodeChatImageDataUrl(value) }
        }
    }

    @Test
    fun appliesByteLimitAfterBase64AndPercentDecoding() {
        assertArrayEquals(byteArrayOf(1, 2, 3), decodeChatImageDataUrl("data:image/png;base64,AQID", maxBytes = 3))
        assertArrayEquals(byteArrayOf(1, 2, 3), decodeChatImageDataUrl("data:image/png,%01%02%03", maxBytes = 3))
        assertThrows(IllegalArgumentException::class.java) {
            decodeChatImageDataUrl("data:image/png;base64,AQID", maxBytes = 2)
        }
        assertThrows(IllegalArgumentException::class.java) {
            decodeChatImageDataUrl("data:image/png,%01%02%03", maxBytes = 2)
        }
    }

    @Test
    fun limitsStreamReadsEvenWithoutAContentLength() {
        assertArrayEquals(byteArrayOf(1, 2, 3), readChatImageBytes(ByteArrayInputStream(byteArrayOf(1, 2, 3)), maxBytes = 3))
        assertThrows(IllegalArgumentException::class.java) {
            readChatImageBytes(ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)), maxBytes = 3)
        }
    }

    @Test
    fun propagatesCancellationDuringStreamReads() {
        assertThrows(CancellationException::class.java) {
            readChatImageBytes(ByteArrayInputStream(byteArrayOf(1))) { throw CancellationException("离开预览") }
        }
    }

    @Test
    fun samplesLargeAndPanoramicImagesBeforeThumbnailDecode() {
        assertEquals(1, bitmapThumbnailSampleSize(64, 48))
        assertEquals(32, bitmapThumbnailSampleSize(8_192, 4_096))
        assertEquals(64, bitmapThumbnailSampleSize(16_384, 128))
        assertEquals(4, bitmapThumbnailSampleSize(513, 128))
    }

    @Test
    fun readsServerFilesOnlyThroughTheProvidedApi() = runBlocking {
        var requestedPath: String? = null
        val loader = ChatImageLoader { path ->
            requestedPath = path
            byteArrayOf(1, 2, 3)
        }
        assertArrayEquals(byteArrayOf(1, 2, 3), loader.loadBytes(ChatImageSource.ServerFile("/workspace/image.png")))
        assertEquals("/workspace/image.png", requestedPath)
        assertArrayEquals(byteArrayOf(1, 2, 3), loader.loadBytes(ChatImageSource.Url("data:image/png;base64,AQID")))
    }

    @Test
    fun rejectsFileReferencesAndDevicePathsWithoutGuessing() {
        val loader = ChatImageLoader { error("不应读取服务端文件") }
        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking { loader.loadBytes(ChatImageSource.FileReference("file-1")) }
        }
        assertTrue(error.message.orEmpty().contains("未提供此图片的可读取地址"))
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { loader.loadBytes(ChatImageSource.Url("file:///device/photo.png")) }
        }
    }

    @Test
    fun propagatesServerReadCancellation() {
        val loader = ChatImageLoader { throw CancellationException("断开连接") }
        assertThrows(CancellationException::class.java) {
            runBlocking { loader.loadBytes(ChatImageSource.ServerFile("/workspace/image.png")) }
        }
    }

    @Test
    fun boundsConcurrentLoadsAndCancelsWaitingRequests() = runBlocking {
        val entered = AtomicInteger()
        val bothEntered = CompletableDeferred<Unit>()
        val loader = ChatImageLoader {
            if (entered.incrementAndGet() == 2) bothEntered.complete(Unit)
            awaitCancellation()
        }
        val requests = List(2) { index ->
            async(Dispatchers.IO) { loader.loadPreview(ChatImageSource.ServerFile("/image-$index.png")) }
        }
        withTimeout(5_000) { bothEntered.await() }
        val waiting = async(start = CoroutineStart.UNDISPATCHED) {
            loader.loadPreview(ChatImageSource.ServerFile("/waiting.png"))
        }
        waiting.cancel()
        assertThrows(CancellationException::class.java) { runBlocking { waiting.await() } }
        requests.forEach { it.cancel() }
        requests.forEach { request ->
            assertThrows(CancellationException::class.java) { runBlocking { request.await() } }
        }
        assertEquals(2, entered.get())
    }

    @Test
    fun rejectsOversizedServerResponses() {
        val loader = ChatImageLoader { ByteArray(16 * 1024 * 1024 + 1) }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { loader.loadBytes(ChatImageSource.ServerFile("/workspace/image.png")) }
        }
    }

    @Test
    fun downloadsHttpImageAndRejectsFailureStatus() = withServer { server, baseUrl ->
        server.createContext("/image") { exchange ->
            exchange.sendResponseHeaders(200, 3)
            exchange.responseBody.use { it.write(byteArrayOf(1, 2, 3)) }
        }
        server.createContext("/missing") { exchange ->
            exchange.sendResponseHeaders(404, -1)
            exchange.close()
        }
        val loader = ChatImageLoader { error("不应读取服务端文件") }
        assertArrayEquals(byteArrayOf(1, 2, 3), runBlocking { loader.loadBytes(ChatImageSource.Url("$baseUrl/image")) })
        val error = assertThrows(IllegalArgumentException::class.java) {
            runBlocking { loader.loadBytes(ChatImageSource.Url("$baseUrl/missing")) }
        }
        assertTrue(error.message.orEmpty().contains("404"))
    }

    @Test
    fun rejectsOversizedHttpContentLengthBeforeReadingBody() = withServer { server, baseUrl ->
        val release = CountDownLatch(1)
        server.createContext("/large") { exchange ->
            exchange.sendResponseHeaders(200, 16 * 1024 * 1024 + 1L)
            exchange.responseBody.flush()
            release.await(5, TimeUnit.SECONDS)
            exchange.close()
        }
        val loader = ChatImageLoader { error("不应读取服务端文件") }
        try {
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { loader.loadBytes(ChatImageSource.Url("$baseUrl/large")) }
            }
        } finally {
            release.countDown()
        }
    }

    @Test
    fun cancelsAnHttpRequestWhileWaitingForResponse() = withServer { server, baseUrl ->
        val requested = CountDownLatch(1)
        val release = CountDownLatch(1)
        server.createContext("/waiting") { exchange ->
            requested.countDown()
            release.await(5, TimeUnit.SECONDS)
            exchange.close()
        }
        val loader = ChatImageLoader { error("不应读取服务端文件") }
        try {
            runBlocking {
                val request = async(Dispatchers.IO) {
                    loader.loadBytes(ChatImageSource.Url("$baseUrl/waiting"))
                }
                assertTrue(requested.await(5, TimeUnit.SECONDS))
                request.cancel()
                assertThrows(CancellationException::class.java) { runBlocking { request.await() } }
            }
        } finally {
            release.countDown()
        }
    }

    private fun withServer(block: (HttpServer, String) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.start()
        try {
            block(server, "http://127.0.0.1:${server.address.port}")
        } finally {
            server.stop(0)
        }
    }
}
