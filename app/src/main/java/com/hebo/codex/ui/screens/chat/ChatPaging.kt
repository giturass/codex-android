package com.hebo.codex.ui.screens.chat

internal data class ChatPagingPosition(
    val liveEdgeIndex: Int,
    val liveEdgeOffset: Int?,
    val outputEndOffset: Int?,
    val firstVisibleItemIndex: Int?,
    val lastVisibleItemIndex: Int?,
    val readingStartOffset: Int,
    val readingEndOffset: Int,
)

internal fun shouldAdvanceChatPage(position: ChatPagingPosition): Boolean {
    if (position.readingEndOffset <= 0) return false
    return position.liveEdgeOffset?.let { it > position.readingEndOffset }
        ?: position.lastVisibleItemIndex?.let { it < position.liveEdgeIndex }
        ?: false
}

internal fun hasReachedChatOutputEnd(position: ChatPagingPosition): Boolean {
    val outputEndIndex = position.liveEdgeIndex - 1
    if (outputEndIndex < 0) return true
    return position.outputEndOffset?.let { it <= position.readingEndOffset }
        ?: position.firstVisibleItemIndex?.let { it > outputEndIndex }
        ?: false
}

internal enum class ChatUserScrollResolution {
    IN_PROGRESS,
    FOLLOW_OUTPUT,
    SHOW_RETURN_TO_OUTPUT,
}

internal fun resolveChatUserScroll(
    position: ChatPagingPosition,
    isScrollInProgress: Boolean,
): ChatUserScrollResolution = when {
    isScrollInProgress -> ChatUserScrollResolution.IN_PROGRESS
    hasReachedChatOutputEnd(position) -> ChatUserScrollResolution.FOLLOW_OUTPUT
    else -> ChatUserScrollResolution.SHOW_RETURN_TO_OUTPUT
}

internal fun isChatOutputEndVisible(position: ChatPagingPosition): Boolean =
    position.outputEndOffset?.let {
        it > position.readingStartOffset && it <= position.readingEndOffset
    } ?: false

internal fun shouldDockCompletedChatOutput(
    wasRunning: Boolean,
    isRunning: Boolean,
    wasOutputEndVisible: Boolean,
    isUserScrollInProgress: Boolean,
): Boolean = wasRunning &&
    !isRunning &&
    wasOutputEndVisible &&
    !isUserScrollInProgress

internal fun shouldFollowChatOutput(
    position: ChatPagingPosition,
    isRunning: Boolean,
): Boolean = if (isRunning) {
    shouldAdvanceChatPage(position)
} else {
    !hasReachedChatOutputEnd(position)
}

internal fun chatPageRunwayForAdvance(
    position: ChatPagingPosition,
    pageDistance: Int,
    liveEdgeHeight: Int,
): Int {
    // 末尾尚未进入测量范围时，先提供一页空间，翻页后按实际布局收敛
    val liveEdgeOffset = position.liveEdgeOffset ?: return pageDistance
    return (position.readingEndOffset - liveEdgeOffset - liveEdgeHeight + pageDistance)
        .coerceIn(0, pageDistance)
}

internal fun remainingChatPageRunway(
    position: ChatPagingPosition,
    currentHeight: Int,
    pageAnchorOffset: Int,
    visibleAnchorOffset: Int?,
    liveEdgeHeight: Int,
): Int {
    // 回看历史时锚点可能未被 LazyColumn 测量，保留原页空间直到重新可测量
    if (visibleAnchorOffset == null) return currentHeight
    val liveEdgeOffset = position.liveEdgeOffset
    if (liveEdgeOffset != null) {
        val anchoredLiveEdgeOffset = liveEdgeOffset + pageAnchorOffset - visibleAnchorOffset
        return (position.readingEndOffset - anchoredLiveEdgeOffset - liveEdgeHeight)
            .coerceIn(0, currentHeight)
    }
    return if (
        visibleAnchorOffset <= pageAnchorOffset &&
        position.lastVisibleItemIndex != null && position.lastVisibleItemIndex < position.liveEdgeIndex
    ) {
        0
    } else {
        currentHeight
    }
}

internal fun chatViewportShrinkCompensation(
    previousReadingEndOffset: Int,
    position: ChatPagingPosition,
    liveEdgeHeight: Int,
): Int {
    val shrink = (previousReadingEndOffset - position.readingEndOffset).coerceAtLeast(0)
    val liveEdgeOffset = position.liveEdgeOffset ?: return if (shouldAdvanceChatPage(position)) shrink else 0
    val coveredOutput = (liveEdgeOffset + liveEdgeHeight - position.readingEndOffset).coerceAtLeast(0)
    return minOf(shrink, coveredOutput)
}

internal fun chatPageAdvanceDistance(
    position: ChatPagingPosition,
    pageOverlap: Int,
): Int = (position.readingEndOffset - pageOverlap).coerceAtLeast(0)
