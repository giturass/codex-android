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

    data class TurnActivity(override val turnId: String) : ConversationRow {
        override val key: String = "turn-activity:${turnId.length}:$turnId"
    }
}

internal fun buildConversationRows(
    turns: List<ConversationTurn>,
): List<ConversationRow> = turns.flatMap { turn ->
    buildList {
        var activityAdded = false
        turn.items.forEach { item ->
            if (item !is ConversationItem.UserMessage && !activityAdded) {
                add(ConversationRow.TurnActivity(turn.id))
                activityAdded = true
            }
            if (!item.isProcessActivity()) add(ConversationRow.Item(item))
        }
        if (!activityAdded) add(ConversationRow.TurnActivity(turn.id))
    }
}

internal data class ActivityDetailsContent(
    val items: List<ConversationItem>,
    val isSealed: Boolean,
)

internal fun activityDetailsContent(
    turn: ConversationTurn,
    activeTurnId: String?,
): ActivityDetailsContent {
    val lastActivityIndex = turn.items.indexOfLast {
        it.isProcessActivity() || it is ConversationItem.UserShellCommand
    }
    val lastAnswerIndex = turn.items.indexOfLast {
        it is ConversationItem.AgentMessage && it.startsFinalAnswer()
    }
    return ActivityDetailsContent(
        items = if (turn.detailsState == TurnDetailsState.Loaded) {
            turn.items.filter { it.isProcessActivity() || it is ConversationItem.UserShellCommand }
        } else {
            emptyList()
        },
        isSealed = turn.id != activeTurnId || lastAnswerIndex > lastActivityIndex,
    )
}

private fun ConversationItem.isProcessActivity(): Boolean =
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
