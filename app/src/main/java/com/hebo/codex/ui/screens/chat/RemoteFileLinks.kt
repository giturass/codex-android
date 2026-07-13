package com.hebo.codex.ui.screens.chat

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

internal data class RemoteFileTarget(
    val path: String,
    val line: Int? = null,
    val column: Int? = null,
)

internal sealed interface ChatLinkDestination {
    data class RemoteFile(val target: RemoteFileTarget) : ChatLinkDestination

    data class External(val uri: String) : ChatLinkDestination

    data class Invalid(val message: String) : ChatLinkDestination
}

internal fun resolveChatLink(destination: String, cwd: String): ChatLinkDestination {
    val value = destination.trim()
    if (value.isEmpty()) return ChatLinkDestination.Invalid("链接地址为空")
    if (value.startsWith('#')) return ChatLinkDestination.External(value)
    if (value.startsWith("//")) return ChatLinkDestination.External(value)

    val location = extractLocation(value)
    val isWindowsAbsolute = WINDOWS_ABSOLUTE_PATH.matchesAt(location.path, 0)
    val scheme = if (isWindowsAbsolute) null else URI_SCHEME.find(location.path)?.groupValues?.get(1)
    if (scheme != null && !scheme.equals("file", ignoreCase = true)) {
        return ChatLinkDestination.External(value)
    }
    if (location.path.startsWith("~/")) {
        return ChatLinkDestination.Invalid("无法解析远端服务器的用户主目录：$value")
    }

    val decodedPath = if (scheme.equals("file", ignoreCase = true)) {
        fileUriPath(location.path)
            ?: return ChatLinkDestination.Invalid("无效的文件链接：$value")
    } else {
        decodePath(location.path)
            ?: return ChatLinkDestination.Invalid("文件链接包含无效编码：$value")
    }
    val absolutePath = when {
        isAbsolutePath(decodedPath) -> normalizeAbsolutePath(decodedPath)
        else -> normalizeAbsolutePath("${cwd.trimEnd('/', '\\')}/$decodedPath")
    } ?: return ChatLinkDestination.Invalid("无法基于会话目录解析文件链接：$value")

    return ChatLinkDestination.RemoteFile(
        RemoteFileTarget(
            path = absolutePath,
            line = location.line,
            column = location.column,
        ),
    )
}

internal fun resolveRemotePath(path: String): RemoteFileTarget? {
    val location = extractLocation(path.trim())
    val decodedPath = if (location.path.startsWith("file://", ignoreCase = true)) {
        fileUriPath(location.path)
    } else {
        decodePath(location.path)
    } ?: return null
    val absolutePath = normalizeAbsolutePath(decodedPath) ?: return null
    return RemoteFileTarget(absolutePath, location.line, location.column)
}

private data class LinkLocation(
    val path: String,
    val line: Int?,
    val column: Int?,
)

private fun extractLocation(value: String): LinkLocation {
    val hashIndex = value.lastIndexOf('#')
    val hashMatch = hashIndex.takeIf { it >= 0 }
        ?.let { HASH_LOCATION.matchEntire(value.substring(it + 1)) }
    if (hashMatch != null) {
        return LinkLocation(
            path = value.substring(0, hashIndex),
            line = hashMatch.groupValues[1].toInt(),
            column = hashMatch.groupValues[2].takeIf(String::isNotEmpty)?.toInt(),
        )
    }

    val pathWithoutFragment = hashIndex.takeIf { it >= 0 }?.let { value.substring(0, it) } ?: value
    val colonMatch = COLON_LOCATION.find(pathWithoutFragment)
    return if (colonMatch != null) {
        LinkLocation(
            path = pathWithoutFragment.removeRange(colonMatch.range),
            line = colonMatch.groupValues[1].toInt(),
            column = colonMatch.groupValues[2].takeIf(String::isNotEmpty)?.toInt(),
        )
    } else {
        LinkLocation(pathWithoutFragment, null, null)
    }
}

private fun fileUriPath(value: String): String? = runCatching {
    val uri = URI(value)
    val decoded = decodePath(uri.rawPath.orEmpty()) ?: return@runCatching null
    val host = uri.host
    when {
        !host.isNullOrEmpty() && host != "localhost" -> "//$host$decoded"
        WINDOWS_FILE_URI_PATH.matchesAt(decoded, 0) -> decoded.removePrefix("/")
        else -> decoded
    }
}.getOrNull()

private fun decodePath(value: String): String? = runCatching {
    URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8.name())
}.getOrNull()

private fun isAbsolutePath(path: String): Boolean =
    path.startsWith('/') || path.startsWith("\\\\") || WINDOWS_ABSOLUTE_PATH.matchesAt(path, 0)

private fun normalizeAbsolutePath(path: String): String? {
    val value = path.replace('\\', '/')
    val (prefix, remainder) = when {
        WINDOWS_ABSOLUTE_PATH.matchesAt(value, 0) -> value.substring(0, 2) to value.substring(2).trimStart('/')
        value.startsWith("//") -> "//" to value.removePrefix("//")
        value.startsWith('/') -> "/" to value.trimStart('/')
        else -> return null
    }
    val segments = mutableListOf<String>()
    remainder.split('/').forEach { segment ->
        when (segment) {
            "", "." -> Unit
            ".." -> if (segments.isNotEmpty()) segments.removeAt(segments.lastIndex)
            else -> segments += segment
        }
    }
    val suffix = segments.joinToString("/")
    return when (prefix) {
        "/", "//" -> prefix + suffix
        else -> "$prefix/$suffix"
    }
}

private val URI_SCHEME = Regex("^([A-Za-z][A-Za-z0-9+.-]*):")
private val WINDOWS_ABSOLUTE_PATH = Regex("^[A-Za-z]:[/\\\\]")
private val WINDOWS_FILE_URI_PATH = Regex("^/[A-Za-z]:/")
private val HASH_LOCATION = Regex("(?i)^L(\\d+)(?:C(\\d+))?(?:-L?\\d+(?:C\\d+)?)?$")
private val COLON_LOCATION = Regex(":(\\d+)(?::(\\d+))?(?:-\\d+(?::\\d+)?)?$")
