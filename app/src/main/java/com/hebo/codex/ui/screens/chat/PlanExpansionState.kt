package com.hebo.codex.ui.screens.chat

internal enum class PlanExpansionPhase {
    COLLAPSED,
    EXPANDING,
    EXPANDED,
    COLLAPSING,
}

internal val PlanExpansionPhase.bodyIncluded: Boolean
    get() = this != PlanExpansionPhase.COLLAPSED

internal val PlanExpansionPhase.bodyVisible: Boolean
    get() = this == PlanExpansionPhase.EXPANDED

internal fun PlanExpansionPhase.startTransition(expanded: Boolean): PlanExpansionPhase = when {
    expanded -> PlanExpansionPhase.EXPANDING
    this == PlanExpansionPhase.COLLAPSED -> PlanExpansionPhase.COLLAPSED
    else -> PlanExpansionPhase.COLLAPSING
}

internal fun PlanExpansionPhase.settle(): PlanExpansionPhase = when (this) {
    PlanExpansionPhase.EXPANDING -> PlanExpansionPhase.EXPANDED
    PlanExpansionPhase.COLLAPSING -> PlanExpansionPhase.COLLAPSED
    PlanExpansionPhase.COLLAPSED,
    PlanExpansionPhase.EXPANDED,
    -> this
}
