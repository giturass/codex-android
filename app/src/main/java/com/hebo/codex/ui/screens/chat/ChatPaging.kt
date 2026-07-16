package com.hebo.codex.ui.screens.chat

internal data class ChatPagingPosition(
    val liveEdgeIndex: Int,
    val liveEdgeOffset: Int?,
    val outputEndOffset: Int?,
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

internal fun hasReachedChatPageAnchor(
    position: ChatPagingPosition,
    anchorOffset: Int,
): Boolean = position.liveEdgeOffset?.let { it <= anchorOffset }
    ?: position.firstVisibleItemIndex?.let { it > position.liveEdgeIndex }
    ?: false

internal fun hasReachedChatOutputEnd(position: ChatPagingPosition): Boolean {
    val outputEndIndex = position.liveEdgeIndex - 1
    if (outputEndIndex < 0) return true
    return position.outputEndOffset?.let { it <= position.readingEndOffset }
        ?: position.firstVisibleItemIndex?.let { it > outputEndIndex }
        ?: false
}

internal fun shouldFollowChatOutput(
    position: ChatPagingPosition,
    pageRunwayEnabled: Boolean,
): Boolean = if (pageRunwayEnabled) {
    shouldAdvanceChatPage(position)
} else {
    !hasReachedChatOutputEnd(position)
}

internal fun shouldStopForwardUserScroll(position: ChatPagingPosition): Boolean =
    position.liveEdgeOffset?.let { it <= position.readingEndOffset }
        ?: position.firstVisibleItemIndex?.let { it > position.liveEdgeIndex }
        ?: false

internal fun canReleaseChatPageRunway(
    position: ChatPagingPosition,
    liveEdgeHeight: Int,
): Boolean {
    val naturalLiveEdgeOffset = (position.readingEndOffset - liveEdgeHeight).coerceAtLeast(0)
    return position.liveEdgeOffset?.let { it >= naturalLiveEdgeOffset }
        ?: position.lastVisibleItemIndex?.let { it < position.liveEdgeIndex }
        ?: false
}

internal fun chatViewportShrinkCompensation(
    previousReadingEndOffset: Int,
    readingEndOffset: Int,
): Int = (previousReadingEndOffset - readingEndOffset).coerceAtLeast(0)

internal fun chatPageAdvanceDistance(
    position: ChatPagingPosition,
    pageOverlap: Int,
): Int = (position.readingEndOffset - pageOverlap).coerceAtLeast(0)
