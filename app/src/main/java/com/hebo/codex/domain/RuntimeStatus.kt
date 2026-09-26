package com.hebo.codex.domain

data class RuntimeStatus(
    val records: List<RuntimeActivity> = emptyList(),
    val diffs: List<RuntimeDiff> = emptyList(),
    val queue: RuntimeInputQueue? = null,
    val tokenUsageStale: Boolean = false,
    val goalStale: Boolean = false,
)

data class RuntimeActivity(
    val id: String,
    val title: String,
    val detail: String,
    val status: RuntimeActivityStatus,
    val turnId: String? = null,
    val text: String? = null,
    val stale: Boolean = false,
    val truncated: Boolean = false,
)

enum class RuntimeActivityStatus {
    RUNNING,
    COMPLETED,
    FAILED,
    WARNING,
    UNKNOWN,
    INFO,
}

data class RuntimeDiff(
    val turnId: String,
    val diff: String,
    val stale: Boolean = false,
    val truncated: Boolean = false,
)

data class RuntimeInputQueue(
    val entries: List<RuntimeQueuedInput> = emptyList(),
    val loading: Boolean = false,
    val error: UserFacingError? = null,
    val stale: Boolean = false,
)

data class RuntimeQueuedInput(
    val id: String,
    val description: String,
    val truncated: Boolean = false,
)

sealed interface RuntimeStatusEvent {
    data class ActivityChanged(val activity: RuntimeActivity) : RuntimeStatusEvent
    data class ActivityCompleted(val id: String) : RuntimeStatusEvent
    data class DiffChanged(val diff: RuntimeDiff) : RuntimeStatusEvent
    data object QueueChanged : RuntimeStatusEvent
    data class QueueLoaded(val queue: RuntimeInputQueue) : RuntimeStatusEvent
    data object TokenUsageSynced : RuntimeStatusEvent
    data object GoalSynced : RuntimeStatusEvent
    data class TurnCompleted(val turnId: String) : RuntimeStatusEvent
}

fun RuntimeStatus.reduce(event: RuntimeStatusEvent): RuntimeStatus = when (event) {
    is RuntimeStatusEvent.ActivityChanged -> copy(
        records = (records.filterNot { it.id == event.activity.id } + event.activity).takeLast(50),
    )
    is RuntimeStatusEvent.ActivityCompleted -> copy(records = records.filterNot { it.id == event.id })
    is RuntimeStatusEvent.DiffChanged -> copy(
        diffs = (diffs.filterNot { it.turnId == event.diff.turnId } + event.diff).takeLast(5),
    )
    RuntimeStatusEvent.QueueChanged -> copy(
        queue = (queue ?: RuntimeInputQueue()).copy(loading = true, error = null),
    )
    is RuntimeStatusEvent.QueueLoaded -> copy(queue = event.queue)
    RuntimeStatusEvent.TokenUsageSynced -> copy(tokenUsageStale = false)
    RuntimeStatusEvent.GoalSynced -> copy(goalStale = false)
    is RuntimeStatusEvent.TurnCompleted -> copy(
        records = records.map { activity ->
            if (activity.turnId == event.turnId && activity.status == RuntimeActivityStatus.RUNNING) {
                activity.copy(status = RuntimeActivityStatus.UNKNOWN, stale = true)
            } else {
                activity
            }
        },
    )
}

fun RuntimeStatus.markStale(): RuntimeStatus = copy(
    records = records.map { activity ->
        if (activity.status == RuntimeActivityStatus.RUNNING) {
            activity.copy(status = RuntimeActivityStatus.UNKNOWN, stale = true)
        } else {
            activity
        }
    },
    diffs = diffs.map { it.copy(stale = true) },
    queue = queue?.copy(loading = false, stale = true),
    tokenUsageStale = true,
    goalStale = true,
)
