package com.hebo.codex.ui.screens.chat

import com.hebo.codex.domain.DraftAttachment
import com.hebo.codex.domain.UserInputPart

internal fun ChatDraft.restoreQueuedInput(input: List<UserInputPart>): ChatDraft = copy(
    message = input.filterIsInstance<UserInputPart.Text>().joinToString("\n") { it.text },
    attachments = input.mapIndexedNotNull { index, part ->
        if (part is UserInputPart.Text) null else DraftAttachment("queued:$index", part)
    },
    queuedInput = input,
)

internal fun ChatDraft.toInput(): List<UserInputPart> {
    val text = message.takeIf(String::isNotBlank)?.let { listOf(UserInputPart.Text(it)) }.orEmpty()
    val original = queuedInput ?: return text + attachments.map(DraftAttachment::input)
    val originalText = original.filterIsInstance<UserInputPart.Text>()
    val unchanged = message == originalText.joinToString("\n") { it.text }
    val remaining = attachments.associateByTo(linkedMapOf(), DraftAttachment::id)
    return buildList {
        var insertedText = false
        if (originalText.isEmpty()) addAll(text)
        original.forEachIndexed { index, part ->
            if (part is UserInputPart.Text) {
                if (unchanged) add(part)
                else if (!insertedText) {
                    addAll(text)
                    insertedText = true
                }
            } else {
                remaining.remove("queued:$index")?.let { add(it.input) }
            }
        }
        addAll(remaining.values.map(DraftAttachment::input))
    }
}

internal val ChatDraft.hasContent: Boolean get() = message.isNotBlank() || attachments.isNotEmpty()
