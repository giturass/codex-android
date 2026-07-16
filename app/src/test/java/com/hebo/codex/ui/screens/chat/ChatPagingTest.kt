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
        assertTrue(hasReachedChatCurrentOutput(position(liveEdgeOffset = 64), anchorOffset = 64))
        assertFalse(hasReachedChatCurrentOutput(position(liveEdgeOffset = 65), anchorOffset = 64))
    }

    @Test
    fun `当前输出已滚过可视条目时视为已跟上`() {
        assertTrue(
            hasReachedChatCurrentOutput(
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
        firstVisibleItemIndex: Int? = 2,
        lastVisibleItemIndex: Int? = 5,
    ) = ChatPagingPosition(
        liveEdgeIndex = 5,
        liveEdgeOffset = liveEdgeOffset,
        firstVisibleItemIndex = firstVisibleItemIndex,
        lastVisibleItemIndex = lastVisibleItemIndex,
        readingEndOffset = 600,
    )
}
