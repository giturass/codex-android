package com.hebo.codex.data.remote

import com.hebo.codex.domain.RuntimeActivity
import com.hebo.codex.domain.RuntimeActivityStatus
import com.hebo.codex.domain.RuntimeDiff
import com.hebo.codex.domain.RuntimeQueuedInput
import com.hebo.codex.domain.RuntimeStatusEvent
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class RuntimeQueuePage(
    val data: List<RuntimeQueuedInput>,
    val nextCursor: String?,
)

object RuntimeStatusMapper {
    private const val MAX_TEXT_LENGTH = 32_768

    fun notification(method: String, params: JsonObject): RuntimeStatusEvent? = when (method) {
        "thread/queue/changed" -> RuntimeStatusEvent.QueueChanged
        "turn/completed" -> RuntimeStatusEvent.TurnCompleted(params.requiredObject("turn").requiredString("id"))
        "turn/diff/updated" -> {
            val diff = params.requiredString("diff")
            RuntimeStatusEvent.DiffChanged(
                RuntimeDiff(
                    turnId = params.requiredString("turnId"),
                    diff = diff.take(MAX_TEXT_LENGTH),
                    truncated = diff.length > MAX_TEXT_LENGTH,
                ),
            )
        }
        "hook/started", "hook/completed" -> hook(params)
        "item/autoApprovalReview/started", "item/autoApprovalReview/completed" -> approvalReview(params)
        "item/mcpToolCall/progress" -> RuntimeStatusEvent.ActivityChanged(
            RuntimeActivity(
                id = "mcp:${params.requiredString("turnId")}:${params.requiredString("itemId")}",
                title = "工具调用",
                detail = params.requiredString("message").take(MAX_TEXT_LENGTH),
                status = RuntimeActivityStatus.RUNNING,
                turnId = params.requiredString("turnId"),
                truncated = params.requiredString("message").length > MAX_TEXT_LENGTH,
            ),
        )
        "item/completed" -> {
            val item = params.requiredObject("item")
            if (item.requiredString("type") == "mcpToolCall") {
                RuntimeStatusEvent.ActivityCompleted("mcp:${params.requiredString("turnId")}:${item.requiredString("id")}")
            } else {
                null
            }
        }
        "model/safetyBuffering/updated" -> RuntimeStatusEvent.ActivityChanged(
            RuntimeActivity(
                id = "safety:${params.requiredString("turnId")}",
                title = "模型安全检查",
                detail = (listOf(params.requiredString("model")) +
                    params.requiredArray("reasons").map { it.jsonPrimitive.content }).joinToString(" · "),
                status = if (params.requiredBoolean("showBufferingUi")) {
                    RuntimeActivityStatus.RUNNING
                } else {
                    RuntimeActivityStatus.COMPLETED
                },
                turnId = params.requiredString("turnId"),
            ),
        )
        "modelProvider/authRecoveryStarted", "modelProvider/authRecoveryCompleted" ->
            RuntimeStatusEvent.ActivityChanged(
                RuntimeActivity(
                    id = "auth:${params.requiredString("turnId")}:${params.requiredString("provider")}",
                    title = "账号认证恢复 · ${params.requiredString("provider")}",
                    detail = params.requiredString("message"),
                    status = if (method == "modelProvider/authRecoveryStarted") {
                        RuntimeActivityStatus.RUNNING
                    } else {
                        RuntimeActivityStatus.COMPLETED
                    },
                    turnId = params.requiredString("turnId"),
                ),
            )
        else -> null
    }

