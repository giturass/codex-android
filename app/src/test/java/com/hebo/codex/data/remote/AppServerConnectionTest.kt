package com.hebo.codex.data.remote

import com.hebo.codex.domain.ConnectionStatus
import com.hebo.codex.util.runSuspendCatching
import java.net.SocketTimeoutException
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
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
            assertEquals(listOf(600_000L, DEFAULT_REQUEST_TIMEOUT_MILLIS), fixture.socket.responseTimeouts)
        } finally {
            fixture.connection.close()
        }
    }
}

private class ConnectionFixture(parentContext: CoroutineContext) {
    lateinit var socket: TestAppServerWebSocket

    val connection = AppServerConnection(
        webSocketFactory = AppServerWebSocketFactory { listener ->
            TestAppServerWebSocket(listener).also { socket = it }
        },
        json = Json,
        connectTimeoutMillis = 1_000,
        parentContext = parentContext,
    )
}

private class TestAppServerWebSocket(
    private val listener: AppServerWebSocketListener,
) : AppServerWebSocket {
    val requests = mutableListOf<JsonObject>()
    val responseTimeouts = mutableListOf<Long>()

    override fun start() = listener.onOpen(this)

    override fun setResponseTimeout(timeoutMillis: Long) {
        responseTimeouts += timeoutMillis
    }

    override fun send(text: String): Boolean {
        val request = Json.parseToJsonElement(text).jsonObject
        when (request.requiredString("method")) {
            "initialize" -> respond(request, buildJsonObject {
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

    override fun close(code: Int, reason: String): Boolean = true

    override fun cancel() = Unit
}
