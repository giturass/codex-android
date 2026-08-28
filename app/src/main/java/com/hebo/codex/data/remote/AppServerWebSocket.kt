package com.hebo.codex.data.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

internal interface AppServerWebSocket {
    fun send(text: String): Boolean
    fun close(code: Int, reason: String): Boolean
    fun cancel()
}

internal interface AppServerWebSocketListener {
    fun onOpen(webSocket: AppServerWebSocket)
    fun onMessage(webSocket: AppServerWebSocket, text: String)
    fun onBinaryMessage(webSocket: AppServerWebSocket)
    fun onClosing(webSocket: AppServerWebSocket, code: Int, reason: String)
    fun onClosed(webSocket: AppServerWebSocket, code: Int, reason: String)
    fun onFailure(webSocket: AppServerWebSocket, error: Throwable, statusCode: Int? = null)
}

internal fun interface AppServerWebSocketFactory {
    fun open(listener: AppServerWebSocketListener): AppServerWebSocket
}

internal class OkHttpAppServerWebSocketFactory(
    private val client: OkHttpClient,
    private val endpoint: String,
    private val token: String,
) : AppServerWebSocketFactory {
    override fun open(listener: AppServerWebSocketListener): AppServerWebSocket {
        lateinit var adapter: OkHttpAppServerWebSocket
        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $token")
            .build()
        val socket = client.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    listener.onOpen(adapter)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    listener.onMessage(adapter, text)
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    listener.onBinaryMessage(adapter)
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    listener.onClosing(adapter, code, reason)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    listener.onClosed(adapter, code, reason)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    listener.onFailure(adapter, t, response?.code)
                }
            },
        )
        adapter = OkHttpAppServerWebSocket(socket)
        return adapter
    }
}

private class OkHttpAppServerWebSocket(
    private val delegate: WebSocket,
) : AppServerWebSocket {
    override fun send(text: String): Boolean = delegate.send(text)

    override fun close(code: Int, reason: String): Boolean = delegate.close(code, reason)

    override fun cancel() = delegate.cancel()
}
