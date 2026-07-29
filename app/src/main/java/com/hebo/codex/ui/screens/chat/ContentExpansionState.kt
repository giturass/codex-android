package com.hebo.codex.ui.screens.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

internal const val EXPANDABLE_CONTENT_SIZE_ANIMATION_DURATION_MILLIS = 220
internal const val EXPANDABLE_CONTENT_FADE_DURATION_MILLIS = 100

internal enum class ContentExpansionPhase {
    COLLAPSED,
    EXPANDING,
    EXPANDED,
    COLLAPSING,
}

internal val ContentExpansionPhase.bodyIncluded: Boolean
    get() = this != ContentExpansionPhase.COLLAPSED

internal val ContentExpansionPhase.bodyVisible: Boolean
    get() = this == ContentExpansionPhase.EXPANDED

internal fun ContentExpansionPhase.startTransition(expanded: Boolean): ContentExpansionPhase = when {
    expanded -> ContentExpansionPhase.EXPANDING
    this == ContentExpansionPhase.COLLAPSED -> ContentExpansionPhase.COLLAPSED
    else -> ContentExpansionPhase.COLLAPSING
}

internal fun ContentExpansionPhase.settle(): ContentExpansionPhase = when (this) {
    ContentExpansionPhase.EXPANDING -> ContentExpansionPhase.EXPANDED
    ContentExpansionPhase.COLLAPSING -> ContentExpansionPhase.COLLAPSED
    ContentExpansionPhase.COLLAPSED,
    ContentExpansionPhase.EXPANDED,
    -> this
}

@Composable
internal fun rememberContentExpansionPhase(
    expanded: Boolean,
    key: Any? = Unit,
): ContentExpansionPhase {
    var phase by remember(key) {
        mutableStateOf(ContentExpansionPhase.COLLAPSED)
    }
    LaunchedEffect(key, expanded) {
        phase = phase.startTransition(expanded)
        when (phase) {
            ContentExpansionPhase.EXPANDING -> {
                delay(EXPANDABLE_CONTENT_SIZE_ANIMATION_DURATION_MILLIS.toLong())
                phase = phase.settle()
            }

            ContentExpansionPhase.COLLAPSING -> {
                delay(EXPANDABLE_CONTENT_FADE_DURATION_MILLIS.toLong())
                phase = phase.settle()
            }

            ContentExpansionPhase.COLLAPSED,
            ContentExpansionPhase.EXPANDED,
            -> Unit
        }
    }
    return phase
}
