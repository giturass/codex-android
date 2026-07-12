package com.hebo.codex.domain

internal fun List<ConversationItem>.upsertConversationItem(newItem: ConversationItem): List<ConversationItem> {
    val index = indexOfFirst { current ->
        current.hasSameIdentity(newItem) || current.hasSameClientMessage(newItem)
    }
    if (index == -1) return this + newItem
    if (get(index) == newItem) return this
    check(!get(index).isFinalizedHistory()) { "已完成的会话历史不可修改：${newItem.turnId}/${newItem.id}" }
    return toMutableList().apply { set(index, newItem) }
}

internal fun List<ConversationItem>.replaceConversationItem(
    turnId: String,
    itemId: String,
    transform: (ConversationItem) -> ConversationItem,
): List<ConversationItem>? {
    val index = indexOfFirst { item -> item.turnId == turnId && item.id == itemId }
    if (index == -1) return null
    check(!get(index).isFinalizedHistory()) { "已完成的会话历史不可修改：$turnId/$itemId" }
    return toMutableList().apply { set(index, transform(get(index))) }
}

private fun ConversationItem.isFinalizedHistory(): Boolean = when (this) {
    is ConversationItem.UserMessage -> !isPending
    is ConversationItem.AgentMessage -> !isStreaming
    is ConversationItem.Reasoning -> !isStreaming
    is ConversationItem.Plan -> !isStreaming
    is ConversationItem.CommandExecution -> status != ActivityStatus.IN_PROGRESS
    is ConversationItem.FileChange -> status != ActivityStatus.IN_PROGRESS
    is ConversationItem.Activity -> status != ActivityStatus.IN_PROGRESS
}

private fun ConversationItem.hasSameIdentity(other: ConversationItem): Boolean =
    turnId == other.turnId && id == other.id

private fun ConversationItem.hasSameClientMessage(other: ConversationItem): Boolean =
    this is ConversationItem.UserMessage &&
        other is ConversationItem.UserMessage &&
        clientId != null &&
        clientId == other.clientId
