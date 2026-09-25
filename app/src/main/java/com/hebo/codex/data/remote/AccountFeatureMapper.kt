package com.hebo.codex.data.remote

import com.hebo.codex.domain.AccountPlan
import com.hebo.codex.domain.AccountRateLimit
import com.hebo.codex.domain.AccountRateLimits
import com.hebo.codex.domain.AccountUsage
import com.hebo.codex.domain.CreditBalance
import com.hebo.codex.domain.DailyTokenUsage
import com.hebo.codex.domain.RateLimitReachedType
import com.hebo.codex.domain.RateLimitResetCredit
import com.hebo.codex.domain.RateLimitResetCredits
import com.hebo.codex.domain.RateLimitResetCreditStatus
import com.hebo.codex.domain.RateLimitResetType
import com.hebo.codex.domain.RateLimitWindow
import com.hebo.codex.domain.SpendControlLimit
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import java.time.LocalDate
import java.time.format.DateTimeParseException

internal object AccountFeatureMapper {
    fun plan(value: String): AccountPlan = when (value) {
        "free" -> AccountPlan.FREE
        "go" -> AccountPlan.GO
        "plus" -> AccountPlan.PLUS
        "pro" -> AccountPlan.PRO
        "prolite" -> AccountPlan.PRO_LITE
        "team" -> AccountPlan.TEAM
        "self_serve_business_prolite" -> AccountPlan.SELF_SERVE_BUSINESS_PRO_LITE
        "self_serve_business_usage_based" -> AccountPlan.BUSINESS_USAGE_BASED
        "business" -> AccountPlan.BUSINESS
        "ent26" -> AccountPlan.ENT26
        "enterprise_cbp_automation" -> AccountPlan.ENTERPRISE_CBP_AUTOMATION
        "enterprise_cbp_usage_based" -> AccountPlan.ENTERPRISE_USAGE_BASED
        "enterprise" -> AccountPlan.ENTERPRISE
        "edu" -> AccountPlan.EDUCATION
        "edu_plus" -> AccountPlan.EDUCATION_PLUS
        "edu_pro" -> AccountPlan.EDUCATION_PRO
        "unknown" -> AccountPlan.UNKNOWN
        else -> throw ProtocolException("未知账号计划：$value")
    }

    fun rateLimits(value: JsonObject): AccountRateLimits {
        val rateLimitsById = value.optionalObject("rateLimitsByLimitId")
        val limits = if (rateLimitsById.isNullOrEmpty()) {
            listOf(value.requiredObject("rateLimits").toAccountRateLimit())
        } else {
            rateLimitsById.map { (id, element) -> element.requireObject(id).toAccountRateLimit(id) }
        }
        return AccountRateLimits(
            limits = limits,
            resetCredits = value.optionalObject("rateLimitResetCredits")?.toRateLimitResetCredits(),
        )
    }

    fun usage(value: JsonObject): AccountUsage {
        val summary = value.requiredObject("summary")
        return AccountUsage(
            lifetimeTokens = summary.optionalLong("lifetimeTokens"),
            peakDailyTokens = summary.optionalLong("peakDailyTokens"),
            longestRunningTurnSeconds = summary.optionalLong("longestRunningTurnSec"),
            currentStreakDays = summary.optionalLong("currentStreakDays"),
            longestStreakDays = summary.optionalLong("longestStreakDays"),
            dailyBuckets = value.optionalArray("dailyUsageBuckets")?.map { element ->
                val bucket = element.requireObject("dailyUsageBuckets")
                DailyTokenUsage(
                    date = bucket.requiredString("startDate").toLocalDate(),
                    tokens = bucket.requiredLong("tokens"),
                )
            },
        )
    }