    fun queuePage(response: JsonObject): RuntimeQueuePage = RuntimeQueuePage(
        data = response.requiredArray("data").map { value ->
            val entry = value.jsonObject
            val description = entry.requiredArray("input").joinToString("\n") { input ->
                val part = input.jsonObject
                when (part.requiredString("type")) {
                    "text" -> part.requiredString("text")
                    "image" -> "已附加图片"
                    "localImage" -> "本地图片 · ${part.requiredString("path")}"
                    "audio" -> "已附加音频"
                    "localAudio" -> "本地音频 · ${part.requiredString("path")}"
                    "skill" -> "技能 ${part.requiredString("name")} · ${part.requiredString("path")}"
                    "mention" -> "引用 ${part.requiredString("name")} · ${part.requiredString("path")}"
                    else -> throw ProtocolException("未知的输入队列内容类型：${part.requiredString("type")}")
                }
            }
            RuntimeQueuedInput(
                id = entry.requiredString("id"),
                description = description.take(MAX_TEXT_LENGTH),
                truncated = description.length > MAX_TEXT_LENGTH,
            )
        },
        nextCursor = response.optionalString("nextCursor"),
    )

    private fun hook(params: JsonObject): RuntimeStatusEvent {
        val run = params.requiredObject("run")
        val entries = run.requiredArray("entries").map { it.jsonObject }
        val text = entries.joinToString("\n") { it.requiredString("text") }
        val status = when (run.requiredString("status")) {
            "running" -> RuntimeActivityStatus.RUNNING
            "failed" -> RuntimeActivityStatus.FAILED
            "blocked", "stopped" -> RuntimeActivityStatus.WARNING
            "completed" -> if (entries.any { it.requiredString("kind") in setOf("warning", "error", "stop") }) {
                RuntimeActivityStatus.WARNING
            } else {
                RuntimeActivityStatus.COMPLETED
            }
            else -> throw ProtocolException("未知 Hook 运行状态：${run.requiredString("status")}")
        }
        return RuntimeStatusEvent.ActivityChanged(
            RuntimeActivity(
                id = "hook:${run.requiredString("id")}",
                title = "Hook · ${hookEventLabel(run.requiredString("eventName"))}",
                detail = run.optionalString("statusMessage").orEmpty(),
                status = status,
                turnId = params.optionalString("turnId"),
                text = text.take(MAX_TEXT_LENGTH).ifEmpty { null },
                truncated = text.length > MAX_TEXT_LENGTH,
            ),
        )
    }

    private fun approvalReview(params: JsonObject): RuntimeStatusEvent {
        val review = params.requiredObject("review")
        val (status, result) = when (review.requiredString("status")) {
            "inProgress" -> RuntimeActivityStatus.RUNNING to "正在审查操作"
            "approved" -> RuntimeActivityStatus.COMPLETED to "审查已通过"
            "denied" -> RuntimeActivityStatus.FAILED to "操作被拒绝"
            "timedOut" -> RuntimeActivityStatus.WARNING to "审查超时"
            "aborted" -> RuntimeActivityStatus.WARNING to "审查已中止"
            else -> throw ProtocolException("未知自动审批审查状态：${review.requiredString("status")}")
        }
        return RuntimeStatusEvent.ActivityChanged(
            RuntimeActivity(
                id = "review:${params.requiredString("reviewId")}",
                title = "自动审批审查",
                detail = listOfNotNull(result, review.optionalString("rationale")).joinToString(" · "),
                status = status,
                turnId = params.requiredString("turnId"),
            ),
        )
    }

    private fun hookEventLabel(event: String): String = when (event) {
        "preToolUse" -> "工具调用前"
        "permissionRequest" -> "权限请求时"
        "postToolUse" -> "工具调用后"
        "preCompact" -> "上下文压缩前"
        "postCompact" -> "上下文压缩后"
        "sessionStart" -> "会话开始时"
        "sessionEnd" -> "会话结束时"
        "userPromptSubmit" -> "用户提交消息时"
        "subagentStart" -> "子 Agent 开始时"
        "subagentStop" -> "子 Agent 结束时"
        "stop" -> "任务停止时"
        "interrupt" -> "任务中断时"
        else -> throw ProtocolException("未知 Hook 事件：$event")
    }
}
