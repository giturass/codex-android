package com.hebo.codex.data.remote

import com.hebo.codex.domain.ConversationItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ReasoningStreamTest {
    @Test
    fun `推理增量按协议索引写入对应分段`() {
        val started = reasoning()
            .appendSummaryDelta(summaryIndex = 0, delta = "第一")
            .appendSummaryDelta(summaryIndex = 0, delta = "段")
            .addSummaryPart(summaryIndex = 1)
            .appendSummaryDelta(summaryIndex = 1, delta = "第二段")
            .appendRawContentDelta(contentIndex = 0, delta = "原始一")
            .appendRawContentDelta(contentIndex = 1, delta = "原始二")

        assertEquals(listOf("第一段", "第二段"), started.summaryParts)
        assertEquals(listOf("原始一", "原始二"), started.rawContentParts)
    }

    @Test
    fun `重复的摘要分段通知不会清空已有内容`() {
        val reasoning = reasoning()
            .appendSummaryDelta(summaryIndex = 0, delta = "已有内容")
            .addSummaryPart(summaryIndex = 0)

        assertEquals(listOf("已有内容"), reasoning.summaryParts)
    }

    @Test
    fun `推理增量拒绝跳过尚未创建的分段`() {
        assertThrows(ProtocolException::class.java) {
            reasoning().appendSummaryDelta(summaryIndex = 1, delta = "越界")
        }
        assertThrows(ProtocolException::class.java) {
            reasoning().appendRawContentDelta(contentIndex = -1, delta = "越界")
        }
    }

    private fun reasoning() = ConversationItem.Reasoning(
        id = "reasoning",
        turnId = "turn",
        summaryParts = emptyList(),
        rawContentParts = emptyList(),
    )
}
