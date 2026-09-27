package com.hebo.codex.data.remote

import com.hebo.codex.domain.ActivityKind
import com.hebo.codex.domain.ActivityStatus
import com.hebo.codex.domain.AgentMessagePhase
import com.hebo.codex.domain.AgentTool
import com.hebo.codex.domain.AgentState
import com.hebo.codex.domain.AgentStatus
import com.hebo.codex.domain.SubAgentActivityKind
import com.hebo.codex.domain.SubAgentEvent
import com.hebo.codex.domain.ApprovalType
import com.hebo.codex.domain.BackgroundTerminal
import com.hebo.codex.domain.ChangedFile
import com.hebo.codex.domain.CommandAction
import com.hebo.codex.domain.ConversationEvent
import com.hebo.codex.domain.ConversationItem
import com.hebo.codex.domain.ConversationItemUpdate
import com.hebo.codex.domain.ConversationNotice
import com.hebo.codex.domain.ConversationTurn
import com.hebo.codex.domain.ConversationTurnStatus
import com.hebo.codex.domain.ConversationRunStatus
import com.hebo.codex.domain.ExecPolicyAmendment
import com.hebo.codex.domain.FileChangeKind
import com.hebo.codex.domain.FileSystemAccessMode
import com.hebo.codex.domain.FileSystemPermissionEntry
import com.hebo.codex.domain.FileSystemPermissionTarget
import com.hebo.codex.domain.NetworkPolicyAmendment
import com.hebo.codex.domain.NetworkPolicyRuleAction
import com.hebo.codex.domain.PendingServerRequest
import com.hebo.codex.domain.ThreadHistoryMode
import com.hebo.codex.domain.ThreadRuntimeStatus
import com.hebo.codex.domain.ThreadActiveFlag
import com.hebo.codex.domain.ThreadSummary
import com.hebo.codex.domain.TokenUsage
import com.hebo.codex.domain.TokenUsageBreakdown
import com.hebo.codex.domain.TurnDetailsState
import com.hebo.codex.domain.ActiveTurnPlan
import com.hebo.codex.domain.TurnPlanStep
import com.hebo.codex.domain.PlanStepStatus
import com.hebo.codex.domain.RequestedFileSystemPermissions
import com.hebo.codex.domain.RequestedPermissions
import com.hebo.codex.domain.ThreadGoal
import com.hebo.codex.domain.ThreadGoalStatus
import com.hebo.codex.domain.UserMessagePart
import com.hebo.codex.domain.UserQuestion
import com.hebo.codex.domain.UserQuestionOption
import com.hebo.codex.util.serverError
import java.util.UUID
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

data class ThreadSnapshot(
    val summary: ThreadSummary,
    val turns: List<ConversationTurn>,
    val olderTurnsCursor: String?,
    val model: String?,
    val serviceTier: String?,
    val reasoningEffort: String?,
    val approvalPolicy: String?,
    val approvalsReviewer: String?,
    val sandbox: String?,
)

data class ThreadTurnPage(
    val data: List<ConversationTurn>,
    val nextCursor: String?,
)

data class ThreadItemPage(
    val data: List<ConversationItem>,
    val nextCursor: String?,
)

data class BackgroundTerminalPage(
    val data: List<BackgroundTerminal>,
    val nextCursor: String?,
)

internal data class ProtocolNotification(
    val threadId: String?,
    val conversationEvent: ConversationEvent? = null,
    val serverNotice: String? = null,
    val bumpThreadRevision: Boolean = false,
    val bumpFeatureRevision: Boolean = false,
    val bumpRateLimitRevision: Boolean = false,
)

object ProtocolMapper {
    internal fun subAgentEvent(method: String, params: JsonObject): SubAgentEvent? = when (method) {
        "thread/started" -> SubAgentEvent.Started(threadSummary(params.requiredObject("thread")))
        "thread/status/changed" -> params.requiredObject("status").let { status ->
            SubAgentEvent.StatusChanged(
                params.requiredString("threadId"), status.toThreadStatus(), status.threadActiveFlags(),
            )
        }
        "thread/closed", "thread/deleted" -> SubAgentEvent.Removed(
            params.requiredString("threadId"), deleted = method == "thread/deleted",
        )
        "item/completed" -> {
            val item = params.requiredObject("item")
            val discoversAgent = when (item.requiredString("type")) {
                "subAgentActivity" -> item.requiredString("kind") == "started"
                "collabAgentToolCall" -> item.requiredString("tool") in setOf("spawnAgent", "resumeAgent")
                else -> false
            }
            if (discoversAgent) SubAgentEvent.Discover(params.requiredString("threadId")) else null
        }
        else -> null
    }

    private fun JsonObject.threadActiveFlags(): Set<ThreadActiveFlag> =
        if (requiredString("type") != "active") emptySet() else requiredArray("activeFlags").map { flag ->
            when (flag.jsonPrimitive.content) {
                "waitingOnApproval" -> ThreadActiveFlag.WAITING_APPROVAL
                "waitingOnUserInput" -> ThreadActiveFlag.WAITING_INPUT
                else -> throw ProtocolException("未知会话活动标记：$flag")
            }
        }.toSet()

