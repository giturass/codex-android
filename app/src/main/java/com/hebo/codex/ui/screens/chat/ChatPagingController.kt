package com.hebo.codex.ui.screens.chat

import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first

internal class ChatPagingController(
    val listState: LazyListState,
    val userScrollConnection: NestedScrollConnection,
    val pageRunwayEnabled: Boolean,
    val pageRunwayHeightPx: Int,
    val showReturnToOutput: Boolean,
    private val updateViewportHeight: (Int) -> Unit,
    private val prepareSubmission: () -> Unit,
    private val requestCurrentOutput: () -> Unit,
) {
    fun onViewportHeightChanged(heightPx: Int) {
        updateViewportHeight(heightPx)
    }

    fun onSubmission() {
        prepareSubmission()
    }

    fun returnToOutput() {
        requestCurrentOutput()
    }
}

@Composable
internal fun rememberChatPagingController(
    conversationId: String?,
    isRunning: Boolean,
    liveEdgeIndex: Int,
    topContentPaddingPx: Int,
    bottomContentPaddingPx: Int,
    pageOverlapPx: Int,
    liveEdgeHeightPx: Int,
): ChatPagingController {
    val listState = rememberLazyListState()
    var autoFollow by rememberSaveable(conversationId) { mutableStateOf(true) }
    var initialListPositioned by remember(conversationId) { mutableStateOf(false) }
    var retainPageRunway by remember(conversationId) { mutableStateOf(isRunning) }
    var pageRunwayEnabled by remember(conversationId) { mutableStateOf(isRunning) }
    var showReturnToOutput by remember(conversationId) { mutableStateOf(false) }
    var listViewportHeightPx by remember { mutableIntStateOf(0) }
    var retainedPageRunwayHeightPx by remember(conversationId) { mutableIntStateOf(0) }
    var currentOutputRequest by remember(conversationId) { mutableIntStateOf(0) }
    var handledCurrentOutputRequest by remember(conversationId) { mutableIntStateOf(0) }
    var completionDockRequest by remember(conversationId) { mutableIntStateOf(0) }
    var handledCompletionDockRequest by remember(conversationId) { mutableIntStateOf(0) }
    var runningOutputEndVisible by remember(conversationId) { mutableStateOf(false) }
    var completionDockInProgress by remember(conversationId) { mutableStateOf(false) }
    val readingViewportHeightPx = (
        listViewportHeightPx - topContentPaddingPx - bottomContentPaddingPx
    ).coerceAtLeast(0)
    val requiredPageRunwayHeightPx = (readingViewportHeightPx - pageOverlapPx).coerceAtLeast(0)
    val pageRunwayHeightPx = maxOf(retainedPageRunwayHeightPx, requiredPageRunwayHeightPx)

    LaunchedEffect(requiredPageRunwayHeightPx) {
        if (requiredPageRunwayHeightPx > retainedPageRunwayHeightPx) {
            retainedPageRunwayHeightPx = requiredPageRunwayHeightPx
        }
    }
    LaunchedEffect(conversationId, isRunning) {
        val shouldDock = shouldDockCompletedChatOutput(
            wasRunning = retainPageRunway,
            isRunning = isRunning,
            wasOutputEndVisible = runningOutputEndVisible,
            isUserScrollInProgress = !autoFollow && listState.isScrollInProgress,
        )
        if (isRunning) {
            runningOutputEndVisible = false
            retainPageRunway = true
            pageRunwayEnabled = true
        } else {
            retainPageRunway = false
            if (shouldDock) completionDockRequest++
        }
    }
    LaunchedEffect(conversationId, isRunning, initialListPositioned, liveEdgeIndex) {
        if (!isRunning || !initialListPositioned) return@LaunchedEffect
        snapshotFlow {
            isChatOutputEndVisible(listState.layoutInfo.chatPagingPosition(liveEdgeIndex))
        }.collect { runningOutputEndVisible = it }
    }
    LaunchedEffect(
        conversationId,
        initialListPositioned,
        listViewportHeightPx,
        pageRunwayEnabled,
        liveEdgeIndex,
        liveEdgeHeightPx,
    ) {
        if (initialListPositioned || listViewportHeightPx <= 0) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > liveEdgeIndex }
        val readingEndOffset = listState.layoutInfo.readingEndOffset()
        val scrollOffset = if (pageRunwayEnabled) {
            -(readingEndOffset - liveEdgeHeightPx).coerceAtLeast(0)
        } else {
            0
        }
        listState.scrollToItem(liveEdgeIndex, scrollOffset)
        initialListPositioned = true
    }
    LaunchedEffect(
        listState,
        autoFollow,
        initialListPositioned,
        pageRunwayEnabled,
        liveEdgeIndex,
        pageOverlapPx,
        completionDockInProgress,
    ) {
        if (!initialListPositioned || completionDockInProgress) return@LaunchedEffect
        var previousReadingEndOffset = listState.layoutInfo.readingEndOffset()
        while (true) {
            val position = snapshotFlow {
                listState.layoutInfo.chatPagingPosition(liveEdgeIndex)
            }.first {
                it.readingEndOffset != previousReadingEndOffset ||
                    (autoFollow && shouldFollowChatOutput(it, pageRunwayEnabled))
            }
            val viewportCompensation = chatViewportShrinkCompensation(
                previousReadingEndOffset = previousReadingEndOffset,
                readingEndOffset = position.readingEndOffset,
            )
            previousReadingEndOffset = position.readingEndOffset
            if (viewportCompensation > 0) {
                listState.scrollBy(viewportCompensation.toFloat())
                continue
            }
            if (!autoFollow) continue
            val currentPosition = listState.layoutInfo.chatPagingPosition(liveEdgeIndex)
            if (!pageRunwayEnabled) {
                if (shouldFollowChatOutput(currentPosition, pageRunwayEnabled = false)) {
                    listState.scrollToItem(liveEdgeIndex)
                }
                continue
            }
            val pageDistance = chatPageAdvanceDistance(currentPosition, pageOverlapPx)
            if (shouldAdvanceChatPage(currentPosition) && pageDistance > 0) {
                listState.animateScrollBy(
                    value = pageDistance.toFloat(),
                    animationSpec = tween(PAGE_SCROLL_ANIMATION_DURATION_MILLIS),
                )
            }
        }
    }
    LaunchedEffect(completionDockRequest, initialListPositioned, liveEdgeIndex, liveEdgeHeightPx) {
        if (!initialListPositioned || completionDockRequest == handledCompletionDockRequest) {
            return@LaunchedEffect
        }
        handledCompletionDockRequest = completionDockRequest
        completionDockInProgress = true
        try {
            withFrameNanos { }
            if (!autoFollow && listState.isScrollInProgress) return@LaunchedEffect
            val readingEndOffset = listState.layoutInfo.readingEndOffset()
            listState.animateScrollToItem(
                index = liveEdgeIndex,
                scrollOffset = -(readingEndOffset - liveEdgeHeightPx).coerceAtLeast(0),
            )
            showReturnToOutput = false
            pageRunwayEnabled = false
        } finally {
            completionDockInProgress = false
        }
    }
    LaunchedEffect(currentOutputRequest, initialListPositioned, liveEdgeIndex, pageOverlapPx) {
        if (!initialListPositioned || currentOutputRequest == handledCurrentOutputRequest) {
            return@LaunchedEffect
        }
        showReturnToOutput = false
        listState.animateScrollToItem(
            index = liveEdgeIndex,
            scrollOffset = if (pageRunwayEnabled) -pageOverlapPx else 0,
        )
        handledCurrentOutputRequest = currentOutputRequest
        autoFollow = true
    }
    LaunchedEffect(autoFollow, retainPageRunway) {
        if (autoFollow && retainPageRunway) pageRunwayEnabled = true
    }
    LaunchedEffect(
        listState,
        autoFollow,
        initialListPositioned,
        pageRunwayEnabled,
        liveEdgeIndex,
        liveEdgeHeightPx,
    ) {
        if (autoFollow || !initialListPositioned || !pageRunwayEnabled) return@LaunchedEffect
        snapshotFlow {
            canReleaseChatPageRunway(
                position = listState.layoutInfo.chatPagingPosition(liveEdgeIndex),
                liveEdgeHeight = liveEdgeHeightPx,
            )
        }.first { it }
        pageRunwayEnabled = false
    }
    LaunchedEffect(listState, autoFollow, initialListPositioned, liveEdgeIndex) {
        if (autoFollow || !initialListPositioned) return@LaunchedEffect
        snapshotFlow {
            resolveChatUserScroll(
                position = listState.layoutInfo.chatPagingPosition(liveEdgeIndex),
                isScrollInProgress = listState.isScrollInProgress,
            )
        }.collectLatest { resolution ->
            when (resolution) {
                ChatUserScrollResolution.IN_PROGRESS -> showReturnToOutput = false
                ChatUserScrollResolution.FOLLOW_OUTPUT -> {
                    showReturnToOutput = false
                    autoFollow = true
                }

                ChatUserScrollResolution.SHOW_RETURN_TO_OUTPUT -> showReturnToOutput = true
            }
        }
    }
    val userScrollConnection = remember(conversationId, listState, liveEdgeIndex) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y >= 0f) return Offset.Zero
                val position = listState.layoutInfo.chatPagingPosition(liveEdgeIndex)
                return if (shouldStopForwardUserScroll(position)) {
                    Offset(x = 0f, y = available.y)
                } else {
                    Offset.Zero
                }
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (available.y >= 0f) return Velocity.Zero
                return if (shouldStopForwardUserScroll(listState.layoutInfo.chatPagingPosition(liveEdgeIndex))) {
                    available
                } else {
                    Velocity.Zero
                }
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source == NestedScrollSource.UserInput && consumed.y != 0f) autoFollow = false
                return Offset.Zero
            }
        }
    }
    return ChatPagingController(
        listState = listState,
        userScrollConnection = userScrollConnection,
        pageRunwayEnabled = pageRunwayEnabled,
        pageRunwayHeightPx = pageRunwayHeightPx,
        showReturnToOutput = showReturnToOutput,
        updateViewportHeight = { listViewportHeightPx = it },
        prepareSubmission = {
            if (!autoFollow) currentOutputRequest++
        },
        requestCurrentOutput = { currentOutputRequest++ },
    )
}

private fun LazyListLayoutInfo.readingEndOffset(): Int =
    (viewportEndOffset - afterContentPadding).coerceAtLeast(0)

private fun LazyListLayoutInfo.readingStartOffset(): Int =
    viewportStartOffset + beforeContentPadding

private fun LazyListLayoutInfo.chatPagingPosition(liveEdgeIndex: Int): ChatPagingPosition {
    val liveEdgeItem = visibleItemsInfo.firstOrNull { it.index == liveEdgeIndex }
    val outputEndItem = visibleItemsInfo.firstOrNull { it.index == liveEdgeIndex - 1 }
    return ChatPagingPosition(
        liveEdgeIndex = liveEdgeIndex,
        liveEdgeOffset = liveEdgeItem?.offset,
        outputEndOffset = outputEndItem?.let { it.offset + it.size },
        firstVisibleItemIndex = visibleItemsInfo.firstOrNull()?.index,
        lastVisibleItemIndex = visibleItemsInfo.lastOrNull()?.index,
        readingStartOffset = readingStartOffset(),
        readingEndOffset = readingEndOffset(),
    )
}

private const val PAGE_SCROLL_ANIMATION_DURATION_MILLIS = 180
