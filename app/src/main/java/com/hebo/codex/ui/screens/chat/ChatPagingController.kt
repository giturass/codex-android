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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first

internal class ChatPagingController(
    val listState: LazyListState,
    val userScrollConnection: NestedScrollConnection,
    val pageRunwayHeightPx: Int,
    val showReturnToOutput: Boolean,
    private val updateViewportHeight: (Int) -> Unit,
    private val prepareSubmission: () -> Unit,
    private val requestCurrentOutput: () -> Unit,
    private val stopFollowing: () -> Unit,
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

    suspend fun locateItem(index: Int) {
        stopFollowing()
        listState.animateScrollToItem(index)
    }
}

@Composable
internal fun rememberChatPagingController(
    conversationId: String?,
    isRunning: Boolean,
    liveEdgeIndex: Int,
    pageOverlapPx: Int,
    liveEdgeHeightPx: Int,
): ChatPagingController {
    val listState = rememberLazyListState()
    var autoFollow by rememberSaveable(conversationId) { mutableStateOf(true) }
    var initialListPositioned by remember(conversationId) { mutableStateOf(false) }
    var wasRunning by remember(conversationId) { mutableStateOf(isRunning) }
    var showReturnToOutput by remember(conversationId) { mutableStateOf(false) }
    var listViewportHeightPx by remember { mutableIntStateOf(0) }
    var pageRunwayHeightPx by remember(conversationId) { mutableIntStateOf(0) }
    var pageAnchor by remember(conversationId) { mutableStateOf<ChatPageAnchor?>(null) }
    var pageAdvanceInProgress by remember(conversationId) { mutableStateOf(false) }
    var currentOutputRequest by remember(conversationId) { mutableIntStateOf(0) }
    var handledCurrentOutputRequest by remember(conversationId) { mutableIntStateOf(0) }
    var completionDockRequest by remember(conversationId) { mutableIntStateOf(0) }
    var handledCompletionDockRequest by remember(conversationId) { mutableIntStateOf(0) }
    var runningOutputEndVisible by remember(conversationId) { mutableStateOf(false) }
    var completionDockInProgress by remember(conversationId) { mutableStateOf(false) }

    LaunchedEffect(conversationId, isRunning) {
        val shouldDock = shouldDockCompletedChatOutput(
            wasRunning = wasRunning,
            isRunning = isRunning,
            wasOutputEndVisible = runningOutputEndVisible,
            isUserScrollInProgress = !autoFollow && listState.isScrollInProgress,
        )
        if (isRunning) {
            runningOutputEndVisible = false
        } else {
            if (shouldDock) {
                completionDockRequest++
            } else {
                pageRunwayHeightPx = 0
                pageAnchor = null
            }
        }
        wasRunning = isRunning
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
        liveEdgeIndex,
    ) {
        if (initialListPositioned || listViewportHeightPx <= 0) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > liveEdgeIndex }
        listState.scrollToItem(liveEdgeIndex)
        initialListPositioned = true
    }
    LaunchedEffect(
        listState,
        autoFollow,
        initialListPositioned,
        isRunning,
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
                    (autoFollow && shouldFollowChatOutput(it, isRunning))
            }
            val viewportCompensation = chatViewportShrinkCompensation(
                previousReadingEndOffset = previousReadingEndOffset,
                position = position,
                liveEdgeHeight = liveEdgeHeightPx,
            )
            previousReadingEndOffset = position.readingEndOffset
            if (viewportCompensation > 0) {
                listState.scrollBy(viewportCompensation.toFloat())
                if (autoFollow && pageRunwayHeightPx > 0) pageAnchor = listState.pageAnchor()
                continue
            }
            if (!autoFollow) continue
            val currentPosition = listState.layoutInfo.chatPagingPosition(liveEdgeIndex)
            if (!isRunning) {
                if (shouldFollowChatOutput(currentPosition, isRunning = false)) {
                    listState.scrollToItem(liveEdgeIndex)
                }
                continue
            }
            val pageDistance = chatPageAdvanceDistance(currentPosition, pageOverlapPx)
            if (shouldAdvanceChatPage(currentPosition) && pageDistance > 0) {
                pageAdvanceInProgress = true
                try {
                    pageRunwayHeightPx = chatPageRunwayForAdvance(
                        position = currentPosition,
                        pageDistance = pageDistance,
                        liveEdgeHeight = liveEdgeHeightPx,
                    )
                    val requiredEndHeight = liveEdgeHeightPx + pageRunwayHeightPx
                    snapshotFlow { listState.layoutInfo }.first { layout ->
                        val endItem = layout.visibleItemsInfo.firstOrNull { it.index == liveEdgeIndex }
                        endItem == null || endItem.size == requiredEndHeight
                    }
                    listState.animateScrollBy(
                        value = pageDistance.toFloat(),
                        animationSpec = tween(PAGE_SCROLL_ANIMATION_DURATION_MILLIS),
                    )
                } finally {
                    pageAnchor = listState.pageAnchor()
                    pageAdvanceInProgress = false
                }
            }
        }
    }
    LaunchedEffect(isRunning, completionDockRequest, initialListPositioned, liveEdgeIndex, liveEdgeHeightPx) {
        if (isRunning || !initialListPositioned || completionDockRequest == handledCompletionDockRequest) {
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
        } finally {
            // 完成后即使归位动画被手势或重新布局取消，也必须释放分页留白
            if (!wasRunning) {
                pageRunwayHeightPx = 0
                pageAnchor = null
            }
            completionDockInProgress = false
        }
    }
    LaunchedEffect(currentOutputRequest, initialListPositioned, liveEdgeIndex) {
        if (!initialListPositioned || currentOutputRequest == handledCurrentOutputRequest) {
            return@LaunchedEffect
        }
        showReturnToOutput = false
        pageRunwayHeightPx = 0
        pageAnchor = null
        listState.animateScrollToItem(
            index = liveEdgeIndex,
        )
        handledCurrentOutputRequest = currentOutputRequest
        autoFollow = true
    }
    LaunchedEffect(
        listState,
        initialListPositioned,
        pageAdvanceInProgress,
        completionDockInProgress,
        liveEdgeIndex,
        liveEdgeHeightPx,
        pageAnchor,
    ) {
        val anchor = pageAnchor ?: return@LaunchedEffect
        if (!initialListPositioned || pageAdvanceInProgress || completionDockInProgress) return@LaunchedEffect
        snapshotFlow {
            if (pageAdvanceInProgress || completionDockInProgress || pageAnchor != anchor) {
                return@snapshotFlow null
            }
            val layout = listState.layoutInfo
            remainingChatPageRunway(
                position = layout.chatPagingPosition(liveEdgeIndex),
                currentHeight = pageRunwayHeightPx,
                pageAnchorOffset = anchor.offset,
                visibleAnchorOffset = layout.visibleItemsInfo.firstOrNull { it.key == anchor.key }?.offset,
                liveEdgeHeight = liveEdgeHeightPx,
            )
        }.collect { height ->
            if (height != null && !pageAdvanceInProgress && !completionDockInProgress && pageAnchor == anchor) {
                pageRunwayHeightPx = height
            }
        }
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
                ChatUserScrollResolution.IN_PROGRESS -> Unit
                ChatUserScrollResolution.FOLLOW_OUTPUT -> {
                    showReturnToOutput = false
                    autoFollow = true
                }

                ChatUserScrollResolution.SHOW_RETURN_TO_OUTPUT -> showReturnToOutput = true
            }
        }
    }
    val userScrollConnection = remember(conversationId) {
        object : NestedScrollConnection {
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
        pageRunwayHeightPx = pageRunwayHeightPx,
        showReturnToOutput = showReturnToOutput,
        updateViewportHeight = { listViewportHeightPx = it },
        prepareSubmission = {
            if (!autoFollow) currentOutputRequest++
        },
        requestCurrentOutput = { currentOutputRequest++ },
        stopFollowing = {
            autoFollow = false
            showReturnToOutput = true
        },
    )
}

private data class ChatPageAnchor(val key: Any, val offset: Int)

private fun LazyListState.pageAnchor(): ChatPageAnchor? =
    layoutInfo.visibleItemsInfo.firstOrNull { it.index == firstVisibleItemIndex }?.let {
        ChatPageAnchor(key = it.key, offset = it.offset)
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
