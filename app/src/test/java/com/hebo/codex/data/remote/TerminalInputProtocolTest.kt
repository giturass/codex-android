package com.hebo.codex.data.remote

import com.hebo.codex.domain.RuntimeStatusEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TerminalInputProtocolTest {
    @Test
    fun `终端输入只进入运行记录并保留原始控制字符与命令标识`() {
        val params = Json.parseToJsonElement(
            """{"threadId":"thread","turnId":"later-turn","itemId":"original-command","processId":"42","stdin":"yes\r\n\u0003"}""",
        ).jsonObject

        val history = ProtocolMapper.notification("server", METHOD, params)
        val runtime = RuntimeStatusMapper.notification(METHOD, params) as RuntimeStatusEvent.TerminalInputReceived

        assertEquals("thread", history.threadId)
        assertNull(history.conversationEvent)
        assertEquals("later-turn", runtime.turnId)
        assertEquals("original-command", runtime.input.itemId)
        assertEquals("42", runtime.input.processId)
        assertEquals("yes\r\n\u0003", runtime.input.stdin)
    }

    @Test
    fun `空输入通知不产生已发送记录`() {
        val params = Json.parseToJsonElement(
            """{"threadId":"thread","turnId":"turn","itemId":"command","processId":"42","stdin":""}""",
        ).jsonObject

        assertNull(RuntimeStatusMapper.notification(METHOD, params))
    }

    private companion object {
        const val METHOD = "item/commandExecution/terminalInteraction"
    }
}
