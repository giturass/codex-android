package com.hebo.codex.domain

import java.util.concurrent.ConcurrentHashMap

val ConversationItem.AgentMessage.isAsyncQuestion: Boolean
    get() = delivery == AgentMessageDelivery.ASYNC && questions.isNotEmpty()

fun ConversationHistory.recentAsyncQuestion(): ConversationItem.AgentMessage? {
    turns.asReversed().forEach { turn ->
        // 摘要省略中间问题和后续用户回复，不能据此推断最近入口
        if (turn.detailsState != TurnDetailsState.Loaded) return null
        turn.items.asReversed().forEach { item ->
            when {
                item is ConversationItem.UserMessage -> return null
                item is ConversationItem.AgentMessage && item.isAsyncQuestion -> return item
            }
        }
    }
    return null
}

internal class AsyncQuestionNotifications {
    private data class Key(
        val serverId: String,
        val threadId: String,
        val turnId: String,
        val itemId: String,
    )

    private val observed = ConcurrentHashMap.newKeySet<Key>()

    fun accept(serverId: String, threadId: String, method: String, event: ConversationEvent): Boolean {
        if (method != "item/completed") return false
        val item = (event as? ConversationEvent.ItemUpserted)?.item as? ConversationItem.AgentMessage
            ?: return false
        if (!item.isAsyncQuestion) return false
        return observed.add(Key(serverId, threadId, item.turnId, item.id))
    }

    fun clearServer(serverId: String) {
        observed.removeIf { it.serverId == serverId }
    }
}
