package com.hebo.codex.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalInputRuntimeTest {
    @Test
    fun `同一命令的输入按到达顺序合并且不会标记命令执行成功`() {
        val runtime = RuntimeStatus()
            .receive("turn", "command", "42", "yes")
            .receive("turn", "command", "42", "\r\n")
            .receive("turn", "command", "42", "\u0003")
            .reduce(RuntimeStatusEvent.TurnCompleted("turn"))
            .markStale()

        val record = runtime.records.single()
        assertEquals("yes\r\n\u0003", record.terminalInput!!.stdin)
        assertEquals(RuntimeActivityStatus.INFO, record.status)
        assertNull(record.text)
    }

    @Test
    fun `不同回合和进程的输入保留独立记录`() {
        val runtime = RuntimeStatus()
            .receive("first", "command", "42", "a")
            .receive("second", "command", "42", "b")
            .receive("second", "command", "99", "c")

        assertEquals(listOf("a", "b", "c"), runtime.records.map { it.terminalInput!!.stdin })
        assertEquals(listOf("first", "second", "second"), runtime.records.map { it.turnId })
    }

    @Test
    fun `终端输入与其他运行记录共用数量限制`() {
        val runtime = (0..50).fold(RuntimeStatus()) { state, index ->
            state.receive("turn", "command-$index", "$index", "\n")
        }

        assertEquals(50, runtime.records.size)
        assertEquals("command-1", runtime.records.first().terminalInput!!.itemId)
    }

    @Test
    fun `持续输入超过文本限额后保持截断标记`() {
        val runtime = RuntimeStatus()
            .receive("turn", "command", "42", "x".repeat(40_000))
            .receive("turn", "command", "42", "\u0003")

        assertEquals("x".repeat(32_768), runtime.records.single().terminalInput!!.stdin)
        assertTrue(runtime.records.single().truncated)
    }

    private fun RuntimeStatus.receive(turnId: String, itemId: String, processId: String, stdin: String) =
        reduce(RuntimeStatusEvent.TerminalInputReceived(turnId, RuntimeTerminalInput(itemId, processId, stdin)))
}
