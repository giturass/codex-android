package com.hebo.codex.ui.screens.chat

import com.hebo.codex.domain.ConversationItem
import com.hebo.codex.domain.ConversationTurn
import com.hebo.codex.domain.ConversationTurnStatus
import com.hebo.codex.domain.UserMessagePart
import org.junit.Assert.assertEquals
import org.junit.Test

class ForkTurnsTest {
    @Test
    fun `回合预览展示用户输入`() {
        val turns = buildForkTurns(
            turns = listOf(
                turn(
                    "turn-1",
                    userMessage("message-1", "turn-1", UserMessagePart.Text("  第一行\n第二行  ")),
                    ConversationItem.AgentMessage("answer-1", "turn-1", "回答"),
                ),
                turn("turn-2", userMessage("message-2", "turn-2", UserMessagePart.Text("执行中"))),
                turn("pending", userMessage("pending", "pending", UserMessagePart.Text("待发送"))),
            ),
            activeTurnId = "turn-2",
        )

        assertEquals(
            listOf(ForkTurn("turn-1", "第一行 第二行", 0)),
            turns,
        )
    }

    @Test
    fun `纯附件回合展示附件内容`() {
        val turns = buildForkTurns(
            turns = listOf(
                turn(
                    "turn-1",
                    userMessage(
                        "message-1",
                        "turn-1",
                        UserMessagePart.LocalImage("/tmp/screen.png"),
                        UserMessagePart.Mention("Main.kt", "/project/Main.kt"),
                    ),
                ),
            ),
            activeTurnId = null,
        )

        assertEquals(ForkTurn("turn-1", "图片：screen.png", 2), turns.single())
    }

    @Test
    fun `来源提示区分最新状态和历史回合`() {
        val turn = ForkTurn("turn-3", "修改交互", 0)

        assertEquals("已从“原会话”的最新状态创建分叉", forkAnnouncement("原会话", null))
        assertEquals("已从“原会话”的所选历史回合创建分叉", forkAnnouncement("原会话", turn))
    }

    @Test
    fun `Shell 回合使用用户执行的命令作为标题`() {
        val turns = buildForkTurns(
            turns = listOf(
                turn(
                    "turn-shell",
                    ConversationItem.UserShellCommand(
                        id = "shell",
                        turnId = "turn-shell",
                        command = "/usr/bin/zsh -lc 'git status --short'",
                        cwd = "/project",
                        output = "",
                        exitCode = 0,
                        durationMillis = 100,
                        status = com.hebo.codex.domain.ActivityStatus.COMPLETED,
                    ),
                ),
            ),
            activeTurnId = null,
        )

        assertEquals("!git status --short", turns.single().title)
    }

    private fun userMessage(
        id: String,
        turnId: String,
        vararg content: UserMessagePart,
    ) = ConversationItem.UserMessage(id, turnId, content.toList())

    private fun turn(
        id: String,
        vararg items: ConversationItem,
    ) = ConversationTurn(id, ConversationTurnStatus.COMPLETED, items.toList())
}
