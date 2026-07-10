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
    data class Failed(val error: UserFacingError) : ConnectionStatus
}

data class UserFacingError(
    val summary: String,
    val technicalDetails: String? = null,
)

data class ThreadSummary(
    val id: String,
    val sessionId: String,
    val title: String,
    val preview: String,
    val cwd: String,
    val createdAt: Long,
    val updatedAt: Long,
    val recencyAt: Long?,
    val status: ThreadRuntimeStatus,
    val parentThreadId: String? = null,
    val forkedFromId: String? = null,
    val ephemeral: Boolean = false,
    val modelProvider: String = "",
    val source: String = "",
)

data class ThreadPage(
    val data: List<ThreadSummary>,
    val nextCursor: String?,
)

enum class ThreadRuntimeStatus {
    NOT_LOADED,
    IDLE,
    ACTIVE,
    SYSTEM_ERROR,
}

enum class ThreadSortKey(val wireValue: String) {
    CREATED_AT("created_at"),
    UPDATED_AT("updated_at"),
    RECENCY_AT("recency_at"),
}

enum class SortDirection(val wireValue: String) {
    ASC("asc"),
    DESC("desc"),
}

enum class ThreadSourceKind(val wireValue: String) {
    CLI("cli"),
    VSCODE("vscode"),
    EXEC("exec"),
    APP_SERVER("appServer"),
    SUB_AGENT("subAgent"),
    SUB_AGENT_REVIEW("subAgentReview"),
    SUB_AGENT_COMPACT("subAgentCompact"),
    SUB_AGENT_THREAD_SPAWN("subAgentThreadSpawn"),
    SUB_AGENT_OTHER("subAgentOther"),
    UNKNOWN("unknown"),
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
    val pendingRequests: List<PendingServerRequest> = emptyList(),
    val plan: TurnPlan? = null,
    val turnDiff: String = "",
    val tokenUsage: TokenUsage? = null,
    val goal: ThreadGoal? = null,
    val goalLoaded: Boolean = false,
    val notices: List<ConversationNotice> = emptyList(),
    val model: String? = null,
    val serviceTier: String? = null,
    val reasoningEffort: String? = null,
    val approvalPolicy: String? = null,
    val approvalsReviewer: String? = null,
    val sandbox: String? = null,
    val errorMessage: UserFacingError? = null,
)

sealed interface ConversationItem {
    val id: String
    val turnId: String

    data class UserMessage(
        override val id: String,
        override val turnId: String,
        val clientId: String?,
        val content: List<UserMessagePart>,
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
        val summary: String? = null,
        val details: String = "",
        val technicalDetails: String? = null,
        val status: ActivityStatus,
    ) : ConversationItem
}

sealed interface UserMessagePart {
    data class Text(val text: String) : UserMessagePart

    data class Image(val source: String) : UserMessagePart

    data class LocalImage(val path: String) : UserMessagePart

    data class Skill(val name: String, val path: String) : UserMessagePart

    data class Mention(val name: String, val path: String) : UserMessagePart
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

sealed interface PendingServerRequest {
    val requestKey: String
    val turnId: String?

    data class Approval(
        override val requestKey: String,
        val itemId: String,
        override val turnId: String,
        val type: ApprovalType,
        val reason: String?,
        val command: String?,
        val cwd: String?,
        val environmentId: String?,
        val networkHost: String?,
        val commandActions: List<CommandAction>,
        val grantRoot: String?,
        val proposedExecPolicy: ExecPolicyAmendment?,
        val proposedNetworkPolicies: List<NetworkPolicyAmendment>,
    ) : PendingServerRequest

    data class Questions(
        override val requestKey: String,
        val itemId: String,
        override val turnId: String,
        val questions: List<UserQuestion>,
        val autoResolutionMillis: Long?,
    ) : PendingServerRequest

    data class McpElicitation(
        override val requestKey: String,
        override val turnId: String?,
        val serverName: String,
        val mode: String,
        val message: String,
        val url: String?,
        val requestedSchema: String?,
    ) : PendingServerRequest

    data class Permissions(
        override val requestKey: String,
        val itemId: String,
        override val turnId: String,
        val environmentId: String?,
        val cwd: String,
        val reason: String?,
        val requestedPermissions: RequestedPermissions,
    ) : PendingServerRequest
}

sealed interface CommandAction {
    val command: String

    data class Read(
        override val command: String,
        val name: String,
        val path: String,
    ) : CommandAction

    data class ListFiles(
        override val command: String,
        val path: String?,
    ) : CommandAction

    data class Search(
        override val command: String,
        val query: String?,
        val path: String?,
    ) : CommandAction

    data class Unknown(override val command: String) : CommandAction
}

data class ExecPolicyAmendment(
    val command: List<String>,
    val responseJson: String,
)

data class NetworkPolicyAmendment(
    val host: String,
    val action: NetworkPolicyRuleAction,
    val responseJson: String,
)

enum class NetworkPolicyRuleAction {
    ALLOW,
    DENY,
}

data class RequestedPermissions(
    val networkEnabled: Boolean?,
    val fileSystem: RequestedFileSystemPermissions?,
    val responseJson: String,
)

data class RequestedFileSystemPermissions(
    val entries: List<FileSystemPermissionEntry>,
    val globScanMaxDepth: Int?,
)

data class FileSystemPermissionEntry(
    val target: FileSystemPermissionTarget,
    val access: FileSystemAccessMode,
)

sealed interface FileSystemPermissionTarget {
    data class Path(val path: String) : FileSystemPermissionTarget

