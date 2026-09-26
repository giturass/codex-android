package com.hebo.codex.ui.screens.chat

import com.hebo.codex.domain.ConversationState
import com.hebo.codex.domain.ConversationNotice
import com.hebo.codex.domain.RuntimeActivity
import com.hebo.codex.domain.RuntimeActivityStatus
import com.hebo.codex.domain.RuntimeStatus
import com.hebo.codex.domain.ThreadGoal
import com.hebo.codex.domain.ThreadGoalStatus
import com.hebo.codex.domain.TokenUsage
import com.hebo.codex.domain.TokenUsageBreakdown
import com.hebo.codex.domain.UserFacingError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class RuntimeStatusPresentationTest {
    @Test
    fun `警告计入关注且同一错误不与通知重复展示`() {
        val conversation = conversation().copy(
            errorMessage = UserFacingError("服务器报告错误", "失败详情"),
            notices = listOf(
                ConversationNotice("info", "模型已切换"),
                ConversationNotice("warning", "需要确认", requiresAttention = true),
                ConversationNotice("error", "服务器报告错误", "失败详情", requiresAttention = true),
            ),
        )

        val presentation = runtimeStatusPresentation(conversation, RuntimeStatus(), connected = true)!!

        assertEquals("运行状态 · 2 项需注意", presentation.summary)
        assertNull(presentation.error)
    }

    @Test
    fun `空状态与全部完成的活动不显示面板`() {
        assertNull(runtimeStatusPresentation(conversation(), RuntimeStatus(), connected = true))
        assertNull(
            runtimeStatusPresentation(
                conversation().copy(goal = ThreadGoal("目标", ThreadGoalStatus.COMPLETE, null, 100, 10)),
                RuntimeStatus(records = listOf(RuntimeActivity("hook", "Hook", "", RuntimeActivityStatus.COMPLETED))),
                connected = true,
            ),
        )
    }

    @Test
    fun `上下文使用最近一次用量计算剩余而不是累计值`() {
        val presentation = runtimeStatusPresentation(
            conversation().copy(tokenUsage = TokenUsage(usage(100_000), usage(3_000), 10_000)),
            RuntimeStatus(),
            connected = true,
        )!!

        assertEquals("运行状态 · 上下文剩余 70%", presentation.summary)
        assertEquals(7_000L, presentation.contextRemaining!!.tokens)
        assertEquals(0.7f, presentation.contextRemaining.ratio, 0.0001f)
    }

    @Test
    fun `断线或等待新用量通知时不将旧比例作为当前上下文`() {
        val conversation = conversation().copy(tokenUsage = TokenUsage(usage(1_000), usage(1_000), 10_000))
        val disconnected = runtimeStatusPresentation(conversation, RuntimeStatus(), connected = false)!!
        val reconnected = runtimeStatusPresentation(conversation, RuntimeStatus(tokenUsageStale = true), connected = true)!!

        assertEquals("运行状态 · 待同步", disconnected.summary)
        assertEquals("运行状态 · 上下文待同步", reconnected.summary)
        assertEquals(9_000L, reconnected.contextRemaining!!.tokens)
        assertFalse(reconnected.summary.contains("90%"))
    }

    @Test
    fun `只有异常和待处理项计入关注数量`() {
        val presentation = runtimeStatusPresentation(
            conversation().copy(errorMessage = UserFacingError("发送失败")),
            RuntimeStatus(
                records = listOf(
                    RuntimeActivity("running", "Hook", "", RuntimeActivityStatus.RUNNING),
                    RuntimeActivity("warning", "审查", "", RuntimeActivityStatus.WARNING),
                    RuntimeActivity("done", "Hook", "", RuntimeActivityStatus.COMPLETED),
                ),
            ),
            connected = true,
        )!!

        assertEquals("运行状态 · 2 项需注意", presentation.summary)
        assertEquals(2, presentation.records.size)
    }

    @Test
    fun `预算耗尽与缺失窗口不产生负进度或除零`() {
        assertEquals(0L, runtimeRemainingTokens(150, 100)!!.tokens)
        assertEquals(0, runtimeRemainingTokens(Long.MAX_VALUE, 100)!!.percent)
        assertEquals(100, runtimeRemainingTokens(-1, 100)!!.percent)
        assertNull(runtimeRemainingTokens(50, 0))
        assertNull(runtimeRemainingTokens(50, null))
    }

    private fun conversation() = ConversationState("server", "服务器", "thread", "对话", "/workspace")
    private fun usage(total: Long) = TokenUsageBreakdown(total, total, 0, 0, 0)
}
