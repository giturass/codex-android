package com.hebo.codex.domain

internal enum class ConversationRunStatus {
    ACTIVE,
    SYSTEM_ERROR,
    INACTIVE,
}

internal fun interface ConversationItemUpdate {
    fun apply(item: ConversationItem): ConversationItem
}

internal sealed interface ConversationEvent {
    data class StatusChanged(val status: ConversationRunStatus) : ConversationEvent

    data class NameUpdated(val name: String?) : ConversationEvent

    data class Closed(val notice: ConversationNotice) : ConversationEvent

    data class TokenUsageUpdated(val usage: TokenUsage) : ConversationEvent

    data class GoalChanged(val goal: ThreadGoal?) : ConversationEvent

    data class TurnStarted(val turnId: String) : ConversationEvent

    data class TurnCompleted(
        val turnId: String,
        val status: ConversationTurnStatus,
        val error: UserFacingError?,
    ) : ConversationEvent

    data class PlanUpdated(val plan: ActiveTurnPlan) : ConversationEvent

    data class NoticeAdded(
        val notice: ConversationNotice,
        val terminalError: UserFacingError? = null,
    ) : ConversationEvent

    data class ItemUpserted(
        val item: ConversationItem,
        val isActive: Boolean,
    ) : ConversationEvent

    data class ItemChanged(
        val method: String,
        val turnId: String,
        val itemId: String,
        val markActive: Boolean,
        val update: ConversationItemUpdate,
    ) : ConversationEvent

    data class RequestResolved(val requestKey: String) : ConversationEvent
}

internal fun ConversationState.reduceConversationEvent(
    event: ConversationEvent,
    announceGoal: Boolean = false,
): ConversationState = when (event) {
    is ConversationEvent.StatusChanged -> {
        val active = event.status == ConversationRunStatus.ACTIVE
        copy(
            activeItemKeys = if (active) activeItemKeys else emptySet(),
            isRunning = active,
        )
    }

    is ConversationEvent.NameUpdated -> copy(title = event.name ?: title)
    is ConversationEvent.Closed -> copy(
        activeItemKeys = emptySet(),
        isRunning = false,
        notices = (notices + event.notice).takeLast(MAX_NOTICES),
    )

    is ConversationEvent.TokenUsageUpdated -> copy(
        tokenUsage = event.usage,
        runtimeStatus = runtimeStatus.reduce(RuntimeStatusEvent.TokenUsageSynced),
    )
    is ConversationEvent.GoalChanged -> withGoal(event.goal, announceGoal).copy(
        runtimeStatus = runtimeStatus.reduce(RuntimeStatusEvent.GoalSynced),
    )
    is ConversationEvent.TurnStarted -> copy(
        history = history.copy(
            turns = history.turns.upsertConversationTurn(
                ConversationTurn(event.turnId, ConversationTurnStatus.IN_PROGRESS),
            ),
        ),
        activeItemKeys = emptySet(),
        activeTurnId = event.turnId,
        isRunning = true,
        plan = null,
        errorMessage = null,
    )

    is ConversationEvent.TurnCompleted -> copy(
        history = history.copy(
            turns = history.turns.upsertConversationTurn(
                ConversationTurn(event.turnId, event.status),
            ),
        ),
        activeTurnId = null,
        isRunning = false,
        pendingRequests = emptyList(),
        plan = null,
        errorMessage = event.error,
    ).withoutActiveItems(event.turnId)

    is ConversationEvent.PlanUpdated -> withActiveTurnPlan(event.plan)
    is ConversationEvent.NoticeAdded -> copy(
        notices = (notices + event.notice).takeLast(MAX_NOTICES),
        errorMessage = event.terminalError ?: errorMessage,
    )

    is ConversationEvent.ItemUpserted -> withConversationItem(event.item, event.isActive)
    is ConversationEvent.ItemChanged -> withUpdatedConversationItem(
        turnId = event.turnId,
        itemId = event.itemId,
        markActive = event.markActive,
        transform = event.update::apply,
    ) ?: this

    is ConversationEvent.RequestResolved -> copy(
        pendingRequests = pendingRequests.filterNot { it.requestKey == event.requestKey },
    )
}

private fun List<ConversationTurn>.upsertConversationTurn(turn: ConversationTurn): List<ConversationTurn> {
    val index = indexOfFirst { it.id == turn.id }
    if (index == -1) return this + turn
    return toMutableList().apply {
        val current = get(index)
        set(index, turn.copy(items = current.items))
    }
}
