package com.hebo.codex.data.remote

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext

internal class ProxyAppServerWebSocketFactory(
    private val endpoint: String,
    private val token: String,
    private val proxy: ResolvedProxyConnection,
    private val tunnelFactory: ProxyTunnelFactory,
    private val parentContext: CoroutineContext,
) : AppServerWebSocketFactory {
    override fun open(listener: AppServerWebSocketListener): AppServerWebSocket =
        StreamAppServerWebSocket(
            target = WebSocketTarget.parse(endpoint),
            token = token,
            proxy = proxy,
            tunnelFactory = tunnelFactory,
            listener = listener,
            parentContext = parentContext,
        ).also(StreamAppServerWebSocket::start)
}

private class StreamAppServerWebSocket(
    private val target: WebSocketTarget,
    private val token: String,
    private val proxy: ResolvedProxyConnection,
    private val tunnelFactory: ProxyTunnelFactory,
    private val listener: AppServerWebSocketListener,
    parentContext: CoroutineContext,
) : AppServerWebSocket {
    private val scope = CoroutineScope(parentContext + SupervisorJob(parentContext[Job]) + Dispatchers.IO)
    private val socket = AtomicReference<Socket?>()
    private val output = AtomicReference<OutputStream?>()
    private val closed = AtomicBoolean(false)
    private val closeSent = AtomicBoolean(false)
    private val writeLock = Any()

    fun start() {
        scope.launch {
            try {
                val rawSocket = tunnelFactory.open(target, proxy)
                if (closed.get()) {
                    rawSocket.close()
                    return@launch
                }
                val connectedSocket = try {
                    wrapTargetTls(rawSocket, target)
                } catch (error: Throwable) {
                    rawSocket.close()
                    throw error
                }
                socket.set(connectedSocket)
                val inputStream = connectedSocket.getInputStream()
                val outputStream = connectedSocket.getOutputStream()
                performWebSocketHandshake(inputStream, outputStream, target, token)
                output.set(outputStream)
                listener.onOpen(this@StreamAppServerWebSocket)
                launchPingLoop()
                readLoop(inputStream)
            } catch (error: Throwable) {
                if (!closed.get()) listener.onFailure(this@StreamAppServerWebSocket, error)
                closeResources()
            }
        }
    }

    override fun send(text: String): Boolean = sendFrame(OPCODE_TEXT, text.toByteArray(StandardCharsets.UTF_8))

    override fun close(code: Int, reason: String): Boolean {
        if (!closeSent.compareAndSet(false, true)) return false
        val reasonBytes = reason.toByteArray(StandardCharsets.UTF_8).take(123).toByteArray()
        val payload = ByteBuffer.allocate(2 + reasonBytes.size)
            .putShort(code.toShort())
            .put(reasonBytes)
            .array()
        val sent = sendFrame(OPCODE_CLOSE, payload)
        closed.set(true)
        closeResources()
        return sent
    }

    override fun cancel() {
        if (!closed.compareAndSet(false, true)) return
        closeResources()
    }

    private fun launchPingLoop() {
        scope.launch {
            while (isActive && !closed.get()) {
                delay(PING_INTERVAL_MILLIS)
                if (!closed.get()) sendFrame(OPCODE_PING, ByteArray(0))
            }
        }
    }

    private fun readLoop(input: InputStream) {
        var fragmentedOpcode: Int? = null
        var fragments: ByteArrayOutputStream? = null
        while (!closed.get()) {
            val frame = readWebSocketFrame(input)
            when (frame.opcode) {
                OPCODE_TEXT, OPCODE_BINARY -> {
                    require(fragmentedOpcode == null) { "收到新的数据帧时上一条消息尚未结束" }
                    if (frame.final) {
                        deliver(frame.opcode, frame.payload)
                    } else {
                        fragmentedOpcode = frame.opcode
                        fragments = ByteArrayOutputStream().apply { write(frame.payload) }
                    }
                }

                OPCODE_CONTINUATION -> {
                    val opcode = fragmentedOpcode ?: throw ProtocolException("收到没有起始帧的 WebSocket continuation")
                    val buffer = requireNotNull(fragments)
                    if (buffer.size() + frame.payload.size > MAX_MESSAGE_BYTES) {
                        throw ProtocolException("WebSocket 消息超过大小限制")
                    }
                    buffer.write(frame.payload)
                    if (frame.final) {
                        deliver(opcode, buffer.toByteArray())
                        fragmentedOpcode = null
                        fragments = null
                    }
                }

                OPCODE_PING -> sendFrame(OPCODE_PONG, frame.payload)
                OPCODE_PONG -> Unit
                OPCODE_CLOSE -> {
                    val (code, reason) = parseClosePayload(frame.payload)
                    listener.onClosing(this, code, reason)
                    if (closeSent.compareAndSet(false, true)) sendFrame(OPCODE_CLOSE, frame.payload)
                    closed.set(true)
                    closeResources()
                    listener.onClosed(this, code, reason)
                    return
                }

                else -> throw ProtocolException("收到未知 WebSocket opcode：${frame.opcode}")
            }
        }
    }

    private fun deliver(opcode: Int, payload: ByteArray) {
        if (opcode == OPCODE_BINARY) {
            listener.onBinaryMessage(this)
            return
        }
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val text = try {
            decoder.decode(ByteBuffer.wrap(payload)).toString()
        } catch (error: Exception) {
            throw ProtocolException("WebSocket 文本消息不是有效 UTF-8", error)
        }
        listener.onMessage(this, text)
    }

    private fun sendFrame(opcode: Int, payload: ByteArray): Boolean {
        val stream = output.get() ?: return false
        return try {
            synchronized(writeLock) {
                writeWebSocketFrame(stream, opcode, payload)
                stream.flush()
            }
            true
        } catch (error: Throwable) {
            if (!closed.get()) listener.onFailure(this, error)
            closeResources()
            false
        }
    }

    private fun closeResources() {
        output.set(null)
        runCatching { socket.getAndSet(null)?.close() }
        scope.cancel()
    }
}

