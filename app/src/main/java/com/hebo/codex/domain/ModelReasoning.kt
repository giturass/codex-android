package com.hebo.codex.domain

fun List<ModelOption>.resolveModelOption(
    modelId: String?,
    inheritedModelId: String? = null,
): ModelOption? {
    val effectiveModelId = modelId ?: inheritedModelId
    return if (effectiveModelId != null) {
        firstOrNull { it.model == effectiveModelId }
    } else {
        firstOrNull { it.isDefault && !it.hidden }
    }
}

fun ModelOption.reasoningEffortForSelection(currentEffort: String?): String? =
    reasoningEfforts.firstOrNull { it.value == currentEffort }?.value
        ?: reasoningEfforts.firstOrNull { it.value == defaultReasoningEffort }?.value

fun TurnOptions.selectModel(
    model: ModelOption?,
    inheritedReasoningEffort: String? = null,
): TurnOptions = when {
    model == null -> copy(model = null, serviceTier = null, reasoningEffort = null)
    this.model == model.model -> this
    else -> copy(
        model = model.model,
        serviceTier = model.defaultServiceTier,
        reasoningEffort = model.reasoningEffortForSelection(reasoningEffort ?: inheritedReasoningEffort),
    )
}

fun ThreadOptions.selectModel(model: ModelOption?): ThreadOptions = when {
    model == null -> copy(model = null, serviceTier = null, reasoningEffort = null)
    this.model == model.model -> this
    else -> copy(
        model = model.model,
        serviceTier = model.defaultServiceTier,
        reasoningEffort = model.reasoningEffortForSelection(reasoningEffort),
    )
}

fun ThreadOptions.toTurnOptions(): TurnOptions = TurnOptions(
    model = model,
    serviceTier = serviceTier,
    reasoningEffort = reasoningEffort,
    approvalPolicy = approvalPolicy,
    approvalsReviewer = approvalsReviewer,
    sandboxMode = sandboxMode,
)
