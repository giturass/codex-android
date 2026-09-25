package com.hebo.codex.domain

enum class AgentTool {
    SPAWN,
    SEND_INPUT,
    RESUME,
    WAIT,
    CLOSE,
    SEND_MESSAGE,
    FOLLOWUP_TASK,
    INTERRUPT,
    LIST,
}

enum class AgentStatus {
    PENDING_INIT,
    RUNNING,
    INTERRUPTED,
    COMPLETED,
    ERRORED,
    SHUTDOWN,
    NOT_FOUND,
}

data class AgentState(
    val status: AgentStatus,
    val message: String?,
)

enum class SubAgentActivityKind {
    STARTED,
    INTERACTED,
    INTERRUPTED,
    COMPLETED,
}

// listAgents can report states for agents that are absent from receiverThreadIds
val ConversationItem.AgentCollaboration.targetThreadIds: List<String>
    get() = (receiverThreadIds + agentsStates.keys).distinct()
