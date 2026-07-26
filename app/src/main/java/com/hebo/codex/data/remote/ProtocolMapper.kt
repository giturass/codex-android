package com.hebo.codex.data.remote

import com.hebo.codex.domain.ActivityKind
import com.hebo.codex.domain.ActivityStatus
import com.hebo.codex.domain.AgentMessagePhase
import com.hebo.codex.domain.ChangedFile
import com.hebo.codex.domain.CommandAction
import com.hebo.codex.domain.ConversationItem
import com.hebo.codex.domain.ExecPolicyAmendment
import com.hebo.codex.domain.FileChangeKind
import com.hebo.codex.domain.FileSystemAccessMode
import com.hebo.codex.domain.FileSystemPermissionEntry
import com.hebo.codex.domain.FileSystemPermissionTarget
import com.hebo.codex.domain.NetworkPolicyAmendment
import com.hebo.codex.domain.NetworkPolicyRuleAction
import com.hebo.codex.domain.ThreadRuntimeStatus
import com.hebo.codex.domain.ThreadSummary
import com.hebo.codex.domain.TokenUsage
import com.hebo.codex.domain.TokenUsageBreakdown
import com.hebo.codex.domain.ActiveTurnPlan
import com.hebo.codex.domain.TurnPlanStep
import com.hebo.codex.domain.PlanStepStatus
import com.hebo.codex.domain.RequestedFileSystemPermissions
import com.hebo.codex.domain.RequestedPermissions
import com.hebo.codex.domain.UserMessagePart
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

data class ThreadSnapshot(
    val summary: ThreadSummary,
    val items: List<ConversationItem>,
    val activeTurnId: String?,
    val model: String?,
    val serviceTier: String?,
    val reasoningEffort: String?,
    val approvalPolicy: String?,
    val approvalsReviewer: String?,
    val sandbox: String?,
)

object ProtocolMapper {
    fun threadSummary(thread: JsonObject): ThreadSummary {
        val preview = thread.requiredString("preview")
        val name = thread.optionalString("name")?.trim().orEmpty()
        return ThreadSummary(
            id = thread.requiredString("id"),
            sessionId = thread.requiredString("sessionId"),
            title = name.ifEmpty { preview.lineSequence().firstOrNull().orEmpty().ifEmpty { "新会话" } },
            preview = preview,
            cwd = thread.requiredString("cwd"),
            createdAt = thread.requiredLong("createdAt"),
            updatedAt = thread.requiredLong("updatedAt"),
            recencyAt = thread.optionalLong("recencyAt"),
            status = thread.requiredObject("status").toThreadStatus(),
            parentThreadId = thread.optionalString("parentThreadId"),
            forkedFromId = thread.optionalString("forkedFromId"),
            ephemeral = thread.optionalBoolean("ephemeral") ?: false,
            modelProvider = thread.optionalString("modelProvider").orEmpty(),
            source = thread["source"].compactText(),
        )
    }

    fun threadSnapshot(response: JsonObject): ThreadSnapshot {
        val thread = response.requiredObject("thread")
        val turns = thread.requiredArray("turns")
        val items = turns.flatMap { turnElement ->
            val turn = turnElement.jsonObject
            val turnId = turn.requiredString("id")
            when (val status = turn.requiredString("status")) {
                "inProgress", "completed", "interrupted", "failed" -> Unit
                else -> throw ProtocolException("未知 turn 状态：$status")
            }
            turn.requiredArray("items").map { itemElement ->
                conversationItem(itemElement.jsonObject, turnId)
            }
        }
        val activeTurnId = turns
            .map { it.jsonObject }
            .lastOrNull { it.requiredString("status") == "inProgress" }
            ?.requiredString("id")
        return ThreadSnapshot(
            summary = threadSummary(thread),
            items = items,
            activeTurnId = activeTurnId,
            model = response.optionalString("model"),
            serviceTier = response.optionalString("serviceTier"),
            reasoningEffort = response.optionalString("reasoningEffort"),
            approvalPolicy = response["approvalPolicy"].compactText().ifEmpty { null },
            approvalsReviewer = response.optionalString("approvalsReviewer"),
            sandbox = response["sandbox"].compactText().ifEmpty { null },
        )
    }

