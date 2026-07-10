package com.hebo.codex.domain

import com.hebo.codex.data.local.ServerSettings
import com.hebo.codex.data.local.ServerStore
import com.hebo.codex.data.remote.AppServerApi
import com.hebo.codex.data.remote.AppServerClientFactory
import com.hebo.codex.data.remote.AppServerConnection
import com.hebo.codex.data.remote.ProtocolException
import com.hebo.codex.data.remote.ProtocolMapper
import com.hebo.codex.data.remote.RpcException
import com.hebo.codex.data.remote.ServerEvent
import com.hebo.codex.data.remote.optionalString
import com.hebo.codex.data.remote.requiredArray
import com.hebo.codex.data.remote.requiredObject
import com.hebo.codex.data.remote.requiredString
import com.hebo.codex.util.runSuspendCatching
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class CodexRepository(
    private val serverStore: ServerStore,
    private val clientFactory: AppServerClientFactory,
    private val applicationScope: CoroutineScope,
) {
    private val connectionMutex = Mutex()
    private val conversationMutex = Mutex()
    private val connections = ConcurrentHashMap<String, AppServerConnection>()
    private val connectionObservers = mutableMapOf<String, List<Job>>()
    private val conversations = ConcurrentHashMap<ConversationKey, MutableStateFlow<ConversationState>>()
    private val approvalRequests = ConcurrentHashMap<String, ApprovalRequest>()
    private val mutableConnectionStatuses = MutableStateFlow<Map<String, ConnectionStatus>>(emptyMap())

    val serverSettings: StateFlow<ServerSettings> = serverStore.settings.stateIn(
        scope = applicationScope,
        started = SharingStarted.Eagerly,
        initialValue = ServerSettings(servers = emptyList(), activeServerId = null),
    )
    val servers: StateFlow<List<ServerProfile>> = serverSettings
        .map { it.servers }
        .stateIn(applicationScope, SharingStarted.Eagerly, emptyList())
    val activeServerId: StateFlow<String?> = serverSettings
        .map { it.activeServerId }
        .stateIn(applicationScope, SharingStarted.Eagerly, null)
    val connectionStatuses: StateFlow<Map<String, ConnectionStatus>> = mutableConnectionStatuses

    suspend fun addServer(name: String, endpoint: String, token: String): ServerProfile {
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) { "请输入服务器名称" }
        require(serverStore.settings.first().servers.none { it.name == normalizedName }) { "服务器名称已存在" }
        val normalizedEndpoint = validateWebSocketEndpoint(endpoint)
        val normalizedToken = token.trim()
        require(normalizedToken.isNotEmpty()) { "请输入 Token" }

        testConnection(normalizedEndpoint, normalizedToken)
        val profile = ServerProfile(
            id = UUID.randomUUID().toString(),
            name = normalizedName,
            webSocketUrl = normalizedEndpoint,
            authentication = ServerAuthentication.CAPABILITY_TOKEN,
        )
        serverStore.addServer(profile, normalizedToken)
        serverSettings.first { settings -> settings.servers.any { it.id == profile.id } }
        applicationScope.launch {
            runSuspendCatching { connect(profile.id) }
        }
        return profile
    }

    suspend fun selectServer(serverId: String) {
        serverStore.selectServer(serverId)
        connect(serverId)
    }

    suspend fun removeServer(serverId: String) {
        connectionMutex.withLock {
            connectionObservers.remove(serverId)?.forEach { it.cancel() }
            connections.remove(serverId)?.close()
            clearServerApprovals(serverId)
        }
        mutableConnectionStatuses.updateValue(serverId, ConnectionStatus.Disconnected)
        conversations.keys.filter { it.serverId == serverId }.forEach { conversations.remove(it) }
        serverStore.removeServer(serverId)
    }

    suspend fun connect(serverId: String) {
        api(serverId)
    }

    suspend fun recentThreads(serverId: String, cwd: String? = null): List<ThreadSummary> =
        api(serverId).listRecentThreads(cwd).map(ProtocolMapper::threadSummary)

    suspend fun startConversation(serverId: String, cwd: String): String {
        val normalizedCwd = cwd.trim()
        require(normalizedCwd.isNotEmpty()) { "请输入服务端项目路径" }
        val snapshot = ProtocolMapper.threadSnapshot(api(serverId).startThread(normalizedCwd))
        val profile = awaitServer(serverId)
        val state = snapshot.toConversationState(profile)
        conversations[ConversationKey(serverId, snapshot.summary.id)] = MutableStateFlow(state)
        return snapshot.summary.id
    }

    suspend fun openConversation(serverId: String, threadId: String): StateFlow<ConversationState> {
        val key = ConversationKey(serverId, threadId)
        conversations[key]?.let { return it }
        return conversationMutex.withLock {
            conversations[key]?.let { return@withLock it }
            val snapshot = ProtocolMapper.threadSnapshot(api(serverId).resumeThread(threadId))
            val state = MutableStateFlow(snapshot.toConversationState(awaitServer(serverId)))
            conversations[key] = state
            state
        }
    }

    suspend fun sendMessage(serverId: String, threadId: String, message: String) {
        val text = message.trim()
        require(text.isNotEmpty()) { "消息不能为空" }
        val state = requireConversation(serverId, threadId)
        check(!state.value.isRunning) { "当前任务仍在运行" }
        val clientId = UUID.randomUUID().toString()
        val localItemId = "local:$clientId"
        state.updateState { current ->
            current.copy(
                title = if (current.title == "新会话") text.lineSequence().first().take(72) else current.title,
                isRunning = true,
                items = current.items + ConversationItem.UserMessage(
                    id = localItemId,
                    turnId = "pending",
                    clientId = clientId,
                    text = text,
                    isPending = true,
                ),
                errorMessage = null,
            )
        }

        try {
            val result = api(serverId).startTurn(threadId, clientId, text)
            val turnId = result.requiredObject("turn").requiredString("id")
            state.updateState { current ->
                current.copy(
                    items = current.items.map { item ->
                        if (item is ConversationItem.UserMessage && item.id == localItemId) {
                            item.copy(turnId = turnId)
                        } else {
                            item
                        }
                    },
                    activeTurnId = turnId,
                    isRunning = true,
                )
            }
        } catch (error: Throwable) {
            state.updateState { current ->
                current.copy(
                    items = current.items.filterNot { it.id == localItemId },
                    isRunning = false,
                    errorMessage = error.userMessage(),
                )
            }
            throw error
        }
    }

    suspend fun interrupt(serverId: String, threadId: String) {
        val state = requireConversation(serverId, threadId)
        val turnId = state.value.activeTurnId ?: return
        api(serverId).interruptTurn(threadId, turnId)
    }

    fun clearConversationError(serverId: String, threadId: String) {
        conversations[ConversationKey(serverId, threadId)]?.updateState { it.copy(errorMessage = null) }
    }

    fun respondToApproval(
        serverId: String,
        threadId: String,
        requestKey: String,
        decision: ApprovalDecision,
    ) {
        val request = approvalRequests[requestKey] ?: error("审批请求已失效")
        check(request.threadId == threadId) { "审批请求不属于当前会话" }
        val connection = connections[serverId] ?: error("服务器未连接")
        connection.respond(
            request.id,
            buildJsonObject { put("decision", decision.wireValue) },
        )
        approvalRequests.remove(requestKey)
        conversations[ConversationKey(serverId, threadId)]?.updateState { current ->
            current.copy(approvals = current.approvals.filterNot { it.requestKey == requestKey })
        }
    }

    private suspend fun api(serverId: String): AppServerApi = connectionMutex.withLock {
        val existing = connections[serverId]
        if (existing?.status?.value is ConnectionStatus.Connected) return@withLock AppServerApi(existing)
        connectionObservers.remove(serverId)?.forEach { it.cancel() }
        existing?.close()
        connections.remove(serverId)
        clearServerApprovals(serverId)

        val server = awaitServer(serverId)
        mutableConnectionStatuses.updateValue(serverId, ConnectionStatus.Connecting)
        val connection = clientFactory.create(
            endpoint = server.webSocketUrl,
            token = serverStore.readToken(serverId),
            parentContext = applicationScope.coroutineContext,
        )
        try {
            connection.connect()
            connections[serverId] = connection
            val api = AppServerApi(connection)
            restoreConversations(serverId, server, api)
            check(connection.status.value is ConnectionStatus.Connected) { "服务器连接已中断" }
            observeConnection(serverId, connection)
            mutableConnectionStatuses.updateValue(serverId, ConnectionStatus.Connected)
            api
        } catch (error: Throwable) {
            connection.close()
            mutableConnectionStatuses.updateValue(serverId, ConnectionStatus.Failed(error.userMessage()))
            throw error
        }
    }

    private suspend fun testConnection(endpoint: String, token: String) {
        val connection = clientFactory.create(endpoint, token, applicationScope.coroutineContext)
        try {
            connection.connect()
        } finally {
            connection.close()
        }
    }

    private suspend fun restoreConversations(
        serverId: String,
        server: ServerProfile,
        api: AppServerApi,
    ) {
        conversations.entries
            .filter { it.key.serverId == serverId }
            .forEach { (key, state) ->
                runSuspendCatching { ProtocolMapper.threadSnapshot(api.resumeThread(key.threadId)) }
                    .onSuccess { snapshot ->
                        val current = state.value
                        val pendingItems = current.items.filter {
                            it is ConversationItem.UserMessage && it.isPending
                        }
                        val restored = snapshot.toConversationState(server)
                        state.value = restored.copy(
                            title = if (pendingItems.isEmpty()) restored.title else current.title,
                            items = restored.items + pendingItems,
                            isRunning = restored.isRunning || pendingItems.isNotEmpty(),
                        )
                    }
                    .onFailure { error ->
                        state.updateState {
                            it.copy(isRunning = false, errorMessage = error.userMessage())
                        }
                    }
            }
    }

    private fun observeConnection(serverId: String, connection: AppServerConnection) {
        val statusObserver = applicationScope.launch {
            connection.status.collect { status ->
                mutableConnectionStatuses.updateValue(serverId, status)
            }
        }
        val eventObserver = applicationScope.launch {
            connection.events.collect { event ->
                try {
                    handleServerEvent(serverId, connection, event)
                } catch (error: Throwable) {
                    val threadId = event.paramsOrNull()?.optionalString("threadId")
                    if (threadId != null) {
                        conversations[ConversationKey(serverId, threadId)]?.updateState {
                            it.copy(errorMessage = error.userMessage())
                        }
                    }
                }
            }
        }
        connectionObservers[serverId] = listOf(statusObserver, eventObserver)
    }

    private fun handleServerEvent(
        serverId: String,
        connection: AppServerConnection,
        event: ServerEvent,
    ) {
        when (event) {
            is ServerEvent.Request -> handleServerRequest(serverId, connection, event)
            is ServerEvent.Notification -> handleNotification(serverId, event.method, event.params)
        }
    }

    private fun handleServerRequest(
        serverId: String,
        connection: AppServerConnection,
        request: ServerEvent.Request,
    ) {
        val type = when (request.method) {
            "item/commandExecution/requestApproval" -> ApprovalType.COMMAND_EXECUTION
            "item/fileChange/requestApproval" -> ApprovalType.FILE_CHANGE
            else -> {
                connection.respondError(request.id, METHOD_NOT_FOUND, "客户端不支持服务端请求：${request.method}")
                return
            }
        }
        val threadId = request.params.requiredString("threadId")
        val requestKey = approvalKey(serverId, request.id)
        val approval = PendingApproval(
            requestKey = requestKey,
            itemId = request.params.requiredString("itemId"),
            turnId = request.params.requiredString("turnId"),
            type = type,
            reason = request.params.optionalString("reason"),
            command = request.params.optionalString("command"),
            cwd = request.params.optionalString("cwd"),
        )
        val conversation = conversations[ConversationKey(serverId, threadId)]
        if (conversation == null) {
            connection.respondError(request.id, INVALID_REQUEST, "会话尚未在客户端打开")
            return
        }
        approvalRequests[requestKey] = ApprovalRequest(request.id, threadId)
        conversation.updateState { current -> current.copy(approvals = current.approvals + approval) }
    }

    private fun handleNotification(serverId: String, method: String, params: JsonObject) {
        val threadId = params.optionalString("threadId") ?: return
        val state = conversations[ConversationKey(serverId, threadId)] ?: return
        when (method) {
            "turn/started" -> {
                val turn = params.requiredObject("turn")
                state.updateState {
                    it.copy(activeTurnId = turn.requiredString("id"), isRunning = true, errorMessage = null)
                }
            }

            "turn/completed" -> {
                val turn = params.requiredObject("turn")
                val errorMessage = (turn["error"] as? JsonObject)?.optionalString("message")
                state.updateState {
                    it.copy(
                        activeTurnId = null,
                        isRunning = false,
                        approvals = emptyList(),
                        errorMessage = errorMessage,
                    )
                }
                removeApprovals(serverId, threadId)
            }

            "item/started", "item/completed" -> {
                val item = ProtocolMapper.conversationItem(
                    item = params.requiredObject("item"),
                    turnId = params.requiredString("turnId"),
                    isCompleted = method == "item/completed",
                )
                state.updateState { current -> current.copy(items = current.items.upsert(item)) }
            }

            "item/agentMessage/delta" -> state.updateItem(params.requiredString("itemId")) { item ->
                val message = item as? ConversationItem.AgentMessage
                    ?: throw ProtocolException("agentMessage delta 的目标类型不正确")
                message.copy(text = message.text + params.requiredString("delta"))
            }

            "item/reasoning/summaryTextDelta" -> state.updateItem(params.requiredString("itemId")) { item ->
                val reasoning = item as? ConversationItem.Reasoning
                    ?: throw ProtocolException("reasoning summary delta 的目标类型不正确")
                reasoning.copy(summary = reasoning.summary + params.requiredString("delta"))
            }

            "item/reasoning/textDelta" -> state.updateItem(params.requiredString("itemId")) { item ->
                val reasoning = item as? ConversationItem.Reasoning
                    ?: throw ProtocolException("reasoning delta 的目标类型不正确")
                reasoning.copy(details = reasoning.details + params.requiredString("delta"))
            }

            "item/commandExecution/outputDelta" -> state.updateItem(params.requiredString("itemId")) { item ->
                val command = item as? ConversationItem.CommandExecution
                    ?: throw ProtocolException("command output delta 的目标类型不正确")
                command.copy(output = command.output + params.requiredString("delta"))
            }

            "item/fileChange/patchUpdated" -> state.updateItem(params.requiredString("itemId")) { item ->
                val fileChange = item as? ConversationItem.FileChange
                    ?: throw ProtocolException("fileChange patch 的目标类型不正确")
                val replacement = ProtocolMapper.conversationItem(
                    item = buildJsonObject {
                        put("id", fileChange.id)
                        put("type", "fileChange")
                        put("changes", params.requiredArray("changes"))
                        put("status", "inProgress")
                    },
                    turnId = fileChange.turnId,
                    isCompleted = false,
                )
                replacement
            }

            "item/plan/delta" -> state.updateItem(params.requiredString("itemId")) { item ->
                val activity = item as? ConversationItem.Activity
                    ?: throw ProtocolException("plan delta 的目标类型不正确")
                activity.copy(details = activity.details + params.requiredString("delta"))
            }

            "serverRequest/resolved" -> {
                val requestId = params["requestId"] as? JsonPrimitive ?: return
                val requestKey = approvalKey(serverId, requestId)
                approvalRequests.remove(requestKey)
                state.updateState { current ->
                    current.copy(approvals = current.approvals.filterNot { it.requestKey == requestKey })
                }
            }
        }
    }

    private fun removeApprovals(serverId: String, threadId: String) {
        approvalRequests.entries
            .filter { (key, request) -> key.startsWith("$serverId:") && request.threadId == threadId }
            .forEach { approvalRequests.remove(it.key) }
        conversations[ConversationKey(serverId, threadId)]?.updateState { it.copy(approvals = emptyList()) }
    }

    private fun clearServerApprovals(serverId: String) {
        approvalRequests.keys
            .filter { it.startsWith("$serverId:") }
            .forEach { approvalRequests.remove(it) }
        conversations.entries
            .filter { it.key.serverId == serverId }
            .forEach { it.value.updateState { state -> state.copy(approvals = emptyList()) } }
    }

    private fun requireConversation(serverId: String, threadId: String): MutableStateFlow<ConversationState> =
        conversations[ConversationKey(serverId, threadId)] ?: error("会话尚未打开")

    private suspend fun awaitServer(serverId: String): ServerProfile =
        withTimeoutOrNull(SERVER_LOAD_TIMEOUT_MILLIS) {
            serverSettings
                .map { settings -> settings.servers.singleOrNull { it.id == serverId } }
                .first { it != null }
        } ?: error("服务器不存在")

    private fun com.hebo.codex.data.remote.ThreadSnapshot.toConversationState(
        profile: ServerProfile,
    ): ConversationState = ConversationState(
        serverId = profile.id,
        serverName = profile.name,
        threadId = summary.id,
        title = summary.title,
        cwd = summary.cwd,
        items = items,
        activeTurnId = activeTurnId,
        isRunning = summary.status == ThreadRuntimeStatus.ACTIVE,
    )

    private fun List<ConversationItem>.upsert(newItem: ConversationItem): List<ConversationItem> {
        val index = indexOfFirst { current ->
            current.id == newItem.id ||
                current is ConversationItem.UserMessage &&
                newItem is ConversationItem.UserMessage &&
                current.clientId != null && current.clientId == newItem.clientId
        }
        if (index == -1) return this + newItem
        return toMutableList().apply { set(index, newItem) }
    }

    private fun MutableStateFlow<ConversationState>.updateItem(
        itemId: String,
        transform: (ConversationItem) -> ConversationItem,
    ) {
        updateState { current ->
            val index = current.items.indexOfFirst { it.id == itemId }
            if (index == -1) throw ProtocolException("流式事件引用了不存在的条目：$itemId")
            current.copy(items = current.items.toMutableList().apply { set(index, transform(get(index))) })
        }
    }

    private fun <T> MutableStateFlow<T>.updateState(transform: (T) -> T) {
        update(transform)
    }

    private fun MutableStateFlow<Map<String, ConnectionStatus>>.updateValue(
        serverId: String,
        status: ConnectionStatus,
    ) {
        update { it + (serverId to status) }
    }

    private fun ServerEvent.paramsOrNull(): JsonObject? = when (this) {
        is ServerEvent.Notification -> params
        is ServerEvent.Request -> params
    }

    private fun approvalKey(serverId: String, id: JsonPrimitive): String = "$serverId:${id}"

    private fun Throwable.userMessage(): String = when (this) {
        is RpcException -> message
        is ProtocolException -> message ?: "协议错误"
        is IllegalArgumentException, is IllegalStateException -> message ?: "操作失败"
        else -> message ?: "网络连接失败"
    }

    private data class ConversationKey(val serverId: String, val threadId: String)

    private data class ApprovalRequest(val id: JsonPrimitive, val threadId: String)

    private companion object {
        const val METHOD_NOT_FOUND = -32601
        const val INVALID_REQUEST = -32600
        const val SERVER_LOAD_TIMEOUT_MILLIS = 5_000L
    }
}
