package com.hebo.codex.domain

internal suspend fun startConversationReview(
    threadId: String,
    cwd: String,
    target: ReviewTarget,
    destination: ReviewDestination,
    createConversation: suspend (cwd: String) -> String,
    reviewInline: suspend (threadId: String, target: ReviewTarget) -> String,
): String {
    val reviewThreadId = when (destination) {
        ReviewDestination.CURRENT_THREAD -> threadId
        ReviewDestination.NEW_THREAD -> createConversation(cwd)
    }
    return reviewInline(reviewThreadId, target)
}
