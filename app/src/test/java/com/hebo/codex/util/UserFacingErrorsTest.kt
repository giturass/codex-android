package com.hebo.codex.util

import com.hebo.codex.data.remote.ConnectionException
import com.hebo.codex.data.remote.ProtocolException
import com.hebo.codex.data.remote.RpcException
import kotlinx.serialization.json.JsonNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserFacingErrorsTest {
    @Test
    fun rpcDetailsDoNotBecomePrimaryMessage() {
        val rawMessage = "Method thread/example is not available"
        val error = RpcException(-32601, rawMessage, JsonNull).toUserFacingError()

        assertEquals("当前服务器不支持此操作", error.summary)
        assertFalse(error.summary.contains(rawMessage))
        assertTrue(error.technicalDetails.orEmpty().contains(rawMessage))
    }

    @Test
    fun protocolFailuresUseHumanSummary() {
        val error = ProtocolException("协议字段 status 缺失").toUserFacingError()

        assertEquals("服务器返回了无法识别的数据", error.summary)
        assertEquals("协议字段 status 缺失", error.technicalDetails)
    }

    @Test
    fun rejectedConnectionsAreSeparatedFromProtocolFailures() {
        val error = ConnectionException("HTTP 401", rejected = true).toUserFacingError()

        assertEquals("服务器拒绝连接", error.summary)
        assertEquals("HTTP 401", error.technicalDetails)
    }
}
