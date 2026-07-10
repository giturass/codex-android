package com.hebo.codex.domain

import java.time.LocalDate

data class ModelOption(
    val id: String,
    val model: String,
    val displayName: String,
    val description: String,
    val isDefault: Boolean,
    val hidden: Boolean,
    val supportsPersonality: Boolean,
    val inputModalities: List<String>,
    val reasoningEfforts: List<OptionValue>,
    val defaultReasoningEffort: String,
    val serviceTiers: List<OptionValue>,
    val defaultServiceTier: String?,
)

data class OptionValue(
    val value: String,
    val label: String,
    val description: String = "",
)

enum class FeatureKind {
    PERMISSION_PROFILE,
    SKILL,
    HOOK,
    APP,
    PLUGIN,
    MARKETPLACE,
    MCP_SERVER,
    MCP_RESOURCE,
    MCP_TOOL,
    SHARED_PLUGIN,
}

data class FeatureEntry(
    val kind: FeatureKind,
    val id: String,
    val title: String,
    val description: String,
    val enabled: Boolean? = null,
    val installed: Boolean? = null,
    val path: String? = null,
    val parentId: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val rawJson: String,
)

data class FeatureCatalog(
    val entries: List<FeatureEntry>,
    val warnings: List<String> = emptyList(),
    val nextCursor: String? = null,
)

enum class FeatureArea {
    PERMISSIONS,
    SKILLS,
    HOOKS,
    APPS,
    PLUGINS,
    MCP,
    SHARED_PLUGINS,
}

sealed interface FeatureAction {
    data class SetSkillEnabled(
        val name: String?,
        val path: String?,
        val enabled: Boolean,
    ) : FeatureAction

    data class SetSkillExtraRoots(val roots: List<String>) : FeatureAction

    data class AddMarketplace(
        val source: String,
        val refName: String?,
        val sparsePaths: List<String> = emptyList(),
    ) : FeatureAction

    data class RemoveMarketplace(val name: String) : FeatureAction

    data class UpgradeMarketplace(val name: String?) : FeatureAction

    data class ReadPlugin(
        val name: String,
        val marketplacePath: String?,
        val remoteMarketplaceName: String?,
    ) : FeatureAction

    data class ReadPluginSkill(
        val marketplaceName: String,
        val pluginId: String,
        val skillName: String,
    ) : FeatureAction

    data class InstallPlugin(
        val name: String,
        val marketplacePath: String?,
        val remoteMarketplaceName: String?,
    ) : FeatureAction

    data class UninstallPlugin(val pluginId: String) : FeatureAction

    data class SaveSharedPlugin(
        val pluginPath: String,
        val optionsJson: String = "{}",
    ) : FeatureAction

    data class UpdateSharedPluginTargets(
        val remotePluginId: String,
        val discoverability: String,
        val shareTargetsJson: String,
    ) : FeatureAction

    data class CheckoutSharedPlugin(val remotePluginId: String) : FeatureAction

    data class DeleteSharedPlugin(val remotePluginId: String) : FeatureAction

    data object ReloadMcpServers : FeatureAction

    data class LoginMcpServer(val name: String, val threadId: String?) : FeatureAction

    data class ReadMcpResource(
        val server: String,
        val uri: String,
        val threadId: String?,
    ) : FeatureAction

    data class CallMcpTool(
        val server: String,
        val tool: String,
        val argumentsJson: String,
        val threadId: String,
    ) : FeatureAction

    data class WriteConfigValue(
        val keyPath: String,
        val valueJson: String,
        val mergeStrategy: String = "replace",
    ) : FeatureAction

    data class WriteConfigBatch(
        val editsJson: String,
        val reloadUserConfig: Boolean,
    ) : FeatureAction

    data class UploadFeedback(
        val classification: String,
        val reason: String?,
        val threadId: String?,
        val includeLogs: Boolean,
    ) : FeatureAction
}

data class FeatureActionResult(
    val message: String,
    val rawJson: String,
    val url: String? = null,
)

data class AccountOverview(
    val signedIn: Boolean,
    val type: AccountType?,
    val email: String?,
    val planType: AccountPlan?,
    val requiresOpenAiAuth: Boolean,
    val rateLimits: AccountRateLimits? = null,
    val usage: AccountUsage? = null,
    val workspaceMessages: List<WorkspaceMessage> = emptyList(),
)

enum class AccountType {
    API_KEY,
    CHATGPT,
    AMAZON_BEDROCK,
}

enum class AccountPlan {
    FREE,
    GO,
    PLUS,
    PRO,
    PRO_LITE,
    TEAM,
    BUSINESS_USAGE_BASED,
    BUSINESS,
    ENTERPRISE_USAGE_BASED,
    ENTERPRISE,
    EDUCATION,
    UNKNOWN,
}

data class AccountRateLimits(
    val limits: List<AccountRateLimit>,
    val resetCredits: RateLimitResetCredits?,
)

data class AccountRateLimit(
    val id: String?,
    val name: String?,
    val primary: RateLimitWindow?,
    val secondary: RateLimitWindow?,
    val credits: CreditBalance?,
    val individualLimit: SpendControlLimit?,
    val planType: AccountPlan?,
    val reachedType: RateLimitReachedType?,
)

data class RateLimitWindow(
    val usedPercent: Double,
    val windowDurationMinutes: Long?,
    val resetsAt: Long?,
)

data class CreditBalance(
    val hasCredits: Boolean,
    val unlimited: Boolean,
    val balance: String?,
)

data class SpendControlLimit(
    val limit: String,
    val used: String,
    val remainingPercent: Double,
    val resetsAt: Long,
)

enum class RateLimitReachedType {
    RATE_LIMIT_REACHED,
    WORKSPACE_OWNER_CREDITS_DEPLETED,
    WORKSPACE_MEMBER_CREDITS_DEPLETED,
    WORKSPACE_OWNER_USAGE_LIMIT_REACHED,
    WORKSPACE_MEMBER_USAGE_LIMIT_REACHED,
}

data class RateLimitResetCredits(
    val availableCount: Long,
    val credits: List<RateLimitResetCredit>?,
)

data class RateLimitResetCredit(
    val id: String,
    val type: RateLimitResetType,
    val status: RateLimitResetCreditStatus,
    val grantedAt: Long,
    val expiresAt: Long?,
    val title: String?,
    val description: String?,
)

enum class RateLimitResetType {
    CODEX_RATE_LIMITS,
    UNKNOWN,
}

enum class RateLimitResetCreditStatus {
    AVAILABLE,
    REDEEMING,
    REDEEMED,
    UNKNOWN,
}

data class AccountUsage(
    val lifetimeTokens: Long?,
    val peakDailyTokens: Long?,
    val longestRunningTurnSeconds: Long?,
    val currentStreakDays: Long?,
    val longestStreakDays: Long?,
    val dailyBuckets: List<DailyTokenUsage>?,
)

data class DailyTokenUsage(
    val date: LocalDate,
    val tokens: Long,
)

data class WorkspaceMessage(
    val id: String,
    val body: String,
    val type: WorkspaceMessageType,
    val createdAt: Long?,
)

enum class WorkspaceMessageType {
    HEADLINE,
    ANNOUNCEMENT,
    UNKNOWN,
}

data class LoginChallenge(
    val loginId: String?,
    val url: String?,
    val userCode: String?,
)

data class ServerConfiguration(
    val configJson: String,
    val requirementsJson: String?,
    val layersJson: String?,
)
