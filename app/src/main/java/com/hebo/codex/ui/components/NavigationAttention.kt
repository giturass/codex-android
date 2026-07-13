package com.hebo.codex.ui.components

import com.hebo.codex.domain.ThreadAttention
import com.hebo.codex.domain.ThreadAttentionState

data class NavigationAttention(
    val count: Int,
    val targetThreadId: String,
    val targetAttention: ThreadAttention,
)

internal fun navigationAttention(
    attentionByThread: Map<String, ThreadAttentionState>,
    currentThreadId: String? = null,
): NavigationAttention? {
    val attention = attentionByThread
        .filterKeys { it != currentThreadId }
        .filterValues { it.attention != ThreadAttention.RUNNING }
    if (attention.isEmpty()) return null
    val target = attention.maxWith(
        compareBy<Map.Entry<String, ThreadAttentionState>>(
            { it.value.attention.navigationPriority },
            { it.value.revision },
        ),
    )

    return NavigationAttention(
        count = attention.size,
        targetThreadId = target.key,
        targetAttention = target.value.attention,
    )
}

private val ThreadAttention.navigationPriority: Int
    get() = when (this) {
        ThreadAttention.RUNNING -> 0
        ThreadAttention.READY -> 1
        ThreadAttention.NEEDS_INPUT -> 2
        ThreadAttention.FAILED -> 3
    }
