package com.hebo.codex.ui.components

internal data class ScrollChromeState(
    val fullyHidden: Boolean = false,
    val offset: Float = 0f,
    val settledHidden: Boolean = false,
)

internal fun ScrollChromeState.afterUserScroll(
    hideDelta: Float,
    hideDistance: Float,
): ScrollChromeState = when {
    hideDistance <= 0f -> this

    hideDelta > 0f && !fullyHidden -> {
        val nextOffset = (offset + hideDelta).coerceAtMost(hideDistance)
        copy(
            fullyHidden = nextOffset >= hideDistance,
            offset = nextOffset,
        )
    }

    hideDelta < 0f -> {
        copy(
            fullyHidden = false,
            offset = (offset + hideDelta).coerceAtLeast(0f),
        )
    }

    else -> this
}

internal fun ScrollChromeState.settleTarget(hideDistance: Float): Float {
    val halfway = hideDistance / 2f
    return when {
        offset > halfway -> hideDistance
        offset < halfway -> 0f
        settledHidden -> hideDistance
        else -> 0f
    }
}
