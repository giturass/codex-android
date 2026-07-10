package com.hebo.codex.domain

data class ServerProfile(
    val id: String,
    val name: String,
    val webSocketUrl: String,
    val authentication: ServerAuthentication,
)

enum class ServerAuthentication {
    CAPABILITY_TOKEN,
}

sealed interface ConnectionStatus {
    data object Disconnected : ConnectionStatus
    data object Connecting : ConnectionStatus
    data object Connected : ConnectionStatus
    data class Failed(val message: String) : ConnectionStatus
}

data class ThreadSummary(
    val id: String,
    val title: String,
    val preview: String,
    val cwd: String,
    val createdAt: Long,
    val updatedAt: Long,
    val recencyAt: Long?,
    val status: ThreadRuntimeStatus,
)

enum class ThreadRuntimeStatus {
    NOT_LOADED,
    IDLE,
    ACTIVE,
    SYSTEM_ERROR,
}

data class RecentProject(
    val path: String,
    val name: String,
    val lastActiveAt: Long,
    val threads: List<ThreadSummary>,
)

data class ConversationState(
    val serverId: String,
    val serverName: String,
    val threadId: String,
    val title: String,
    val cwd: String,
    val items: List<ConversationItem> = emptyList(),
    val activeTurnId: String? = null,
    val isRunning: Boolean = false,
    val approvals: List<PendingApproval> = emptyList(),
    val errorMessage: String? = null,
)

sealed interface ConversationItem {
    val id: String
    val turnId: String

    data class UserMessage(
        override val id: String,
        override val turnId: String,
        val clientId: String?,
        val text: String,
        val isPending: Boolean = false,
    ) : ConversationItem

    data class AgentMessage(
        override val id: String,
        override val turnId: String,
        val text: String,
        val isStreaming: Boolean,
    ) : ConversationItem

    data class Reasoning(
        override val id: String,
        override val turnId: String,
        val summary: String,
        val details: String,
        val isStreaming: Boolean,
    ) : ConversationItem

    data class CommandExecution(
        override val id: String,
        override val turnId: String,
        val command: String,
        val cwd: String,
        val output: String,
        val exitCode: Int?,
        val status: ActivityStatus,
    ) : ConversationItem

    data class FileChange(
        override val id: String,
        override val turnId: String,
        val changes: List<ChangedFile>,
        val status: ActivityStatus,
    ) : ConversationItem

    data class Activity(
        override val id: String,
        override val turnId: String,
        val title: String,
        val details: String,
        val status: ActivityStatus,
    ) : ConversationItem
}

data class ChangedFile(
    val path: String,
    val kind: FileChangeKind,
    val diff: String,
)

sealed interface FileChangeKind {
    data object Add : FileChangeKind

    data object Delete : FileChangeKind

    data class Update(val movePath: String?) : FileChangeKind
}

enum class ActivityStatus {
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    DECLINED,
}

data class PendingApproval(
    val requestKey: String,
    val itemId: String,
    val turnId: String,
    val type: ApprovalType,
    val reason: String?,
    val command: String?,
    val cwd: String?,
)

enum class ApprovalType {
    COMMAND_EXECUTION,
    FILE_CHANGE,
}

enum class ApprovalDecision(val wireValue: String) {
    ACCEPT("accept"),
    ACCEPT_FOR_SESSION("acceptForSession"),
    DECLINE("decline"),
    CANCEL("cancel"),
}