    fun activeTurnPlan(params: JsonObject): ActiveTurnPlan = ActiveTurnPlan(
        turnId = params.requiredString("turnId"),
        explanation = params.optionalString("explanation"),
        steps = params.requiredArray("plan").map { element ->
            val step = element.jsonObject
            TurnPlanStep(
                text = step.requiredString("step"),
                status = when (step.requiredString("status")) {
                    "pending" -> PlanStepStatus.PENDING
                    "inProgress" -> PlanStepStatus.IN_PROGRESS
                    "completed" -> PlanStepStatus.COMPLETED
                    else -> throw ProtocolException("未知计划状态：${step.requiredString("status")}")
                },
            )
        },
    )

    fun tokenUsage(params: JsonObject): TokenUsage {
        val usage = params.requiredObject("tokenUsage")
        return TokenUsage(
            total = usage.requiredObject("total").tokenUsageBreakdown(),
            last = usage.requiredObject("last").tokenUsageBreakdown(),
            modelContextWindow = usage.optionalLong("modelContextWindow"),
        )
    }

    private fun JsonObject.tokenUsageBreakdown() = TokenUsageBreakdown(
        totalTokens = requiredLong("totalTokens"),
        inputTokens = requiredLong("inputTokens"),
        cachedInputTokens = requiredLong("cachedInputTokens"),
        outputTokens = requiredLong("outputTokens"),
        reasoningOutputTokens = requiredLong("reasoningOutputTokens"),
    )

    fun commandActions(value: JsonElement?): List<CommandAction> = value
        .optionalArray("commandActions")
        .map { element ->
            val action = element.jsonObject
            val command = action.requiredString("command")
            when (action.requiredString("type")) {
                "read" -> CommandAction.Read(command, action.requiredString("name"), action.requiredString("path"))
                "listFiles" -> CommandAction.ListFiles(command, action.optionalString("path"))
                "search" -> CommandAction.Search(
                    command = command,
                    query = action.optionalString("query"),
                    path = action.optionalString("path"),
                )

                "unknown" -> CommandAction.Unknown(command)
                else -> throw ProtocolException("未知命令行为：${action.requiredString("type")}")
            }
        }

    fun execPolicyAmendment(value: JsonElement?): ExecPolicyAmendment? {
        if (value == null || value is JsonNull) return null
        val commands = (value as? JsonArray)
            ?.map { it.jsonPrimitive.content }
            ?: throw ProtocolException("命令规则修订不是数组")
        return ExecPolicyAmendment(commands, value.toString())
    }

    fun networkPolicyAmendments(value: JsonElement?): List<NetworkPolicyAmendment> =
        value.optionalArray("proposedNetworkPolicyAmendments").map { element ->
            val amendment = element.jsonObject
            NetworkPolicyAmendment(
                host = amendment.requiredString("host"),
                action = when (amendment.requiredString("action")) {
                    "allow" -> NetworkPolicyRuleAction.ALLOW
                    "deny" -> NetworkPolicyRuleAction.DENY
                    else -> throw ProtocolException("未知网络规则操作：${amendment.requiredString("action")}")
                },
                responseJson = element.toString(),
            )
        }

    fun requestedPermissions(value: JsonObject): RequestedPermissions = RequestedPermissions(
        networkEnabled = value.requiredNullableObject("network")?.optionalBoolean("enabled"),
        fileSystem = value.requiredNullableObject("fileSystem")?.toRequestedFileSystemPermissions(),
        responseJson = value.toString(),
    )

