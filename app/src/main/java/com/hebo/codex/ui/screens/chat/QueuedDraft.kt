package com.hebo.codex.ui.screens.chat

import com.hebo.codex.domain.DraftAttachment
import com.hebo.codex.domain.UserInputPart
import java.util.UUID

internal fun ChatDraft.restoreQueuedInput(input: List<UserInputPart>): ChatDraft = copy(
    message = input.filterIsInstance<UserInputPart.Text>().joinToString("\n") { it.text },
    attachments = input.mapNotNull { part ->
        if (part is UserInputPart.Text) null else DraftAttachment(UUID.randomUUID().toString(), part)
    },
)

internal fun ChatDraft.toInput(): List<UserInputPart> {
    val text = message.takeIf(String::isNotBlank)?.let { listOf(UserInputPart.Text(it)) }.orEmpty()
    return text + attachments.map(DraftAttachment::input)
}

internal val ChatDraft.hasContent: Boolean get() = message.isNotBlank() || attachments.isNotEmpty()
