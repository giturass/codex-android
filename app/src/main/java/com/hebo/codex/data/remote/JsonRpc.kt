package com.hebo.codex.data.remote

import com.hebo.codex.domain.ThreadHistoryMode
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.IOException

sealed interface ServerEvent {
    data class Notification(
        val method: String,
        val params: JsonObject,
    ) : ServerEvent

    data class Request(
        val id: JsonPrimitive,
        val method: String,
        val params: JsonObject,
    ) : ServerEvent
}

class RpcException(
    val code: Int,
    override val message: String,
    val data: JsonElement?,
) : Exception(message)

class ProtocolException(message: String, cause: Throwable? = null) : Exception(message, cause)

class UnsupportedThreadHistoryModeException(
    val historyMode: ThreadHistoryMode,
) : Exception("不支持 ${historyMode.name.lowercase()} 历史格式")

class ConnectionException(
    message: String,
    cause: Throwable? = null,
    val rejected: Boolean = false,
) : IOException(message, cause)
