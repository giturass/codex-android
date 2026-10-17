package com.hebo.codex.domain

import java.util.UUID

sealed interface DraftTarget {
    val id: String

    data class NewTask(override val id: String) : DraftTarget

    data class Thread(override val id: String) : DraftTarget
}

data class DraftKey(
    val serverId: String,
    val target: DraftTarget,
)

data class DraftAttachment(
    val id: String,
    val input: UserInputPart,
    val previewUri: String? = null,
)

sealed interface ConversationDraft {
    val serverId: String
    val message: String
    val cwd: String
    val updatedAtMillis: Long
    val revision: String

    val key: DraftKey

    data class NewTask(
        override val serverId: String,
        val draftId: String,
        override val message: String,
        override val cwd: String,
        val options: ThreadOptions,
        val attachments: List<DraftAttachment>,
        override val updatedAtMillis: Long,
        override val revision: String,
    ) : ConversationDraft {
        override val key: DraftKey = DraftKey(serverId, DraftTarget.NewTask(draftId))
    }

    data class Thread(
        override val serverId: String,
        val threadId: String,
        val threadTitle: String,
        override val message: String,
        override val cwd: String,
        val options: TurnOptions,
        val attachments: List<DraftAttachment>,
        override val updatedAtMillis: Long,
        override val revision: String,
    ) : ConversationDraft {
        override val key: DraftKey = DraftKey(serverId, DraftTarget.Thread(threadId))
    }
}

data class DraftSummary(
    val key: DraftKey,
    val preview: String,
    val contextTitle: String,
    val cwd: String,
    val attachmentCount: Int,
    val updatedAtMillis: Long,
    val revision: String,
)

fun ConversationDraft.hasContent(): Boolean = when (this) {
    is ConversationDraft.NewTask -> message.isNotBlank() || attachments.isNotEmpty()
    is ConversationDraft.Thread -> message.isNotBlank() || attachments.isNotEmpty()
}

fun ConversationDraft.shouldPersist(): Boolean = hasContent() || when (this) {
    is ConversationDraft.NewTask -> options != ThreadOptions()
    is ConversationDraft.Thread -> options != TurnOptions()
}

fun ConversationDraft.toSummary(): DraftSummary {
    val attachmentCount = when (this) {
        is ConversationDraft.NewTask -> attachments.size
        is ConversationDraft.Thread -> attachments.size
    }
    return DraftSummary(
        key = key,
        preview = draftPreview(message, attachmentCount),
        contextTitle = when (this) {
            is ConversationDraft.NewTask -> "新建任务"
            is ConversationDraft.Thread -> threadTitle
        },
        cwd = cwd,
        attachmentCount = attachmentCount,
        updatedAtMillis = updatedAtMillis,
        revision = revision,
    )
}

fun newDraftRevision(): String = UUID.randomUUID().toString()

internal fun draftPreview(message: String, attachmentCount: Int): String =
    message.lineSequence()
        .map(String::trim)
        .firstOrNull(String::isNotEmpty)
        ?: when (attachmentCount) {
            0 -> ""
            1 -> "1 个附件"
            else -> "$attachmentCount 个附件"
        }
