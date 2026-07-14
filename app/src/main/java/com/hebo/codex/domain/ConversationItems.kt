package com.hebo.codex.domain

internal val ConversationItem.key: ConversationItemKey
    get() = ConversationItemKey(turnId, id)

internal fun ConversationState.withConversationItem(
    item: ConversationItem,
    isActive: Boolean,
): ConversationState = copy(
    items = items.upsertConversationItem(item),
    activeItemKeys = if (isActive) activeItemKeys + item.key else activeItemKeys - item.key,
)

internal fun ConversationState.withUpdatedConversationItem(
    turnId: String,
    itemId: String,
    markActive: Boolean,
    transform: (ConversationItem) -> ConversationItem,
): ConversationState? {
    val items = items.replaceConversationItem(turnId, itemId, transform) ?: return null
    val key = ConversationItemKey(turnId, itemId)
    return copy(
        items = items,
        activeItemKeys = if (markActive) activeItemKeys + key else activeItemKeys,
    )
}

internal fun ConversationState.withoutActiveItems(turnId: String): ConversationState = copy(
    activeItemKeys = activeItemKeys.filterTo(mutableSetOf()) { key -> key.turnId != turnId },
)

internal fun List<ConversationItem>.upsertConversationItem(newItem: ConversationItem): List<ConversationItem> {
    val index = indexOfFirst { current -> current.hasSameIdentity(newItem) }
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