    data class GlobPattern(val pattern: String) : FileSystemPermissionTarget

    data object Root : FileSystemPermissionTarget

    data object Minimal : FileSystemPermissionTarget

    data class ProjectRoots(val subpath: String?) : FileSystemPermissionTarget

    data object TempDirectory : FileSystemPermissionTarget

    data object SlashTmp : FileSystemPermissionTarget

    data class Unknown(val path: String, val subpath: String?) : FileSystemPermissionTarget
}

enum class FileSystemAccessMode {
    READ,
    WRITE,
    DENY,
}

data class UserQuestion(
    val id: String,
    val header: String,
    val question: String,
    val options: List<UserQuestionOption>,
    val allowsOther: Boolean,
    val isSecret: Boolean,
)

data class UserQuestionOption(
    val label: String,
    val description: String,
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

data class TurnPlan(
    val explanation: String?,
    val steps: List<TurnPlanStep>,
)

data class TurnPlanStep(
    val text: String,
    val status: PlanStepStatus,
)

enum class PlanStepStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
}

data class TokenUsage(
    val totalTokens: Long,
    val inputTokens: Long,
    val cachedInputTokens: Long,
    val outputTokens: Long,
    val reasoningOutputTokens: Long,
    val modelContextWindow: Long?,
)

data class ConversationNotice(
    val id: String,
    val level: NoticeLevel,
    val message: String,
    val technicalDetails: String? = null,
)

enum class NoticeLevel {
    INFO,
    WARNING,
    ERROR,
}

enum class ApprovalPolicy(val wireValue: String) {
    UNTRUSTED("untrusted"),
    ON_REQUEST("on-request"),
    NEVER("never"),
}

enum class ApprovalReviewer(val wireValue: String) {
    USER("user"),
    AUTO_REVIEW("auto_review"),
}

enum class SandboxMode(val wireValue: String) {
    READ_ONLY("read-only"),
    WORKSPACE_WRITE("workspace-write"),
    DANGER_FULL_ACCESS("danger-full-access"),
}

enum class Personality(val wireValue: String) {
    NONE("none"),
    FRIENDLY("friendly"),
    PRAGMATIC("pragmatic"),
}

enum class ReasoningSummary(val wireValue: String) {
    AUTO("auto"),
    CONCISE("concise"),
    DETAILED("detailed"),
    NONE("none"),
}

data class ThreadOptions(
    val model: String? = null,
    val modelProvider: String? = null,
    val serviceTier: String? = null,
    val approvalPolicy: ApprovalPolicy? = null,
    val approvalsReviewer: ApprovalReviewer? = null,
    val sandboxMode: SandboxMode? = null,
    val configJson: String? = null,
    val baseInstructions: String? = null,
    val developerInstructions: String? = null,
    val personality: Personality? = null,
    val ephemeral: Boolean = false,
)

data class TurnOptions(
    val cwd: String? = null,
    val model: String? = null,
    val serviceTier: String? = null,
    val reasoningEffort: String? = null,
    val reasoningSummary: ReasoningSummary? = null,
    val personality: Personality? = null,
    val approvalPolicy: ApprovalPolicy? = null,
    val approvalsReviewer: ApprovalReviewer? = null,
    val sandboxMode: SandboxMode? = null,
    val sandboxNetworkAccess: Boolean = false,
    val sandboxWritableRoots: List<String> = emptyList(),
    val sandboxExcludeTmpdirEnvVar: Boolean = false,
    val sandboxExcludeSlashTmp: Boolean = false,
    val outputSchema: String? = null,
)

sealed interface UserInputPart {
    data class Text(val text: String) : UserInputPart

    data class Image(val url: String) : UserInputPart

    data class LocalImage(val path: String) : UserInputPart

    data class Skill(val name: String, val path: String) : UserInputPart

    data class Mention(val name: String, val path: String) : UserInputPart
}

sealed interface ReviewTarget {
    data object UncommittedChanges : ReviewTarget

    data class BaseBranch(val branch: String) : ReviewTarget

    data class Commit(val sha: String, val title: String? = null) : ReviewTarget

    data class Custom(val instructions: String) : ReviewTarget
}

enum class ReviewDelivery(val wireValue: String) {
    INLINE("inline"),
    DETACHED("detached"),
}

data class ThreadGoal(
    val objective: String,
    val status: ThreadGoalStatus,
    val tokenBudget: Long?,
    val tokensUsed: Long,
    val timeUsedSeconds: Long,
)

enum class ThreadGoalStatus(val wireValue: String) {
    ACTIVE("active"),
    PAUSED("paused"),
    BLOCKED("blocked"),
    USAGE_LIMITED("usageLimited"),
    BUDGET_LIMITED("budgetLimited"),
    COMPLETE("complete"),
}
