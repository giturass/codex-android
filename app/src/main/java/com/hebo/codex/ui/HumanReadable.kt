package com.hebo.codex.ui

import com.hebo.codex.R
import com.hebo.codex.domain.AccountPlan
import com.hebo.codex.domain.AccountType
import com.hebo.codex.domain.ApprovalPolicy
import com.hebo.codex.domain.ApprovalReviewer
import com.hebo.codex.domain.Personality
import com.hebo.codex.domain.RateLimitReachedType
import com.hebo.codex.domain.RateLimitResetCreditStatus
import com.hebo.codex.domain.RateLimitResetType
import com.hebo.codex.domain.ReasoningSummary
import com.hebo.codex.domain.SandboxMode
import com.hebo.codex.domain.ThreadGoalStatus
import com.hebo.codex.domain.WorkspaceMessageType
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val SandboxMode.displayLabelRes: Int
    get() = when (this) {
        SandboxMode.READ_ONLY -> R.string.sandbox_read_only
        SandboxMode.WORKSPACE_WRITE -> R.string.sandbox_workspace_write
        SandboxMode.DANGER_FULL_ACCESS -> R.string.sandbox_full_access
    }

val SandboxMode.displayDescriptionRes: Int
    get() = when (this) {
        SandboxMode.READ_ONLY -> R.string.sandbox_read_only_description
        SandboxMode.WORKSPACE_WRITE -> R.string.sandbox_workspace_write_description
        SandboxMode.DANGER_FULL_ACCESS -> R.string.sandbox_full_access_description
    }

val ApprovalPolicy.displayLabelRes: Int
    get() = when (this) {
        ApprovalPolicy.UNTRUSTED -> R.string.approval_untrusted
        ApprovalPolicy.ON_REQUEST -> R.string.approval_on_request
        ApprovalPolicy.NEVER -> R.string.approval_never
    }

val ApprovalPolicy.displayDescriptionRes: Int
    get() = when (this) {
        ApprovalPolicy.UNTRUSTED -> R.string.approval_untrusted_description
        ApprovalPolicy.ON_REQUEST -> R.string.approval_on_request_description
        ApprovalPolicy.NEVER -> R.string.approval_never_description
    }

val ApprovalReviewer.displayLabelRes: Int
    get() = when (this) {
        ApprovalReviewer.USER -> R.string.approval_reviewer_user
        ApprovalReviewer.AUTO_REVIEW -> R.string.approval_reviewer_auto
    }

val ApprovalReviewer.displayDescriptionRes: Int
    get() = when (this) {
        ApprovalReviewer.USER -> R.string.approval_reviewer_user_description
        ApprovalReviewer.AUTO_REVIEW -> R.string.approval_reviewer_auto_description
    }

val Personality.displayLabelRes: Int
    get() = when (this) {
        Personality.NONE -> R.string.personality_default
        Personality.FRIENDLY -> R.string.personality_friendly
        Personality.PRAGMATIC -> R.string.personality_pragmatic
    }

val ReasoningSummary.displayLabelRes: Int
    get() = when (this) {
        ReasoningSummary.AUTO -> R.string.reasoning_summary_auto
        ReasoningSummary.CONCISE -> R.string.reasoning_summary_concise
        ReasoningSummary.DETAILED -> R.string.reasoning_summary_detailed
        ReasoningSummary.NONE -> R.string.reasoning_summary_none
    }

val ThreadGoalStatus.displayLabelRes: Int
    get() = when (this) {
        ThreadGoalStatus.ACTIVE -> R.string.goal_status_active
        ThreadGoalStatus.PAUSED -> R.string.goal_status_paused
        ThreadGoalStatus.BLOCKED -> R.string.goal_status_blocked
        ThreadGoalStatus.USAGE_LIMITED -> R.string.goal_status_usage_limited
        ThreadGoalStatus.BUDGET_LIMITED -> R.string.goal_status_budget_limited
        ThreadGoalStatus.COMPLETE -> R.string.goal_status_complete
    }

val AccountType.displayLabelRes: Int
    get() = when (this) {
        AccountType.API_KEY -> R.string.account_type_api_key
        AccountType.CHATGPT -> R.string.account_type_chatgpt
        AccountType.AMAZON_BEDROCK -> R.string.account_type_amazon_bedrock
    }

val AccountPlan.displayLabelRes: Int
    get() = when (this) {
        AccountPlan.FREE -> R.string.account_plan_free
        AccountPlan.GO -> R.string.account_plan_go
        AccountPlan.PLUS -> R.string.account_plan_plus
        AccountPlan.PRO -> R.string.account_plan_pro
        AccountPlan.PRO_LITE -> R.string.account_plan_pro_lite
        AccountPlan.TEAM -> R.string.account_plan_team
        AccountPlan.BUSINESS_USAGE_BASED -> R.string.account_plan_business_usage_based
        AccountPlan.BUSINESS -> R.string.account_plan_business
        AccountPlan.ENTERPRISE_USAGE_BASED -> R.string.account_plan_enterprise_usage_based
        AccountPlan.ENTERPRISE -> R.string.account_plan_enterprise
        AccountPlan.EDUCATION -> R.string.account_plan_education
        AccountPlan.UNKNOWN -> R.string.account_plan_unknown
    }

