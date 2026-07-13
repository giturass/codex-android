package com.hebo.codex.ui.screens.chat

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
