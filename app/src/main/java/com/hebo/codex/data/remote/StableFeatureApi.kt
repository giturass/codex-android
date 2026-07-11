package com.hebo.codex.data.remote

import com.hebo.codex.domain.AccountRateLimits
import com.hebo.codex.domain.AccountOverview
import com.hebo.codex.domain.AccountType
import com.hebo.codex.domain.FeatureAction
import com.hebo.codex.domain.FeatureActionResult
import com.hebo.codex.domain.FeatureArea
import com.hebo.codex.domain.FeatureCatalog
import com.hebo.codex.domain.FeatureEntry
import com.hebo.codex.domain.FeatureKind
import com.hebo.codex.domain.LoginChallenge
import com.hebo.codex.domain.ModelOption
import com.hebo.codex.domain.OptionValue
import com.hebo.codex.domain.ServerConfiguration
import com.hebo.codex.domain.WorkspaceMessage
import com.hebo.codex.domain.WorkspaceMessageType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

class StableFeatureApi(
    private val api: AppServerApi,
) {
    suspend fun models(): List<ModelOption> {
        val results = mutableListOf<ModelOption>()
        var cursor: String? = null
        do {
            val response = api.request(
                StableRpcMethod.MODEL_LIST,
                buildJsonObject {
                    put("limit", PAGE_LIMIT)
                    cursor?.let { put("cursor", it) }
                },
            ).jsonObject
            results += response.requiredArray("data").map { it.jsonObject.toModelOption() }
            cursor = response.optionalString("nextCursor")
        } while (cursor != null)
        return results
    }

    suspend fun modelProviderCapabilities(): FeatureActionResult =
        api.request(StableRpcMethod.MODEL_PROVIDER_CAPABILITIES, buildJsonObject {}).toResult("模型能力已加载")

    suspend fun catalog(
        area: FeatureArea,
        cwd: String? = null,
        threadId: String? = null,
        forceRefresh: Boolean = false,
    ): FeatureCatalog = when (area) {
        FeatureArea.PERMISSIONS -> permissionProfiles(cwd)
        FeatureArea.SKILLS -> skills(cwd, forceRefresh)
        FeatureArea.HOOKS -> hooks(cwd)
        FeatureArea.APPS -> apps(threadId, forceRefresh)
        FeatureArea.PLUGINS -> plugins(cwd)
        FeatureArea.MCP -> mcpServers(threadId)
        FeatureArea.SHARED_PLUGINS -> sharedPlugins()
    }

    suspend fun account(): AccountOverview {
        val response = api.request(
            StableRpcMethod.ACCOUNT_READ,
            buildJsonObject { put("refreshToken", false) },
        ).jsonObject
        val account = response["account"] as? JsonObject
        val accountTypeValue = account?.optionalString("type")
        val accountType = accountTypeValue?.toAccountType()
        val rateLimitsResponse = if (accountType == AccountType.CHATGPT) {
            optionalRequest(StableRpcMethod.ACCOUNT_RATE_LIMITS) as? JsonObject
        } else {
            null
        }
        val usageResponse = if (accountType == AccountType.CHATGPT) {
            optionalRequest(StableRpcMethod.ACCOUNT_USAGE) as? JsonObject
        } else {
            null
        }
        val messagesResponse = if (accountType == AccountType.CHATGPT) {
            optionalRequest(StableRpcMethod.ACCOUNT_WORKSPACE_MESSAGES) as? JsonObject
        } else {
            null
        }
        val messages = messagesResponse?.requiredArray("messages").orEmpty().map { element ->
            val message = element.jsonObject
            WorkspaceMessage(
                id = message.requiredString("messageId"),
                body = message.requiredString("messageBody"),
                type = when (message.requiredString("messageType")) {
                    "headline" -> WorkspaceMessageType.HEADLINE
                    "announcement" -> WorkspaceMessageType.ANNOUNCEMENT
                    "unknown" -> WorkspaceMessageType.UNKNOWN
                    else -> throw ProtocolException("未知账号消息类型：${message.requiredString("messageType")}")
                },
                createdAt = message.optionalLong("createdAt"),
            )
        }
        return AccountOverview(
            signedIn = account != null,
            type = accountType,
            email = account?.optionalString("email"),
            planType = account?.optionalString("planType")?.let { AccountFeatureMapper.plan(it) },
            requiresOpenAiAuth = response.optionalBoolean("requiresOpenaiAuth") ?: false,
            rateLimits = rateLimitsResponse?.let(AccountFeatureMapper::rateLimits),
            usage = usageResponse?.let(AccountFeatureMapper::usage),
            workspaceMessages = messages,
        )
    }

    suspend fun rateLimits(): AccountRateLimits? {
        val response = api.request(
            StableRpcMethod.ACCOUNT_READ,
            buildJsonObject { put("refreshToken", false) },
        ).jsonObject
        val account = response["account"] as? JsonObject ?: return null
        if (account.requiredString("type").toAccountType() != AccountType.CHATGPT) return null

        return AccountFeatureMapper.rateLimits(
            api.request(StableRpcMethod.ACCOUNT_RATE_LIMITS).jsonObject,
        )
    }

    suspend fun loginWithApiKey(apiKey: String): LoginChallenge {
        require(apiKey.isNotBlank()) { "API Key 不能为空" }
        val response = api.request(
            StableRpcMethod.ACCOUNT_LOGIN_START,
            buildJsonObject {
                put("type", "apiKey")
                put("apiKey", apiKey.trim())
            },
        ).jsonObject
        return response.toLoginChallenge()
    }

    suspend fun loginWithChatGpt(deviceCode: Boolean): LoginChallenge {
        val response = api.request(
            StableRpcMethod.ACCOUNT_LOGIN_START,
            buildJsonObject { put("type", if (deviceCode) "chatgptDeviceCode" else "chatgpt") },
        ).jsonObject
        return response.toLoginChallenge()
    }

    suspend fun cancelLogin(loginId: String) {
        api.request(
            StableRpcMethod.ACCOUNT_LOGIN_CANCEL,
            buildJsonObject { put("loginId", loginId) },
        )
    }

    suspend fun logout() {
        api.request(StableRpcMethod.ACCOUNT_LOGOUT)
    }

    suspend fun consumeRateLimitReset(creditId: String?): FeatureActionResult = api.request(
        StableRpcMethod.ACCOUNT_RATE_LIMIT_RESET,
        buildJsonObject {
            put("idempotencyKey", java.util.UUID.randomUUID().toString())
            creditId?.takeIf(String::isNotBlank)?.let { put("creditId", it) }
        },
    ).toResult("额度重置请求已提交")

    suspend fun sendCreditsNudge(creditType: String): FeatureActionResult {
        require(creditType == "credits" || creditType == "usage_limit") {
            "请选择有效的额度类型"
        }
        return api.request(
            StableRpcMethod.ACCOUNT_SEND_CREDITS_NUDGE,
            buildJsonObject { put("creditType", creditType) },
        ).toResult("邮件请求已提交")
    }

    suspend fun configuration(cwd: String?): ServerConfiguration {
        val config = api.request(
            StableRpcMethod.CONFIG_READ,
            buildJsonObject {
                put("includeLayers", true)
                cwd?.takeIf(String::isNotBlank)?.let { put("cwd", it) }
            },
        ).jsonObject
        val requirements = api.request(StableRpcMethod.CONFIG_REQUIREMENTS_READ).jsonObject["requirements"]
        return ServerConfiguration(
            configJson = (config["config"] ?: JsonNull).pretty(),
            requirementsJson = requirements?.takeUnless { it is JsonNull }?.pretty(),
            layersJson = config["layers"]?.takeUnless { it is JsonNull }?.pretty(),
        )
    }

    suspend fun execute(action: FeatureAction): FeatureActionResult {
        val (method, params, message) = when (action) {
            is FeatureAction.SetSkillEnabled -> Triple(
                StableRpcMethod.SKILLS_CONFIG_WRITE,
                buildJsonObject {
                    action.name?.let { put("name", it) }
                    action.path?.let { put("path", it) }
                    put("enabled", action.enabled)
                },
                if (action.enabled) "Skill 已启用" else "Skill 已停用",
            )

            is FeatureAction.SetSkillExtraRoots -> Triple(
                StableRpcMethod.SKILLS_EXTRA_ROOTS_SET,
                buildJsonObject {
                    put("extraRoots", buildJsonArray { action.roots.forEach { add(JsonPrimitive(it)) } })
                },
                "Skill 额外目录已更新",
            )

            is FeatureAction.AddMarketplace -> Triple(
                StableRpcMethod.MARKETPLACE_ADD,
                buildJsonObject {
                    put("source", action.source)
                    action.refName?.takeIf(String::isNotBlank)?.let { put("refName", it) }
                    if (action.sparsePaths.isNotEmpty()) {
                        put(
                            "sparsePaths",
                            buildJsonArray { action.sparsePaths.forEach { add(JsonPrimitive(it)) } },
                        )
                    }
                },
                "Marketplace 已添加",
            )

            is FeatureAction.RemoveMarketplace -> Triple(
                StableRpcMethod.MARKETPLACE_REMOVE,
                buildJsonObject { put("marketplaceName", action.name) },
                "Marketplace 已移除",
            )

            is FeatureAction.UpgradeMarketplace -> Triple(
                StableRpcMethod.MARKETPLACE_UPGRADE,
                buildJsonObject { action.name?.takeIf(String::isNotBlank)?.let { put("marketplaceName", it) } },
                "Marketplace 升级完成",
            )

            is FeatureAction.ReadPlugin -> Triple(
                StableRpcMethod.PLUGIN_READ,
                pluginSelector(action.name, action.marketplacePath, action.remoteMarketplaceName),
                "插件详情已加载",
            )

            is FeatureAction.ReadPluginSkill -> Triple(
                StableRpcMethod.PLUGIN_SKILL_READ,
                buildJsonObject {
                    put("remoteMarketplaceName", action.marketplaceName)
                    put("remotePluginId", action.pluginId)
                    put("skillName", action.skillName)
                },
                "插件 Skill 已加载",
            )

            is FeatureAction.InstallPlugin -> Triple(
                StableRpcMethod.PLUGIN_INSTALL,
                pluginSelector(action.name, action.marketplacePath, action.remoteMarketplaceName),
                "插件已安装",
            )

            is FeatureAction.UninstallPlugin -> Triple(
                StableRpcMethod.PLUGIN_UNINSTALL,
                buildJsonObject { put("pluginId", action.pluginId) },
                "插件已卸载",
            )

            is FeatureAction.SaveSharedPlugin -> Triple(
                StableRpcMethod.PLUGIN_SHARE_SAVE,
                buildJsonObject {
                    put("pluginPath", action.pluginPath)
                    val options = Json.parseToJsonElement(action.optionsJson.ifBlank { "{}" }) as? JsonObject
                        ?: throw IllegalArgumentException("分享选项必须是 JSON 对象")
                    require(options.keys.all { it in SHARE_OPTION_KEYS }) {
                        "分享选项仅支持 remotePluginId、discoverability 和 shareTargets"
                    }
                    options.forEach { (key, value) -> put(key, value) }
                },
                "插件已分享",
            )

            is FeatureAction.UpdateSharedPluginTargets -> Triple(
                StableRpcMethod.PLUGIN_SHARE_UPDATE_TARGETS,
                buildJsonObject {
                    require(action.discoverability in setOf("UNLISTED", "PRIVATE")) {
                        "请选择有效的插件可见范围"
                    }
                    val targets = Json.parseToJsonElement(action.shareTargetsJson) as? JsonArray
                        ?: throw IllegalArgumentException("共享目标必须是 JSON 数组")
                    put("remotePluginId", action.remotePluginId)
                    put("discoverability", action.discoverability)
                    put("shareTargets", targets)
                },
                "共享目标已更新",
            )

            is FeatureAction.CheckoutSharedPlugin -> Triple(
                StableRpcMethod.PLUGIN_SHARE_CHECKOUT,
                buildJsonObject { put("remotePluginId", action.remotePluginId) },
                "共享插件已检出",
            )

            is FeatureAction.DeleteSharedPlugin -> Triple(
                StableRpcMethod.PLUGIN_SHARE_DELETE,
                buildJsonObject { put("remotePluginId", action.remotePluginId) },
                "共享插件已删除",
            )

            FeatureAction.ReloadMcpServers -> Triple(
                StableRpcMethod.MCP_CONFIG_RELOAD,
                null,
                "MCP 配置已重新加载",
            )

            is FeatureAction.LoginMcpServer -> Triple(
                StableRpcMethod.MCP_OAUTH_LOGIN,
                buildJsonObject {
                    put("name", action.name)
                    action.threadId?.let { put("threadId", it) }
                },
                "MCP 登录已开始",
            )

            is FeatureAction.ReadMcpResource -> Triple(
                StableRpcMethod.MCP_RESOURCE_READ,
                buildJsonObject {
                    action.threadId?.let { put("threadId", it) }
                    put("server", action.server)
                    put("uri", action.uri)
                },
                "MCP 资源已读取",
            )

            is FeatureAction.CallMcpTool -> Triple(
                StableRpcMethod.MCP_TOOL_CALL,
                buildJsonObject {
                    put("threadId", action.threadId)
                    put("server", action.server)
                    put("tool", action.tool)
                    put("arguments", Json.parseToJsonElement(action.argumentsJson.ifBlank { "{}" }))
                },
                "MCP 工具调用完成",
            )

            is FeatureAction.WriteConfigValue -> Triple(
                StableRpcMethod.CONFIG_VALUE_WRITE,
                buildJsonObject {
                    put("keyPath", action.keyPath)
                    put("value", Json.parseToJsonElement(action.valueJson))
                    require(action.mergeStrategy == "replace" || action.mergeStrategy == "upsert") {
                        "配置合并策略必须是 replace 或 upsert"
                    }
                    put("mergeStrategy", action.mergeStrategy)
                },
                "配置已写入",
            )

            is FeatureAction.WriteConfigBatch -> Triple(
                StableRpcMethod.CONFIG_BATCH_WRITE,
                buildJsonObject {
                    val edits = Json.parseToJsonElement(action.editsJson) as? JsonArray
                        ?: throw IllegalArgumentException("配置编辑必须是 JSON 数组")
                    put("edits", edits)
                    put("reloadUserConfig", action.reloadUserConfig)
                },
                "批量配置已写入",
            )

            is FeatureAction.UploadFeedback -> Triple(
                StableRpcMethod.FEEDBACK_UPLOAD,
                buildJsonObject {
                    put("classification", action.classification)
                    action.reason?.takeIf(String::isNotBlank)?.let { put("reason", it) }
                    action.threadId?.let { put("threadId", it) }
                    put("includeLogs", action.includeLogs)
                },
                "反馈已提交",
            )
        }
        val result = api.request(method, params)
        val url = (result as? JsonObject)?.optionalString("authorizationUrl")
            ?: (result as? JsonObject)?.optionalString("shareUrl")
        return FeatureActionResult(message, result.pretty(), url)
    }

    private suspend fun permissionProfiles(cwd: String?): FeatureCatalog {
        val entries = mutableListOf<FeatureEntry>()
        var cursor: String? = null
        do {
            val response = api.request(
                StableRpcMethod.PERMISSION_PROFILE_LIST,
                buildJsonObject {
                    put("limit", PAGE_LIMIT)
                    cursor?.let { put("cursor", it) }
                    cwd?.takeIf(String::isNotBlank)?.let { put("cwd", it) }
                },
            ).jsonObject
            entries += response.requiredArray("data").map { element ->
                val value = element.jsonObject
                FeatureEntry(
                    kind = FeatureKind.PERMISSION_PROFILE,
                    id = value.requiredString("id"),
                    title = value.requiredString("id"),
                    description = value.optionalString("description").orEmpty(),
                    enabled = value.optionalBoolean("allowed"),
                    rawJson = value.pretty(),
                )
            }
            cursor = response.optionalString("nextCursor")
        } while (cursor != null)
        return FeatureCatalog(entries)
    }

    private suspend fun skills(cwd: String?, forceRefresh: Boolean): FeatureCatalog {
        val response = api.request(
            StableRpcMethod.SKILLS_LIST,
            buildJsonObject {
                cwd?.takeIf(String::isNotBlank)?.let {
                    put("cwds", buildJsonArray { add(JsonPrimitive(it)) })
                }
                if (forceRefresh) put("forceReload", true)
            },
        ).jsonObject
        val entries = mutableListOf<FeatureEntry>()
        val warnings = mutableListOf<String>()
        response.requiredArray("data").forEach { groupElement ->
            val group = groupElement.jsonObject
            val groupCwd = group.optionalString("cwd")
            group.array("skills").forEach { element ->
                val value = element.jsonObject
                entries += FeatureEntry(
                    kind = FeatureKind.SKILL,
                    id = value.requiredString("path"),
                    title = value.requiredString("name"),
                    description = value.requiredString("description"),
                    enabled = value.optionalBoolean("enabled"),
                    path = value.requiredString("path"),
                    metadata = mapOf(
                        "cwd" to groupCwd.orEmpty(),
                        "scope" to value.optionalString("scope").orEmpty(),
                    ),
                    rawJson = value.pretty(),
                )
            }
            warnings += group.array("errors").map { it.humanReadableLoadError("path") }
        }
        return FeatureCatalog(entries, warnings)
    }

    private suspend fun hooks(cwd: String?): FeatureCatalog {
        val response = api.request(
            StableRpcMethod.HOOKS_LIST,
            buildJsonObject {
                cwd?.takeIf(String::isNotBlank)?.let {
                    put("cwds", buildJsonArray { add(JsonPrimitive(it)) })
                }
            },
        ).jsonObject
        val entries = mutableListOf<FeatureEntry>()
        val warnings = mutableListOf<String>()
        response.requiredArray("data").forEach { groupElement ->
            val group = groupElement.jsonObject
            group.array("hooks").forEach { element ->
                val value = element.jsonObject
                entries += FeatureEntry(
                    kind = FeatureKind.HOOK,
                    id = value.requiredString("key"),
                    title = value.optionalString("statusMessage") ?: value.requiredString("eventName"),
                    description = listOfNotNull(
                        value.optionalString("command"),
                        value.optionalString("matcher"),
                    ).joinToString(" · "),
                    enabled = value.optionalBoolean("enabled"),
                    path = value.optionalString("sourcePath"),
                    metadata = mapOf(
                        "event" to value.optionalString("eventName").orEmpty(),
                        "trust" to value.optionalString("trustStatus").orEmpty(),
                    ),
                    rawJson = value.pretty(),
                )
            }
            warnings += group.array("warnings").map { it.primitiveContent() }
            warnings += group.array("errors").map { it.humanReadableLoadError("path") }
        }
        return FeatureCatalog(entries, warnings)
    }

    private suspend fun apps(threadId: String?, forceRefresh: Boolean): FeatureCatalog {
        val entries = mutableListOf<FeatureEntry>()
        var cursor: String? = null
        do {
            val response = api.request(
                StableRpcMethod.APP_LIST,
                buildJsonObject {
                    put("limit", PAGE_LIMIT)
                    cursor?.let { put("cursor", it) }
                    threadId?.let { put("threadId", it) }
                    if (forceRefresh) put("forceRefetch", true)
                },
            ).jsonObject
            entries += response.requiredArray("data").map { element ->
                val value = element.jsonObject
                FeatureEntry(
                    kind = FeatureKind.APP,
                    id = value.requiredString("id"),
                    title = value.requiredString("name"),
                    description = value.optionalString("description").orEmpty(),
                    enabled = value.optionalBoolean("isEnabled"),
                    metadata = mapOf(
                        "accessible" to (value.optionalBoolean("isAccessible") ?: false).toString(),
                        "installUrl" to value.optionalString("installUrl").orEmpty(),
                        "mentionPath" to "app://${value.requiredString("id")}",
                    ),
                    rawJson = value.pretty(),
                )
            }
            cursor = response.optionalString("nextCursor")
        } while (cursor != null)
        return FeatureCatalog(entries)
    }

    private suspend fun plugins(cwd: String?): FeatureCatalog {
        val params = buildJsonObject {
            cwd?.takeIf(String::isNotBlank)?.let {
                put("cwds", buildJsonArray { add(JsonPrimitive(it)) })
            }
        }
        val catalogResponse = api.request(
            StableRpcMethod.PLUGIN_LIST,
            params,
        ).jsonObject
        val installedResponse = api.request(StableRpcMethod.PLUGIN_INSTALLED, params).jsonObject
        val catalog = catalogResponse.toPluginCatalog()
        val installed = installedResponse.toPluginCatalog()
        return FeatureCatalog(
            entries = (catalog.entries + installed.entries).distinctBy { "${it.kind}:${it.id}" },
            warnings = (catalog.warnings + installed.warnings).distinct(),
        )
    }

    private fun JsonObject.toPluginCatalog(): FeatureCatalog {
        val entries = mutableListOf<FeatureEntry>()
        requiredArray("marketplaces").forEach { marketplaceElement ->
            val marketplace = marketplaceElement.jsonObject
            val marketplaceName = marketplace.requiredString("name")
            val marketplacePath = marketplace.optionalString("path")
            entries += FeatureEntry(
                kind = FeatureKind.MARKETPLACE,
                id = marketplaceName,
                title = marketplaceName,
                description = marketplacePath.orEmpty(),
                path = marketplacePath,
                rawJson = marketplace.pretty(),
            )
            marketplace.array("plugins").forEach { pluginElement ->
                val plugin = pluginElement.jsonObject
                val pluginName = plugin.requiredString("name")
                entries += FeatureEntry(
                    kind = FeatureKind.PLUGIN,
                    id = plugin.requiredString("id"),
                    title = pluginName,
                    description = plugin["interface"]?.let { interfaceElement ->
                        (interfaceElement as? JsonObject)?.optionalString("shortDescription")
                    }.orEmpty(),
                    enabled = plugin.optionalBoolean("enabled"),
                    installed = plugin.optionalBoolean("installed"),
                    parentId = marketplaceName,
                    metadata = mapOf(
                        "marketplaceName" to marketplaceName,
                        "marketplacePath" to marketplacePath.orEmpty(),
                        "remotePluginId" to plugin.optionalString("remotePluginId").orEmpty(),
                        "mentionPath" to "plugin://$pluginName@$marketplaceName",
                    ),
                    rawJson = plugin.pretty(),
                )
            }
        }
        val warnings = array("marketplaceLoadErrors").map { it.humanReadableLoadError("marketplacePath") }
        return FeatureCatalog(entries, warnings)
    }

    private suspend fun mcpServers(threadId: String?): FeatureCatalog {
        val entries = mutableListOf<FeatureEntry>()
        var cursor: String? = null
        do {
            val response = api.request(
                StableRpcMethod.MCP_STATUS_LIST,
                buildJsonObject {
                    put("limit", PAGE_LIMIT)
                    put("detail", "full")
                    cursor?.let { put("cursor", it) }
                    threadId?.let { put("threadId", it) }
                },
            ).jsonObject
            response.requiredArray("data").forEach { serverElement ->
                val server = serverElement.jsonObject
                val serverName = server.requiredString("name")
                entries += FeatureEntry(
                    kind = FeatureKind.MCP_SERVER,
                    id = serverName,
                    title = serverName,
                    description = "认证：${server.optionalString("authStatus").orEmpty().toMcpAuthStatusLabel()}",
                    metadata = mapOf("authStatus" to server.optionalString("authStatus").orEmpty()),
                    rawJson = server.pretty(),
                )
                (server["tools"] as? JsonObject).orEmpty().forEach { (toolName, toolElement) ->
                    val tool = toolElement as? JsonObject
                    entries += FeatureEntry(
                        kind = FeatureKind.MCP_TOOL,
                        id = "$serverName/$toolName",
                        title = tool?.optionalString("title") ?: toolName,
                        description = tool?.optionalString("description").orEmpty(),
                        parentId = serverName,
                        metadata = mapOf("server" to serverName, "tool" to toolName),
                        rawJson = toolElement.pretty(),
                    )
                }
                server.array("resources").forEach { resourceElement ->
                    val resource = resourceElement.jsonObject
                    entries += FeatureEntry(
                        kind = FeatureKind.MCP_RESOURCE,
                        id = "$serverName/${resource.requiredString("uri")}",
                        title = resource.optionalString("title") ?: resource.requiredString("name"),
                        description = resource.optionalString("description").orEmpty(),
                        parentId = serverName,
                        metadata = mapOf(
                            "server" to serverName,
                            "uri" to resource.requiredString("uri"),
                        ),
                        rawJson = resource.pretty(),
                    )
                }
            }
            cursor = response.optionalString("nextCursor")
        } while (cursor != null)
        return FeatureCatalog(entries)
    }

    private suspend fun sharedPlugins(): FeatureCatalog {
        val response = api.request(StableRpcMethod.PLUGIN_SHARE_LIST, buildJsonObject {}).jsonObject
        return FeatureCatalog(
            entries = response.requiredArray("data").map { element ->
                val value = element.jsonObject
                val plugin = value.requiredObject("plugin")
                val shareContext = plugin["shareContext"] as? JsonObject
                val remoteId = plugin.optionalString("remotePluginId")
                    ?: shareContext?.optionalString("remotePluginId")
                    ?: plugin.requiredString("id")
                val shareTargets = buildJsonArray {
                    (shareContext?.get("sharePrincipals") as? JsonArray).orEmpty().forEach { principalElement ->
                        val principal = principalElement.jsonObject
                        val role = principal.requiredString("role")
                        if (role == "reader" || role == "editor") {
                            add(
                                buildJsonObject {
                                    put("principalType", principal.requiredString("principalType"))
                                    put("principalId", principal.requiredString("principalId"))
                                    put("role", role)
                                },
                            )
                        }
                    }
                }
                FeatureEntry(
                    kind = FeatureKind.SHARED_PLUGIN,
                    id = remoteId,
                    title = plugin.requiredString("name"),
                    description = listOfNotNull(
                        plugin.optionalString("version"),
                        shareContext?.optionalString("discoverability")?.toShareDiscoverabilityLabel(),
                    ).joinToString(" · "),
                    path = value.optionalString("localPluginPath"),
                    metadata = mapOf(
                        "remotePluginId" to remoteId,
                        "discoverability" to shareContext?.optionalString("discoverability").orEmpty(),
                        "shareTargets" to shareTargets.pretty(),
                        "shareUrl" to shareContext?.optionalString("shareUrl").orEmpty(),
                    ),
                    rawJson = value.pretty(),
                )
            },
        )
    }

    private fun String.toAccountType(): AccountType = when (this) {
        "apiKey" -> AccountType.API_KEY
        "chatgpt" -> AccountType.CHATGPT
        "amazonBedrock" -> AccountType.AMAZON_BEDROCK
        else -> throw ProtocolException("未知账号类型：$this")
    }

    private fun String.toShareDiscoverabilityLabel(): String = when (this) {
        "LISTED" -> "公开展示"
        "UNLISTED" -> "仅通过链接访问"
        "PRIVATE" -> "仅指定成员访问"
        else -> throw ProtocolException("未知插件可发现性：$this")
    }

    private fun String.toMcpAuthStatusLabel(): String = when (this) {
        "notLoggedIn" -> "待登录"
        "bearerToken" -> "令牌认证"
        "oAuth" -> "OAuth 已认证"
        "unsupported" -> "无需认证"
        else -> throw ProtocolException("未知 MCP 认证状态：$this")
    }

    private fun String.toReasoningEffortLabel(): String = when (this) {
        "none" -> "不使用推理"
        "minimal" -> "最少"
        "low" -> "较低"
        "medium" -> "中等"
        "high" -> "较高"
        "xhigh" -> "最高"
        else -> "自定义强度"
    }

    private suspend fun optionalRequest(method: StableRpcMethod): JsonElement? = try {
        api.request(method)
    } catch (_: RpcException) {
        null
    }

    private fun JsonObject.toModelOption(): ModelOption = ModelOption(
        id = requiredString("id"),
        model = requiredString("model"),
        displayName = requiredString("displayName"),
        description = requiredString("description"),
        isDefault = optionalBoolean("isDefault") ?: false,
        hidden = optionalBoolean("hidden") ?: false,
        supportsPersonality = optionalBoolean("supportsPersonality") ?: false,
        inputModalities = array("inputModalities").map { it.primitiveContent() },
        reasoningEfforts = array("supportedReasoningEfforts").map { element ->
            val value = element.jsonObject
            val effort = value.requiredString("reasoningEffort")
            OptionValue(effort, effort.toReasoningEffortLabel(), value.requiredString("description"))
        },
        defaultReasoningEffort = requiredString("defaultReasoningEffort"),
        serviceTiers = array("serviceTiers").map { element ->
            val value = element.jsonObject
            OptionValue(value.requiredString("id"), value.requiredString("name"), value.requiredString("description"))
        },
        defaultServiceTier = optionalString("defaultServiceTier"),
    )

    private fun pluginSelector(name: String, path: String?, remoteMarketplace: String?) = buildJsonObject {
        put("pluginName", name)
        path?.takeIf(String::isNotBlank)?.let { put("marketplacePath", it) }
        remoteMarketplace?.takeIf(String::isNotBlank)?.let { put("remoteMarketplaceName", it) }
    }

    private fun JsonObject.toLoginChallenge() = LoginChallenge(
        loginId = optionalString("loginId"),
        url = optionalString("authUrl") ?: optionalString("verificationUrl"),
        userCode = optionalString("userCode"),
    )

    private fun JsonObject.array(name: String): JsonArray = this[name] as? JsonArray ?: JsonArray(emptyList())

    private fun JsonElement.primitiveContent(): String = (this as? JsonPrimitive)?.content ?: pretty()

    private fun JsonElement.humanReadableLoadError(pathField: String): String {
        val error = jsonObject
        return listOf(error.requiredString("message"), error.requiredString(pathField))
            .filter(String::isNotBlank)
            .joinToString(" · ")
    }

    private fun JsonElement.toResult(message: String) = FeatureActionResult(message, pretty())

    private fun JsonElement.pretty(): String = PRETTY_JSON.encodeToString(JsonElement.serializer(), this)

    private companion object {
        const val PAGE_LIMIT = 100
        val SHARE_OPTION_KEYS = setOf("remotePluginId", "discoverability", "shareTargets")
        val PRETTY_JSON = Json { prettyPrint = true }
    }
}
