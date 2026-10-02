package com.hebo.codex.data.remote

import com.hebo.codex.BuildConfig
import com.hebo.codex.domain.ConnectionStatus
import com.hebo.codex.util.toUserFacingError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
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
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.CoroutineContext
import kotlin.random.Random

internal const val DEFAULT_REQUEST_TIMEOUT_MILLIS = 30_000L

class AppServerConnection internal constructor(
    private val webSocketFactory: AppServerWebSocketFactory,
    private val json: Json,
    private val connectTimeoutMillis: Long,
    parentContext: CoroutineContext,
    logWarning: (String) -> Unit = {},
) {
    private val scope = CoroutineScope(parentContext + SupervisorJob(parentContext[Job]))
    private val requestIds = AtomicLong(1)
    private val pendingRequests = ConcurrentHashMap<Long, PendingRequest>()
    private val pendingRequestsLock = Any()

    @Volatile
    private var responseTimeoutMillis = DEFAULT_REQUEST_TIMEOUT_MILLIS
    private val ready = CompletableDeferred<Unit>()
    private val started = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val failed = AtomicBoolean(false)
    private val eventConsumerActive = AtomicBoolean(false)
    private val receivedEvents = AtomicLong()
    private val deliveredEvents = AtomicLong()
    private val eventChannel = Channel<ServerEvent>(capacity = EVENT_QUEUE_CAPACITY)
    private val mutableStatus = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)

    private lateinit var webSocket: AppServerWebSocket

    internal val diagnostics = ConnectionDiagnostics(logWarning, context = {
        val state = when (mutableStatus.value) {
            ConnectionStatus.Connecting -> "connecting"
            ConnectionStatus.Connected -> "connected"
            ConnectionStatus.Disconnected -> "disconnected"
            is ConnectionStatus.Failed -> "failed"
        }
        "state=$state pending_requests=${pendingRequests.size}" +
            " event_consumer_active=${eventConsumerActive.get()}" +
            " events_received=${receivedEvents.get()} events_delivered=${deliveredEvents.get()}" +
            " max_request_timeout_ms=$responseTimeoutMillis client_closed=${closed.get()}"
    })

    val events: Flow<ServerEvent> = eventChannel.receiveAsFlow()
        .onStart { eventConsumerActive.set(true) }
        .onEach { deliveredEvents.incrementAndGet() }
        .onCompletion { eventConsumerActive.set(false) }
    val status: StateFlow<ConnectionStatus> = mutableStatus.asStateFlow()

    suspend fun connect() {
        check(started.compareAndSet(false, true)) { "连接对象只能启动一次" }
        mutableStatus.value = ConnectionStatus.Connecting
        diagnostics.setPhase("websocket")
        try {
            webSocket = webSocketFactory.create(Listener())
            webSocket.start()
            withTimeout(connectTimeoutMillis) { ready.await() }
        } catch (error: TimeoutCancellationException) {
            val connectionError = ConnectionException("连接超时", error)
            failConnection(connectionError, "connect_timeout", "timeout_ms=$connectTimeoutMillis")
            throw connectionError
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            diagnostics.failure("connect_failed", error)
            throw error
        }
    }

    suspend fun request(
        method: String,
        params: JsonObject? = null,
        timeoutMillis: Long = DEFAULT_REQUEST_TIMEOUT_MILLIS,
    ): JsonElement {
        ready.await()
        var attempt = 0
        while (true) {
            try {
                return requestOnce(method, params, timeoutMillis)
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
        terminate(abort = false)
    }

    fun abort() {
        terminate(abort = true)
    }

    private fun terminate(abort: Boolean) {
        if (!closed.compareAndSet(false, true)) return
        val error = ProtocolException("连接已关闭")
        if (::webSocket.isInitialized) {
            if (abort) {
                webSocket.cancel()
            } else {
                webSocket.close(NORMAL_CLOSURE, "client closed")
            }
        }
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
                put(
                    "capabilities",
                    buildJsonObject {
                        put("experimentalApi", true)
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
        diagnostics.setPhase("ready")
        ready.complete(Unit)
    }

    private suspend fun requestOnce(
        method: String,
        params: JsonObject?,
        timeoutMillis: Long = DEFAULT_REQUEST_TIMEOUT_MILLIS,
    ): JsonElement {
        val id = requestIds.getAndIncrement()
        val response = CompletableDeferred<JsonElement>()
        synchronized(pendingRequestsLock) {
            pendingRequests[id] = PendingRequest(response, timeoutMillis)
            updateResponseTimeout()
        }
        try {
            send(
                buildJsonObject {
                    put("method", method)
                    put("id", id)
                    if (params != null) put("params", params)
                },
            )
            return withTimeoutOrNull(timeoutMillis) { response.await() }
                ?: run {
                    if (method != "initialize") {
                        diagnostics.warning(
                            "request_timeout",
                            fields = "method=$method request_id=$id timeout_ms=$timeoutMillis",
                            rateLimitKey = "request_timeout:$method",
                        )
                    }
                    throw SocketTimeoutException("请求超时：$method")
                }
        } finally {
            synchronized(pendingRequestsLock) {
                pendingRequests.remove(id)
                updateResponseTimeout()
            }
        }
    }

    private fun updateResponseTimeout() {
        val timeoutMillis = maxOf(
            DEFAULT_REQUEST_TIMEOUT_MILLIS,
            pendingRequests.values.maxOfOrNull(PendingRequest::timeoutMillis) ?: DEFAULT_REQUEST_TIMEOUT_MILLIS,
        )
        if (responseTimeoutMillis == timeoutMillis) return
        responseTimeoutMillis = timeoutMillis
        webSocket.setResponseTimeout(timeoutMillis)
    }

    private fun send(message: JsonObject) {
        check(::webSocket.isInitialized) { "WebSocket 尚未建立" }
        if (!webSocket.send(json.encodeToString(JsonObject.serializer(), message))) {
            val error = ConnectionException("WebSocket 发送失败")
            failConnection(error, "send_failed")
            throw error
        }
    }

    private fun receive(text: String) {
        val message = try {
            json.parseToJsonElement(text).jsonObject
        } catch (error: Exception) {
            failConnection(ProtocolException("服务端发送了无效 JSON-RPC 消息", error), "invalid_json")
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
            receivedEvents.incrementAndGet()
            val delivery = eventChannel.trySend(event)
            if (delivery.isFailure) {
                failConnection(
                    ProtocolException("服务端事件队列已满"),
                    if (delivery.isClosed) "event_queue_closed" else "event_queue_full",
                    "event_queue_capacity=$EVENT_QUEUE_CAPACITY",
                )
            }
            return
        }

        val responseId = id?.longOrNull
        if (responseId == null) {
            failConnection(ProtocolException("JSON-RPC 响应缺少整数 id"), "invalid_response_id")
            return
        }
        val pending = pendingRequests[responseId]?.response ?: return
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

    private fun failConnection(error: Throwable, reason: String, fields: String = "") {
        if (closed.get()) return
        if (!failed.compareAndSet(false, true)) return
        diagnostics.failure(reason, error, fields)
        mutableStatus.value = ConnectionStatus.Failed(error.toUserFacingError("连接服务器失败"))
        if (!ready.isCompleted) ready.completeExceptionally(error)
        failPending(error)
        eventChannel.close()
        if (::webSocket.isInitialized) webSocket.cancel()
    }

    private fun failPending(error: Throwable) {
        synchronized(pendingRequestsLock) {
            pendingRequests.values.forEach { it.response.completeExceptionally(error) }
            pendingRequests.clear()
            updateResponseTimeout()
        }
    }

    private inner class Listener : AppServerWebSocketListener {
        override fun onOpen(webSocket: AppServerWebSocket) {
            diagnostics.setPhase("initialize")
            scope.launch {
                try {
                    initialize()
                } catch (error: Throwable) {
                    failConnection(
                        error,
                        if (error is SocketTimeoutException) "initialize_timeout" else "initialize_failed",
                    )
                }
            }
        }

        override fun onMessage(webSocket: AppServerWebSocket, text: String) {
            receive(text)
        }

        override fun onBinaryMessage(webSocket: AppServerWebSocket) {
            failConnection(ProtocolException("服务端发送了不受支持的二进制消息"), "binary_message")
        }

        override fun onClosed(webSocket: AppServerWebSocket, code: Int, reason: String) {
            if (!closed.get()) diagnostics.failure("websocket_closed", fields = "close_code=$code")
            val error = ConnectionException("连接已关闭：$reason")
            if (mutableStatus.value !is ConnectionStatus.Failed) {
                mutableStatus.value = ConnectionStatus.Disconnected
            }
            if (!ready.isCompleted) ready.completeExceptionally(error)
            failPending(error)
            eventChannel.close()
        }

        override fun onFailure(webSocket: AppServerWebSocket, error: Throwable, statusCode: Int?) {
            if (closed.get()) return
            val message = statusCode?.let { "HTTP $it" } ?: error.message ?: "WebSocket 连接失败"
            failConnection(
                ConnectionException(message, error, rejected = statusCode != null),
                "websocket_failed",
                statusCode?.let { "http_status=$it" }.orEmpty(),
            )
        }
    }

    private val JsonPrimitive.longOrNull: Long?
        get() = content.toLongOrNull()

    private data class PendingRequest(
        val response: CompletableDeferred<JsonElement>,
        val timeoutMillis: Long,
    )

    private companion object {
        const val NORMAL_CLOSURE = 1000
        const val SERVER_OVERLOADED = -32001
        const val MAX_OVERLOAD_ATTEMPTS = 3
        const val OVERLOAD_BASE_DELAY_MILLIS = 250L
        const val EVENT_QUEUE_CAPACITY = 256
    }
}
