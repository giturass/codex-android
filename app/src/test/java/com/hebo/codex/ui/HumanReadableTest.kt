package com.hebo.codex.ui

import com.hebo.codex.R
import com.hebo.codex.domain.ApprovalPolicy
import com.hebo.codex.domain.SandboxMode
import com.hebo.codex.domain.ThreadGoalStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class HumanReadableTest {
    @Test
    fun securityOptionsUseHumanLabelsAndExplainRisk() {
        assertEquals(R.string.sandbox_full_access, SandboxMode.DANGER_FULL_ACCESS.displayLabelRes)
        assertEquals(
            R.string.sandbox_full_access_description,
            SandboxMode.DANGER_FULL_ACCESS.displayDescriptionRes,
        )
        assertEquals(R.string.approval_on_request, ApprovalPolicy.ON_REQUEST.displayLabelRes)
    }

    @Test
    fun goalStatusAndMetricsAreFormattedForReading() {
        assertEquals(R.string.goal_status_usage_limited, ThreadGoalStatus.USAGE_LIMITED.displayLabelRes)
        assertEquals("123,456", formatCount(123_456))
        assertEquals("1 小时 2 分钟", formatDurationSeconds(3_720))
        assertEquals("12.5%", formatPercent(12.5))
    }

    @Test
    fun protocolMetadataUsesHumanLabels() {
        assertEquals("个人", featureScopeLabel("user"))
        assertEquals("工具调用前", hookEventLabel("PreToolUse"))
        assertEquals("OAuth 已认证", mcpAuthStatusLabel("oAuth"))
        assertEquals("仅指定成员访问", pluginDiscoverabilityLabel("PRIVATE"))
    }
}
