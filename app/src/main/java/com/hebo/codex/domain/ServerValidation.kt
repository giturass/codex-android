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

fun validateCapabilityToken(value: String): String = value.trim().also {
    require(it.isNotEmpty()) { "请输入 Token" }
    require(it.all { character -> character.code in 0x21..0x7e }) { "Token 包含无效字符" }
    require(it.toByteArray().size <= 16 * 1024) { "Token 过长" }
}

fun validateProxyName(value: String): String = value.trim().also {
    require(it.isNotEmpty()) { "请输入代理名称" }
    require(it.toByteArray().size <= 128) { "代理名称过长" }
}

fun validateProxyConfiguration(value: ProxyConfiguration): ProxyConfiguration = when (value) {
    is ProxyConfiguration.HttpConnect -> {
        val url = validateHttpProxyUrl(value.url)
        val username = when (value.authentication) {
            HttpProxyAuthentication.BASIC -> validateUsername(value.username, "请输入代理用户名").also {
                require(!it.contains(':')) { "HTTP Basic 用户名不能包含冒号" }
            }
            HttpProxyAuthentication.NONE, HttpProxyAuthentication.BEARER -> null
        }
        validateProxyHeaders(value.headers)
        value.copy(
            url = url,
            username = username,
            headers = value.headers.mapKeys { it.key.trim() }.mapValues { it.value.trim() },
            connectTimeoutMillis = validateProxyTimeout(value.connectTimeoutMillis),
        )
    }

    is ProxyConfiguration.Socks5 -> value.copy(
        host = validateHost(value.host),
        port = validatePort(value.port),
        username = when (value.authentication) {
            Socks5Authentication.NONE -> null
            Socks5Authentication.USERNAME_PASSWORD -> validateUsername(value.username, "请输入代理用户名")
        },
        connectTimeoutMillis = validateProxyTimeout(value.connectTimeoutMillis),
    )

    is ProxyConfiguration.Ssh -> {
        val privateKeyUri = when (value.authentication) {
            SshAuthentication.PRIVATE_KEY -> value.privateKeyUri?.trim()?.also {
                require(URI(it).scheme == "content") { "请选择 Android 文档提供器中的私钥" }
            } ?: throw IllegalArgumentException("请选择 SSH 私钥")
            SshAuthentication.PASSWORD -> null
        }
        value.copy(
            host = validateHost(value.host),
            port = validatePort(value.port),
            username = validateUsername(value.username, "请输入 SSH 用户名"),
            privateKeyUri = privateKeyUri,
            connectTimeoutMillis = validateProxyTimeout(value.connectTimeoutMillis),
            keepAliveIntervalMillis = value.keepAliveIntervalMillis.also {
                require(it in 1_000..300_000) { "SSH 保活间隔必须在 1 到 300 秒之间" }
            },
            keepAliveMaxFailures = value.keepAliveMaxFailures.also {
                require(it in 1..10) { "SSH 保活失败次数必须在 1 到 10 之间" }
            },
        )
    }
}

fun parseProxyHeaders(value: String): Map<String, String> {
    if (value.isBlank()) return emptyMap()
    val entries = value.lineSequence().filter(String::isNotBlank).map { line ->
        val separator = line.indexOf(':')
        require(separator > 0) { "自定义请求头必须使用“名称: 值”格式" }
        line.substring(0, separator).trim() to line.substring(separator + 1).trim()
    }.toList()
    require(entries.map { it.first.lowercase() }.distinct().size == entries.size) { "自定义请求头名称不能重复" }
    return entries.toMap().also(::validateProxyHeaders)
}

fun ProxyConfiguration.requiresCredential(): Boolean = when (this) {
    is ProxyConfiguration.HttpConnect -> authentication != HttpProxyAuthentication.NONE
    is ProxyConfiguration.Socks5 -> authentication != Socks5Authentication.NONE
    is ProxyConfiguration.Ssh -> authentication == SshAuthentication.PASSWORD
}

fun ProxyConfiguration.typeLabel(): String = when (this) {
    is ProxyConfiguration.HttpConnect -> "HTTP CONNECT"
    is ProxyConfiguration.Socks5 -> "SOCKS5"
    is ProxyConfiguration.Ssh -> "SSH"
}

fun ProxyConfiguration.addressSummary(): String = when (this) {
    is ProxyConfiguration.HttpConnect -> url
    is ProxyConfiguration.Socks5 -> formatAuthority(host, port)
    is ProxyConfiguration.Ssh -> "${username}@${formatAuthority(host, port)}"
}

private fun validateHttpProxyUrl(value: String): String {
    val normalized = value.trim()
    val uri = try {
        URI(normalized)
    } catch (error: Exception) {
        throw IllegalArgumentException("HTTP 代理 URL 格式无效", error)
    }
    require(uri.scheme == "http" || uri.scheme == "https") { "HTTP 代理 URL 必须使用 http:// 或 https://" }
    require(!uri.host.isNullOrBlank()) { "HTTP 代理 URL 缺少主机名" }
    require(uri.port == -1 || uri.port in 1..65_535) { "HTTP 代理端口无效" }
    require(uri.userInfo == null) { "代理 URL 不能包含用户名或密码" }
    require(uri.rawQuery == null && uri.fragment == null) { "代理 URL 不能包含查询参数或片段" }
    require(uri.path.isNullOrEmpty() || uri.path == "/") { "代理 URL 不能包含路径" }
    return normalized
}

private fun validateHost(value: String): String = value.trim().also {
    require(it.isNotEmpty()) { "请输入代理主机" }
    require(it.toByteArray().size <= 253) { "代理主机名过长" }
    require(!it.contains('/') && !it.contains('@') && !it.any(Char::isWhitespace)) { "代理主机格式无效" }
}

private fun validatePort(value: Int): Int = value.also {
    require(it in 1..65_535) { "代理端口必须在 1 到 65535 之间" }
}

private fun validateUsername(value: String?, message: String): String = value?.trim().orEmpty().also {
    require(it.isNotEmpty()) { message }
    require(it.toByteArray().size <= 255 && !it.any(Char::isISOControl)) { "代理用户名格式无效" }
}

private fun validateProxyTimeout(value: Long): Long = value.also {
    require(it in 1_000..120_000) { "连接超时必须在 1 到 120 秒之间" }
}

private fun validateProxyHeaders(headers: Map<String, String>) {
    require(headers.size <= 32) { "自定义请求头不能超过 32 个" }
    require(headers.keys.map { it.trim().lowercase() }.distinct().size == headers.size) {
        "自定义请求头名称不能重复"
    }
    val reserved = setOf(
        "host",
        "connection",
        "content-length",
        "proxy-authorization",
        "proxy-connection",
    )
    headers.forEach { (name, value) ->
        val normalized = name.trim()
        require(normalized.matches(Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]+"))) { "自定义请求头名称无效" }
        require(normalized.lowercase() !in reserved) { "不能覆盖保留的代理请求头：$normalized" }
        require(!value.contains('\r') && !value.contains('\n')) { "自定义请求头值不能换行" }
        require(value.all { character -> character == '\t' || character.code in 0x20..0xff }) {
            "自定义请求头值包含无效字符"
        }
        require(value.toByteArray().size <= 8 * 1024) { "自定义请求头值过长" }
    }
}

private fun formatAuthority(host: String, port: Int): String =
    if (host.contains(':')) "[$host]:$port" else "$host:$port"
