package com.hebo.codex.data.remote

import org.java_websocket.exceptions.InvalidHandshakeException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.net.SocketFactory
import kotlin.concurrent.thread

class AppServerWebSocketTest {
    @Test
    fun startsAfterCreationAndUsesProvidedTunnelSocket() {
        val loopback = InetAddress.getLoopbackAddress()
        val server = ServerSocket(0, 1, loopback)
        val request = AtomicReference<String>()
        val serverFailure = AtomicReference<Throwable>()
        val releaseServer = CountDownLatch(1)
        val serverThread = thread(isDaemon = true, name = "websocket-test-server") {
            try {
                server.accept().use { socket ->
                    socket.soTimeout = TEST_TIMEOUT_MILLIS
                    val handshake = readTestHttpHeaders(socket.getInputStream())
                    request.set(handshake)
                    val key = handshake.lineSequence()
                        .single { it.startsWith("Sec-WebSocket-Key:", ignoreCase = true) }
                        .substringAfter(':')
                        .trim()
                    val accept = Base64.getEncoder().encodeToString(
                        MessageDigest.getInstance("SHA-1")
                            .digest((key + WEBSOCKET_GUID).toByteArray(StandardCharsets.ISO_8859_1)),
                    )
                    socket.getOutputStream().apply {
                        write(
                            buildString {
                                append("HTTP/1.1 101 Switching Protocols\r\n")
                                append("Upgrade: websocket\r\n")
                                append("Connection: Upgrade\r\n")
                                append("Sec-WebSocket-Accept: $accept\r\n\r\n")
                            }.toByteArray(StandardCharsets.ISO_8859_1),
                        )
                        flush()
                    }
                    releaseServer.await(TEST_TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS)
                }
            } catch (error: Throwable) {
                serverFailure.set(error)
            }
        }
        val socketCreations = AtomicInteger()
        val socketFactory = object : SocketFactory() {
            override fun createSocket(): Socket {
                socketCreations.incrementAndGet()
                return Socket(loopback, server.localPort)
            }

            override fun createSocket(host: String, port: Int): Socket = error("unexpected overload")

            override fun createSocket(
                host: String,
                port: Int,
                localHost: InetAddress,
                localPort: Int,
            ): Socket = error("unexpected overload")

            override fun createSocket(host: InetAddress, port: Int): Socket = error("unexpected overload")

            override fun createSocket(
                address: InetAddress,
                port: Int,
                localAddress: InetAddress,
                localPort: Int,
            ): Socket = error("unexpected overload")
        }
        val opened = CountDownLatch(1)
        val failure = AtomicReference<Throwable>()
        val webSocket = JavaWebSocketAppServerWebSocketFactory(
            endpoint = "ws://target.invalid/app?client=android",
            token = "server-secret",
            socketFactory = socketFactory,
        ).create(
            object : AppServerWebSocketListener {
                override fun onOpen(webSocket: AppServerWebSocket) = opened.countDown()
                override fun onMessage(webSocket: AppServerWebSocket, text: String) = Unit
                override fun onBinaryMessage(webSocket: AppServerWebSocket) = Unit
                override fun onClosed(webSocket: AppServerWebSocket, code: Int, reason: String) = Unit

                override fun onFailure(
                    webSocket: AppServerWebSocket,
                    error: Throwable,
                    statusCode: Int?,
                ) {
                    failure.compareAndSet(null, error)
                }
            },
        )

        try {
            assertEquals(0, socketCreations.get())
            webSocket.start()
            assertTrue("WebSocket 未建立", opened.await(TEST_TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS))
            assertEquals(1, socketCreations.get())
            assertNull(failure.get())
            assertTrue(request.get().startsWith("GET /app?client=android HTTP/1.1\r\n"))
            assertTrue(request.get().contains("Authorization: Bearer server-secret\r\n"))
        } finally {
            webSocket.cancel()
            releaseServer.countDown()
            server.close()
            serverThread.join(TEST_TIMEOUT_MILLIS.toLong())
        }
        assertNull(serverFailure.get())
    }

    @Test
    fun extractsRejectedHandshakeStatusCode() {
        val error = InvalidHandshakeException(
            "Invalid status code received: 401 Status line: HTTP/1.1 401 Unauthorized",
        )

        assertEquals(401, rejectedHandshakeStatusCode(error))
    }

    @Test
    fun ignoresNonHandshakeErrors() {
        assertNull(rejectedHandshakeStatusCode(IllegalStateException("failed")))
    }
}

private fun readTestHttpHeaders(input: InputStream): String {
    val result = StringBuilder()
    var suffix = 0
    while (suffix < 4) {
        val value = input.read()
        check(value >= 0) { "连接在握手完成前关闭" }
        result.append(value.toChar())
        suffix = when {
            suffix == 0 && value == '\r'.code -> 1
            suffix == 1 && value == '\n'.code -> 2
            suffix == 2 && value == '\r'.code -> 3
            suffix == 3 && value == '\n'.code -> 4
            value == '\r'.code -> 1
            else -> 0
        }
    }
    return result.toString()
}

private const val TEST_TIMEOUT_MILLIS = 5_000
private const val WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
