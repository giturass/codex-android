package com.hebo.codex.ui.screens.chat

import com.hebo.codex.domain.ActivityStatus
import com.hebo.codex.domain.ConversationItem

internal sealed interface ConversationRow {
    val key: String

    data class Item(val item: ConversationItem) : ConversationRow {
        override val key: String = item.rowKey("item")
    }

    data class ActivityGroup(val items: List<ConversationItem>) : ConversationRow {
        override val key: String = items.first().rowKey("activity-group")
    }
}

internal fun buildConversationRows(items: List<ConversationItem>): List<ConversationRow> {
    val rows = mutableListOf<ConversationRow>()
    var activityItems = mutableListOf<ConversationItem>()

    fun flushActivities() {
        if (activityItems.isNotEmpty()) {
            rows += ConversationRow.ActivityGroup(activityItems.toList())
        }
        activityItems = mutableListOf()
    }

    items.forEach { item ->
        if (item.isGroupableActivity() &&
            (activityItems.isEmpty() || activityItems.last().turnId == item.turnId)
        ) {
            activityItems += item
        } else {
            flushActivities()
            if (item.isGroupableActivity()) activityItems += item else rows += ConversationRow.Item(item)
        }
    }
    flushActivities()
    return rows
}

private fun ConversationItem.isGroupableActivity(): Boolean =
    this !is ConversationItem.UserMessage && this !is ConversationItem.AgentMessage

internal fun List<ConversationRow>.hasInProgressActivity(): Boolean = any { row ->
    val items = when (row) {
        is ConversationRow.Item -> listOf(row.item)
        is ConversationRow.ActivityGroup -> row.items
    }
    items.any { it.activityStatus() == ActivityStatus.IN_PROGRESS }
}

private fun ConversationItem.rowKey(prefix: String): String =
    "$prefix:${turnId.length}:$turnId:${id.length}:$id"
