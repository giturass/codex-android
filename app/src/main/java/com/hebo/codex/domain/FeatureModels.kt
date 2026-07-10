package com.hebo.codex.domain

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
    val type: String?,
    val email: String?,
    val planType: String?,
    val requiresOpenAiAuth: Boolean,
    val rateLimitsJson: String? = null,
    val usageJson: String? = null,
    val workspaceMessages: List<WorkspaceMessage> = emptyList(),
)

data class WorkspaceMessage(
    val id: String,
    val body: String,
    val type: String,
    val createdAt: Long?,
)

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
