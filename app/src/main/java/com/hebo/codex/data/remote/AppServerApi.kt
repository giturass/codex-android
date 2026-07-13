package com.hebo.codex.data.remote

import com.hebo.codex.domain.ReviewDelivery
import com.hebo.codex.domain.ReviewTarget
import com.hebo.codex.domain.RemoteFileMetadata
import com.hebo.codex.domain.SandboxMode
import com.hebo.codex.domain.SortDirection
import com.hebo.codex.domain.ThreadSortKey
import com.hebo.codex.domain.ThreadSourceKind
import com.hebo.codex.domain.ThreadOptions
import com.hebo.codex.domain.TurnOptions
import com.hebo.codex.domain.UserInputPart
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.util.Base64

enum class StableRpcMethod(val wireName: String) {
    THREAD_FORK("thread/fork"),
    THREAD_ARCHIVE("thread/archive"),
    THREAD_DELETE("thread/delete"),
    THREAD_UNSUBSCRIBE("thread/unsubscribe"),
    THREAD_NAME_SET("thread/name/set"),
    THREAD_GOAL_SET("thread/goal/set"),
    THREAD_GOAL_GET("thread/goal/get"),
    THREAD_GOAL_CLEAR("thread/goal/clear"),
    THREAD_UNARCHIVE("thread/unarchive"),
    THREAD_COMPACT("thread/compact/start"),
    SKILLS_LIST("skills/list"),
    SKILLS_EXTRA_ROOTS_SET("skills/extraRoots/set"),
    SKILLS_CONFIG_WRITE("skills/config/write"),
    HOOKS_LIST("hooks/list"),
    MARKETPLACE_ADD("marketplace/add"),
    MARKETPLACE_REMOVE("marketplace/remove"),
    MARKETPLACE_UPGRADE("marketplace/upgrade"),
    PLUGIN_LIST("plugin/list"),
    PLUGIN_INSTALLED("plugin/installed"),
    PLUGIN_READ("plugin/read"),
    PLUGIN_SKILL_READ("plugin/skill/read"),
    PLUGIN_SHARE_SAVE("plugin/share/save"),
    PLUGIN_SHARE_UPDATE_TARGETS("plugin/share/updateTargets"),
    PLUGIN_SHARE_LIST("plugin/share/list"),
    PLUGIN_SHARE_CHECKOUT("plugin/share/checkout"),
    PLUGIN_SHARE_DELETE("plugin/share/delete"),
    PLUGIN_INSTALL("plugin/install"),
    PLUGIN_UNINSTALL("plugin/uninstall"),
    APP_LIST("app/list"),
    FS_GET_METADATA("fs/getMetadata"),
    FS_READ_FILE("fs/readFile"),
    REVIEW_START("review/start"),
    MODEL_LIST("model/list"),
    MODEL_PROVIDER_CAPABILITIES("modelProvider/capabilities/read"),
    PERMISSION_PROFILE_LIST("permissionProfile/list"),
    MCP_OAUTH_LOGIN("mcpServer/oauth/login"),
    MCP_CONFIG_RELOAD("config/mcpServer/reload"),
    MCP_STATUS_LIST("mcpServerStatus/list"),
    MCP_RESOURCE_READ("mcpServer/resource/read"),
    MCP_TOOL_CALL("mcpServer/tool/call"),
    ACCOUNT_LOGIN_START("account/login/start"),
    ACCOUNT_LOGIN_CANCEL("account/login/cancel"),
    ACCOUNT_LOGOUT("account/logout"),
    ACCOUNT_RATE_LIMITS("account/rateLimits/read"),
    ACCOUNT_RATE_LIMIT_RESET("account/rateLimitResetCredit/consume"),
    ACCOUNT_USAGE("account/usage/read"),
    ACCOUNT_WORKSPACE_MESSAGES("account/workspaceMessages/read"),
    ACCOUNT_SEND_CREDITS_NUDGE("account/sendAddCreditsNudgeEmail"),
    ACCOUNT_READ("account/read"),
    FEEDBACK_UPLOAD("feedback/upload"),
    CONFIG_READ("config/read"),
    CONFIG_VALUE_WRITE("config/value/write"),
    CONFIG_BATCH_WRITE("config/batchWrite"),
    CONFIG_REQUIREMENTS_READ("configRequirements/read"),
}

