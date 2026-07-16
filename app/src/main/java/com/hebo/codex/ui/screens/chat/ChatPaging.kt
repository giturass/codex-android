package com.hebo.codex.ui.screens.chat

internal data class ChatPagingPosition(
    val liveEdgeIndex: Int,
    val liveEdgeOffset: Int?,
    val firstVisibleItemIndex: Int?,
    val lastVisibleItemIndex: Int?,
    val readingEndOffset: Int,
)

internal fun shouldAdvanceChatPage(position: ChatPagingPosition): Boolean {
    if (position.readingEndOffset <= 0) return false
    return position.liveEdgeOffset?.let { it > position.readingEndOffset }
        ?: position.lastVisibleItemIndex?.let { it < position.liveEdgeIndex }
        ?: false
}

internal fun hasReachedChatCurrentOutput(
    position: ChatPagingPosition,
    pageOverlap: Int,
): Boolean = position.liveEdgeOffset?.let { it <= pageOverlap }
    ?: position.firstVisibleItemIndex?.let { it > position.liveEdgeIndex }
    ?: false

internal fun chatPageAdvanceDistance(
    position: ChatPagingPosition,
    pageOverlap: Int,
): Int = (position.readingEndOffset - pageOverlap).coerceAtLeast(0)
