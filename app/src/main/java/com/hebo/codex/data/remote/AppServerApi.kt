package com.hebo.codex.data.remote

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

class AppServerApi(
    private val connection: AppServerConnection,
) {
    suspend fun listRecentThreads(cwd: String? = null): List<JsonObject> {
        val result = connection.request(
            "thread/list",
            buildJsonObject {
                put("limit", RECENT_THREAD_LIMIT)
                put("sortKey", "recency_at")
                put("sortDirection", "desc")
                if (cwd != null) put("cwd", cwd)
            },
        ).jsonObject
        return result.requiredArray("data").map { it.jsonObject }
    }

    suspend fun startThread(cwd: String): JsonObject = connection.request(
        "thread/start",
        buildJsonObject {
            put("cwd", cwd)
            put("serviceName", "codex_android")
        },
    ).jsonObject

    suspend fun resumeThread(threadId: String): JsonObject = connection.request(
        "thread/resume",
        buildJsonObject { put("threadId", threadId) },
    ).jsonObject

    suspend fun startTurn(threadId: String, clientMessageId: String, text: String): JsonObject =
        connection.request(
            "turn/start",
            buildJsonObject {
                put("threadId", threadId)
                put("clientUserMessageId", clientMessageId)
                put(
                    "input",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("type", "text")
                                put("text", text)
                            },
                        )
                    },
                )
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

    private companion object {
        const val RECENT_THREAD_LIMIT = 100
    }
}
