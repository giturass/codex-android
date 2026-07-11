package com.hebo.codex.ui

import com.hebo.codex.R
import com.hebo.codex.domain.ApprovalPolicy
import com.hebo.codex.domain.RateLimitReachedType
import com.hebo.codex.domain.RateLimitResetCreditStatus
import com.hebo.codex.domain.SandboxMode
import com.hebo.codex.domain.ThreadGoalStatus
import java.time.LocalDate
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
        assertEquals("2026年7月11日", formatDate(LocalDate.of(2026, 7, 11)))
        assertEquals(
            R.string.rate_limit_member_usage_reached,
            RateLimitReachedType.WORKSPACE_MEMBER_USAGE_LIMIT_REACHED.displayLabelRes,
        )
        assertEquals(
            R.string.rate_limit_reset_status_available,
            RateLimitResetCreditStatus.AVAILABLE.displayLabelRes,
        )
    }

    @Test
    fun largeCountsUseCompactUnitsAndPromoteRoundedBoundaries() {
        assertEquals("999", formatCompactCount(999))
        assertEquals("1.25K", formatCompactCount(1_250))
        assertEquals("547K", formatCompactCount(547_000))
        assertEquals("66M", formatCompactCount(66_000_000))
        assertEquals("3B", formatCompactCount(3_000_000_000))
        assertEquals("1M", formatCompactCount(999_999))
        assertEquals("1B", formatCompactCount(999_999_999))
    }

    @Test
    fun protocolMetadataUsesHumanLabels() {
        assertEquals("个人", featureScopeLabel("user"))
        assertEquals("工具调用前", hookEventLabel("PreToolUse"))
        assertEquals("OAuth 已认证", mcpAuthStatusLabel("oAuth"))
        assertEquals("仅指定成员访问", pluginDiscoverabilityLabel("PRIVATE"))
    }
}