    fun threadSummary(thread: JsonObject): ThreadSummary {
        val preview = thread.requiredString("preview")
        val name = thread.optionalString("name")?.trim().orEmpty()
        return ThreadSummary(
            id = thread.requiredString("id"),
            sessionId = thread.requiredString("sessionId"),
            title = name.ifEmpty { preview.lineSequence().firstOrNull().orEmpty().ifEmpty { "新会话" } },
            preview = preview,
            cwd = thread.requiredString("cwd"),
            createdAt = thread.requiredLong("createdAt"),
            updatedAt = thread.requiredLong("updatedAt"),
            recencyAt = thread.optionalLong("recencyAt"),
            status = thread.requiredObject("status").toThreadStatus(),
            historyMode = (thread.optionalString("historyMode") ?: "legacy").toThreadHistoryMode(),
            parentThreadId = thread.optionalString("parentThreadId"),
            forkedFromId = thread.optionalString("forkedFromId"),
            ephemeral = thread.optionalBoolean("ephemeral") ?: false,
            modelProvider = thread.optionalString("modelProvider").orEmpty(),
            source = thread["source"].compactText(),
            agentNickname = thread.optionalString("agentNickname"),
            agentRole = thread.optionalString("agentRole"),
            canAcceptDirectInput = thread.optionalBoolean("canAcceptDirectInput"),
            agentPath = (((thread["source"] as? JsonObject)?.get("subAgent") as? JsonObject)
                ?.get("thread_spawn") as? JsonObject)?.optionalString("agent_path"),
            activeFlags = thread.requiredObject("status").threadActiveFlags(),
        )
    }

    fun threadMetadata(response: JsonObject): ThreadSnapshot {
        val thread = response.requiredObject("thread")
        val summary = threadSummary(thread)
        if (summary.historyMode != ThreadHistoryMode.PAGINATED) {
            throw UnsupportedThreadHistoryModeException(summary.historyMode)
        }
        return ThreadSnapshot(
            summary = summary,
            turns = emptyList(),
            olderTurnsCursor = null,
            model = response.optionalString("model"),
            serviceTier = response.optionalString("serviceTier"),
            reasoningEffort = response.optionalString("reasoningEffort"),
            approvalPolicy = response["approvalPolicy"].compactText().ifEmpty { null },
            approvalsReviewer = response.optionalString("approvalsReviewer"),
            sandbox = response["sandbox"].compactText().ifEmpty { null },
        )
    }

    fun resumedThreadSnapshot(response: JsonObject): ThreadSnapshot {
        val metadata = threadMetadata(response)
        val page = response.requiredObject("initialTurnsPage").toThreadTurnPage()
        return metadata.copy(
            turns = page.data,
            olderTurnsCursor = page.nextCursor,
        )
    }

    fun threadTurnPage(response: JsonObject): ThreadTurnPage = response.toThreadTurnPage()

    fun threadItemPage(response: JsonObject, expectedTurnId: String): ThreadItemPage {
        val items = response.requiredArray("data").map { entryElement ->
            val entry = entryElement.jsonObject
            val turnId = entry.requiredString("turnId")
            if (turnId != expectedTurnId) {
                throw ProtocolException("thread/items/list 返回了其他回合的项目：$turnId")
            }
            conversationItem(entry.requiredObject("item"), turnId)
        }
        return ThreadItemPage(
            data = items,
            nextCursor = response.optionalString("nextCursor"),
        )
    }

    fun backgroundTerminalPage(
        response: JsonObject,
        observedAtMillis: Long,
    ): BackgroundTerminalPage = BackgroundTerminalPage(
        data = response.requiredArray("data").map { element ->
            val terminal = element.jsonObject
            BackgroundTerminal(
                itemId = terminal.requiredString("itemId"),
                processId = terminal.requiredString("processId"),
                command = terminal.requiredString("command"),
                cwd = terminal.requiredString("cwd"),
                observedAtMillis = observedAtMillis,
            )
        },
        nextCursor = response.optionalString("nextCursor"),
    )

    private fun JsonObject.toThreadTurnPage(): ThreadTurnPage = ThreadTurnPage(
        data = requiredArray("data").map { element ->
            val turn = element.jsonObject
            val itemsView = turn.requiredString("itemsView")
            if (itemsView != "summary") {
                throw ProtocolException("分页回合必须使用 itemsView=summary")
            }
            val turnId = turn.requiredString("id")
            ConversationTurn(
                id = turnId,
                status = turn.requiredString("status").toConversationTurnStatus(),
                items = turn.requiredArray("items").map { conversationItem(it.jsonObject, turnId) },
                detailsState = TurnDetailsState.Unloaded,
            )
        },
        nextCursor = optionalString("nextCursor"),
    )

    fun activeTurnPlan(params: JsonObject): ActiveTurnPlan = ActiveTurnPlan(
        turnId = params.requiredString("turnId"),
        explanation = params.optionalString("explanation"),
        steps = params.requiredArray("plan").map { element ->
            val step = element.jsonObject
            TurnPlanStep(
                text = step.requiredString("step"),
                status = when (step.requiredString("status")) {
                    "pending" -> PlanStepStatus.PENDING
                    "inProgress" -> PlanStepStatus.IN_PROGRESS
                    "completed" -> PlanStepStatus.COMPLETED
                    else -> throw ProtocolException("未知计划状态：${step.requiredString("status")}")
                },
            )
        },
    )

    fun tokenUsage(params: JsonObject): TokenUsage {
        val usage = params.requiredObject("tokenUsage")
        return TokenUsage(
            total = usage.requiredObject("total").tokenUsageBreakdown(),
            last = usage.requiredObject("last").tokenUsageBreakdown(),
            modelContextWindow = usage.optionalLong("modelContextWindow"),
        )
    }

