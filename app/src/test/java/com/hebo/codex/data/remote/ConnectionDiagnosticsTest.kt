package com.hebo.codex.data.remote

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionDiagnosticsTest {
    @Test
    fun warningsContainPhaseDurationAndProgressWithoutExceptionPayloads() {
        val warnings = mutableListOf<String>()
        var now = 0L
        val diagnostics = ConnectionDiagnostics(warnings::add, nanoTime = { now })
        now = 2_000_000_000
        diagnostics.setPhase("restore")
        diagnostics.restoreTotal = 3
        diagnostics.restoreCompleted = 1
        now = 5_000_000_000

        diagnostics.warning(
            "restore_failed",
            ProtocolException(
                "sensitive protocol response",
                RpcException(-32602, "sensitive RPC message", JsonPrimitive("sensitive RPC data")),
            ),
        )

        val warning = warnings.single()
        assertTrue(warning.contains("phase=restore elapsed_ms=5000 phase_elapsed_ms=3000"))
        assertTrue(warning.contains("restore_completed=1 restore_total=3"))
        assertTrue(warning.contains("error_types=ProtocolException>RpcException rpc_code=-32602"))
        assertFalse(warning.contains("sensitive"))
    }

    @Test
    fun repeatedWarningsAreRateLimitedPerOperationAndTerminalFailureIsLoggedOnce() {
        val warnings = mutableListOf<String>()
        var now = 0L
        val diagnostics = ConnectionDiagnostics(warnings::add, nanoTime = { now })

        diagnostics.warning("request_timeout", rateLimitKey = "thread/read")
        now = 59_000_000_000
        diagnostics.warning("request_timeout", rateLimitKey = "thread/read")
        diagnostics.warning("request_timeout", rateLimitKey = "thread/resume")
        assertEquals(2, warnings.size)

        now = 60_000_000_000
        diagnostics.warning("request_timeout", rateLimitKey = "thread/read")
        assertEquals(3, warnings.size)

        diagnostics.failure("websocket_failed")
        diagnostics.failure("websocket_closed")
        diagnostics.warning("restore_failed")
        assertEquals(4, warnings.size)
        assertTrue(warnings.last().contains("reason=websocket_failed"))
    }

    @Test
    fun distinctFailureTypesAndRpcCodesAreNotSuppressedTogether() {
        val warnings = mutableListOf<String>()
        val diagnostics = ConnectionDiagnostics(warnings::add)

        diagnostics.warning("restore_failed", RpcException(-32602, "invalid params", null))
        diagnostics.warning("restore_failed", RpcException(-32001, "overloaded", null))
        diagnostics.warning("restore_failed", ProtocolException("invalid response"))
        diagnostics.warning("restore_failed", ProtocolException("another invalid response"))

        assertEquals(3, warnings.size)
    }

    @Test
    fun cancellationIsSilentAndDoesNotHideLaterFailures() {
        val warnings = mutableListOf<String>()
        val diagnostics = ConnectionDiagnostics(warnings::add)

        diagnostics.warning("restore_failed", CancellationException("cancelled"))
        diagnostics.failure("initialize_failed", CancellationException("cancelled"))
        assertTrue(warnings.isEmpty())

        diagnostics.failure("websocket_failed")
        assertEquals(1, warnings.size)
    }
}
