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
        assertTrue(hasReachedChatCurrentOutput(position(liveEdgeOffset = 64), pageOverlap = 64))
        assertFalse(hasReachedChatCurrentOutput(position(liveEdgeOffset = 65), pageOverlap = 64))
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
                pageOverlap = 64,
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