    fun conversationItem(item: JsonObject, turnId: String): ConversationItem {
        val id = item.requiredString("id")
        return when (val type = item.requiredString("type")) {
            "userMessage" -> ConversationItem.UserMessage(
                id = id,
                turnId = turnId,
                content = item.requiredArray("content").map(::userMessagePart),
            )

            "agentMessage" -> ConversationItem.AgentMessage(
                id = id,
                turnId = turnId,
                text = item.requiredString("text"),
                phase = agentMessagePhase(item.optionalString("phase")),
            )

            "reasoning" -> ConversationItem.Reasoning(
                id = id,
                turnId = turnId,
                summaryParts = item.stringArray("summary"),
                rawContentParts = item.stringArray("content"),
            )

            "commandExecution" -> commandExecution(item, id, turnId)

            "fileChange" -> ConversationItem.FileChange(
                id = id,
                turnId = turnId,
                changes = item.requiredArray("changes").map(::changedFile),
                status = activityStatus(item.optionalString("status")),
            )

            "plan" -> ConversationItem.Plan(
                id = id,
                turnId = turnId,
                text = item.requiredString("text"),
            )

            "mcpToolCall" -> genericActivity(
                item = item,
                turnId = turnId,
                kind = ActivityKind.MCP_TOOL,
                title = item.requiredString("tool"),
                summary = item.requiredString("server"),
                details = (item["error"] as? JsonObject)?.optionalString("message").orEmpty(),
                technicalDetails = item.compactText(),
            )

            "dynamicToolCall" -> genericActivity(
                item = item,
                turnId = turnId,
                kind = ActivityKind.DYNAMIC_TOOL,
                title = item.requiredString("tool"),
                summary = item.optionalString("namespace"),
                details = item.dynamicToolOutputText(),
                technicalDetails = item.compactText(),
            )

            "collabAgentToolCall" -> genericActivity(
                item = item,
                turnId = turnId,
                kind = ActivityKind.COLLAB_AGENT,
                title = collabActivityTitle(item.requiredString("tool")),
                summary = item.optionalString("prompt"),
                technicalDetails = item.compactText(),
            )

            "webSearch" -> genericActivity(
                item = item,
                turnId = turnId,
                kind = ActivityKind.WEB_SEARCH,
                title = webSearchTitle(item),
                technicalDetails = item.compactText(),
            )

            "contextCompaction" -> ConversationItem.Activity(
                id = id,
                turnId = turnId,
                kind = ActivityKind.CONTEXT_COMPACTION,
                title = "压缩上下文",
                details = "",
                status = ActivityStatus.COMPLETED,
            )

            "enteredReviewMode", "exitedReviewMode" -> ConversationItem.Activity(
                id = id,
                turnId = turnId,
                kind = ActivityKind.REVIEW,
                title = if (type == "enteredReviewMode") "进入审查" else "完成审查",
                details = item.optionalString("review").orEmpty(),
                status = ActivityStatus.COMPLETED,
            )

            "hookPrompt" -> genericActivity(
                item = item,
                turnId = turnId,
                kind = ActivityKind.HOOK,
                title = "Hook 提示",
                details = item.requiredArray("fragments").joinToString("\n") { fragment ->
                    fragment.jsonObject.requiredString("text")
                },
                technicalDetails = item.compactText(),
            )

            "subAgentActivity" -> genericActivity(
                item = item,
                turnId = turnId,
                kind = ActivityKind.SUB_AGENT,
                title = subAgentActivityTitle(item.requiredString("kind")),
                summary = item.requiredString("agentPath"),
                technicalDetails = item.compactText(),
            )

            "imageView" -> genericActivity(
                item = item,
                turnId = turnId,
                kind = ActivityKind.IMAGE_VIEW,
                title = item.requiredString("path").fileName(),
                details = item.requiredString("path"),
                remotePath = item.requiredString("path"),
            )

            "imageGeneration" -> genericActivity(
                item = item,
                turnId = turnId,
                kind = ActivityKind.IMAGE_GENERATION,
                title = "生成图片",
                summary = item.optionalString("revisedPrompt"),
                details = item.optionalString("savedPath").orEmpty(),
                technicalDetails = item.withoutLargeImageResult(),
                remotePath = item.optionalString("savedPath"),
                status = imageGenerationStatus(item.requiredString("status")),
            )

            "sleep" -> genericActivity(
                item = item,
                turnId = turnId,
                kind = ActivityKind.WAIT,
                title = formatDurationMillis(item.requiredLong("durationMs")),
            )

            else -> genericActivity(
                item = item,
                turnId = turnId,
                kind = ActivityKind.UNKNOWN,
                title = "未知活动",
                summary = "客户端尚未支持此类活动",
                technicalDetails = item.compactText(),
            )
        }
    }