    fun pendingServerRequest(
        method: String,
        params: JsonObject,
        requestKey: String,
        receivedAtMillis: Long,
    ): PendingServerRequest = when (method) {
        "item/commandExecution/requestApproval" -> PendingServerRequest.Approval(
            requestKey = requestKey,
            itemId = params.requiredString("itemId"),
            turnId = params.requiredString("turnId"),
            type = ApprovalType.COMMAND_EXECUTION,
            reason = params.optionalString("reason"),
            command = params.optionalString("command"),
            cwd = params.optionalString("cwd"),
            environmentId = params.optionalString("environmentId"),
            networkHost = (params["networkApprovalContext"] as? JsonObject)?.optionalString("host"),
            commandActions = commandActions(params["commandActions"]),
            grantRoot = null,
            proposedExecPolicy = execPolicyAmendment(params["proposedExecpolicyAmendment"]),
            proposedNetworkPolicies = networkPolicyAmendments(params["proposedNetworkPolicyAmendments"]),
        )

        "item/fileChange/requestApproval" -> PendingServerRequest.Approval(
            requestKey = requestKey,
            itemId = params.requiredString("itemId"),
            turnId = params.requiredString("turnId"),
            type = ApprovalType.FILE_CHANGE,
            reason = params.optionalString("reason"),
            command = null,
            cwd = null,
            environmentId = null,
            networkHost = null,
            commandActions = emptyList(),
            grantRoot = params.optionalString("grantRoot"),
            proposedExecPolicy = null,
            proposedNetworkPolicies = emptyList(),
        )

        "item/tool/requestUserInput" -> PendingServerRequest.Questions(
            requestKey = requestKey,
            itemId = params.requiredString("itemId"),
            turnId = params.requiredString("turnId"),
            questions = params.requiredArray("questions").map { element ->
                val question = element.jsonObject
                UserQuestion(
                    id = question.requiredString("id"),
                    header = question.requiredString("header"),
                    question = question.requiredString("question"),
                    options = (question["options"] as? JsonArray).orEmpty().map { optionElement ->
                        val option = optionElement.jsonObject
                        UserQuestionOption(
                            label = option.requiredString("label"),
                            description = option.requiredString("description"),
                        )
                    },
                    allowsOther = question.optionalBoolean("isOther") ?: false,
                    isSecret = question.optionalBoolean("isSecret") ?: false,
                )
            },
            autoResolutionMillis = params.optionalLong("autoResolutionMs"),
            receivedAtMillis = receivedAtMillis,
        )

        "mcpServer/elicitation/request" -> PendingServerRequest.McpElicitation(
            requestKey = requestKey,
            turnId = params.optionalString("turnId"),
            serverName = params.requiredString("serverName"),
            mode = params.requiredString("mode"),
            message = params.requiredString("message"),
            url = params.optionalString("url"),
            requestedSchema = params["requestedSchema"]?.toString(),
        )

        "item/permissions/requestApproval" -> PendingServerRequest.Permissions(
            requestKey = requestKey,
            itemId = params.requiredString("itemId"),
            turnId = params.requiredString("turnId"),
            environmentId = params.optionalString("environmentId"),
            cwd = params.requiredString("cwd"),
            reason = params.optionalString("reason"),
            requestedPermissions = requestedPermissions(params.requiredObject("permissions")),
        )

        else -> throw ProtocolException("支持的服务端请求未实现：$method")
    }

    internal fun notification(
        serverId: String,
        method: String,
        params: JsonObject,
    ): ProtocolNotification {
        val threadId = params.optionalString("threadId")
        val featureRevision = method == "account/updated" ||
            method == "account/rateLimits/updated" ||
            method == "account/login/completed" ||
            method == "app/list/updated" ||
            method == "skills/changed" ||
            method == "mcpServer/oauthLogin/completed" ||
            method == "mcpServer/startupStatus/updated"
        val rateLimitRevision = method == "account/updated" ||
            method == "account/rateLimits/updated" ||
            method == "account/login/completed"
        val serverNotice = when {
            method == "configWarning" || method == "deprecationNotice" ->
                listOfNotNull(params.optionalString("summary"), params.optionalString("details")).joinToString("\n")

            method == "warning" && threadId == null -> params.requiredString("message")
            else -> null
        }
        val event = if (threadId == null) {
            null
        } else {
            conversationEvent(serverId, method, params)
        }
        val itemStarted = event as? ConversationEvent.ItemUpserted
        val bumpThreadRevision = (
            method.startsWith("thread/") &&
                method != "thread/tokenUsage/updated" &&
                method != "thread/goal/updated" &&
                method != "thread/goal/cleared"
            ) ||
            method == "turn/completed" ||
            (
                method == "item/started" &&
                    (itemStarted?.item is ConversationItem.UserMessage)
                )
        return ProtocolNotification(
            threadId = threadId,
            conversationEvent = event,
            serverNotice = serverNotice,
            bumpThreadRevision = bumpThreadRevision,
            bumpFeatureRevision = featureRevision,
            bumpRateLimitRevision = rateLimitRevision,
        )
    }

    fun threadGoal(value: JsonObject): ThreadGoal = ThreadGoal(
        objective = value.requiredString("objective"),
        status = ThreadGoalStatus.entries.singleOrNull { it.wireValue == value.requiredString("status") }
            ?: throw ProtocolException("未知目标状态：${value.requiredString("status")}"),
        tokenBudget = value.optionalLong("tokenBudget"),
        tokensUsed = value.optionalLong("tokensUsed") ?: 0L,
        timeUsedSeconds = value.optionalLong("timeUsedSeconds") ?: 0L,
    )

