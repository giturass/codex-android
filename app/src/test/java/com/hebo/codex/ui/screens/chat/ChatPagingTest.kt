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
    fun `分页锚点轻微下移但末尾内容仍可见时视为已到末尾`() {
        val position = position(liveEdgeOffset = 65, outputEndOffset = 47)

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
    fun `查看历史的轻微滚动结束后继续跟随`() {
        assertEquals(
            ChatUserScrollResolution.FOLLOW_OUTPUT,
            resolveChatUserScroll(
                position = position(outputEndOffset = 600),
                isScrollInProgress = false,
            ),
        )
    }

    @Test
    fun `查看历史使末尾离开阅读区后显示返回按钮`() {
        assertEquals(
            ChatUserScrollResolution.SHOW_RETURN_TO_OUTPUT,
            resolveChatUserScroll(
                position = position(outputEndOffset = 601),
                isScrollInProgress = false,
            ),
        )
    }

    @Test
    fun `滚动过程中暂不改变跟随状态`() {
        assertEquals(
            ChatUserScrollResolution.IN_PROGRESS,
            resolveChatUserScroll(
                position = position(outputEndOffset = 601),
                isScrollInProgress = true,
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
    fun `生成结束后末尾内容二次布局到阅读区下方后继续跟随`() {
        assertTrue(
            shouldFollowChatOutput(
                position = position(outputEndOffset = 601),
                isRunning = false,
            ),
        )
    }

    @Test
    fun `生成结束后末尾内容仍在阅读区内不重复跟随`() {
        assertFalse(
            shouldFollowChatOutput(
                position = position(outputEndOffset = 600),
                isRunning = false,
            ),
        )
    }

    @Test
    fun `生成中即使尚无留白也按分页锚点跟随`() {
        assertTrue(
            shouldFollowChatOutput(
                position = position(liveEdgeOffset = 601, outputEndOffset = 47),
                isRunning = true,
            ),
        )
    }

    @Test
    fun `短回答尚未填满页面时不触发翻页`() {
        assertFalse(
            shouldFollowChatOutput(
                position = position(liveEdgeOffset = 120, outputEndOffset = 102),
                isRunning = true,
            ),
        )
    }

    @Test
    fun `翻页只分配目标位置缺少的空间`() {
        assertEquals(
            534,
            chatPageRunwayForAdvance(position(liveEdgeOffset = 601), pageDistance = 536, liveEdgeHeight = 1),
        )
        assertEquals(
            235,
            chatPageRunwayForAdvance(position(liveEdgeOffset = 900), pageDistance = 536, liveEdgeHeight = 1),
        )
        assertEquals(
            0,
            chatPageRunwayForAdvance(position(liveEdgeOffset = 1200), pageDistance = 536, liveEdgeHeight = 1),
        )
    }

    @Test
    fun `翻页前未测量到末尾时最多临时分配一页`() {
        assertEquals(
            536,
            chatPageRunwayForAdvance(position(liveEdgeOffset = null), pageDistance = 536, liveEdgeHeight = 1),
        )
    }

    @Test
    fun `新增内容逐步消耗本页留白`() {
        var runway = remainingRunway(position(liveEdgeOffset = 64))
        assertEquals(535, runway)
        runway = remainingRunway(position(liveEdgeOffset = 200), currentHeight = runway)
        assertEquals(399, runway)
        runway = remainingRunway(position(liveEdgeOffset = 599), currentHeight = runway)
        assertEquals(0, runway)
    }

    @Test
    fun `回看历史不消耗留白且可以返回原分页位置`() {
        val originalRunway = remainingRunway(position(liveEdgeOffset = 120))
        val historyRunway = remainingRunway(
            position(liveEdgeOffset = 200),
            currentHeight = originalRunway,
            visibleAnchorOffset = 80,
        )

        assertEquals(479, originalRunway)
        assertEquals(originalRunway, historyRunway)
        assertEquals(originalRunway, remainingRunway(position(liveEdgeOffset = 120), historyRunway))
    }

    @Test
    fun `回看期间新增内容仍消耗原页留白`() {
        assertEquals(
            379,
            remainingRunway(position(liveEdgeOffset = 300), currentHeight = 479, visibleAnchorOffset = 80),
        )
    }

    @Test
    fun `回看过远导致锚点或末尾未测量时保留原页空间`() {
        assertEquals(479, remainingRunway(position(liveEdgeOffset = 200), 479, visibleAnchorOffset = null))
        assertEquals(
            479,
            remainingRunway(position(liveEdgeOffset = null, lastVisibleItemIndex = 3), 479, visibleAnchorOffset = null),
        )
    }

    @Test
    fun `回看时锚点仍可见但末尾已离开测量范围也保留原页空间`() {
        assertEquals(
            499,
            remainingChatPageRunway(
                position = position(liveEdgeOffset = null, lastVisibleItemIndex = 4),
                currentHeight = 499,
                pageAnchorOffset = -200,
                visibleAnchorOffset = 800,
                liveEdgeHeight = 1,
            ),
        )
    }

    @Test
    fun `锚点可见且新增内容超出可测量范围时释放留白`() {
        assertEquals(
            0,
            remainingRunway(
                position = position(
                    liveEdgeOffset = null,
                    firstVisibleItemIndex = 2,
                    lastVisibleItemIndex = 4,
                ),
            ),
        )
    }

    @Test
    fun `阅读区缩小时留白也随可用空间缩短`() {
        assertEquals(235, remainingRunway(position(liveEdgeOffset = 64, readingEndOffset = 300)))
    }

    @Test
    fun `阅读区扩大或内容收缩时不会重新制造额外留白`() {
        assertEquals(200, remainingRunway(position(liveEdgeOffset = 64), currentHeight = 200))
        assertEquals(0, remainingRunway(position(liveEdgeOffset = 64), currentHeight = 0))
    }

    @Test
    fun `页面锚点本身带有偏移时仍按内容相对距离计算留白`() {
        assertEquals(
            399,
            remainingChatPageRunway(
                position = position(liveEdgeOffset = 280),
                currentHeight = 535,
                pageAnchorOffset = -40,
                visibleAnchorOffset = 40,
                liveEdgeHeight = 1,
            ),
        )
    }

    @Test
    fun `阅读区因输入法缩小时补偿被遮挡的输出`() {
        assertEquals(
            240,
            chatViewportShrinkCompensation(
                previousReadingEndOffset = 600,
                position = position(liveEdgeOffset = 599, readingEndOffset = 360),
                liveEdgeHeight = 1,
            ),
        )
        assertEquals(
            41,
            chatViewportShrinkCompensation(
                previousReadingEndOffset = 600,
                position = position(liveEdgeOffset = 400, readingEndOffset = 360),
                liveEdgeHeight = 1,
            ),
        )
    }

    @Test
    fun `输入区展开后输出仍可见时不把内容继续向上推`() {
        assertEquals(
            0,
            chatViewportShrinkCompensation(
                previousReadingEndOffset = 600,
                position = position(liveEdgeOffset = 120, readingEndOffset = 360),
                liveEdgeHeight = 1,
            ),
        )
    }

    @Test
    fun `阅读区扩大时不反向补偿滚动`() {
        assertEquals(
            0,
            chatViewportShrinkCompensation(
                previousReadingEndOffset = 360,
                position = position(liveEdgeOffset = 599),
                liveEdgeHeight = 1,
            ),
        )
    }

    @Test
    fun `翻页保留上下文重叠高度`() {
        assertEquals(536, chatPageAdvanceDistance(position(), pageOverlap = 64))
    }

    private fun remainingRunway(
        position: ChatPagingPosition,
        currentHeight: Int = 535,
        visibleAnchorOffset: Int? = 0,
    ) = remainingChatPageRunway(
        position = position,
        currentHeight = currentHeight,
        pageAnchorOffset = 0,
        visibleAnchorOffset = visibleAnchorOffset,
        liveEdgeHeight = 1,
    )

    private fun position(
        liveEdgeOffset: Int? = 600,
        outputEndOffset: Int? = 582,
        firstVisibleItemIndex: Int? = 2,
        lastVisibleItemIndex: Int? = 5,
        readingStartOffset: Int = 0,
        readingEndOffset: Int = 600,
    ) = ChatPagingPosition(
        liveEdgeIndex = 5,
        liveEdgeOffset = liveEdgeOffset,
        outputEndOffset = outputEndOffset,
        firstVisibleItemIndex = firstVisibleItemIndex,
        lastVisibleItemIndex = lastVisibleItemIndex,
        readingStartOffset = readingStartOffset,
        readingEndOffset = readingEndOffset,
    )
}