    private fun commandExecution(item: JsonObject, id: String, turnId: String): ConversationItem {
        val command = item.requiredString("command")
        val cwd = item.requiredString("cwd")
        val output = item.optionalString("aggregatedOutput").orEmpty()
        val exitCode = item["exitCode"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.intOrNull
        val status = activityStatus(item.optionalString("status"))
        return when (item.optionalString("source") ?: "agent") {
            "userShell" -> ConversationItem.UserShellCommand(
                id = id,
                turnId = turnId,
                command = command,
                cwd = cwd,
                output = output,
                exitCode = exitCode,
                durationMillis = item.optionalLong("durationMs"),
                status = status,
            )

            "agent", "unifiedExecStartup", "unifiedExecInteraction" -> ConversationItem.CommandExecution(
                id = id,
                turnId = turnId,
                command = command,
                cwd = cwd,
                output = output,
                exitCode = exitCode,
                status = status,
            )

            else -> throw ProtocolException("未知命令执行来源：${item.optionalString("source")}")
        }
    }

    private fun genericActivity(
        item: JsonObject,
        turnId: String,
        kind: ActivityKind,
        title: String,
        summary: String? = null,
        details: String = "",
        technicalDetails: String? = null,
        remotePath: String? = null,
        status: ActivityStatus = activityStatus(item.optionalString("status")),
    ) = ConversationItem.Activity(
        id = item.requiredString("id"),
        turnId = turnId,
        kind = kind,
        title = title,
        summary = summary,
        details = details,
        technicalDetails = technicalDetails,
        remotePath = remotePath,
        status = status,
    )

    private fun userMessagePart(input: JsonElement): UserMessagePart {
        val value = input.jsonObject
        return when (value.requiredString("type")) {
            "text" -> UserMessagePart.Text(value.requiredString("text"))
            "image" -> UserMessagePart.Image(value.requiredString("url"))
            "localImage" -> UserMessagePart.LocalImage(value.requiredString("path"))
            "skill" -> UserMessagePart.Skill(value.requiredString("name"), value.requiredString("path"))
            "mention" -> UserMessagePart.Mention(value.requiredString("name"), value.requiredString("path"))
            else -> throw ProtocolException("未知用户输入类型：${value.requiredString("type")}")
        }
    }

    private fun changedFile(element: JsonElement): ChangedFile {
        val value = element.jsonObject
        val kindValue = value.requiredObject("kind")
        val kind = when (val type = kindValue.requiredString("type")) {
            "add" -> FileChangeKind.Add
            "delete" -> FileChangeKind.Delete
            "update" -> FileChangeKind.Update(movePath = kindValue.optionalString("move_path"))
            else -> throw ProtocolException("未知文件修改类型：$type")
        }
        return ChangedFile(
            path = value.requiredString("path"),
            kind = kind,
            diff = value.requiredString("diff"),
        )
    }

    private fun JsonObject.toRequestedFileSystemPermissions(): RequestedFileSystemPermissions {
        val entries = requiredArray("entries").map { element ->
            val entry = element.jsonObject
            FileSystemPermissionEntry(
                target = entry.requiredObject("path").toFileSystemPermissionTarget(),
                access = when (entry.requiredString("access")) {
                    "read" -> FileSystemAccessMode.READ
                    "write" -> FileSystemAccessMode.WRITE
                    "deny" -> FileSystemAccessMode.DENY
                    else -> throw ProtocolException("未知文件权限：${entry.requiredString("access")}")
                },
            )
        }
        return RequestedFileSystemPermissions(
            entries = entries,
            globScanMaxDepth = optionalLong("globScanMaxDepth")?.toInt(),
        )
    }

    private fun JsonObject.toFileSystemPermissionTarget(): FileSystemPermissionTarget =
        when (requiredString("type")) {
            "path" -> FileSystemPermissionTarget.Path(requiredString("path"))
            "glob_pattern" -> FileSystemPermissionTarget.GlobPattern(requiredString("pattern"))
            "special" -> requiredObject("value").toSpecialFileSystemPermissionTarget()
            else -> throw ProtocolException("未知文件权限目标：${requiredString("type")}")
        }

    private fun JsonObject.toSpecialFileSystemPermissionTarget(): FileSystemPermissionTarget =
        when (requiredString("kind")) {
            "root" -> FileSystemPermissionTarget.Root
            "minimal" -> FileSystemPermissionTarget.Minimal
            "project_roots" -> FileSystemPermissionTarget.ProjectRoots(optionalString("subpath"))
            "tmpdir" -> FileSystemPermissionTarget.TempDirectory
            "slash_tmp" -> FileSystemPermissionTarget.SlashTmp
            "unknown" -> FileSystemPermissionTarget.Unknown(requiredString("path"), optionalString("subpath"))
            else -> throw ProtocolException("未知特殊文件权限目标：${requiredString("kind")}")
        }

    private fun activityStatus(value: String?): ActivityStatus = when (value) {
        "inProgress" -> ActivityStatus.IN_PROGRESS
        "completed" -> ActivityStatus.COMPLETED
        "failed" -> ActivityStatus.FAILED
        "declined" -> ActivityStatus.DECLINED
        null -> ActivityStatus.COMPLETED
        else -> throw ProtocolException("未知活动状态：$value")
    }

    private fun agentMessagePhase(value: String?): AgentMessagePhase? = when (value) {
        null -> null
        "commentary" -> AgentMessagePhase.COMMENTARY
        "final_answer" -> AgentMessagePhase.FINAL_ANSWER
        else -> throw ProtocolException("未知助手消息阶段：$value")
    }

    private fun imageGenerationStatus(value: String): ActivityStatus = when (value) {
        "in_progress" -> ActivityStatus.IN_PROGRESS
        "completed" -> ActivityStatus.COMPLETED
        "failed" -> ActivityStatus.FAILED
        else -> throw ProtocolException("未知图片生成状态：$value")
    }

    private fun JsonObject.toThreadStatus(): ThreadRuntimeStatus = when (requiredString("type")) {
        "notLoaded" -> ThreadRuntimeStatus.NOT_LOADED
        "idle" -> ThreadRuntimeStatus.IDLE
        "active" -> ThreadRuntimeStatus.ACTIVE
        "systemError" -> ThreadRuntimeStatus.SYSTEM_ERROR
        else -> throw ProtocolException("未知会话状态：${requiredString("type")}")
    }

    private fun collabActivityTitle(tool: String): String = when (tool) {
        "spawnAgent" -> "创建协作代理"
        "sendInput" -> "向协作代理发送消息"
        "resumeAgent" -> "恢复协作代理"
        "wait" -> "等待协作代理"
        "closeAgent" -> "关闭协作代理"
        else -> throw ProtocolException("未知协作代理操作：$tool")
    }

    private fun subAgentActivityTitle(kind: String): String = when (kind) {
        "started" -> "子代理已启动"
        "interacted" -> "子代理正在交互"
        "interrupted" -> "子代理已中断"
        else -> throw ProtocolException("未知子代理活动：$kind")
    }

    private fun webSearchTitle(item: JsonObject): String {
        val query = item.requiredString("query")
        val action = item["action"] as? JsonObject ?: return query
        return when (action.requiredString("type")) {
            "search" -> action.optionalString("query") ?: query
            "openPage" -> action.optionalString("url") ?: "搜索结果"
            "findInPage" -> action.optionalString("pattern") ?: "页面内容"
            "other" -> query
            else -> throw ProtocolException("未知网页搜索操作：${action.requiredString("type")}")
        }
    }

    private fun JsonObject.dynamicToolOutputText(): String = (this["contentItems"] as? JsonArray).orEmpty()
        .map { it.jsonObject }
        .joinToString("\n") { content ->
            when (content.requiredString("type")) {
                "inputText" -> content.requiredString("text")
                "inputImage" -> "[图片]"
                else -> throw ProtocolException("未知动态工具输出类型：${content.requiredString("type")}")
            }
        }

    private fun JsonObject.withoutLargeImageResult(): String = JsonObject(
        filterKeys { it != "result" },
    ).compactText()

    private fun String.fileName(): String = substringAfterLast('/').ifBlank { this }

    private fun formatDurationMillis(durationMillis: Long): String = when {
        durationMillis < 1_000 -> "$durationMillis 毫秒"
        durationMillis < 60_000 -> "${durationMillis / 1_000} 秒"
        else -> "${durationMillis / 60_000} 分钟"
    }

    private fun JsonElement?.optionalArray(fieldName: String): JsonArray = when (this) {
        null, JsonNull -> JsonArray(emptyList())
        is JsonArray -> this
        else -> throw ProtocolException("协议字段 $fieldName 不是数组")
    }
}

internal fun JsonObject.requiredString(name: String): String =
    (this[name] as? JsonPrimitive)?.contentOrNull
        ?: throw ProtocolException("协议字段 $name 缺失或不是字符串")

internal fun JsonObject.optionalString(name: String): String? =
    (this[name] as? JsonPrimitive)?.contentOrNull

internal fun JsonObject.requiredLong(name: String): Long =
    (this[name] as? JsonPrimitive)?.longOrNull
        ?: throw ProtocolException("协议字段 $name 缺失或不是整数")

internal fun JsonObject.requiredInt(name: String): Int =
    (this[name] as? JsonPrimitive)?.intOrNull
        ?: throw ProtocolException("协议字段 $name 缺失或不是 Int 范围内的整数")

internal fun JsonObject.optionalLong(name: String): Long? =
    (this[name] as? JsonPrimitive)?.longOrNull

internal fun JsonObject.requiredDouble(name: String): Double =
    (this[name] as? JsonPrimitive)?.doubleOrNull
        ?: throw ProtocolException("协议字段 $name 缺失或不是数字")

internal fun JsonObject.optionalBoolean(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()

internal fun JsonObject.requiredBoolean(name: String): Boolean =
    optionalBoolean(name) ?: throw ProtocolException("协议字段 $name 缺失或不是布尔值")

internal fun JsonObject.requiredObject(name: String): JsonObject =
    this[name] as? JsonObject ?: throw ProtocolException("协议字段 $name 缺失或不是对象")

internal fun JsonObject.requiredNullableObject(name: String): JsonObject? = when (val value = this[name]) {
    null -> throw ProtocolException("协议字段 $name 缺失")
    JsonNull -> null
    is JsonObject -> value
    else -> throw ProtocolException("协议字段 $name 不是对象或 null")
}

internal fun JsonObject.requiredArray(name: String): JsonArray =
    this[name] as? JsonArray ?: throw ProtocolException("协议字段 $name 缺失或不是数组")

private fun JsonObject.stringArray(name: String): List<String> =
    requiredArray(name).map { it.jsonPrimitive.content }

private fun JsonElement?.compactText(): String = when (this) {
    null, JsonNull -> ""
    is JsonPrimitive -> content
    else -> toString().take(MAX_ACTIVITY_DETAIL_LENGTH)
}

private const val MAX_ACTIVITY_DETAIL_LENGTH = 4_000
