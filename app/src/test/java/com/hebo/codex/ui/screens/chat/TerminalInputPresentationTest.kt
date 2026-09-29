package com.hebo.codex.ui.screens.chat

import com.hebo.codex.domain.ActivityStatus
import com.hebo.codex.domain.ConversationHistory
import com.hebo.codex.domain.ConversationItem
import com.hebo.codex.domain.ConversationTurn
import com.hebo.codex.domain.ConversationTurnStatus
import com.hebo.codex.domain.RuntimeTerminalInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class TerminalInputPresentationTest {
    @Test
    fun `Ctrl C 只表达请求中断且普通输入保持原样`() {
        assertEquals("⟦Ctrl+C：请求中断⟧", formatTerminalInput("\u0003"))
        assertEquals("继续处理", formatTerminalInput("继续处理"))
    }

    @Test
    fun `Enter 区分换行回车并将回车换行识别为一个操作`() {
        assertEquals(
            "yes⟦Enter：回车换行⟧\nnext⟦Enter：换行⟧\n⟦Enter：回车⟧",
            formatTerminalInput("yes\r\nnext\n\r"),
        )
    }

    @Test
    fun `不可见控制字符转换为按键名称或 Unicode 码点`() {
        assertEquals(
            "⟦Ctrl+D：结束输入⟧⟦Backspace：退格⟧⟦Tab：制表⟧⟦Ctrl+Z：请求挂起⟧⟦Esc⟧⟦Backspace：退格⟧⟦U+0000⟧⟦U+0085⟧",
            formatTerminalInput("\u0004\b\t\u001a\u001b\u007f\u0000\u0085"),
        )
    }

    @Test
    fun `后续回合的输入通过条目标识关联先前启动的命令`() {
        val original = command("original-command", "first-turn")
        val history = ConversationHistory(
            turns = listOf(
                ConversationTurn("first-turn", ConversationTurnStatus.COMPLETED, listOf(original)),
                ConversationTurn("later-turn", ConversationTurnStatus.IN_PROGRESS, listOf(command("other", "later-turn"))),
            ),
        )
        val input = RuntimeTerminalInput("original-command", "42", "\u0003")

        assertSame(original, terminalInputCommand(input, history.items))
        assertNull(terminalInputCommand(input.copy(itemId = "unloaded"), history.items))
        assertEquals(ActivityStatus.IN_PROGRESS, original.status)
    }

    private fun command(id: String, turnId: String) = ConversationItem.CommandExecution(
        id = id,
        turnId = turnId,
        command = "npm run dev",
        cwd = "/workspace",
        output = "ready",
        exitCode = null,
        status = ActivityStatus.IN_PROGRESS,
        processId = "42",
    )
}
