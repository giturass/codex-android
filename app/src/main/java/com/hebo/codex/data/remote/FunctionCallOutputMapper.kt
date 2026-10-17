package com.hebo.codex.data.remote

import com.hebo.codex.domain.ActivityKind
import com.hebo.codex.domain.ActivityStatus
import com.hebo.codex.domain.ConversationItem
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal fun functionCallOutput(item: JsonObject, turnId: String): ConversationItem.Activity {
    val details = when (val output = item["output"]) {
        is JsonPrimitive -> {
            if (!output.isString) throw ProtocolException("工具输出必须是字符串或内容数组")
            output.content
        }
        is JsonArray -> output.joinToString("\n") { element ->
            val content = element as? JsonObject ?: throw ProtocolException("工具输出内容不是对象")
            when (val type = content.requiredString("type")) {
                "input_text" -> content.requiredString("text")
                "input_image" -> "[图片]"
                "input_audio" -> "[音频]"
                "encrypted_content" -> "[加密内容]"
                else -> throw ProtocolException("未知工具输出内容类型：$type")
            }
        }
        else -> throw ProtocolException("工具输出必须是字符串或内容数组")
    }
    return ConversationItem.Activity(
        id = item.requiredString("id"),
        turnId = turnId,
        kind = ActivityKind.TOOL_OUTPUT,
        title = item.requiredString("name"),
        summary = item.optionalString("namespace"),
        details = details,
        status = ActivityStatus.COMPLETED,
    )
}
