package com.hebo.codex.ui.screens.chat

internal fun resolveChatMarkdownImageSource(link: String, cwd: String): ChatImageSource =
    when (val destination = resolveChatLink(link, cwd)) {
        is ChatLinkDestination.RemoteFile -> ChatImageSource.ServerFile(destination.target.path)
        is ChatLinkDestination.Invalid -> throw IllegalArgumentException(destination.message)
        is ChatLinkDestination.External -> {
            val value = destination.uri
            require(
                value.startsWith("http:", ignoreCase = true) ||
                    value.startsWith("https:", ignoreCase = true) ||
                    value.startsWith("data:image/", ignoreCase = true),
            ) { "仅支持 HTTP、HTTPS、图片 data URL 或远端文件路径" }
            ChatImageSource.Url(value)
        }
    }
