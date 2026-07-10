package com.hebo.codex.data.remote

import com.hebo.codex.domain.ActivityStatus
import com.hebo.codex.domain.ChangedFile
import com.hebo.codex.domain.ConversationItem
import com.hebo.codex.domain.FileChangeKind
import com.hebo.codex.domain.ThreadRuntimeStatus
import com.hebo.codex.domain.ThreadSummary
import com.hebo.codex.domain.TokenUsage
import com.hebo.codex.domain.TurnPlan
import com.hebo.codex.domain.TurnPlanStep
import com.hebo.codex.domain.PlanStepStatus
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
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
            val turnCompleted = when (val status = turn.requiredString("status")) {
                "inProgress" -> false
                "completed", "interrupted", "failed" -> true
                else -> throw ProtocolException("未知 turn 状态：$status")
            }
            turn.requiredArray("items").map { itemElement ->
                conversationItem(itemElement.jsonObject, turnId, isCompleted = turnCompleted)
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

    fun turnPlan(params: JsonObject): TurnPlan = TurnPlan(
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
        val total = usage.requiredObject("total")
        return TokenUsage(
            totalTokens = total.optionalLong("totalTokens") ?: 0L,
            inputTokens = total.optionalLong("inputTokens") ?: 0L,
            cachedInputTokens = total.optionalLong("cachedInputTokens") ?: 0L,
            outputTokens = total.optionalLong("outputTokens") ?: 0L,
            reasoningOutputTokens = total.optionalLong("reasoningOutputTokens") ?: 0L,
            modelContextWindow = usage.optionalLong("modelContextWindow"),
        )
    }

    fun conversationItem(item: JsonObject, turnId: String, isCompleted: Boolean): ConversationItem {
        val id = item.requiredString("id")
        return when (val type = item.requiredString("type")) {
            "userMessage" -> ConversationItem.UserMessage(
                id = id,
                turnId = turnId,
                clientId = item.optionalString("clientId"),
                text = item.requiredArray("content").joinToString("\n", transform = ::userInputText),
            )

            "agentMessage" -> ConversationItem.AgentMessage(
                id = id,
                turnId = turnId,
                text = item.requiredString("text"),
                isStreaming = !isCompleted,
            )

            "reasoning" -> ConversationItem.Reasoning(
                id = id,
                turnId = turnId,
                summary = item.stringArray("summary").joinToString("\n"),
                details = item.stringArray("content").joinToString("\n"),
                isStreaming = !isCompleted,
            )

            "commandExecution" -> ConversationItem.CommandExecution(
                id = id,
                turnId = turnId,
                command = item.requiredString("command"),
                cwd = item.requiredString("cwd"),
                output = item.optionalString("aggregatedOutput").orEmpty(),
                exitCode = item["exitCode"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.intOrNull,
                status = activityStatus(item.optionalString("status"), isCompleted),
            )

            "fileChange" -> ConversationItem.FileChange(
                id = id,
                turnId = turnId,
                changes = item.requiredArray("changes").map(::changedFile),
                status = activityStatus(item.optionalString("status"), isCompleted),
            )

            "plan" -> ConversationItem.Activity(
                id = id,
                turnId = turnId,
                title = "计划",
                details = item.requiredString("text"),
                status = if (isCompleted) ActivityStatus.COMPLETED else ActivityStatus.IN_PROGRESS,
            )

            "mcpToolCall" -> genericActivity(
                item = item,
                turnId = turnId,
                title = "MCP · ${item.optionalString("server").orEmpty()}/${item.optionalString("tool").orEmpty()}",
                details = item["arguments"].compactText(),
                isCompleted = isCompleted,
            )

            "dynamicToolCall" -> genericActivity(
                item = item,
                turnId = turnId,
                title = "工具 · ${item.optionalString("tool").orEmpty()}",
                details = item["arguments"].compactText(),
                isCompleted = isCompleted,
            )

            "collabAgentToolCall" -> genericActivity(
                item = item,
                turnId = turnId,
                title = "协作代理 · ${item.optionalString("tool").orEmpty()}",
                details = item.optionalString("prompt").orEmpty(),
                isCompleted = isCompleted,
            )

            "webSearch" -> genericActivity(
                item = item,
                turnId = turnId,
                title = "网页搜索",
                details = item["action"].compactText(),
                isCompleted = isCompleted,
            )

            "contextCompaction" -> ConversationItem.Activity(
                id = id,
                turnId = turnId,
                title = "压缩上下文",
                details = "",
                status = if (isCompleted) ActivityStatus.COMPLETED else ActivityStatus.IN_PROGRESS,
            )

            "enteredReviewMode", "exitedReviewMode" -> ConversationItem.Activity(
                id = id,
                turnId = turnId,
                title = if (type == "enteredReviewMode") "进入审查" else "完成审查",
                details = item.optionalString("review").orEmpty(),
                status = if (isCompleted) ActivityStatus.COMPLETED else ActivityStatus.IN_PROGRESS,
            )

            "hookPrompt", "subAgentActivity", "imageView", "imageGeneration", "sleep" ->
                genericActivity(
                    item = item,
                    turnId = turnId,
                    title = activityTitle(type),
                    details = item.compactText(),
                    isCompleted = isCompleted,
                )

            else -> genericActivity(
                item = item,
                turnId = turnId,
                title = type,
                details = item.compactText(),
                isCompleted = isCompleted,
            )
        }
    }

    private fun genericActivity(
        item: JsonObject,
        turnId: String,
        title: String,
        details: String,
        isCompleted: Boolean,
    ) = ConversationItem.Activity(
        id = item.requiredString("id"),
        turnId = turnId,
        title = title,
        details = details,
        status = activityStatus(item.optionalString("status"), isCompleted),
    )

    private fun userInputText(input: JsonElement): String {
        val value = input.jsonObject
        return when (value.requiredString("type")) {
            "text" -> value.requiredString("text")
            "image" -> "[图片] ${value.requiredString("url")}"
            "localImage" -> "[本地图片] ${value.requiredString("path")}"
            "skill" -> "[技能] ${value.requiredString("name")}"
            "mention" -> "[引用] ${value.compactText()}"
            else -> value.compactText()
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

    private fun activityStatus(value: String?, isCompleted: Boolean): ActivityStatus = when (value) {
        "inProgress" -> ActivityStatus.IN_PROGRESS
        "completed" -> ActivityStatus.COMPLETED
        "failed" -> ActivityStatus.FAILED
        "declined" -> ActivityStatus.DECLINED
        null -> if (isCompleted) ActivityStatus.COMPLETED else ActivityStatus.IN_PROGRESS
        else -> throw ProtocolException("未知活动状态：$value")
    }

    private fun JsonObject.toThreadStatus(): ThreadRuntimeStatus = when (requiredString("type")) {
        "notLoaded" -> ThreadRuntimeStatus.NOT_LOADED
        "idle" -> ThreadRuntimeStatus.IDLE
        "active" -> ThreadRuntimeStatus.ACTIVE
        "systemError" -> ThreadRuntimeStatus.SYSTEM_ERROR
        else -> throw ProtocolException("未知会话状态：${requiredString("type")}")
    }

    private fun activityTitle(type: String): String = when (type) {
        "hookPrompt" -> "Hook 提示"
        "subAgentActivity" -> "子代理活动"
        "imageView" -> "查看图片"
        "imageGeneration" -> "生成图片"
        "sleep" -> "等待"
        else -> type
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

internal fun JsonObject.optionalLong(name: String): Long? =
    (this[name] as? JsonPrimitive)?.longOrNull

internal fun JsonObject.optionalBoolean(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()

internal fun JsonObject.requiredObject(name: String): JsonObject =
    this[name] as? JsonObject ?: throw ProtocolException("协议字段 $name 缺失或不是对象")

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