val RateLimitReachedType.displayLabelRes: Int
    get() = when (this) {
        RateLimitReachedType.RATE_LIMIT_REACHED -> R.string.rate_limit_reached
        RateLimitReachedType.WORKSPACE_OWNER_CREDITS_DEPLETED -> R.string.rate_limit_owner_credits_depleted
        RateLimitReachedType.WORKSPACE_MEMBER_CREDITS_DEPLETED -> R.string.rate_limit_member_credits_depleted
        RateLimitReachedType.WORKSPACE_OWNER_USAGE_LIMIT_REACHED -> R.string.rate_limit_owner_usage_reached
        RateLimitReachedType.WORKSPACE_MEMBER_USAGE_LIMIT_REACHED -> R.string.rate_limit_member_usage_reached
    }

val RateLimitResetType.displayLabelRes: Int
    get() = when (this) {
        RateLimitResetType.CODEX_RATE_LIMITS -> R.string.rate_limit_reset_type_codex
        RateLimitResetType.UNKNOWN -> R.string.rate_limit_reset_type_unknown
    }

val RateLimitResetCreditStatus.displayLabelRes: Int
    get() = when (this) {
        RateLimitResetCreditStatus.AVAILABLE -> R.string.rate_limit_reset_status_available
        RateLimitResetCreditStatus.REDEEMING -> R.string.rate_limit_reset_status_redeeming
        RateLimitResetCreditStatus.REDEEMED -> R.string.rate_limit_reset_status_redeemed
        RateLimitResetCreditStatus.UNKNOWN -> R.string.rate_limit_reset_status_unknown
    }

val WorkspaceMessageType.displayLabelRes: Int
    get() = when (this) {
        WorkspaceMessageType.HEADLINE -> R.string.workspace_message_headline
        WorkspaceMessageType.ANNOUNCEMENT -> R.string.workspace_message_announcement
        WorkspaceMessageType.UNKNOWN -> R.string.workspace_message_unknown
    }

fun formatCount(value: Long): String = NumberFormat.getIntegerInstance(Locale.CHINA).format(value)

fun formatCompactCount(value: Long): String {
    val safeValue = value.coerceAtLeast(0)
    if (safeValue < 1_000) return safeValue.toString()

    val (divisor, suffix) = when {
        safeValue >= 1_000_000_000_000 -> 1_000_000_000_000.0 to "T"
        safeValue >= 1_000_000_000 -> 1_000_000_000.0 to "B"
        safeValue >= 1_000_000 -> 1_000_000.0 to "M"
        else -> 1_000.0 to "K"
    }
    val scaled = safeValue / divisor
    val decimalPlaces = when {
        scaled < 10 -> 2
        scaled < 100 -> 1
        else -> 0
    }
    var formatted = String.format(Locale.ROOT, "%.${decimalPlaces}f", scaled)
    if ('.' in formatted) formatted = formatted.trimEnd('0').trimEnd('.')
    return "$formatted$suffix"
}

fun formatDurationSeconds(seconds: Long): String {
    val safeSeconds = seconds.coerceAtLeast(0)
    val days = safeSeconds / 86_400
    val hours = safeSeconds % 86_400 / 3_600
    val minutes = safeSeconds % 3_600 / 60
    val remainingSeconds = safeSeconds % 60
    return when {
        days > 0 -> if (hours > 0) "$days 天 $hours 小时" else "$days 天"
        hours > 0 -> if (minutes > 0) "$hours 小时 $minutes 分钟" else "$hours 小时"
        minutes > 0 -> if (remainingSeconds > 0) "$minutes 分钟 $remainingSeconds 秒" else "$minutes 分钟"
        else -> "$remainingSeconds 秒"
    }
}

fun formatPercent(value: Double): String = if (value % 1.0 == 0.0) {
    "${value.toInt()}%"
} else {
    String.format(Locale.CHINA, "%.1f%%", value)
}

fun formatDateTime(epochSeconds: Long, zoneId: ZoneId = ZoneId.systemDefault()): String =
    DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.CHINA)
        .withZone(zoneId)
        .format(Instant.ofEpochSecond(epochSeconds))

fun formatDate(date: LocalDate): String =
    DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA).format(date)

fun featureScopeLabel(value: String): String = when (value.lowercase()) {
    "system" -> "系统"
    "user", "personal" -> "个人"
    "project", "repo" -> "项目"
    "admin", "managed" -> "管理员"
    else -> "其他作用域"
}

fun featureTrustLabel(value: String): String = when (value.lowercase()) {
    "trusted" -> "可信"
    "untrusted" -> "不可信"
    else -> "信任状态未知"
}

fun hookEventLabel(value: String): String = when (value) {
    "PreToolUse" -> "工具调用前"
    "PermissionRequest" -> "权限请求时"
    "PostToolUse" -> "工具调用后"
    "PreCompact" -> "上下文压缩前"
    "PostCompact" -> "上下文压缩后"
    "SessionStart" -> "会话开始时"
    "UserPromptSubmit" -> "用户提交消息时"
    "SubagentStart" -> "子代理开始时"
    "SubagentStop" -> "子代理结束时"
    "Stop" -> "任务停止时"
    else -> "未知事件"
}

fun mcpAuthStatusLabel(value: String): String = when (value) {
    "notLoggedIn" -> "待登录"
    "bearerToken" -> "令牌认证"
    "oAuth" -> "OAuth 已认证"
    "unsupported" -> "无需认证"
    else -> "认证状态未知"
}

fun pluginDiscoverabilityLabel(value: String): String = when (value) {
    "LISTED" -> "公开展示"
    "UNLISTED" -> "仅通过链接访问"
    "PRIVATE" -> "仅指定成员访问"
    else -> "可见范围未知"
}
