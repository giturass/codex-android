package com.hebo.codex.domain

internal val ConversationItem.key: ConversationItemKey
    get() = ConversationItemKey(turnId, id)

internal fun ConversationState.withConversationItem(
    item: ConversationItem,
    isActive: Boolean,
): ConversationState = copy(
    history = history.copy(
        turns = history.turns.upsertConversationItem(
            item = item,
            defaultStatus = if (activeTurnId == item.turnId || isRunning) {
                ConversationTurnStatus.IN_PROGRESS
            } else {
                ConversationTurnStatus.COMPLETED
            },
        ),
    ),
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
    val turns = history.turns.replaceTurnItem(turnId, itemId, transform) ?: return null
    val key = ConversationItemKey(turnId, itemId)
    return copy(
        history = history.copy(turns = turns),
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
    val restoredTurnIds = restored.history.turns.mapTo(mutableSetOf(), ConversationTurn::id)
    val firstSharedTurnIndex = history.turns.indexOfFirst { it.id in restoredTurnIds }
    val retainedTurns = if (firstSharedTurnIndex > 0) {
        history.turns.take(firstSharedTurnIndex)
    } else {
        emptyList()
    }
    fun mergedHistory(turns: List<ConversationTurn>) = restored.history.copy(
        turns = retainedTurns + turns,
        olderTurnsCursor = if (retainedTurns.isEmpty()) {
            restored.history.olderTurnsCursor
        } else {
            history.olderTurnsCursor
        },
    )
    val restoredTurnId = restored.activeTurnId
    if (restoredTurnId == null || activeTurnId != restoredTurnId) {
        return restored.copy(
            history = mergedHistory(restored.history.turns),
        )
    }

    val restoredKeys = restored.items.mapTo(mutableSetOf(), ConversationItem::key)
    val currentItems = items.associateBy { it.key }
    val retainedKeys = activeItemKeys.filterTo(mutableSetOf()) { key ->
        key.turnId == restoredTurnId && key !in restoredKeys &&
            currentItems[key]?.usesTransientActivityState == true
    }
    if (retainedKeys.isEmpty()) {
        return restored.copy(
            history = mergedHistory(restored.history.turns),
        )
    }

    val retainedItems = history.turns
        .singleOrNull { it.id == restoredTurnId }
        ?.items
        .orEmpty()
        .filter { it.key in retainedKeys }
    val mergedTurns = restored.history.turns.map { turn ->
        if (turn.id == restoredTurnId) turn.copy(items = turn.items + retainedItems) else turn
    }
    return restored.copy(
        history = mergedHistory(mergedTurns),
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
        parentThreadId = merged.parentThreadId,
        agentNickname = merged.agentNickname,
        agentRole = merged.agentRole,
        canAcceptDirectInput = merged.canAcceptDirectInput,
        history = merged.history,
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
        is ConversationItem.AgentCollaboration,
        is ConversationItem.SubAgentActivity,
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

private fun List<ConversationTurn>.upsertConversationItem(
    item: ConversationItem,
    defaultStatus: ConversationTurnStatus,
): List<ConversationTurn> {
    val turnIndex = indexOfFirst { it.id == item.turnId }
    if (turnIndex == -1) {
        return this + ConversationTurn(
            id = item.turnId,
            status = defaultStatus,
            items = listOf(item),
        )
    }
    return toMutableList().apply {
        val turn = get(turnIndex)
        set(turnIndex, turn.copy(items = turn.items.upsertConversationItem(item)))
    }
}

private fun List<ConversationTurn>.replaceTurnItem(
    turnId: String,
    itemId: String,
    transform: (ConversationItem) -> ConversationItem,
): List<ConversationTurn>? {
    val turnIndex = indexOfFirst { it.id == turnId }
    if (turnIndex == -1) return null
    val turn = get(turnIndex)
    val updatedItems = turn.items.replaceConversationItem(turnId, itemId, transform) ?: return null
    return toMutableList().apply {
        set(turnIndex, turn.copy(items = updatedItems))
    }
}

private fun ConversationItem.hasSameIdentity(other: ConversationItem): Boolean =
    turnId == other.turnId && id == other.id
