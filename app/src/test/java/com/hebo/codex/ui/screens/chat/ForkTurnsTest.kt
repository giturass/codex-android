package com.hebo.codex.ui.screens.chat

import com.hebo.codex.domain.ConversationItem
import com.hebo.codex.domain.UserMessagePart
import org.junit.Assert.assertEquals
import org.junit.Test

class ForkTurnsTest {
    @Test
    fun `回合预览展示用户输入并保留原始轮次`() {
        val turns = buildForkTurns(
            items = listOf(
                userMessage("message-1", "turn-1", UserMessagePart.Text("  第一行\n第二行  ")),
                ConversationItem.AgentMessage("answer-1", "turn-1", "回答", false),
                userMessage("message-2", "turn-2", UserMessagePart.Text("执行中")),
                userMessage("pending", "pending", UserMessagePart.Text("待发送")),
            ),
            activeTurnId = "turn-2",
        )

        assertEquals(
            listOf(ForkTurn("turn-1", 1, "第一行 第二行", 0)),
            turns,
        )
    }

    @Test
    fun `纯附件回合展示附件内容`() {
        val turns = buildForkTurns(
            items = listOf(
                userMessage(
                    "message-1",
                    "turn-1",
                    UserMessagePart.LocalImage("/tmp/screen.png"),
                    UserMessagePart.Mention("Main.kt", "/project/Main.kt"),
                ),
            ),
            activeTurnId = null,
        )

        assertEquals(ForkTurn("turn-1", 1, "图片：screen.png", 2), turns.single())
    }

    @Test
    fun `来源提示区分最新状态和历史回合`() {
        val turn = ForkTurn("turn-3", 3, "修改交互", 0)

        assertEquals("已从“原会话”的最新状态创建分叉", forkAnnouncement("原会话", null))
        assertEquals("已从“原会话”的第 3 轮创建分叉", forkAnnouncement("原会话", turn))
    }

    private fun userMessage(
        id: String,
        turnId: String,
        vararg content: UserMessagePart,
    ) = ConversationItem.UserMessage(id, turnId, content.toList())
}
