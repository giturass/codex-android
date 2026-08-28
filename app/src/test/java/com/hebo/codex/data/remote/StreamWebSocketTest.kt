package com.hebo.codex.data.remote

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

class StreamWebSocketTest {
    @Test
    fun clientFramesAreMaskedAndRoundTrip() {
        val output = ByteArrayOutputStream()
        writeWebSocketFrame(output, 1, "hello".toByteArray())
        val bytes = output.toByteArray()

        assertEquals(0x81, bytes[0].toInt() and 0xff)
        assertEquals(0x80 or 5, bytes[1].toInt() and 0xff)
        val decoded = ByteArray(5) { index ->
            (bytes[6 + index].toInt() xor bytes[2 + index % 4].toInt()).toByte()
        }
        assertArrayEquals("hello".toByteArray(), decoded)
    }

    @Test
    fun validatesWebSocketUpgradeAndKeepsServerTokenOutOfProxyHandshake() {
        val request = ByteArrayOutputStream()
        val response = LazyHandshakeInput(request)
        performWebSocketHandshake(
            input = response,
            output = request,
            target = WebSocketTarget.parse("wss://codex.example/app?client=android"),
            token = "server-secret",
        )

        val text = request.toString(StandardCharsets.ISO_8859_1.name())
        assertTrue(text.startsWith("GET /app?client=android HTTP/1.1\r\n"))
        assertTrue(text.contains("Authorization: Bearer server-secret\r\n"))
    }
}

private class LazyHandshakeInput(
    private val request: ByteArrayOutputStream,
) : InputStream() {
    private var delegate: ByteArrayInputStream? = null

    override fun read(): Int {
        val input = delegate ?: createResponse().also { delegate = it }
        return input.read()
    }

    private fun createResponse(): ByteArrayInputStream {
        val text = request.toString(StandardCharsets.ISO_8859_1.name())
        val key = text.lineSequence()
            .single { it.startsWith("Sec-WebSocket-Key:") }
            .substringAfter(':')
            .trim()
        val accept = Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-1")
                .digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray()),
        )
        return ByteArrayInputStream(
            buildString {
                append("HTTP/1.1 101 Switching Protocols\r\n")
                append("Upgrade: websocket\r\n")
                append("Connection: Upgrade\r\n")
                append("Sec-WebSocket-Accept: $accept\r\n\r\n")
            }.toByteArray(StandardCharsets.ISO_8859_1),
        )
    }
}