internal fun performWebSocketHandshake(
    input: InputStream,
    output: OutputStream,
    target: WebSocketTarget,
    token: String,
) {
    val nonce = ByteArray(16).also(SecureRandom()::nextBytes)
    val key = Base64.getEncoder().encodeToString(nonce)
    val request = buildString {
        append("GET ").append(target.requestTarget).append(" HTTP/1.1\r\n")
        append("Host: ").append(target.hostHeader).append("\r\n")
        append("Upgrade: websocket\r\n")
        append("Connection: Upgrade\r\n")
        append("Sec-WebSocket-Key: ").append(key).append("\r\n")
        append("Sec-WebSocket-Version: 13\r\n")
        append("Authorization: Bearer ").append(token).append("\r\n")
        append("\r\n")
    }
    output.write(request.toByteArray(StandardCharsets.ISO_8859_1))
    output.flush()

    val rawResponse = readHttpHeaders(input)
    val lines = rawResponse.split("\r\n")
    val statusCode = lines.firstOrNull()?.split(' ')?.getOrNull(1)?.toIntOrNull()
        ?: throw ProtocolException("WebSocket 握手响应无效")
    if (statusCode != 101) throw ConnectionException("WebSocket 握手被拒绝：HTTP $statusCode", rejected = true)
    val headers = lines.drop(1).mapNotNull { line ->
        val separator = line.indexOf(':')
        if (separator <= 0) null else line.substring(0, separator).trim().lowercase(Locale.ROOT) to
            line.substring(separator + 1).trim()
    }.groupBy({ it.first }, { it.second })
    requireHeaderToken(headers, "upgrade", "websocket")
    requireHeaderToken(headers, "connection", "upgrade")
    val expectedAccept = Base64.getEncoder().encodeToString(
        MessageDigest.getInstance("SHA-1")
            .digest((key + WEBSOCKET_GUID).toByteArray(StandardCharsets.ISO_8859_1)),
    )
    if (headers["sec-websocket-accept"]?.singleOrNull() != expectedAccept) {
        throw ProtocolException("WebSocket 握手校验失败")
    }
}

