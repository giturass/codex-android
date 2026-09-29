package com.hebo.codex.ui.screens.chat

import com.hebo.codex.domain.UserInputPart
import com.hebo.codex.domain.UserMessagePart

internal sealed interface ChatImageSource {
    data class Url(val value: String) : ChatImageSource

    data class ServerFile(val path: String) : ChatImageSource

    data class FileReference(val fileId: String) : ChatImageSource
}

internal fun UserInputPart.chatImageSource(): ChatImageSource? = when (this) {
    is UserInputPart.Image -> ChatImageSource.Url(url)
    is UserInputPart.UploadedImage -> ChatImageSource.FileReference(fileId)
    is UserInputPart.LocalImage -> ChatImageSource.ServerFile(path)
    else -> null
}

internal fun UserMessagePart.chatImageSource(): ChatImageSource? = when (this) {
    is UserMessagePart.Image -> when {
        url != null -> ChatImageSource.Url(url)
        else -> ChatImageSource.FileReference(requireNotNull(fileId))
    }
    is UserMessagePart.LocalImage -> ChatImageSource.ServerFile(path)
    else -> null
}
