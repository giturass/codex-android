package com.hebo.codex.data.remote

import com.hebo.codex.BuildConfig
import com.hebo.codex.domain.ConnectionStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.CoroutineContext
import kotlin.random.Random

class AppServerConnection(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val endpoint: String,
    private val token: String,
    parentContext: CoroutineContext,
) {
    private val scope = CoroutineScope(parentContext + SupervisorJob(parentContext[Job]))
    private val requestIds = AtomicLong(1)
    private val pendingRequests = ConcurrentHashMap<Long, CompletableDeferred<JsonElement>>()
    private val ready = CompletableDeferred<Unit>()
    private val started = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val failed = AtomicBoolean(false)
    private val eventChannel = Channel<ServerEvent>(capacity = EVENT_QUEUE_CAPACITY)
    private val mutableStatus = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)

    private lateinit var webSocket: WebSocket

    val events: Flow<ServerEvent> = eventChannel.receiveAsFlow()
    val status: StateFlow<ConnectionStatus> = mutableStatus.asStateFlow()

    suspend fun connect() {
        check(started.compareAndSet(false, true)) { "连接对象只能启动一次" }
        mutableStatus.value = ConnectionStatus.Connecting
        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $token")
            .build()
        webSocket = okHttpClient.newWebSocket(request, Listener())
        withTimeout(CONNECT_TIMEOUT_MILLIS) { ready.await() }
    }

    suspend fun request(method: String, params: JsonObject? = null): JsonElement {
        ready.await()
        var attempt = 0
        while (true) {
            try {
                return requestOnce(method, params)
            } catch (error: RpcException) {
                attempt += 1
                if (error.code != SERVER_OVERLOADED || attempt >= MAX_OVERLOAD_ATTEMPTS) throw error
                delay((OVERLOAD_BASE_DELAY_MILLIS shl (attempt - 1)) + Random.nextLong(100))
            }
        }
    }

    fun respond(id: JsonPrimitive, result: JsonObject) {
        send(
            buildJsonObject {
                put("id", id)
                put("result", result)
            },
        )
    }

    fun respondError(id: JsonPrimitive, code: Int, message: String) {
        send(
            buildJsonObject {
                put("id", id)
                put(
                    "error",
                    buildJsonObject {
                        put("code", code)
                        put("message", message)
                    },
                )
            },
        )
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        val error = ProtocolException("连接已关闭")
        if (::webSocket.isInitialized) webSocket.close(NORMAL_CLOSURE, "client closed")
        if (!ready.isCompleted) ready.completeExceptionally(error)
        failPending(error)
        eventChannel.close()
        mutableStatus.value = ConnectionStatus.Disconnected
        scope.cancel()
    }

    private suspend fun initialize() {
        val result = requestOnce(
            method = "initialize",
            params = buildJsonObject {
                put(
                    "clientInfo",
                    buildJsonObject {
                        put("name", "codex_android")
                        put("title", "Codex Android")
                        put("version", BuildConfig.VERSION_NAME)
                    },
                )
            },
        )
        if (result !is JsonObject) throw ProtocolException("initialize 响应不是对象")
        result.requiredString("userAgent")
        result.requiredString("codexHome")
        result.requiredString("platformFamily")
        result.requiredString("platformOs")
        send(
            buildJsonObject {
                put("method", "initialized")
                put("params", buildJsonObject {})
            },
        )
        mutableStatus.value = ConnectionStatus.Connected
        ready.complete(Unit)
    }

    private suspend fun requestOnce(method: String, params: JsonObject?): JsonElement {
        val id = requestIds.getAndIncrement()
        val response = CompletableDeferred<JsonElement>()
        pendingRequests[id] = response
        try {
            send(
                buildJsonObject {
                    put("method", method)
                    put("id", id)
                    if (params != null) put("params", params)
                },
            )
            return withTimeout(REQUEST_TIMEOUT_MILLIS) { response.await() }
        } finally {
            pendingRequests.remove(id)
        }
    }

    private fun send(message: JsonObject) {
        check(::webSocket.isInitialized) { "WebSocket 尚未建立" }
        if (!webSocket.send(json.encodeToString(JsonObject.serializer(), message))) {
            throw ProtocolException("WebSocket 发送失败")
        }
    }

    private fun receive(text: String) {
        val message = try {
            json.parseToJsonElement(text).jsonObject
        } catch (error: Exception) {
            failConnection(ProtocolException("服务端发送了无效 JSON-RPC 消息", error))
            return
        }

        val method = message["method"]?.jsonPrimitive?.content
        val id = message["id"] as? JsonPrimitive
        if (method != null) {
            val params = (message["params"] as? JsonObject) ?: buildJsonObject {}
            val event = if (id == null) {
                ServerEvent.Notification(method, params)
            } else {
                ServerEvent.Request(id, method, params)
            }
            if (eventChannel.trySend(event).isFailure) {
                failConnection(ProtocolException("服务端事件队列已满"))
            }
            return
        }

        val responseId = id?.longOrNull
        if (responseId == null) {
            failConnection(ProtocolException("JSON-RPC 响应缺少整数 id"))
            return
        }
        val pending = pendingRequests[responseId] ?: return
        val error = message["error"] as? JsonObject
        if (error != null) {
            pending.completeExceptionally(
                RpcException(
                    code = error["code"]?.jsonPrimitive?.int ?: -1,
                    message = error["message"]?.jsonPrimitive?.content ?: "未知服务端错误",
                    data = error["data"],
                ),
            )
        } else {
            pending.complete(message["result"] ?: JsonNull)
        }
    }

    private fun failConnection(error: Throwable) {
        if (closed.get()) return
        if (!failed.compareAndSet(false, true)) return
        val message = error.message ?: "连接失败"
        mutableStatus.value = ConnectionStatus.Failed(message)
        if (!ready.isCompleted) ready.completeExceptionally(error)
        failPending(error)
        eventChannel.close(error)
        if (::webSocket.isInitialized) webSocket.cancel()
    }

    private fun failPending(error: Throwable) {
        pendingRequests.values.forEach { it.completeExceptionally(error) }
        pendingRequests.clear()
    }

    private inner class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            scope.launch {
                try {
                    initialize()
                } catch (error: Throwable) {
                    failConnection(error)
                }
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            receive(text)
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            failConnection(ProtocolException("服务端发送了不受支持的二进制消息"))
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, reason)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            val error = ProtocolException("连接已关闭：$reason")
            if (mutableStatus.value !is ConnectionStatus.Failed) {
                mutableStatus.value = ConnectionStatus.Disconnected
            }
            if (!ready.isCompleted) ready.completeExceptionally(error)
            failPending(error)
            eventChannel.close(error)
        }

        override fun onFailure(webSocket: WebSocket, error: Throwable, response: Response?) {
            if (closed.get()) return
            val statusCode = response?.code?.let { "HTTP $it" }
            val message = statusCode ?: error.message ?: "WebSocket 连接失败"
            failConnection(ProtocolException(message, error))
        }
    }

    private val JsonPrimitive.longOrNull: Long?
        get() = content.toLongOrNull()

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 12_000L
        const val REQUEST_TIMEOUT_MILLIS = 30_000L
        const val NORMAL_CLOSURE = 1000
        const val SERVER_OVERLOADED = -32001
        const val MAX_OVERLOAD_ATTEMPTS = 3
        const val OVERLOAD_BASE_DELAY_MILLIS = 250L
        const val EVENT_QUEUE_CAPACITY = 256
    }
}