internal data class WebSocketFrame(
    val final: Boolean,
    val opcode: Int,
    val payload: ByteArray,
)

internal fun readWebSocketFrame(input: InputStream): WebSocketFrame {
    val first = readFrameByte(input)
    val second = readFrameByte(input)
    val final = first and 0x80 != 0
    val rsv = first and 0x70
    val opcode = first and 0x0f
    if (rsv != 0) throw ProtocolException("WebSocket 帧包含未协商的扩展位")
    if (second and 0x80 != 0) throw ProtocolException("服务端 WebSocket 帧不应使用掩码")
    var length = (second and 0x7f).toLong()
    if (length == 126L) {
        length = ((readFrameByte(input) shl 8) or readFrameByte(input)).toLong()
    } else if (length == 127L) {
        length = 0
        repeat(8) {
            val next = readFrameByte(input)
            if (it == 0 && next and 0x80 != 0) throw ProtocolException("WebSocket 帧长度无效")
            length = (length shl 8) or next.toLong()
        }
    }
    val control = opcode >= 0x8
    if (control && (!final || length > 125)) throw ProtocolException("WebSocket 控制帧无效")
    if (length > MAX_MESSAGE_BYTES) throw ProtocolException("WebSocket 帧超过大小限制")
    return WebSocketFrame(final, opcode, readFrameFully(input, length.toInt()))
}

internal fun writeWebSocketFrame(output: OutputStream, opcode: Int, payload: ByteArray) {
    require(payload.size <= MAX_MESSAGE_BYTES) { "WebSocket 消息超过大小限制" }
    output.write(0x80 or opcode)
    when {
        payload.size <= 125 -> output.write(0x80 or payload.size)
        payload.size <= 0xffff -> {
            output.write(0x80 or 126)
            output.write(payload.size ushr 8)
            output.write(payload.size and 0xff)
        }
        else -> {
            output.write(0x80 or 127)
            val length = payload.size.toLong()
            for (shift in 56 downTo 0 step 8) output.write((length ushr shift).toInt() and 0xff)
        }
    }
    val mask = ByteArray(4).also(SecureRandom()::nextBytes)
    output.write(mask)
    payload.forEachIndexed { index, byte ->
        output.write((byte.toInt() xor mask[index % 4].toInt()) and 0xff)
    }
}

private fun requireHeaderToken(headers: Map<String, List<String>>, name: String, token: String) {
    val present = headers[name].orEmpty().flatMap { it.split(',') }.any { it.trim().equals(token, ignoreCase = true) }
    if (!present) throw ProtocolException("WebSocket 握手缺少有效的 $name 响应头")
}

private fun parseClosePayload(payload: ByteArray): Pair<Int, String> {
    if (payload.isEmpty()) return 1000 to ""
    if (payload.size == 1) throw ProtocolException("WebSocket close 帧负载无效")
    val code = ((payload[0].toInt() and 0xff) shl 8) or (payload[1].toInt() and 0xff)
    val reason = payload.copyOfRange(2, payload.size).toString(StandardCharsets.UTF_8)
    return code to reason
}

private fun readFrameByte(input: InputStream): Int = input.read().also {
    if (it < 0) throw EOFException("WebSocket 连接已关闭")
}

private fun readFrameFully(input: InputStream, length: Int): ByteArray = ByteArray(length).also { bytes ->
    var offset = 0
    while (offset < bytes.size) {
        val count = input.read(bytes, offset, bytes.size - offset)
        if (count < 0) throw EOFException("WebSocket 帧尚未完成时连接已关闭")
        offset += count
    }
}

private fun List<Byte>.toByteArray(): ByteArray = ByteArray(size) { this[it] }

private const val WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
private const val OPCODE_CONTINUATION = 0x0
private const val OPCODE_TEXT = 0x1
private const val OPCODE_BINARY = 0x2
private const val OPCODE_CLOSE = 0x8
private const val OPCODE_PING = 0x9
private const val OPCODE_PONG = 0xa
private const val MAX_MESSAGE_BYTES = 64 * 1024 * 1024
private const val PING_INTERVAL_MILLIS = 20_000L
