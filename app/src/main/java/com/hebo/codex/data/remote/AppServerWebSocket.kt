package com.hebo.codex.data.remote

import org.java_websocket.client.WebSocketClient
import org.java_websocket.drafts.Draft_6455
import org.java_websocket.exceptions.InvalidHandshakeException
import org.java_websocket.framing.CloseFrame
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import java.nio.ByteBuffer
import java.util.Collections
import javax.net.SocketFactory

internal interface AppServerWebSocket {
    fun start()
    fun send(text: String): Boolean
    fun setResponseTimeout(timeoutMillis: Long)
    fun close(code: Int, reason: String): Boolean
    fun cancel()
}

internal interface AppServerWebSocketListener {
    fun onOpen(webSocket: AppServerWebSocket)
    fun onMessage(webSocket: AppServerWebSocket, text: String)
    fun onBinaryMessage(webSocket: AppServerWebSocket)
    fun onClosed(webSocket: AppServerWebSocket, code: Int, reason: String)
    fun onFailure(webSocket: AppServerWebSocket, error: Throwable, statusCode: Int? = null)
}

internal fun interface AppServerWebSocketFactory {
    fun create(listener: AppServerWebSocketListener): AppServerWebSocket
}

internal class JavaWebSocketAppServerWebSocketFactory(
    private val endpoint: String,
    private val token: String,
    private val socketFactory: SocketFactory? = null,
) : AppServerWebSocketFactory {
    override fun create(listener: AppServerWebSocketListener): AppServerWebSocket =
        JavaWebSocketAppServerWebSocket(
            endpoint = endpoint,
            token = token,
            socketFactory = socketFactory,
            listener = listener,
        )
}

private class JavaWebSocketAppServerWebSocket(
    endpoint: String,
    token: String,
    socketFactory: SocketFactory?,
    private val listener: AppServerWebSocketListener,
) : AppServerWebSocket {
    private val delegate = object : WebSocketClient(
        URI(endpoint),
        Draft_6455(Collections.emptyList(), MAX_MESSAGE_BYTES),
        mapOf("Authorization" to "Bearer $token"),
        CONNECT_TIMEOUT_MILLIS,
    ) {
        override fun onOpen(handshakedata: ServerHandshake) {
            listener.onOpen(this@JavaWebSocketAppServerWebSocket)
        }

        override fun onMessage(message: String) {
            listener.onMessage(this@JavaWebSocketAppServerWebSocket, message)
        }

        override fun onMessage(bytes: ByteBuffer) {
            listener.onBinaryMessage(this@JavaWebSocketAppServerWebSocket)
        }

        override fun onClose(code: Int, reason: String, remote: Boolean) {
            listener.onClosed(this@JavaWebSocketAppServerWebSocket, code, reason)
        }

        override fun onError(error: Exception) {
            listener.onFailure(
                this@JavaWebSocketAppServerWebSocket,
                error,
                rejectedHandshakeStatusCode(error),
            )
        }
    }.apply {
        setConnectionLostTimeout(PING_INTERVAL_SECONDS)
        setTcpNoDelay(true)
        socketFactory?.let(::setSocketFactory)
    }

    override fun start() = delegate.connect()

    override fun send(text: String): Boolean {
        if (!delegate.isOpen) return false
        return runCatching {
            delegate.send(text)
            true
        }.getOrDefault(false)
    }

    override fun setResponseTimeout(timeoutMillis: Long) {
        // 大消息与 ping 共用发送队列，等待响应期间不能让心跳先判定断连
        val timeoutSeconds = if (timeoutMillis <= DEFAULT_REQUEST_TIMEOUT_MILLIS) {
            PING_INTERVAL_SECONDS
        } else {
            ((timeoutMillis + 999) / 1_000).toInt()
        }
        delegate.setConnectionLostTimeout(timeoutSeconds)
    }

    override fun close(code: Int, reason: String): Boolean {
        if (delegate.isClosing || delegate.isClosed) return false
        return runCatching {
            delegate.close(code, reason)
            true
        }.getOrDefault(false)
    }

    override fun cancel() {
        runCatching { delegate.socket?.close() }
        delegate.closeConnection(CloseFrame.ABNORMAL_CLOSE, "client cancelled")
    }
}

internal fun rejectedHandshakeStatusCode(error: Throwable): Int? {
    if (error !is InvalidHandshakeException) return null
    return INVALID_STATUS_CODE.find(error.message.orEmpty())?.groupValues?.get(1)?.toIntOrNull()
}

private val INVALID_STATUS_CODE = Regex("^Invalid status code received: ([0-9]{3})\\b")
private const val CONNECT_TIMEOUT_MILLIS = 10_000
private const val MAX_MESSAGE_BYTES = 64 * 1024 * 1024
private const val PING_INTERVAL_SECONDS = 20
