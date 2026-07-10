package com.hebo.codex.data.remote

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

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

