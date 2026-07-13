package com.hebo.codex.ui.screens.chat

import androidx.compose.foundation.lazy.LazyListLayoutInfo

internal fun chromeScrollDelta(
    scrollDelta: Float,
    distanceToBottom: Float,
    hideStartDistance: Float,
): Float = when {
    scrollDelta > 0f -> scrollDelta.coerceAtMost(
        (distanceToBottom - hideStartDistance).coerceAtLeast(0f),
    )

    scrollDelta < 0f -> scrollDelta.coerceAtLeast(
        -(hideStartDistance - distanceToBottom).coerceAtLeast(0f),
    )

    else -> 0f
}

internal fun LazyListLayoutInfo.distanceToBottom(): Float {
    if (totalItemsCount == 0) return 0f
    val lastVisibleItem = visibleItemsInfo.lastOrNull() ?: return Float.POSITIVE_INFINITY
    if (lastVisibleItem.index < totalItemsCount - 1) return Float.POSITIVE_INFINITY
    return (
        lastVisibleItem.offset +
            lastVisibleItem.size +
            afterContentPadding -
            viewportEndOffset
        ).coerceAtLeast(0).toFloat()
}
