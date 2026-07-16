package com.hebo.codex.domain

internal val ConversationItem.key: ConversationItemKey
    get() = ConversationItemKey(turnId, id)

internal fun ConversationState.withConversationItem(
    item: ConversationItem,
    isActive: Boolean,
): ConversationState = copy(
    items = items.upsertConversationItem(item),
    activeItemKeys = if (isActive && item.usesTransientActivityState) {
        activeItemKeys + item.key
    } else {
        activeItemKeys - item.key
    },
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

internal fun ConversationState.withRestoredConversation(
    restored: ConversationState,
): ConversationState {
    require(serverId == restored.serverId && threadId == restored.threadId) {
        "恢复状态不属于当前会话"
    }
    val restoredTurnId = restored.activeTurnId ?: return restored
    if (activeTurnId != restoredTurnId) return restored

    val restoredKeys = restored.items.mapTo(mutableSetOf(), ConversationItem::key)
    val currentItems = items.associateBy { it.key }
    val retainedKeys = activeItemKeys.filterTo(mutableSetOf()) { key ->
        key.turnId == restoredTurnId && key !in restoredKeys &&
            currentItems[key]?.usesTransientActivityState == true
    }
    if (retainedKeys.isEmpty()) return restored

    val restoredByKey = restored.items.associateByTo(LinkedHashMap(), ConversationItem::key)
    val mergedItems = buildList {
        items.forEach { current ->
            val replacement = restoredByKey.remove(current.key)
            if (replacement != null) add(replacement)
            else if (current.key in retainedKeys) add(current)
        }
        addAll(restoredByKey.values)
    }
    return restored.copy(
        items = mergedItems,
        activeItemKeys = restored.activeItemKeys + retainedKeys,
    )
}

internal fun ConversationState.withRefreshedConversation(
    refreshed: ConversationState,
): ConversationState {
    val merged = withRestoredConversation(refreshed)
    return copy(
        serverName = merged.serverName,
        title = merged.title,
        cwd = merged.cwd,
        items = merged.items,
        activeItemKeys = merged.activeItemKeys,
        activeTurnId = merged.activeTurnId,
        isRunning = merged.isRunning,
        plan = plan?.takeIf { it.turnId == merged.activeTurnId },
    )
}

internal val ConversationItem.usesTransientActivityState: Boolean
    get() = when (this) {
        is ConversationItem.AgentMessage,
        is ConversationItem.Reasoning,
        is ConversationItem.Plan,
        -> true

        is ConversationItem.UserMessage,
        is ConversationItem.CommandExecution,
        is ConversationItem.UserShellCommand,
        is ConversationItem.FileChange,
        is ConversationItem.Activity,
        -> false
    }

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