    private fun conversationEvent(
        serverId: String,
        method: String,
        params: JsonObject,
    ): ConversationEvent? = when (method) {
        "thread/status/changed" -> ConversationEvent.StatusChanged(
            when (params.requiredObject("status").requiredString("type")) {
                "active" -> ConversationRunStatus.ACTIVE
                "systemError" -> ConversationRunStatus.SYSTEM_ERROR
                else -> ConversationRunStatus.INACTIVE
            },
        )

        "thread/name/updated" -> ConversationEvent.NameUpdated(params.optionalString("threadName"))
        "thread/archived", "thread/deleted", "thread/closed" -> ConversationEvent.Closed(
            ConversationNotice(
                id = UUID.randomUUID().toString(),
                message = when (method) {
                    "thread/archived" -> "会话已归档"
                    "thread/deleted" -> "会话已删除"
                    else -> "服务端已关闭会话"
                },
            ),
        )

        "thread/tokenUsage/updated" -> ConversationEvent.TokenUsageUpdated(tokenUsage(params))
        "thread/goal/updated" -> ConversationEvent.GoalChanged(threadGoal(params.requiredObject("goal")))
        "thread/goal/cleared" -> ConversationEvent.GoalChanged(null)
        "turn/started" -> ConversationEvent.TurnStarted(
            params.requiredObject("turn").requiredString("id"),
        )

        "turn/completed" -> {
            val turn = params.requiredObject("turn")
            ConversationEvent.TurnCompleted(
                turnId = turn.requiredString("id"),
                status = turn.requiredString("status").toConversationTurnStatus(),
                error = (turn["error"] as? JsonObject)?.optionalString("message")
                    ?.let { serverError("任务执行失败", it) },
            )
        }

        "turn/plan/updated" -> ConversationEvent.PlanUpdated(activeTurnPlan(params))
        "error" -> {
            val message = params.requiredObject("error").requiredString("message")
            ConversationEvent.NoticeAdded(
                notice = ConversationNotice(UUID.randomUUID().toString(), "服务器报告错误", message, requiresAttention = true),
                terminalError = if (params.optionalBoolean("willRetry") == true) {
                    null
                } else {
                    serverError("服务器报告错误", message)
                },
            )
        }

        "warning", "guardianWarning" -> ConversationEvent.NoticeAdded(
            ConversationNotice(
                UUID.randomUUID().toString(),
                params.requiredString("message"),
                requiresAttention = true,
            ),
        )

        "model/rerouted" -> ConversationEvent.NoticeAdded(
            ConversationNotice(
                UUID.randomUUID().toString(),
                "模型已从 ${params.requiredString("fromModel")} 切换为 ${params.requiredString("toModel")}",
            ),
        )

        "model/verification" -> ConversationEvent.NoticeAdded(
            ConversationNotice(UUID.randomUUID().toString(), "模型请求需要额外账号验证", requiresAttention = true),
        )

        "item/started", "item/completed" -> ConversationEvent.ItemUpserted(
            item = conversationItem(
                item = params.requiredObject("item"),
                turnId = params.requiredString("turnId"),
            ),
            isActive = method == "item/started",
        )

        "item/agentMessage/delta" -> itemChanged(params, method, markActive = true) { item ->
            val message = item as? ConversationItem.AgentMessage
                ?: throw ProtocolException("agentMessage delta 的目标类型不正确")
            message.copy(text = message.text + params.requiredString("delta"))
        }

        "item/reasoning/summaryTextDelta" -> itemChanged(params, method, markActive = true) { item ->
            val reasoning = item as? ConversationItem.Reasoning
                ?: throw ProtocolException("reasoning summary delta 的目标类型不正确")
            reasoning.appendSummaryDelta(
                summaryIndex = params.requiredInt("summaryIndex"),
                delta = params.requiredString("delta"),
            )
        }

        "item/reasoning/summaryPartAdded" -> itemChanged(params, method, markActive = true) { item ->
            val reasoning = item as? ConversationItem.Reasoning
                ?: throw ProtocolException("reasoning summary part 的目标类型不正确")
            reasoning.addSummaryPart(params.requiredInt("summaryIndex"))
        }

        "item/reasoning/textDelta" -> itemChanged(params, method, markActive = true) { item ->
            val reasoning = item as? ConversationItem.Reasoning
                ?: throw ProtocolException("reasoning delta 的目标类型不正确")
            reasoning.appendRawContentDelta(
                contentIndex = params.requiredInt("contentIndex"),
                delta = params.requiredString("delta"),
            )
        }

        "item/commandExecution/outputDelta" -> itemChanged(params, method) { item ->
            item.appendCommandOutput(params.requiredString("delta"))
        }

        "item/commandExecution/terminalInteraction" -> itemChanged(params, method) { item ->
            item.appendCommandOutput(params.requiredString("stdin"))
        }

        "item/fileChange/patchUpdated" -> itemChanged(params, method) { item ->
            val fileChange = item as? ConversationItem.FileChange
                ?: throw ProtocolException("fileChange patch 的目标类型不正确")
            conversationItem(
                item = JsonObject(
                    mapOf(
                        "id" to JsonPrimitive(fileChange.id),
                        "type" to JsonPrimitive("fileChange"),
                        "changes" to params.requiredArray("changes"),
                        "status" to JsonPrimitive("inProgress"),
                    ),
                ),
                turnId = fileChange.turnId,
            )
        }

        "item/plan/delta" -> itemChanged(params, method, markActive = true) { item ->
            val plan = item as? ConversationItem.Plan
                ?: throw ProtocolException("plan delta 的目标类型不正确")
            plan.copy(text = plan.text + params.requiredString("delta"))
        }

        "item/mcpToolCall/progress" -> itemChanged(params, method) { item ->
            val activity = item as? ConversationItem.Activity
                ?: throw ProtocolException("MCP progress 的目标类型不正确")
            activity.copy(
                details = listOf(activity.details, params.requiredString("message"))
                    .filter(String::isNotBlank)
                    .joinToString("\n"),
            )
        }

        "serverRequest/resolved" -> (params["requestId"] as? JsonPrimitive)?.let { requestId ->
            ConversationEvent.RequestResolved("$serverId:$requestId")
        }

        else -> null
    }

