package com.hebo.codex.ui.screens.chat

import com.hebo.codex.domain.SubAgentRuntimeStatus
import com.hebo.codex.domain.SubAgentSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class SubAgentsPanelTest {
    @Test
    fun `子代理面板隐藏完成及未加载会话但保留需要关注的状态`() {
        val agents = SubAgentRuntimeStatus.entries.map { status ->
            SubAgentSummary(status.name, status.name, null, status)
        }

        assertEquals(
            listOf(
                SubAgentRuntimeStatus.WAITING_APPROVAL,
                SubAgentRuntimeStatus.WAITING_INPUT,
                SubAgentRuntimeStatus.ERRORED,
                SubAgentRuntimeStatus.RUNNING,
                SubAgentRuntimeStatus.IDLE,
                SubAgentRuntimeStatus.INTERRUPTED,
            ),
            visibleSubAgents(agents).map { it.status },
        )
    }

    @Test
    fun `子代理摘要汇总当前状态并优先提示同步失败`() {
        val agents = listOf(
            SubAgentSummary("running-1", "worker", null, SubAgentRuntimeStatus.RUNNING),
            SubAgentSummary("approval", "reviewer", null, SubAgentRuntimeStatus.WAITING_APPROVAL),
            SubAgentSummary("running-2", "worker", null, SubAgentRuntimeStatus.RUNNING),
        )

        assertEquals(
            "子 Agent · 状态同步失败 · 1 个等待审批 · 2 个运行中",
            subAgentsPanelSummary(agents, hasError = true),
        )
        assertEquals("子 Agent · 状态同步失败", subAgentsPanelSummary(emptyList(), hasError = true))
    }
}
