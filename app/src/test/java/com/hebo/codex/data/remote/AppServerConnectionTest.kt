package com.hebo.codex.data.remote

import com.hebo.codex.domain.ConnectionStatus
import com.hebo.codex.util.runSuspendCatching
import java.net.SocketTimeoutException
import java.io.IOException
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppServerConnectionTest {
    @Test
    fun healthyConnectionAndClientClosureAreSilentEvenWithManyEvents() = runTest {
        val fixture = ConnectionFixture(coroutineContext)
        try {
            fixture.connection.connect()
            val collector = launch { fixture.connection.events.collect() }
            runCurrent()
            repeat(300) {
                fixture.socket.notifyEvent()
                runCurrent()
            }
            collector.cancelAndJoin()
        } finally {
            fixture.connection.close()
        }

        assertTrue(fixture.warnings.isEmpty())
    }

    @Test
    fun failureCallbacksProduceOneWarningWithoutNetworkExceptionContents() = runTest {
        val fixture = ConnectionFixture(coroutineContext)
        try {
            fixture.connection.connect()
            val pendingRequest = async {
                runSuspendCatching { fixture.connection.request("thread/read") }
            }
            runCurrent()

            fixture.socket.fail(IOException("wss://sensitive-token@example.invalid/private"))
            fixture.socket.disconnect()
            assertTrue(pendingRequest.await().isFailure)

            val warning = fixture.warnings.single()
            assertTrue(warning.contains("reason=websocket_failed phase=ready"))
            assertTrue(warning.contains("pending_requests=1"))
            assertTrue(warning.contains("error_types=ConnectionException>IOException"))
            assertFalse(warning.contains("sensitive"))
        } finally {
            fixture.connection.close()
        }
    }

    @Test
    fun brokenLogWriterDoesNotPreventFailingPendingRequestsOrClosingSocket() = runTest {
        val fixture = ConnectionFixture(coroutineContext, logWarning = { error("logger unavailable") })
        try {
            fixture.connection.connect()
            val pendingRequest = async {
                runSuspendCatching { fixture.connection.request("thread/read") }
            }
            runCurrent()

            fixture.socket.fail(IOException("network unavailable"))

            assertTrue(pendingRequest.await().exceptionOrNull() is ConnectionException)
            assertTrue(fixture.connection.status.value is ConnectionStatus.Failed)
            assertTrue(fixture.socket.cancelled)
            assertEquals(0L, testScheduler.currentTime)
        } finally {
            fixture.connection.close()
        }
    }

    @Test
    fun restoreQueueOverflowReportsProgressAndInactiveConsumerOnce() = runTest {
        val fixture = ConnectionFixture(coroutineContext)
        try {
            fixture.connection.connect()
            fixture.connection.diagnostics.setPhase("restore")
            fixture.connection.diagnostics.restoreTotal = 2
            fixture.connection.diagnostics.restoreCompleted = 1
            repeat(257) { fixture.socket.notifyEvent() }
            fixture.socket.disconnect()
            fixture.socket.notifyEvent()

            val warning = fixture.warnings.single()
            assertTrue(warning.contains("reason=event_queue_full phase=restore"))
            assertTrue(warning.contains("restore_completed=1 restore_total=2"))
            assertTrue(warning.contains("event_consumer_active=false"))
            assertTrue(warning.contains("events_received=257 events_delivered=0"))
            assertTrue(warning.contains("event_queue_capacity=256"))
        } finally {
            fixture.connection.close()
        }
    }

    @Test
    fun invalidJsonLogsOnlyItsFailureCategoryAndExceptionTypes() = runTest {
        val fixture = ConnectionFixture(coroutineContext)
        try {
            fixture.connection.connect()
            fixture.socket.receive("{sensitive-conversation-text}")

            val warning = fixture.warnings.single()
            assertTrue(warning.contains("reason=invalid_json"))
            assertTrue(warning.contains("error_types=ProtocolException>"))
            assertFalse(warning.contains("sensitive"))
        } finally {
            fixture.connection.close()
        }
    }

    @Test
    fun connectionTimeoutDistinguishesTransportAndInitializationStages() = runTest {
        for (autoOpen in listOf(false, true)) {
            val fixture = ConnectionFixture(coroutineContext, autoOpen = autoOpen, autoInitialize = false)
            try {
                assertTrue(runSuspendCatching { fixture.connection.connect() }.isFailure)

                val warning = fixture.warnings.single()
                assertTrue(warning.contains("reason=connect_timeout"))
                assertTrue(warning.contains("phase=${if (autoOpen) "initialize" else "websocket"}"))
                assertTrue(warning.contains("timeout_ms=1000"))
            } finally {
                fixture.connection.close()
            }
        }
    }

    @Test
    fun rejectedHandshakeLogsStatusCodeWithoutHttpResponseContents() = runTest {
        val fixture = ConnectionFixture(coroutineContext, autoOpen = false)
        try {
            val connecting = async { runSuspendCatching { fixture.connection.connect() } }
            runCurrent()
            fixture.socket.fail(IOException("sensitive HTTP response"), statusCode = 401)
            assertTrue(connecting.await().isFailure)

            val warning = fixture.warnings.single()
            assertTrue(warning.contains("reason=websocket_failed phase=websocket"))
            assertTrue(warning.contains("http_status=401"))
            assertFalse(warning.contains("sensitive"))
        } finally {
            fixture.connection.close()
        }
    }

    @Test
    fun rejectedInitializationLogsRpcCodeOnceWithoutRpcErrorMessage() = runTest {
        val fixture = ConnectionFixture(coroutineContext, autoInitialize = false)
        try {
            val connecting = async { runSuspendCatching { fixture.connection.connect() } }
            runCurrent()
            fixture.socket.receive("""{"id":1,"error":{"code":-32602,"message":"sensitive RPC error"}}""")
            assertTrue(connecting.await().isFailure)

            val warning = fixture.warnings.single()
            assertTrue(warning.contains("reason=initialize_failed phase=initialize"))
            assertTrue(warning.contains("rpc_code=-32602"))
            assertFalse(warning.contains("sensitive"))
        } finally {
            fixture.connection.close()
        }
    }

    @Test
    fun requestTimeoutIsReportedAsFailureAndNextRequestCanSucceed() = runTest {
        val fixture = ConnectionFixture(coroutineContext)
        try {
            fixture.connection.connect()
            var reportedFailure: Throwable? = null

            val result = runSuspendCatching {
                fixture.connection.request("turn/start")
            }.onFailure { reportedFailure = it }

            assertTrue(result.isFailure)
            assertTrue(reportedFailure is SocketTimeoutException)
            assertEquals("请求超时：turn/start", reportedFailure?.message)
            assertEquals(30_000L, testScheduler.currentTime)
            assertEquals(ConnectionStatus.Connected, fixture.connection.status.value)
            val warning = fixture.warnings.single()
            assertTrue(warning.contains("reason=request_timeout"))
            assertTrue(warning.contains("method=turn/start request_id=2 timeout_ms=30000"))

            val nextRequest = async { fixture.connection.request("thread/read") }
            runCurrent()
            fixture.socket.respond(fixture.socket.requests.first(), JsonPrimitive("late response"))
            runCurrent()
            assertFalse(nextRequest.isCompleted)

            fixture.socket.respond(fixture.socket.requests.last(), JsonNull)
            assertEquals(JsonNull, nextRequest.await())
            assertTrue(fixture.socket.responseTimeouts.isEmpty())
        } finally {
            fixture.connection.close()
        }
    }

    @Test
    fun extendedRequestCanFinishAfterTheDefaultTimeoutAndRestoresHeartbeat() = runTest {
        val fixture = ConnectionFixture(coroutineContext)
        try {
            fixture.connection.connect()
            val request = async {
                fixture.connection.request("turn/start", timeoutMillis = 600_000)
            }
            runCurrent()

            advanceTimeBy(45_000)
            runCurrent()

            assertFalse(request.isCompleted)
            assertEquals(listOf(600_000L), fixture.socket.responseTimeouts)
            fixture.socket.respond(fixture.socket.requests.single(), JsonPrimitive("accepted"))

            assertEquals(JsonPrimitive("accepted"), request.await())
            assertEquals(listOf(600_000L, DEFAULT_REQUEST_TIMEOUT_MILLIS), fixture.socket.responseTimeouts)
        } finally {
            fixture.connection.close()
        }
    }

    @Test
    fun heartbeatTracksLongestPendingRequestWithoutResettingForOrdinaryRequests() = runTest {
        val fixture = ConnectionFixture(coroutineContext)
        try {
            fixture.connection.connect()
            val longestRequest = async {
                fixture.connection.request("turn/start", timeoutMillis = 600_000)
            }
            val shorterRequest = async {
                fixture.connection.request("turn/steer", timeoutMillis = 300_000)
            }
            val ordinaryRequest = async { fixture.connection.request("thread/read") }
            runCurrent()

            fixture.socket.respond(fixture.socket.requests.last(), JsonNull)
            ordinaryRequest.await()
            assertEquals(listOf(600_000L), fixture.socket.responseTimeouts)

            fixture.socket.respond(fixture.socket.requests.first(), JsonNull)
            longestRequest.await()
            assertEquals(listOf(600_000L, 300_000L), fixture.socket.responseTimeouts)

            shorterRequest.cancelAndJoin()
            assertEquals(listOf(600_000L, 300_000L, DEFAULT_REQUEST_TIMEOUT_MILLIS), fixture.socket.responseTimeouts)
        } finally {
            fixture.connection.close()
        }
    }

    @Test
    fun extendedRequestStillTimesOutAndRestoresHeartbeat() = runTest {
        val fixture = ConnectionFixture(coroutineContext)
        try {
            fixture.connection.connect()

            val result = runSuspendCatching {
                fixture.connection.request("turn/start", timeoutMillis = 600_000)
            }

            assertTrue(result.exceptionOrNull() is SocketTimeoutException)
            assertEquals(600_000L, testScheduler.currentTime)
            assertEquals(listOf(600_000L, DEFAULT_REQUEST_TIMEOUT_MILLIS), fixture.socket.responseTimeouts)
        } finally {
            fixture.connection.close()
        }
    }

    @Test
    fun outerTimeoutRemainsCancellationInsteadOfRequestFailure() = runTest {
        val fixture = ConnectionFixture(coroutineContext)
        try {
            fixture.connection.connect()
            var reportedFailure: Throwable? = null

            val result = withTimeoutOrNull(100) {
                runSuspendCatching {
                    fixture.connection.request("turn/start", timeoutMillis = 600_000)
                }.onFailure { reportedFailure = it }
                true
            }

            assertNull(result)
            assertNull(reportedFailure)
            assertTrue(fixture.warnings.isEmpty())
            assertEquals(100L, testScheduler.currentTime)
            assertEquals(ConnectionStatus.Connected, fixture.connection.status.value)
            assertEquals(listOf(600_000L, DEFAULT_REQUEST_TIMEOUT_MILLIS), fixture.socket.responseTimeouts)
        } finally {
            fixture.connection.close()
        }
    }

    @Test
    fun callerCancellationDoesNotReportRequestFailure() = runTest {
        val fixture = ConnectionFixture(coroutineContext)
        try {
            fixture.connection.connect()
            var reportedFailure: Throwable? = null
            val caller = launch {
                runSuspendCatching {
                    fixture.connection.request("turn/start", timeoutMillis = 600_000)
                }.onFailure { reportedFailure = it }
            }
            runCurrent()

            caller.cancelAndJoin()

            assertTrue(caller.isCancelled)
            assertNull(reportedFailure)
            assertTrue(fixture.warnings.isEmpty())
            assertEquals(0L, testScheduler.currentTime)
            assertEquals(listOf(600_000L, DEFAULT_REQUEST_TIMEOUT_MILLIS), fixture.socket.responseTimeouts)
        } finally {
            fixture.connection.close()
        }
    }

    @Test
    fun serverDisconnectionFailsPendingRequestWithoutWaitingForTimeout() = runTest {
        val fixture = ConnectionFixture(coroutineContext)
        try {
            fixture.connection.connect()
            val pendingRequest = async {
                runSuspendCatching { fixture.connection.request("turn/start", timeoutMillis = 600_000) }
            }
            runCurrent()

            fixture.socket.disconnect()
            runCurrent()

            assertTrue(pendingRequest.isCompleted)
            val error = pendingRequest.await().exceptionOrNull()
            assertTrue(error is ConnectionException)
            assertEquals("连接已关闭：server closed", error?.message)
            assertEquals(0L, testScheduler.currentTime)
            assertEquals(ConnectionStatus.Disconnected, fixture.connection.status.value)
            val warning = fixture.warnings.single()
            assertTrue(warning.contains("reason=websocket_closed"))
            assertTrue(warning.contains("close_code=1000"))
            assertFalse(warning.contains("server closed"))
            assertEquals(listOf(600_000L, DEFAULT_REQUEST_TIMEOUT_MILLIS), fixture.socket.responseTimeouts)
        } finally {
            fixture.connection.close()
        }
    }
}

