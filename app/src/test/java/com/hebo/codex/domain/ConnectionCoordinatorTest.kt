package com.hebo.codex.domain

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionCoordinatorTest {
    @Test
    fun `故障服务器不阻塞其他服务器`() = runTest {
        val brokenGate = CompletableDeferred<Unit>()
        val openedServers = mutableListOf<String>()
        val coordinator = coordinator { serverId ->
            openedServers += serverId
            if (serverId == "broken") brokenGate.await()
            FakeConnection(serverId)
        }

        val brokenResult = async {
            runCatching { coordinator.connection("broken") }
        }
        runCurrent()

        val healthy = coordinator.connection("healthy")

        assertEquals("healthy", healthy.serverId)
        assertEquals(listOf("broken", "healthy"), openedServers)

        coordinator.cancelAttempt("broken")
        assertTrue(brokenResult.await().exceptionOrNull() is CancellationException)
    }

    @Test
    fun `同一服务器共享正在进行的连接`() = runTest {
        val connectGate = CompletableDeferred<Unit>()
        val connectionCount = AtomicInteger()
        val coordinator = coordinator { serverId ->
            connectionCount.incrementAndGet()
            connectGate.await()
            FakeConnection(serverId)
        }

        val first = async { coordinator.connection("server") }
        val second = async { coordinator.connection("server") }
        runCurrent()

        assertEquals(1, connectionCount.get())
        connectGate.complete(Unit)
        assertSame(first.await(), second.await())
    }

    @Test
    fun `连接失败由并发请求共享直到明确重试`() = runTest {
        val connectionCount = AtomicInteger()
        val coordinator = coordinator { serverId ->
            if (connectionCount.incrementAndGet() == 1) error("连接失败")
            FakeConnection(serverId)
        }

        val firstFailure = runCatching { coordinator.connection("server") }.exceptionOrNull()
        val cachedFailure = runCatching { coordinator.connection("server") }.exceptionOrNull()

        assertSame(firstFailure, cachedFailure)
        assertEquals(1, connectionCount.get())

        val connected = coordinator.connection("server", retryFailed = true)

        assertEquals("server", connected.serverId)
        assertEquals(2, connectionCount.get())
    }

    private fun kotlinx.coroutines.test.TestScope.coordinator(
        openConnection: suspend (String) -> FakeConnection,
    ) = ConnectionCoordinator(
        scope = this,
        isAvailable = { !it.closed },
        openConnection = openConnection,
        closeConnection = { it.closed = true },
    )

    private data class FakeConnection(
        val serverId: String,
        var closed: Boolean = false,
    )
}
