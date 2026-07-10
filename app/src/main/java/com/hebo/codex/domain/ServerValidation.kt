package com.hebo.codex.domain

import java.net.URI

fun validateWebSocketEndpoint(value: String): String {
    val endpoint = value.trim()
    val uri = try {
        URI(endpoint)
    } catch (error: Exception) {
        throw IllegalArgumentException("WebSocket URL 格式无效", error)
    }
    require(uri.scheme == "ws" || uri.scheme == "wss") { "WebSocket URL 必须使用 ws:// 或 wss://" }
    require(!uri.host.isNullOrBlank()) { "WebSocket URL 缺少主机名" }
    require(uri.port == -1 || uri.port in 1..65_535) { "WebSocket URL 端口无效" }
    require(uri.userInfo == null) { "WebSocket URL 不能包含用户信息" }
    require(uri.fragment == null) { "WebSocket URL 不能包含片段" }
    return endpoint
}
