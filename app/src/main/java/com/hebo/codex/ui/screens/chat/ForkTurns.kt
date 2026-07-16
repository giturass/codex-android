package com.hebo.codex.ui.screens.chat

import com.hebo.codex.domain.ConversationItem
import com.hebo.codex.domain.UserMessagePart

internal data class ForkTurn(
    val turnId: String,
    val number: Int,
    val title: String,
    val attachmentCount: Int,
)

internal fun buildForkTurns(
    items: List<ConversationItem>,
    activeTurnId: String?,
): List<ForkTurn> = items
    .filter { it.turnId != "pending" }
    .groupBy(ConversationItem::turnId)
    .entries
    .mapIndexedNotNull { index, (turnId, turnItems) ->
        if (turnId == activeTurnId) return@mapIndexedNotNull null
        val input = turnItems
            .filterIsInstance<ConversationItem.UserMessage>()
            .flatMap(ConversationItem.UserMessage::content)
        val shellCommand = turnItems.filterIsInstance<ConversationItem.UserShellCommand>().lastOrNull()
        ForkTurn(
            turnId = turnId,
            number = index + 1,
            title = if (input.isNotEmpty()) {
                input.forkTurnTitle()
            } else {
                shellCommand?.let { "!${shellCommandForDisplay(it.command)}" } ?: input.forkTurnTitle()
            },
            attachmentCount = input.count { it !is UserMessagePart.Text },
        )
    }

internal fun forkAnnouncement(title: String, turn: ForkTurn?): String = if (turn == null) {
    "已从“$title”的最新状态创建分叉"
} else {
    "已从“$title”的第 ${turn.number} 轮创建分叉"
}

private fun List<UserMessagePart>.forkTurnTitle(): String {
    val text = filterIsInstance<UserMessagePart.Text>()
        .joinToString(" ", transform = UserMessagePart.Text::text)
        .replace(Regex("\\s+"), " ")
        .trim()
    if (text.isNotEmpty()) return text
    return when (val attachment = firstOrNull { it !is UserMessagePart.Text }) {
        is UserMessagePart.Image -> "图片消息"
        is UserMessagePart.LocalImage -> "图片：${attachment.path.substringAfterLast('/')}"
        is UserMessagePart.Skill -> "技能：${attachment.name}"
        is UserMessagePart.Mention -> "引用：${attachment.name}"
        is UserMessagePart.Text, null -> "未记录用户输入"
    }
}
