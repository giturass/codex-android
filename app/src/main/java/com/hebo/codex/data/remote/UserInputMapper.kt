package com.hebo.codex.data.remote

import com.hebo.codex.domain.InputByteRange
import com.hebo.codex.domain.InputTextElement
import com.hebo.codex.domain.UserInputPart
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

internal fun userInputPart(value: JsonObject): UserInputPart = when (val type = value.requiredString("type")) {
    "text" -> UserInputPart.Text(
        value.requiredString("text"),
        value["text_elements"]?.let { it as? JsonArray ?: throw ProtocolException("text_elements 必须为数组") }?.map { element ->
            val item = element.jsonObject
            val range = item.requiredObject("byteRange")
            InputTextElement(InputByteRange(range.requiredInt("start"), range.requiredInt("end")), item.optionalString("placeholder"))
        }.orEmpty(),
    )
    "image" -> if (value.optionalString("url") != null) {
        UserInputPart.Image(value.requiredString("url"), value.optionalString("detail"))
    } else {
        UserInputPart.UploadedImage(value.requiredString("fileId"), value.optionalString("detail"))
    }
    "localImage" -> UserInputPart.LocalImage(value.requiredString("path"), value.optionalString("detail"))
    "audio" -> UserInputPart.Audio(value.requiredString("url"))
    "localAudio" -> UserInputPart.LocalAudio(value.requiredString("path"))
    "skill" -> UserInputPart.Skill(value.requiredString("name"), value.requiredString("path"))
    "mention" -> UserInputPart.Mention(value.requiredString("name"), value.requiredString("path"))
    else -> throw ProtocolException("未知的输入类型：$type")
}

internal fun List<UserInputPart>.toWire(): JsonArray = JsonArray(map { part ->
    buildJsonObject {
        when (part) {
            is UserInputPart.Text -> {
                put("type", "text")
                put("text", part.text)
                put("text_elements", Json.encodeToJsonElement(part.textElements))
            }
            is UserInputPart.Image -> {
                put("type", "image")
                put("url", part.url)
                part.detail?.let { put("detail", it) }
            }
            is UserInputPart.UploadedImage -> {
                put("type", "image")
                put("fileId", part.fileId)
                part.detail?.let { put("detail", it) }
            }
            is UserInputPart.LocalImage -> {
                put("type", "localImage")
                put("path", part.path)
                part.detail?.let { put("detail", it) }
            }
            is UserInputPart.Audio -> {
                put("type", "audio")
                put("url", part.url)
            }
            is UserInputPart.LocalAudio -> {
                put("type", "localAudio")
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
    }
})
