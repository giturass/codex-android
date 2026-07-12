package com.hebo.codex.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationGoalsTest {
    @Test
    fun `恢复快照静默同步无目标状态`() {
        val result = conversationState().withGoal(null, announce = false)

        assertNull(result.goal)
        assertTrue(result.goalLoaded)
        assertTrue(result.notices.isEmpty())
    }

    @Test
    fun `真实目标清除会追加提示`() {
        val result = conversationState().withGoal(null, announce = true)

        assertEquals("目标已清除", result.notices.single().message)
    }

    @Test
    fun `真实目标更新会追加提示`() {
        val goal = ThreadGoal(
            objective = "完成实现",
            status = ThreadGoalStatus.ACTIVE,
            tokenBudget = null,
            tokensUsed = 0,
            timeUsedSeconds = 0,
        )

        val result = conversationState().withGoal(goal, announce = true)

        assertEquals(goal, result.goal)
        assertEquals("目标已更新：完成实现", result.notices.single().message)
    }

    private fun conversationState() = ConversationState(
        serverId = "server-1",
        serverName = "测试服务器",
        threadId = "thread-1",
        title = "测试会话",
        cwd = "/workspace",
    )
}