    private fun JsonObject.toAccountRateLimit(fallbackId: String? = null) = AccountRateLimit(
        id = optionalString("limitId") ?: fallbackId,
        name = optionalString("limitName"),
        primary = optionalObject("primary")?.toRateLimitWindow(),
        secondary = optionalObject("secondary")?.toRateLimitWindow(),
        credits = optionalObject("credits")?.let { credits ->
            CreditBalance(
                hasCredits = credits.optionalBoolean("hasCredits")
                    ?: throw ProtocolException("额度信息缺少 hasCredits"),
                unlimited = credits.optionalBoolean("unlimited")
                    ?: throw ProtocolException("额度信息缺少 unlimited"),
                balance = credits.optionalString("balance"),
            )
        },
        individualLimit = optionalObject("individualLimit")?.let { limit ->
            SpendControlLimit(
                limit = limit.requiredString("limit"),
                used = limit.requiredString("used"),
                remainingPercent = limit.requiredDouble("remainingPercent"),
                resetsAt = limit.requiredLong("resetsAt"),
            )
        },
        planType = optionalString("planType")?.let { plan(it) },
        reachedType = optionalString("rateLimitReachedType")?.toRateLimitReachedType(),
    )

    private fun JsonObject.toRateLimitWindow() = RateLimitWindow(
        usedPercent = requiredDouble("usedPercent"),
        windowDurationMinutes = optionalLong("windowDurationMins"),
        resetsAt = optionalLong("resetsAt"),
    )

    private fun JsonObject.toRateLimitResetCredits() = RateLimitResetCredits(
        availableCount = requiredLong("availableCount"),
        credits = optionalArray("credits")?.map { element ->
            val credit = element.requireObject("credits")
            RateLimitResetCredit(
                id = credit.requiredString("id"),
                type = credit.requiredString("resetType").toRateLimitResetType(),
                status = credit.requiredString("status").toRateLimitResetCreditStatus(),
                grantedAt = credit.requiredLong("grantedAt"),
                expiresAt = credit.optionalLong("expiresAt"),
                title = credit.optionalString("title"),
                description = credit.optionalString("description"),
            )
        },
    )

    private fun JsonObject.optionalObject(name: String): JsonObject? = when (val element = this[name]) {
        null, JsonNull -> null
        is JsonObject -> element
        else -> throw ProtocolException("协议字段 $name 不是对象或 null")
    }

    private fun JsonObject.optionalArray(name: String): JsonArray? = when (val element = this[name]) {
        null, JsonNull -> null
        is JsonArray -> element
        else -> throw ProtocolException("协议字段 $name 不是数组或 null")
    }

    private fun JsonElement.requireObject(name: String): JsonObject = this as? JsonObject
        ?: throw ProtocolException("协议字段 $name 的元素不是对象")

    private fun String.toLocalDate(): LocalDate = try {
        LocalDate.parse(this)
    } catch (error: DateTimeParseException) {
        throw ProtocolException("Token 用量日期格式无效：$this", error)
    }

    private fun String.toRateLimitReachedType(): RateLimitReachedType = when (this) {
        "rate_limit_reached" -> RateLimitReachedType.RATE_LIMIT_REACHED
        "workspace_owner_credits_depleted" -> RateLimitReachedType.WORKSPACE_OWNER_CREDITS_DEPLETED
        "workspace_member_credits_depleted" -> RateLimitReachedType.WORKSPACE_MEMBER_CREDITS_DEPLETED
        "workspace_owner_usage_limit_reached" -> RateLimitReachedType.WORKSPACE_OWNER_USAGE_LIMIT_REACHED
        "workspace_member_usage_limit_reached" -> RateLimitReachedType.WORKSPACE_MEMBER_USAGE_LIMIT_REACHED
        else -> throw ProtocolException("未知额度状态：$this")
    }

    private fun String.toRateLimitResetType(): RateLimitResetType = when (this) {
        "codexRateLimits" -> RateLimitResetType.CODEX_RATE_LIMITS
        "unknown" -> RateLimitResetType.UNKNOWN
        else -> throw ProtocolException("未知额度重置类型：$this")
    }

    private fun String.toRateLimitResetCreditStatus(): RateLimitResetCreditStatus = when (this) {
        "available" -> RateLimitResetCreditStatus.AVAILABLE
        "redeeming" -> RateLimitResetCreditStatus.REDEEMING
        "redeemed" -> RateLimitResetCreditStatus.REDEEMED
        "unknown" -> RateLimitResetCreditStatus.UNKNOWN
        else -> throw ProtocolException("未知额度重置状态：$this")
    }
}
