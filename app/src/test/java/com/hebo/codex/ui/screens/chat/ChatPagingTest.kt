package com.hebo.codex.ui.screens.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatPagingTest {
    @Test
    fun `当前输出尚未越过窗口底部时不翻页`() {
        assertFalse(shouldAdvanceChatPage(position(liveEdgeOffset = 600)))
    }

    @Test
    fun `当前输出越过窗口底部时翻页`() {
        assertTrue(shouldAdvanceChatPage(position(liveEdgeOffset = 601)))
    }

    @Test
    fun `当前输出位于可视条目之后时翻页`() {
        assertTrue(
            shouldAdvanceChatPage(
                position(
                    liveEdgeOffset = null,
                    firstVisibleItemIndex = 2,
                    lastVisibleItemIndex = 4,
                ),
            ),
        )
    }

    @Test
    fun `当前输出已滚过可视条目时不重复翻页`() {
        assertFalse(
            shouldAdvanceChatPage(
                position(
                    liveEdgeOffset = null,
                    firstVisibleItemIndex = 6,
                    lastVisibleItemIndex = 6,
                ),
            ),
        )
    }

    @Test
    fun `当前输出位于页面锚点时视为已跟上`() {
        assertTrue(hasReachedChatPageAnchor(position(liveEdgeOffset = 64), anchorOffset = 64))
        assertFalse(hasReachedChatPageAnchor(position(liveEdgeOffset = 65), anchorOffset = 64))
    }

    @Test
    fun `当前输出已滚过可视条目时视为已跟上`() {
        assertTrue(
            hasReachedChatPageAnchor(
                position(
                    liveEdgeOffset = null,
                    firstVisibleItemIndex = 6,
                    lastVisibleItemIndex = 6,
                ),
                anchorOffset = 64,
            ),
        )
    }

    @Test
    fun `分页锚点轻微下移但末尾内容仍可见时视为已到末尾`() {
        val position = position(liveEdgeOffset = 65, outputEndOffset = 47)

        assertFalse(hasReachedChatPageAnchor(position, anchorOffset = 64))
        assertTrue(hasReachedChatOutputEnd(position))
    }

    @Test
    fun `末尾内容位于阅读区下方时视为未到末尾`() {
        assertFalse(hasReachedChatOutputEnd(position(outputEndOffset = 601)))
    }

    @Test
    fun `末尾内容已滚过阅读区时视为已到末尾`() {
        assertTrue(
            hasReachedChatOutputEnd(
                position(
                    outputEndOffset = null,
                    firstVisibleItemIndex = 5,
                    lastVisibleItemIndex = 6,
                ),
            ),
        )
    }

    @Test
    fun `末尾文字位于阅读区内时可见`() {
        assertTrue(isChatOutputEndVisible(position(outputEndOffset = 600)))
        assertTrue(
            isChatOutputEndVisible(
                position(outputEndOffset = 101, readingStartOffset = 100),
            ),
        )
    }

    @Test
    fun `末尾文字位于阅读区外时不可见`() {
        assertFalse(isChatOutputEndVisible(position(outputEndOffset = 601)))
        assertFalse(
            isChatOutputEndVisible(
                position(outputEndOffset = 100, readingStartOffset = 100),
            ),
        )
        assertFalse(isChatOutputEndVisible(position(outputEndOffset = null)))
    }

    @Test
    fun `运行完成且末尾可见时归位`() {
        assertTrue(
            shouldDockCompletedChatOutput(
                wasRunning = true,
                isRunning = false,
                wasOutputEndVisible = true,
                isUserScrollInProgress = false,
            ),
        )
    }

    @Test
    fun `非完成转换或用户正在滚动时不归位`() {
        assertFalse(
            shouldDockCompletedChatOutput(
                wasRunning = false,
                isRunning = false,
                wasOutputEndVisible = true,
                isUserScrollInProgress = false,
            ),
        )
        assertFalse(
            shouldDockCompletedChatOutput(
                wasRunning = true,
                isRunning = false,
                wasOutputEndVisible = false,
                isUserScrollInProgress = false,
            ),
        )
        assertFalse(
            shouldDockCompletedChatOutput(
                wasRunning = true,
                isRunning = false,
                wasOutputEndVisible = true,
                isUserScrollInProgress = true,
            ),
        )
    }

    @Test
    fun `无分页跑道时末尾内容二次布局到阅读区下方后继续跟随`() {
        assertTrue(
            shouldFollowChatOutput(
                position = position(outputEndOffset = 601),
                pageRunwayEnabled = false,
            ),
        )
    }

    @Test
    fun `无分页跑道时末尾内容仍在阅读区内不重复跟随`() {
        assertFalse(
            shouldFollowChatOutput(
                position = position(outputEndOffset = 600),
                pageRunwayEnabled = false,
            ),
        )
    }

    @Test
    fun `分页跑道启用时仍按分页锚点跟随`() {
        assertTrue(
            shouldFollowChatOutput(
                position = position(liveEdgeOffset = 601, outputEndOffset = 47),
                pageRunwayEnabled = true,
            ),
        )
    }

    @Test
    fun `当前输出已到窗口底部时阻止用户继续向前滚动`() {
        assertTrue(shouldStopForwardUserScroll(position(liveEdgeOffset = 600)))
        assertFalse(shouldStopForwardUserScroll(position(liveEdgeOffset = 601)))
    }

    @Test
    fun `当前输出已滚过窗口时阻止用户继续向前滚动`() {
        assertTrue(
            shouldStopForwardUserScroll(
                position(
                    liveEdgeOffset = null,
                    firstVisibleItemIndex = 6,
                    lastVisibleItemIndex = 6,
                ),
            ),
        )
    }

    @Test
    fun `当前输出回到底部后可以安全移除分页跑道`() {
        assertTrue(canReleaseChatPageRunway(position(liveEdgeOffset = 599), liveEdgeHeight = 1))
        assertFalse(canReleaseChatPageRunway(position(liveEdgeOffset = 598), liveEdgeHeight = 1))
    }

    @Test
    fun `当前输出位于可视条目之后时可以安全移除分页跑道`() {
        assertTrue(
            canReleaseChatPageRunway(
                position = position(
                    liveEdgeOffset = null,
                    firstVisibleItemIndex = 2,
                    lastVisibleItemIndex = 4,
                ),
                liveEdgeHeight = 1,
            ),
        )
    }

    @Test
    fun `阅读区因输入法缩小时按缩小量补偿滚动`() {
        assertEquals(
            240,
            chatViewportShrinkCompensation(
                previousReadingEndOffset = 600,
                readingEndOffset = 360,
            ),
        )
    }

    @Test
    fun `阅读区扩大时不反向补偿滚动`() {
        assertEquals(
            0,
            chatViewportShrinkCompensation(
                previousReadingEndOffset = 360,
                readingEndOffset = 600,
            ),
        )
    }

    @Test
    fun `翻页保留上下文重叠高度`() {
        assertEquals(536, chatPageAdvanceDistance(position(), pageOverlap = 64))
    }

    private fun position(
        liveEdgeOffset: Int? = 600,
        outputEndOffset: Int? = 582,
        firstVisibleItemIndex: Int? = 2,
        lastVisibleItemIndex: Int? = 5,
        readingStartOffset: Int = 0,
    ) = ChatPagingPosition(
        liveEdgeIndex = 5,
        liveEdgeOffset = liveEdgeOffset,
        outputEndOffset = outputEndOffset,
        firstVisibleItemIndex = firstVisibleItemIndex,
        lastVisibleItemIndex = lastVisibleItemIndex,
        readingStartOffset = readingStartOffset,
        readingEndOffset = 600,
    )
}
