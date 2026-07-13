package com.hebo.codex.ui.components

import com.hebo.codex.domain.ThreadAttention

data class NavigationAttention(
    val count: Int,
    val highestPriority: ThreadAttention,
)

internal fun navigationAttention(
    attentionByThread: Map<String, ThreadAttention>,
    currentThreadId: String? = null,
): NavigationAttention? {
    val attention = attentionByThread
        .filterKeys { it != currentThreadId }
        .values
        .filter { it != ThreadAttention.RUNNING }
    if (attention.isEmpty()) return null

    return NavigationAttention(
        count = attention.size,
        highestPriority = attention.maxBy(ThreadAttention::navigationPriority),
    )
}

private val ThreadAttention.navigationPriority: Int
    get() = when (this) {
        ThreadAttention.RUNNING -> 0
        ThreadAttention.READY -> 1
        ThreadAttention.NEEDS_INPUT -> 2
        ThreadAttention.FAILED -> 3
    }
