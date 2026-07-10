package com.hebo.codex.ui.screens.chat

import com.hebo.codex.domain.ConversationItem

internal sealed interface ConversationRow {
    val key: String

    data class Item(val item: ConversationItem) : ConversationRow {
        override val key: String = item.id
    }

    data class ActivityGroup(val items: List<ConversationItem>) : ConversationRow {
        override val key: String = "activity-group:${items.first().id}"
    }
}

internal fun buildConversationRows(items: List<ConversationItem>): List<ConversationRow> {
    val rows = mutableListOf<ConversationRow>()
    var activityItems = mutableListOf<ConversationItem>()

    fun flushActivities() {
        when (activityItems.size) {
            0 -> Unit
            1 -> rows += ConversationRow.Item(activityItems.single())
            else -> rows += ConversationRow.ActivityGroup(activityItems.toList())
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
    this is ConversationItem.CommandExecution ||
        this is ConversationItem.FileChange ||
        this is ConversationItem.Activity