class AppServerApi(
    private val connection: AppServerConnection,
) {
    suspend fun listThreads(
        cursor: String? = null,
        cwd: String? = null,
        archived: Boolean = false,
        searchTerm: String? = null,
        sortKey: ThreadSortKey = ThreadSortKey.RECENCY_AT,
        sortDirection: SortDirection = SortDirection.DESC,
        modelProviders: List<String> = emptyList(),
        sourceKinds: Set<ThreadSourceKind> = emptySet(),
        limit: Int = THREAD_PAGE_LIMIT,
    ): JsonObject = connection.request(
        "thread/list",
        buildJsonObject {
            put("limit", limit)
            put("sortKey", sortKey.wireValue)
            put("sortDirection", sortDirection.wireValue)
            cursor?.let { put("cursor", it) }
            cwd?.let { put("cwd", it) }
            if (archived) put("archived", true)
            searchTerm?.takeIf(String::isNotBlank)?.let { put("searchTerm", it) }
            if (modelProviders.isNotEmpty()) {
                put("modelProviders", buildJsonArray { modelProviders.forEach { add(JsonPrimitive(it)) } })
            }
            if (sourceKinds.isNotEmpty()) {
                put("sourceKinds", buildJsonArray { sourceKinds.forEach { add(JsonPrimitive(it.wireValue)) } })
            }
        },
    ).jsonObject

    suspend fun startThread(cwd: String, options: ThreadOptions = ThreadOptions()): JsonObject = connection.request(
        "thread/start",
        buildJsonObject {
            put("cwd", cwd)
            put("serviceName", CLIENT_NAME)
            options.model?.let { put("model", it) }
            options.modelProvider?.let { put("modelProvider", it) }
            options.serviceTier?.let { put("serviceTier", it) }
            options.approvalPolicy?.let { put("approvalPolicy", it.wireValue) }
            options.approvalsReviewer?.let { put("approvalsReviewer", it.wireValue) }
            options.sandboxMode?.let { put("sandbox", it.wireValue) }
            options.configJson?.takeIf(String::isNotBlank)?.let {
                val config = Json.parseToJsonElement(it) as? JsonObject
                    ?: throw IllegalArgumentException("线程配置必须是 JSON 对象")
                put("config", config)
            }
            options.baseInstructions?.takeIf(String::isNotBlank)?.let { put("baseInstructions", it) }
            options.developerInstructions?.takeIf(String::isNotBlank)?.let { put("developerInstructions", it) }
            options.personality?.let { put("personality", it.wireValue) }
            if (options.ephemeral) put("ephemeral", true)
        },
    ).jsonObject

    suspend fun resumeThread(threadId: String): JsonObject = connection.request(
        "thread/resume",
        buildJsonObject { put("threadId", threadId) },
    ).jsonObject

    suspend fun forkThread(threadId: String, lastTurnId: String? = null): JsonObject = request(
        StableRpcMethod.THREAD_FORK,
        buildJsonObject {
            put("threadId", threadId)
            lastTurnId?.let { put("lastTurnId", it) }
        },
    ).jsonObject

    suspend fun startTurn(
        threadId: String,
        clientMessageId: String,
        input: List<UserInputPart>,
        options: TurnOptions = TurnOptions(),
    ): JsonObject = connection.request(
        "turn/start",
        buildTurnParams(threadId, clientMessageId, input, options),
    ).jsonObject

    suspend fun steerTurn(
        threadId: String,
        turnId: String,
        clientMessageId: String,
        input: List<UserInputPart>,
    ): JsonObject = connection.request(
        "turn/steer",
        buildJsonObject {
            put("threadId", threadId)
            put("expectedTurnId", turnId)
            put("clientUserMessageId", clientMessageId)
            put("input", input.toWire())
        },
    ).jsonObject

    suspend fun interruptTurn(threadId: String, turnId: String) {
        connection.request(
            "turn/interrupt",
            buildJsonObject {
                put("threadId", threadId)
                put("turnId", turnId)
            },
        )
    }

    suspend fun review(
        threadId: String,
        target: ReviewTarget,
        delivery: ReviewDelivery,
    ): JsonObject = request(
        StableRpcMethod.REVIEW_START,
        buildJsonObject {
            put("threadId", threadId)
            put("delivery", delivery.wireValue)
            put("target", target.toWire())
        },
    ).jsonObject

    suspend fun request(method: StableRpcMethod, params: JsonObject? = null): JsonElement =
        connection.request(method.wireName, params)

    suspend fun getFileMetadata(path: String): RemoteFileMetadata {
        val response = request(
            StableRpcMethod.FS_GET_METADATA,
            buildJsonObject { put("path", path) },
        ).jsonObject
        return RemoteFileMetadata(
            isDirectory = response.requiredBoolean("isDirectory"),
            isFile = response.requiredBoolean("isFile"),
            isSymlink = response.requiredBoolean("isSymlink"),
            createdAtMillis = response.requiredLong("createdAtMs"),
            modifiedAtMillis = response.requiredLong("modifiedAtMs"),
        )
    }

    suspend fun readFile(path: String): ByteArray {
        val encoded = request(
            StableRpcMethod.FS_READ_FILE,
            buildJsonObject { put("path", path) },
        ).jsonObject.requiredString("dataBase64")
        if (encoded.length > MAX_REMOTE_FILE_BASE64_LENGTH) {
            throw ProtocolException("远端文件超过客户端 $MAX_REMOTE_FILE_MEBIBYTES MiB 限制")
        }
        return try {
            Base64.getDecoder().decode(encoded)
        } catch (error: IllegalArgumentException) {
            throw ProtocolException("fs/readFile 返回了无效的 Base64 内容", error)
        }
    }

    private fun buildTurnParams(
        threadId: String,
        clientMessageId: String,
        input: List<UserInputPart>,
        options: TurnOptions,
    ): JsonObject = buildJsonObject {
        put("threadId", threadId)
        put("clientUserMessageId", clientMessageId)
        put("input", input.toWire())
        options.cwd?.takeIf(String::isNotBlank)?.let { put("cwd", it) }
        options.model?.let { put("model", it) }
        options.serviceTier?.let { put("serviceTier", it) }
        options.reasoningEffort?.let { put("effort", it) }
        options.reasoningSummary?.let { put("summary", it.wireValue) }
        options.personality?.let { put("personality", it.wireValue) }
        options.approvalPolicy?.let { put("approvalPolicy", it.wireValue) }
        options.approvalsReviewer?.let { put("approvalsReviewer", it.wireValue) }
        options.sandboxMode?.let { put("sandboxPolicy", options.toSandboxPolicyWire(it)) }
        options.outputSchema?.takeIf(String::isNotBlank)?.let {
            put("outputSchema", Json.parseToJsonElement(it))
        }
    }

    private fun List<UserInputPart>.toWire() = buildJsonArray {
        forEach { part ->
            add(
                buildJsonObject {
                    when (part) {
                        is UserInputPart.Text -> {
                            put("type", "text")
                            put("text", part.text)
                        }

                        is UserInputPart.Image -> {
                            put("type", "image")
                            put("url", part.url)
                        }

                        is UserInputPart.LocalImage -> {
                            put("type", "localImage")
                            put("path", part.path)
                        }

                        is UserInputPart.Skill -> {
                            put("type", "skill")
                            put("name", part.name)
                            put("path", part.path)
                        }

                        is UserInputPart.Mention -> {
                            put("type", "mention")
                            put("name", part.name)
                            put("path", part.path)
                        }
                    }
                },
            )
        }
    }

    private fun TurnOptions.toSandboxPolicyWire(mode: SandboxMode): JsonObject = buildJsonObject {
        when (mode) {
            SandboxMode.READ_ONLY -> {
                put("type", "readOnly")
                put("networkAccess", sandboxNetworkAccess)
            }

            SandboxMode.WORKSPACE_WRITE -> {
                put("type", "workspaceWrite")
                put(
                    "writableRoots",
                    buildJsonArray { sandboxWritableRoots.forEach { add(JsonPrimitive(it)) } },
                )
                put("networkAccess", sandboxNetworkAccess)
                put("excludeTmpdirEnvVar", sandboxExcludeTmpdirEnvVar)
                put("excludeSlashTmp", sandboxExcludeSlashTmp)
            }

            SandboxMode.DANGER_FULL_ACCESS -> put("type", "dangerFullAccess")
        }
    }

    private fun ReviewTarget.toWire(): JsonObject = buildJsonObject {
        when (this@toWire) {
            ReviewTarget.UncommittedChanges -> put("type", "uncommittedChanges")
            is ReviewTarget.BaseBranch -> {
                put("type", "baseBranch")
                put("branch", branch)
            }

            is ReviewTarget.Commit -> {
                put("type", "commit")
                put("sha", sha)
                title?.let { put("title", it) }
            }

            is ReviewTarget.Custom -> {
                put("type", "custom")
                put("instructions", instructions)
            }
        }
    }

    private companion object {
        const val THREAD_PAGE_LIMIT = 50
        const val CLIENT_NAME = "codex_android"
        private const val MAX_REMOTE_FILE_MEBIBYTES = 16
        private const val MAX_REMOTE_FILE_BYTES = MAX_REMOTE_FILE_MEBIBYTES * 1024 * 1024
        private const val MAX_REMOTE_FILE_BASE64_LENGTH = ((MAX_REMOTE_FILE_BYTES + 2) / 3) * 4
    }
}