    private fun itemChanged(
        params: JsonObject,
        method: String,
        markActive: Boolean = false,
        update: ConversationItemUpdate,
    ): ConversationEvent.ItemChanged = ConversationEvent.ItemChanged(
        method = method,
        turnId = params.requiredString("turnId"),
        itemId = params.requiredString("itemId"),
        markActive = markActive,
        update = update,
    )

    private fun ConversationItem.appendCommandOutput(delta: String): ConversationItem = when (this) {
        is ConversationItem.CommandExecution -> copy(output = output + delta)
        is ConversationItem.UserShellCommand -> copy(output = output + delta)
        else -> throw ProtocolException("command output delta 的目标类型不正确")
    }

    private fun JsonObject.tokenUsageBreakdown() = TokenUsageBreakdown(
        totalTokens = requiredLong("totalTokens"),
        inputTokens = requiredLong("inputTokens"),
        cachedInputTokens = requiredLong("cachedInputTokens"),
        outputTokens = requiredLong("outputTokens"),
        reasoningOutputTokens = requiredLong("reasoningOutputTokens"),
    )

    fun commandActions(value: JsonElement?): List<CommandAction> = value
        .optionalArray("commandActions")
        .map { element ->
            val action = element.jsonObject
            val command = action.requiredString("command")
            when (action.requiredString("type")) {
                "read" -> CommandAction.Read(command, action.requiredString("name"), action.requiredString("path"))
                "listFiles" -> CommandAction.ListFiles(command, action.optionalString("path"))
                "search" -> CommandAction.Search(
                    command = command,
                    query = action.optionalString("query"),
                    path = action.optionalString("path"),
                )

                "unknown" -> CommandAction.Unknown(command)
                else -> throw ProtocolException("未知命令行为：${action.requiredString("type")}")
            }
        }

    fun execPolicyAmendment(value: JsonElement?): ExecPolicyAmendment? {
        if (value == null || value is JsonNull) return null
        val commands = (value as? JsonArray)
            ?.map { it.jsonPrimitive.content }
            ?: throw ProtocolException("命令规则修订不是数组")
        return ExecPolicyAmendment(commands, value.toString())
    }

    fun networkPolicyAmendments(value: JsonElement?): List<NetworkPolicyAmendment> =
        value.optionalArray("proposedNetworkPolicyAmendments").map { element ->
            val amendment = element.jsonObject
            NetworkPolicyAmendment(
                host = amendment.requiredString("host"),
                action = when (amendment.requiredString("action")) {
                    "allow" -> NetworkPolicyRuleAction.ALLOW
                    "deny" -> NetworkPolicyRuleAction.DENY
                    else -> throw ProtocolException("未知网络规则操作：${amendment.requiredString("action")}")
                },
                responseJson = element.toString(),
            )
        }

    fun requestedPermissions(value: JsonObject): RequestedPermissions = RequestedPermissions(
        networkEnabled = value.optionalObject("network")?.optionalBoolean("enabled"),
        fileSystem = value.optionalObject("fileSystem")?.toRequestedFileSystemPermissions(),
        responseJson = value.toString(),
    )

