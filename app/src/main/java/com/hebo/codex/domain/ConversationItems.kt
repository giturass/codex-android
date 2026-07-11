package com.hebo.codex.domain

internal fun List<ConversationItem>.upsertConversationItem(newItem: ConversationItem): List<ConversationItem> {
    val index = indexOfFirst { current ->
        current.hasSameIdentity(newItem) || current.hasSameClientMessage(newItem)
    }
    if (index == -1) return this + newItem
    return toMutableList().apply { set(index, newItem) }
}

internal fun List<ConversationItem>.replaceConversationItem(
    turnId: String,
    itemId: String,
    transform: (ConversationItem) -> ConversationItem,
): List<ConversationItem>? {
    val index = indexOfFirst { item -> item.turnId == turnId && item.id == itemId }
    if (index == -1) return null
    return toMutableList().apply { set(index, transform(get(index))) }
}

private fun ConversationItem.hasSameIdentity(other: ConversationItem): Boolean =
    turnId == other.turnId && id == other.id

private fun ConversationItem.hasSameClientMessage(other: ConversationItem): Boolean =
    this is ConversationItem.UserMessage &&
        other is ConversationItem.UserMessage &&
        clientId != null &&
        clientId == other.clientId