private class ConnectionFixture(
    parentContext: CoroutineContext,
    autoOpen: Boolean = true,
    autoInitialize: Boolean = true,
    logWarning: ((String) -> Unit)? = null,
) {
    lateinit var socket: TestAppServerWebSocket
    val warnings = mutableListOf<String>()

    val connection = AppServerConnection(
        webSocketFactory = AppServerWebSocketFactory { listener ->
            TestAppServerWebSocket(listener, autoOpen, autoInitialize).also { socket = it }
        },
        json = Json,
        connectTimeoutMillis = 1_000,
        parentContext = parentContext,
        logWarning = { message ->
            warnings += message
            logWarning?.invoke(message)
        },
    )
}

private class TestAppServerWebSocket(
    private val listener: AppServerWebSocketListener,
    private val autoOpen: Boolean,
    private val autoInitialize: Boolean,
) : AppServerWebSocket {
    val requests = mutableListOf<JsonObject>()
    val responseTimeouts = mutableListOf<Long>()
    var cancelled = false
        private set

    override fun start() {
        if (autoOpen) listener.onOpen(this)
    }

    override fun setResponseTimeout(timeoutMillis: Long) {
        responseTimeouts += timeoutMillis
    }

    override fun send(text: String): Boolean {
        val request = Json.parseToJsonElement(text).jsonObject
        when (request.requiredString("method")) {
            "initialize" -> if (autoInitialize) respond(request, buildJsonObject {
                put("userAgent", "test")
                put("codexHome", "/tmp/codex")
                put("platformFamily", "unix")
                put("platformOs", "linux")
            })
            "initialized" -> Unit
            else -> requests += request
        }
        return true
    }

    fun respond(request: JsonObject, result: JsonElement) {
        listener.onMessage(this, buildJsonObject {
            put("id", requireNotNull(request["id"]))
            put("result", result)
        }.toString())
    }

    fun disconnect() = listener.onClosed(this, 1000, "server closed")

    fun fail(error: Throwable, statusCode: Int? = null) = listener.onFailure(this, error, statusCode)

    fun receive(text: String) = listener.onMessage(this, text)

    fun notifyEvent() = receive("""{"method":"thread/status/changed","params":{}}""")

    override fun close(code: Int, reason: String): Boolean {
        listener.onClosed(this, code, reason)
        return true
    }

    override fun cancel() {
        cancelled = true
    }
}
