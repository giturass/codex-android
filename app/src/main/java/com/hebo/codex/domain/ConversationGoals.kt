package com.hebo.codex.domain

import java.util.UUID

internal const val MAX_NOTICES = 20

internal fun ConversationState.withGoal(goal: ThreadGoal?, announce: Boolean): ConversationState = copy(
    goal = goal,
    goalLoaded = true,
    notices = when {
        !announce -> notices
        goal == null -> notices.appendGoalNotice("目标已清除")
        else -> notices.appendGoalNotice("目标已更新：${goal.objective}")
    },
)

private fun List<ConversationNotice>.appendGoalNotice(message: String): List<ConversationNotice> =
    (this + ConversationNotice(UUID.randomUUID().toString(), message)).takeLast(MAX_NOTICES)
