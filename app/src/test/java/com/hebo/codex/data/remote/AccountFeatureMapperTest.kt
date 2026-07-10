package com.hebo.codex.data.remote

import com.hebo.codex.domain.AccountPlan
import com.hebo.codex.domain.RateLimitReachedType
import com.hebo.codex.domain.RateLimitResetCreditStatus
import com.hebo.codex.domain.RateLimitResetType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

class AccountFeatureMapperTest {
    private val json = Json

    @Test
    fun mapsEveryHumanReadableRateLimitField() {
        val response = json.parseToJsonElement(
            """
            {
              "rateLimits": {
                "primary": { "usedPercent": 25, "windowDurationMins": 300, "resetsAt": 1784246400 }
              },
              "rateLimitsByLimitId": {
                "codex": {
                  "limitId": "codex",
                  "limitName": "Codex",
                  "primary": { "usedPercent": 25, "windowDurationMins": 300, "resetsAt": 1784246400 },
                  "secondary": { "usedPercent": 10, "windowDurationMins": 10080, "resetsAt": 1784851200 },
                  "credits": { "hasCredits": true, "unlimited": false, "balance": "${'$'}20" },
                  "individualLimit": {
                    "limit": "${'$'}100",
                    "used": "${'$'}25",
                    "remainingPercent": 75,
                    "resetsAt": 1785542400
                  },
                  "planType": "plus",
                  "rateLimitReachedType": "workspace_member_usage_limit_reached"
                }
              },
              "rateLimitResetCredits": {
                "availableCount": 2,
                "credits": [
                  {
                    "id": "RateLimitResetCredit_1",
                    "resetType": "codexRateLimits",
                    "status": "available",
                    "grantedAt": 1781654400,
                    "expiresAt": 1784246400,
                    "title": "完整重置",
                    "description": "可重置当前额度"
                  }
                ]
              }
            }
            """.trimIndent(),
        ).jsonObject

        val mapped = AccountFeatureMapper.rateLimits(response)
        val limit = mapped.limits.single()
        val resetCredits = requireNotNull(mapped.resetCredits)
        val resetCredit = resetCredits.credits.orEmpty().single()

        assertEquals("codex", limit.id)
        assertEquals("Codex", limit.name)
        assertEquals(25.0, limit.primary?.usedPercent ?: -1.0, 0.0)
        assertEquals(300L, limit.primary?.windowDurationMinutes)
        assertEquals(1_784_246_400L, limit.primary?.resetsAt)
        assertEquals(10_080L, limit.secondary?.windowDurationMinutes)
        assertEquals(true, limit.credits?.hasCredits)
        assertEquals(false, limit.credits?.unlimited)
        assertEquals("\$20", limit.credits?.balance)
        assertEquals("\$100", limit.individualLimit?.limit)
        assertEquals("\$25", limit.individualLimit?.used)
        assertEquals(75.0, limit.individualLimit?.remainingPercent ?: -1.0, 0.0)
        assertEquals(1_785_542_400L, limit.individualLimit?.resetsAt)
        assertEquals(AccountPlan.PLUS, limit.planType)
        assertEquals(RateLimitReachedType.WORKSPACE_MEMBER_USAGE_LIMIT_REACHED, limit.reachedType)
        assertEquals(2L, resetCredits.availableCount)
        assertEquals("RateLimitResetCredit_1", resetCredit.id)
        assertEquals(RateLimitResetType.CODEX_RATE_LIMITS, resetCredit.type)
        assertEquals(RateLimitResetCreditStatus.AVAILABLE, resetCredit.status)
        assertEquals(1_781_654_400L, resetCredit.grantedAt)
        assertEquals(1_784_246_400L, resetCredit.expiresAt)
        assertEquals("完整重置", resetCredit.title)
        assertEquals("可重置当前额度", resetCredit.description)
    }

    @Test
    fun mapsSummaryAndEveryDailyTokenBucket() {
        val response = json.parseToJsonElement(
            """
            {
              "summary": {
                "lifetimeTokens": 120000,
                "peakDailyTokens": 30000,
                "longestRunningTurnSec": 3720,
                "currentStreakDays": 3,
                "longestStreakDays": 8
              },
              "dailyUsageBuckets": [
                { "startDate": "2026-07-10", "tokens": 12000 },
                { "startDate": "2026-07-11", "tokens": 18000 }
              ]
            }
            """.trimIndent(),
        ).jsonObject

        val mapped = AccountFeatureMapper.usage(response)

        assertEquals(120_000L, mapped.lifetimeTokens)
        assertEquals(30_000L, mapped.peakDailyTokens)
        assertEquals(3_720L, mapped.longestRunningTurnSeconds)
        assertEquals(3L, mapped.currentStreakDays)
        assertEquals(8L, mapped.longestStreakDays)
        assertEquals(
            listOf(LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 11)),
            mapped.dailyBuckets?.map { it.date },
        )
        assertEquals(listOf(12_000L, 18_000L), mapped.dailyBuckets?.map { it.tokens })
    }

    @Test
    fun preservesUnavailableOptionalDetailsAndRejectsInvalidDates() {
        val withoutDetails = json.parseToJsonElement(
            """{"summary":{},"dailyUsageBuckets":null}""",
        ).jsonObject
        assertNull(AccountFeatureMapper.usage(withoutDetails).dailyBuckets)

        val invalidDate = json.parseToJsonElement(
            """{"summary":{},"dailyUsageBuckets":[{"startDate":"07/11/2026","tokens":1}]}""",
        ).jsonObject
        assertThrows(ProtocolException::class.java) {
            AccountFeatureMapper.usage(invalidDate)
        }
    }
}
