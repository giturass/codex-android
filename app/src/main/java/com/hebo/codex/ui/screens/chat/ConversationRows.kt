package com.hebo.codex.ui.screens.chat

import com.hebo.codex.domain.AgentMessagePhase
import com.hebo.codex.domain.ConversationItem
import com.hebo.codex.domain.ConversationTurn
import com.hebo.codex.domain.isAsyncQuestion
import com.hebo.codex.domain.TurnDetailsState

internal sealed interface ConversationRow {
    val key: String
    val turnId: String

    data class Item(val item: ConversationItem) : ConversationRow {
        override val key: String = item.rowKey("item")
        override val turnId: String = item.turnId
    }

    data class ActivityGroup(
        val items: List<ConversationItem>,
        val isSealed: Boolean,
    ) : ConversationRow {
        override val key: String = items.first().rowKey("activity-group")
        override val turnId: String = items.first().turnId
    }

    data class TurnActivity(override val turnId: String) : ConversationRow {
        override val key: String = "turn-activity:${turnId.length}:$turnId"
    }
}

internal fun buildConversationRows(
    turns: List<ConversationTurn>,
    activeTurnId: String?,
): List<ConversationRow> = turns.flatMap { turn ->
    if (turn.detailsState == TurnDetailsState.Loaded) {
        buildLoadedConversationRows(turn.items, activeTurnId)
    } else {
        buildList {
            var activityAdded = false
            turn.items.forEach { item ->
                if (item !is ConversationItem.UserMessage && !activityAdded) {
                    add(ConversationRow.TurnActivity(turn.id))
                    activityAdded = true
                }
                if (!item.isGroupableActivity()) add(ConversationRow.Item(item))
            }
            if (!activityAdded) add(ConversationRow.TurnActivity(turn.id))
        }
    }
}

internal fun buildLoadedConversationRows(
    items: List<ConversationItem>,
    activeTurnId: String?,
): List<ConversationRow> {
    val rows = mutableListOf<ConversationRow>()
    var activityItems = mutableListOf<ConversationItem>()

    fun flushActivities() {
        if (activityItems.isNotEmpty()) {
            rows += ConversationRow.ActivityGroup(
                items = activityItems.toList(),
                isSealed = false,
            )
        }
        activityItems = mutableListOf()
    }

    fun sealActivityGroups(turnId: String) {
        rows.indices.forEach { index ->
            val row = rows[index] as? ConversationRow.ActivityGroup ?: return@forEach
            if (!row.isSealed && row.items.first().turnId == turnId) {
                rows[index] = row.copy(isSealed = true)
            }
        }
    }

    items.forEach { item ->
        if (item.isGroupableActivity() &&
            (activityItems.isEmpty() || activityItems.last().turnId == item.turnId)
        ) {
            activityItems += item
        } else {
            flushActivities()
            if (item is ConversationItem.AgentMessage && item.startsFinalAnswer()) {
                sealActivityGroups(item.turnId)
            }
            if (item.isGroupableActivity()) activityItems += item else rows += ConversationRow.Item(item)
        }
    }
    flushActivities()
    return rows.map { row ->
        if (row is ConversationRow.ActivityGroup && row.items.first().turnId != activeTurnId) {
            row.copy(isSealed = true)
        } else {
            row
        }
    }
}

internal data class ActivityDetailsContent(
    val items: List<ConversationItem>,
    val isSealed: Boolean,
)

internal fun activityDetailsContent(
    turn: ConversationTurn,
    rows: List<ConversationRow>,
    rowKey: String?,
): ActivityDetailsContent? {
    if (rowKey == null) {
        return ActivityDetailsContent(
            items = if (turn.detailsState == TurnDetailsState.Loaded) {
                turn.items.filter { it.isGroupableActivity() || it is ConversationItem.UserShellCommand }
            } else {
                emptyList()
            },
            isSealed = true,
        )
    }
    return when (val row = rows.firstOrNull { it.turnId == turn.id && it.key == rowKey }) {
        is ConversationRow.ActivityGroup -> ActivityDetailsContent(row.items, row.isSealed)
        is ConversationRow.Item -> ActivityDetailsContent(listOf(row.item), isSealed = true)
        is ConversationRow.TurnActivity, null -> null
    }
}

private fun ConversationItem.isGroupableActivity(): Boolean =
    when (this) {
        is ConversationItem.UserMessage,
        is ConversationItem.UserShellCommand,
        -> false

        is ConversationItem.AgentMessage -> phase == AgentMessagePhase.COMMENTARY && !isAsyncQuestion
        is ConversationItem.Reasoning,
        is ConversationItem.Plan,
        is ConversationItem.CommandExecution,
        is ConversationItem.FileChange,
        is ConversationItem.Activity,
        is ConversationItem.AgentCollaboration,
        is ConversationItem.SubAgentActivity,
        -> true
    }

private fun ConversationItem.AgentMessage.startsFinalAnswer(): Boolean =
    !isAsyncQuestion && (phase == AgentMessagePhase.FINAL_ANSWER || (phase == null && text.isNotBlank()))

private fun ConversationItem.rowKey(prefix: String): String =
    "$prefix:${turnId.length}:$turnId:${id.length}:$id"
