package com.hebo.codex.ui.screens.chat

import com.hebo.codex.domain.TurnOptions

internal fun ChatDraft.afterSuccessfulSubmission(
    submittedDraft: ChatDraft,
    optionsApplied: Boolean,
): ChatDraft {
    val remaining = if (this == submittedDraft) copy(message = "", attachments = emptyList()) else this
    return if (optionsApplied) remaining.withoutAppliedOptions(submittedDraft.options) else remaining
}

internal fun ChatDraft.withoutAppliedOptions(appliedOptions: TurnOptions): ChatDraft =
    if (options == appliedOptions) copy(options = TurnOptions()) else this