    fun conversationItem(item: JsonObject, turnId: String): ConversationItem {
        val id = item.requiredString("id")
        return when (val type = item.requiredString("type")) {
            "userMessage" -> ConversationItem.UserMessage(
                id = id,
                turnId = turnId,
                content = item.requiredArray("content").map(::userMessagePart),
            )

            "agentMessage" -> ConversationItem.AgentMessage(
                id = id,
                turnId = turnId,
                text = item.requiredString("text"),
                phase = agentMessagePhase(item.optionalString("phase")),
            )

            "reasoning" -> ConversationItem.Reasoning(
                id = id,
                turnId = turnId,
                summaryParts = item.stringArray("summary"),
                rawContentParts = item.stringArray("content"),
            )

            "commandExecution" -> commandExecution(item, id, turnId)

            "fileChange" -> ConversationItem.FileChange(
                id = id,
                turnId = turnId,
                changes = item.requiredArray("changes").map(::changedFile),
                status = activityStatus(item.optionalString("status")),
            )

            "plan" -> ConversationItem.Plan(
                id = id,
                turnId = turnId,
                text = item.requiredString("text"),
            )

            "mcpToolCall" -> genericActivity(
                item = item,
                turnId = turnId,
                kind = ActivityKind.MCP_TOOL,
                title = item.requiredString("tool"),
                summary = item.requiredString("server"),
                details = (item["error"] as? JsonObject)?.optionalString("message").orEmpty(),
                technicalDetails = item.compactText(),
            )

            "dynamicToolCall" -> genericActivity(
                item = item,
                turnId = turnId,
                kind = ActivityKind.DYNAMIC_TOOL,
                title = item.requiredString("tool"),
                summary = item.optionalString("namespace"),
                details = item.dynamicToolOutputText(),
                technicalDetails = item.compactText(),
            )

            "collabAgentToolCall" -> ConversationItem.AgentCollaboration(
                id = id,
                turnId = turnId,
                tool = agentTool(item.requiredString("tool")),
                status = activityStatus(item.requiredString("status")),
                senderThreadId = item.requiredString("senderThreadId"),
                receiverThreadIds = item.requiredArray("receiverThreadIds").map { it.jsonPrimitive.content },
                prompt = item.optionalString("prompt"),
                model = item.optionalString("model"),
                reasoningEffort = item.optionalString("reasoningEffort"),
                agentsStates = item.requiredObject("agentsStates").mapValues { (_, value) ->
                    val state = value.jsonObject
                    AgentState(
                        status = agentStatus(state.requiredString("status")),
                        message = state.optionalString("message"),
                    )
                },
            )

            "webSearch" -> genericActivity(
                item = item,
                turnId = turnId,
                kind = ActivityKind.WEB_SEARCH,
                title = webSearchTitle(item),
                technicalDetails = item.compactText(),
            )

            "contextCompaction" -> ConversationItem.Activity(
                id = id,
                turnId = turnId,
                kind = ActivityKind.CONTEXT_COMPACTION,
                title = "压缩上下文",
                details = "",
                status = ActivityStatus.COMPLETED,
            )

            "enteredReviewMode", "exitedReviewMode" -> ConversationItem.Activity(
                id = id,
                turnId = turnId,
                kind = ActivityKind.REVIEW,
                title = if (type == "enteredReviewMode") "进入审查" else "完成审查",
                details = item.optionalString("review").orEmpty(),
                status = ActivityStatus.COMPLETED,
            )

            "hookPrompt" -> genericActivity(
                item = item,
                turnId = turnId,
                kind = ActivityKind.HOOK,
                title = "Hook 提示",
                details = item.requiredArray("fragments").joinToString("\n") { fragment ->
                    fragment.jsonObject.requiredString("text")
                },
                technicalDetails = item.compactText(),
            )

            "subAgentActivity" -> ConversationItem.SubAgentActivity(
                id = id,
                turnId = turnId,
                kind = subAgentActivityKind(item.requiredString("kind")),
                agentThreadId = item.requiredString("agentThreadId"),
                agentPath = item.requiredString("agentPath"),
            )

            "imageView" -> genericActivity(
                item = item,
                turnId = turnId,
                kind = ActivityKind.IMAGE_VIEW,
                title = item.requiredString("path").fileName(),
                details = item.requiredString("path"),
                remotePath = item.requiredString("path"),
            )

            "imageGeneration" -> genericActivity(
                item = item,
                turnId = turnId,
                kind = ActivityKind.IMAGE_GENERATION,
                title = "生成图片",
                summary = item.optionalString("revisedPrompt"),
                details = item.optionalString("savedPath").orEmpty(),
                technicalDetails = item.withoutLargeImageResult(),
                remotePath = item.optionalString("savedPath"),
                status = imageGenerationStatus(item.requiredString("status")),
            )

            "sleep" -> genericActivity(
                item = item,
                turnId = turnId,
                kind = ActivityKind.WAIT,
                title = formatDurationMillis(item.requiredLong("durationMs")),
            )

            else -> genericActivity(
                item = item,
                turnId = turnId,
                kind = ActivityKind.UNKNOWN,
                title = "未知活动",
                summary = "客户端尚未支持此类活动",
                technicalDetails = item.compactText(),
            )
        }
    }

