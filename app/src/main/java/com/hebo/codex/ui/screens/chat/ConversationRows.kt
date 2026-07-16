package com.hebo.codex.ui.screens.chat

import com.hebo.codex.domain.ConversationItem

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
}

internal fun buildConversationRows(
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
            if (item is ConversationItem.AgentMessage && item.text.isNotBlank()) {
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

private fun ConversationItem.isGroupableActivity(): Boolean =
    this !is ConversationItem.UserMessage &&
        this !is ConversationItem.AgentMessage &&
        this !is ConversationItem.UserShellCommand &&
        this !is ConversationItem.Plan

private fun ConversationItem.rowKey(prefix: String): String =
    "$prefix:${turnId.length}:$turnId:${id.length}:$id"
