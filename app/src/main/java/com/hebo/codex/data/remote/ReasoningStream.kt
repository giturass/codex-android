package com.hebo.codex.data.remote

import com.hebo.codex.domain.ConversationItem

internal fun ConversationItem.Reasoning.appendSummaryDelta(
    summaryIndex: Int,
    delta: String,
): ConversationItem.Reasoning = copy(
    summaryParts = summaryParts.appendIndexedDelta(summaryIndex, delta, "summaryIndex"),
)

internal fun ConversationItem.Reasoning.addSummaryPart(
    summaryIndex: Int,
): ConversationItem.Reasoning = copy(
    summaryParts = summaryParts.ensureIndexedPart(summaryIndex, "summaryIndex"),
)

internal fun ConversationItem.Reasoning.appendRawContentDelta(
    contentIndex: Int,
    delta: String,
): ConversationItem.Reasoning = copy(
    rawContentParts = rawContentParts.appendIndexedDelta(contentIndex, delta, "contentIndex"),
)

private fun List<String>.appendIndexedDelta(
    index: Int,
    delta: String,
    fieldName: String,
): List<String> = when {
    index < 0 -> throw ProtocolException("协议字段 $fieldName 不能为负数")
    index < size -> toMutableList().apply { this[index] += delta }
    index == size -> this + delta
    else -> throw ProtocolException("协议字段 $fieldName 跳过了尚未创建的分段")
}

private fun List<String>.ensureIndexedPart(index: Int, fieldName: String): List<String> = when {
    index < 0 -> throw ProtocolException("协议字段 $fieldName 不能为负数")
    index < size -> this
    index == size -> this + ""
    else -> throw ProtocolException("协议字段 $fieldName 跳过了尚未创建的分段")
}