    private fun commandExecution(item: JsonObject, id: String, turnId: String): ConversationItem {
        val command = item.requiredString("command")
        val cwd = item.requiredString("cwd")
        val output = item.optionalString("aggregatedOutput").orEmpty()
        val exitCode = item["exitCode"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.intOrNull
        val status = activityStatus(item.optionalString("status"))
        return when (item.optionalString("source") ?: "agent") {
            "userShell" -> ConversationItem.UserShellCommand(
                id = id,
                turnId = turnId,
                command = command,
                cwd = cwd,
                output = output,
                exitCode = exitCode,
                durationMillis = item.optionalLong("durationMs"),
                status = status,
            )

            "agent", "unifiedExecStartup", "unifiedExecInteraction" -> ConversationItem.CommandExecution(
                id = id,
                turnId = turnId,
                command = command,
                cwd = cwd,
                output = output,
                exitCode = exitCode,
                status = status,
                processId = item.optionalString("processId"),
            )

            else -> throw ProtocolException("未知命令执行来源：${item.optionalString("source")}")
        }
    }

    private fun genericActivity(
        item: JsonObject,
        turnId: String,
        kind: ActivityKind,
        title: String,
        summary: String? = null,
        details: String = "",
        technicalDetails: String? = null,
        remotePath: String? = null,
        status: ActivityStatus = activityStatus(item.optionalString("status")),
    ) = ConversationItem.Activity(
        id = item.requiredString("id"),
        turnId = turnId,
        kind = kind,
        title = title,
        summary = summary,
        details = details,
        technicalDetails = technicalDetails,
        remotePath = remotePath,
        status = status,
    )

    private fun userMessagePart(input: JsonElement): UserMessagePart {
        val value = input.jsonObject
        return when (value.requiredString("type")) {
            "text" -> UserMessagePart.Text(value.requiredString("text"))
            "image" -> {
                val url = value.optionalString("url")
                val fileId = value.optionalString("fileId")
                if (url == null && fileId == null) throw ProtocolException("图片缺少 url 或 fileId")
                UserMessagePart.Image(url = url, fileId = fileId)
            }
            "localImage" -> UserMessagePart.LocalImage(value.requiredString("path"))
            "audio" -> UserMessagePart.Audio(value.requiredString("url"))
            "localAudio" -> UserMessagePart.LocalAudio(value.requiredString("path"))
            "skill" -> UserMessagePart.Skill(value.requiredString("name"), value.requiredString("path"))
            "mention" -> UserMessagePart.Mention(value.requiredString("name"), value.requiredString("path"))
            else -> throw ProtocolException("未知用户输入类型：${value.requiredString("type")}")
        }
    }

    private fun changedFile(element: JsonElement): ChangedFile {
        val value = element.jsonObject
        val kindValue = value.requiredObject("kind")
        val kind = when (val type = kindValue.requiredString("type")) {
            "add" -> FileChangeKind.Add
            "delete" -> FileChangeKind.Delete
            "update" -> FileChangeKind.Update(movePath = kindValue.optionalString("move_path"))
            else -> throw ProtocolException("未知文件修改类型：$type")
        }
        return ChangedFile(
            path = value.requiredString("path"),
            kind = kind,
            diff = value.requiredString("diff"),
        )
    }

    private fun JsonObject.toRequestedFileSystemPermissions(): RequestedFileSystemPermissions {
        val entries = requiredArray("entries").map { element ->
            val entry = element.jsonObject
            FileSystemPermissionEntry(
                target = entry.requiredObject("path").toFileSystemPermissionTarget(),
                access = when (entry.requiredString("access")) {
                    "read" -> FileSystemAccessMode.READ
                    "write" -> FileSystemAccessMode.WRITE
                    "deny" -> FileSystemAccessMode.DENY
                    else -> throw ProtocolException("未知文件权限：${entry.requiredString("access")}")
                },
            )
        }
        return RequestedFileSystemPermissions(
            entries = entries,
            globScanMaxDepth = optionalLong("globScanMaxDepth")?.toInt(),
        )
    }

    private fun JsonObject.toFileSystemPermissionTarget(): FileSystemPermissionTarget =
        when (requiredString("type")) {
            "path" -> FileSystemPermissionTarget.Path(requiredString("path"))
            "glob_pattern" -> FileSystemPermissionTarget.GlobPattern(requiredString("pattern"))
            "special" -> requiredObject("value").toSpecialFileSystemPermissionTarget()
            else -> throw ProtocolException("未知文件权限目标：${requiredString("type")}")
        }

    private fun JsonObject.toSpecialFileSystemPermissionTarget(): FileSystemPermissionTarget =
        when (requiredString("kind")) {
            "root" -> FileSystemPermissionTarget.Root
            "minimal" -> FileSystemPermissionTarget.Minimal
            "project_roots" -> FileSystemPermissionTarget.ProjectRoots(optionalString("subpath"))
            "tmpdir" -> FileSystemPermissionTarget.TempDirectory
            "slash_tmp" -> FileSystemPermissionTarget.SlashTmp
            "unknown" -> FileSystemPermissionTarget.Unknown(requiredString("path"), optionalString("subpath"))
            else -> throw ProtocolException("未知特殊文件权限目标：${requiredString("kind")}")
        }

    private fun activityStatus(value: String?): ActivityStatus = when (value) {
        "inProgress" -> ActivityStatus.IN_PROGRESS
        "completed" -> ActivityStatus.COMPLETED
        "interrupted" -> ActivityStatus.INTERRUPTED
        "failed" -> ActivityStatus.FAILED
        "declined" -> ActivityStatus.DECLINED
        null -> ActivityStatus.COMPLETED
        else -> throw ProtocolException("未知活动状态：$value")
    }

    private fun agentMessagePhase(value: String?): AgentMessagePhase? = when (value) {
        null -> null
        "commentary" -> AgentMessagePhase.COMMENTARY
        "final_answer" -> AgentMessagePhase.FINAL_ANSWER
        else -> throw ProtocolException("未知助手消息阶段：$value")
    }

    private fun imageGenerationStatus(value: String): ActivityStatus = when (value) {
        "in_progress" -> ActivityStatus.IN_PROGRESS
        "completed" -> ActivityStatus.COMPLETED
        "failed" -> ActivityStatus.FAILED
        else -> throw ProtocolException("未知图片生成状态：$value")
    }

    private fun JsonObject.toThreadStatus(): ThreadRuntimeStatus = when (requiredString("type")) {
        "notLoaded" -> ThreadRuntimeStatus.NOT_LOADED
        "idle" -> ThreadRuntimeStatus.IDLE
        "active" -> ThreadRuntimeStatus.ACTIVE
        "systemError" -> ThreadRuntimeStatus.SYSTEM_ERROR
        else -> throw ProtocolException("未知会话状态：${requiredString("type")}")
    }

    private fun String.toThreadHistoryMode(): ThreadHistoryMode = when (this) {
        "legacy" -> ThreadHistoryMode.LEGACY
        "paginated" -> ThreadHistoryMode.PAGINATED
        else -> throw ProtocolException("未知历史格式：$this")
    }

    private fun String.toConversationTurnStatus(): ConversationTurnStatus = when (this) {
        "inProgress" -> ConversationTurnStatus.IN_PROGRESS
        "completed" -> ConversationTurnStatus.COMPLETED
        "interrupted" -> ConversationTurnStatus.INTERRUPTED
        "failed" -> ConversationTurnStatus.FAILED
        else -> throw ProtocolException("未知 turn 状态：$this")
    }

    private fun agentTool(tool: String): AgentTool = when (tool) {
        "spawnAgent" -> AgentTool.SPAWN
        "sendInput" -> AgentTool.SEND_INPUT
        "resumeAgent" -> AgentTool.RESUME
        "wait" -> AgentTool.WAIT
        "closeAgent" -> AgentTool.CLOSE
        "sendMessage" -> AgentTool.SEND_MESSAGE
        "followupTask" -> AgentTool.FOLLOWUP_TASK
        "interruptAgent" -> AgentTool.INTERRUPT
        "listAgents" -> AgentTool.LIST
        else -> throw ProtocolException("未知协作代理操作：$tool")
    }

    private fun agentStatus(status: String): AgentStatus = when (status) {
        "pendingInit" -> AgentStatus.PENDING_INIT
        "running" -> AgentStatus.RUNNING
        "interrupted" -> AgentStatus.INTERRUPTED
        "completed" -> AgentStatus.COMPLETED
        "errored" -> AgentStatus.ERRORED
        "shutdown" -> AgentStatus.SHUTDOWN
        "notFound" -> AgentStatus.NOT_FOUND
        else -> throw ProtocolException("未知子代理状态：$status")
    }

    private fun subAgentActivityKind(kind: String): SubAgentActivityKind = when (kind) {
        "started" -> SubAgentActivityKind.STARTED
        "interacted" -> SubAgentActivityKind.INTERACTED
        "interrupted" -> SubAgentActivityKind.INTERRUPTED
        "completed" -> SubAgentActivityKind.COMPLETED
        else -> throw ProtocolException("未知子代理活动：$kind")
    }

    private fun webSearchTitle(item: JsonObject): String {
        val query = item.requiredString("query")
        val action = item["action"] as? JsonObject ?: return query
        return when (action.requiredString("type")) {
            "search" -> action.optionalString("query") ?: query
            "openPage" -> action.optionalString("url") ?: "搜索结果"
            "findInPage" -> action.optionalString("pattern") ?: "页面内容"
            "other" -> query
            else -> throw ProtocolException("未知网页搜索操作：${action.requiredString("type")}")
        }
    }

    private fun JsonObject.dynamicToolOutputText(): String = (this["contentItems"] as? JsonArray).orEmpty()
        .map { it.jsonObject }
        .joinToString("\n") { content ->
            when (content.requiredString("type")) {
                "inputText" -> content.requiredString("text")
                "inputImage" -> "[图片]"
                else -> throw ProtocolException("未知动态工具输出类型：${content.requiredString("type")}")
            }
        }

    private fun JsonObject.withoutLargeImageResult(): String = JsonObject(
        filterKeys { it != "result" },
    ).compactText()

    private fun String.fileName(): String = substringAfterLast('/').ifBlank { this }

    private fun formatDurationMillis(durationMillis: Long): String = when {
        durationMillis < 1_000 -> "$durationMillis 毫秒"
        durationMillis < 60_000 -> "${durationMillis / 1_000} 秒"
        else -> "${durationMillis / 60_000} 分钟"
    }

    private fun JsonElement?.optionalArray(fieldName: String): JsonArray = when (this) {
        null, JsonNull -> JsonArray(emptyList())
        is JsonArray -> this
        else -> throw ProtocolException("协议字段 $fieldName 不是数组")
    }
}

internal fun JsonObject.requiredString(name: String): String =
    (this[name] as? JsonPrimitive)?.contentOrNull
        ?: throw ProtocolException("协议字段 $name 缺失或不是字符串")

internal fun JsonObject.optionalString(name: String): String? =
    (this[name] as? JsonPrimitive)?.contentOrNull

internal fun JsonObject.requiredLong(name: String): Long =
    (this[name] as? JsonPrimitive)?.longOrNull
        ?: throw ProtocolException("协议字段 $name 缺失或不是整数")

internal fun JsonObject.requiredInt(name: String): Int =
    (this[name] as? JsonPrimitive)?.intOrNull
        ?: throw ProtocolException("协议字段 $name 缺失或不是 Int 范围内的整数")

internal fun JsonObject.optionalLong(name: String): Long? =
    (this[name] as? JsonPrimitive)?.longOrNull

internal fun JsonObject.requiredDouble(name: String): Double =
    (this[name] as? JsonPrimitive)?.doubleOrNull
        ?: throw ProtocolException("协议字段 $name 缺失或不是数字")

internal fun JsonObject.optionalBoolean(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()

internal fun JsonObject.requiredBoolean(name: String): Boolean =
    optionalBoolean(name) ?: throw ProtocolException("协议字段 $name 缺失或不是布尔值")

internal fun JsonObject.requiredObject(name: String): JsonObject =
    this[name] as? JsonObject ?: throw ProtocolException("协议字段 $name 缺失或不是对象")

internal fun JsonObject.optionalObject(name: String): JsonObject? = when (val value = this[name]) {
    null, JsonNull -> null
    is JsonObject -> value
    else -> throw ProtocolException("协议字段 $name 不是对象或 null")
}

internal fun JsonObject.requiredArray(name: String): JsonArray =
    this[name] as? JsonArray ?: throw ProtocolException("协议字段 $name 缺失或不是数组")

private fun JsonObject.stringArray(name: String): List<String> =
    if (containsKey(name)) requiredArray(name).map { it.jsonPrimitive.content } else emptyList()

private fun JsonElement?.compactText(): String = when (this) {
    null, JsonNull -> ""
    is JsonPrimitive -> content
    else -> toString().take(MAX_ACTIVITY_DETAIL_LENGTH)
}

private const val MAX_ACTIVITY_DETAIL_LENGTH = 4_000
