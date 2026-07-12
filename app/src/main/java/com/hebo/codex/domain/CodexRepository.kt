package com.hebo.codex.domain

import com.hebo.codex.data.local.ServerSettings
import com.hebo.codex.data.local.ServerStore
import com.hebo.codex.data.remote.AppServerApi
import com.hebo.codex.data.remote.AppServerClientFactory
import com.hebo.codex.data.remote.AppServerConnection
import com.hebo.codex.data.remote.ProtocolException
import com.hebo.codex.data.remote.ProtocolMapper
import com.hebo.codex.data.remote.ServerEvent
import com.hebo.codex.data.remote.StableFeatureApi
import com.hebo.codex.data.remote.StableRpcMethod
import com.hebo.codex.data.remote.ThreadSnapshot
import com.hebo.codex.data.remote.optionalBoolean
import com.hebo.codex.data.remote.optionalLong
import com.hebo.codex.data.remote.optionalString
import com.hebo.codex.data.remote.requiredArray
import com.hebo.codex.data.remote.requiredObject
import com.hebo.codex.data.remote.requiredString
import com.hebo.codex.util.runSuspendCatching
import com.hebo.codex.util.serverError
import com.hebo.codex.util.toUserFacingError
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class CodexRepository(
    private val serverStore: ServerStore,
    private val clientFactory: AppServerClientFactory,
    private val applicationScope: CoroutineScope,
    private val notifyConversation: (ConversationNotification) -> Unit = {},
) {
    private val connectionMutex = Mutex()
    private val conversationMutex = Mutex()
    private val connections = ConcurrentHashMap<String, AppServerConnection>()
    private val connectionObservers = mutableMapOf<String, List<Job>>()
    private val conversations = ConcurrentHashMap<ConversationKey, MutableStateFlow<ConversationState>>()
    private val pendingResumeGoalSnapshots = ConcurrentHashMap.newKeySet<ConversationKey>()
    private val pendingRequestRoutes = ConcurrentHashMap<String, PendingRequestRoute>()
    private val mutableConnectionStatuses = MutableStateFlow<Map<String, ConnectionStatus>>(emptyMap())
    private val mutableThreadRevisions = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val mutableFeatureRevisions = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val mutableRateLimitRevisions = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val mutableServerNotices = MutableStateFlow<Map<String, List<ConversationNotice>>>(emptyMap())
    private val mutableThreadAttention = MutableStateFlow<Map<String, Map<String, ThreadAttention>>>(emptyMap())

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
    val threadRevisions: StateFlow<Map<String, Long>> = mutableThreadRevisions
    val featureRevisions: StateFlow<Map<String, Long>> = mutableFeatureRevisions
    val rateLimitRevisions: StateFlow<Map<String, Long>> = mutableRateLimitRevisions
    val serverNotices: StateFlow<Map<String, List<ConversationNotice>>> = mutableServerNotices
    val threadAttention: StateFlow<Map<String, Map<String, ThreadAttention>>> = mutableThreadAttention

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

    suspend fun serverConnectionDetails(serverId: String): ServerConnectionDetails = ServerConnectionDetails(
        profile = awaitServer(serverId),
        token = serverStore.readToken(serverId),
    )

    suspend fun updateServer(serverId: String, name: String, endpoint: String, token: String): ServerProfile {
        val settings = serverStore.settings.first()
        val current = settings.servers.singleOrNull { it.id == serverId } ?: error("服务器不存在")
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) { "请输入服务器名称" }
        require(settings.servers.none { it.id != serverId && it.name == normalizedName }) { "服务器名称已存在" }
        val normalizedEndpoint = validateWebSocketEndpoint(endpoint)
        val normalizedToken = token.trim()
        require(normalizedToken.isNotEmpty()) { "请输入 Token" }

        testConnection(normalizedEndpoint, normalizedToken)
        val profile = current.copy(name = normalizedName, webSocketUrl = normalizedEndpoint)
        serverStore.updateServer(profile, normalizedToken)
        serverSettings.first { stored -> stored.servers.singleOrNull { it.id == serverId } == profile }

        connectionMutex.withLock {
            closeConnectionLocked(serverId)
        }
        mutableConnectionStatuses.updateValue(serverId, ConnectionStatus.Disconnected)
        conversations.entries
            .filter { it.key.serverId == serverId }
            .forEach { (_, conversation) ->
                conversation.updateState { state -> state.copy(serverName = profile.name) }
            }
        applicationScope.launch {
            runSuspendCatching { connect(serverId) }
                .onSuccess { bumpThreadRevision(serverId) }
        }
        return profile
    }

    suspend fun selectServer(serverId: String) {
        serverStore.selectServer(serverId)
        connect(serverId)
    }

    suspend fun removeServer(serverId: String) {
        connectionMutex.withLock {
            closeConnectionLocked(serverId)
        }
        mutableConnectionStatuses.updateValue(serverId, ConnectionStatus.Disconnected)
        conversations.keys.filter { it.serverId == serverId }.forEach { conversations.remove(it) }
        serverStore.removeServer(serverId)
    }

    suspend fun connect(serverId: String) {
        api(serverId)
    }

    suspend fun threadPage(
        serverId: String,
        cursor: String? = null,
        cwd: String? = null,
        archived: Boolean = false,
        searchTerm: String? = null,
        sortKey: ThreadSortKey = ThreadSortKey.RECENCY_AT,
        sortDirection: SortDirection = SortDirection.DESC,
        modelProviders: List<String> = emptyList(),
        sourceKinds: Set<ThreadSourceKind> = emptySet(),
    ): ThreadPage {
        val response = api(serverId).listThreads(
            cursor = cursor,
            cwd = cwd,
            archived = archived,
            searchTerm = searchTerm,
            sortKey = sortKey,
            sortDirection = sortDirection,
            modelProviders = modelProviders,
            sourceKinds = sourceKinds,
        )
        return ThreadPage(
            data = response.requiredArray("data").map { ProtocolMapper.threadSummary(it.jsonObject) },
            nextCursor = response.optionalString("nextCursor"),
        )
    }

    suspend fun startConversation(
        serverId: String,
        cwd: String,
        options: ThreadOptions = ThreadOptions(),
    ): String {
        val normalizedCwd = cwd.trim()
        require(normalizedCwd.isNotEmpty()) { "请输入服务端项目路径" }
        val snapshot = ProtocolMapper.threadSnapshot(api(serverId).startThread(normalizedCwd, options))
        val profile = awaitServer(serverId)
        val state = snapshot.toConversationState(profile)
        conversations[ConversationKey(serverId, snapshot.summary.id)] = MutableStateFlow(state)
        return snapshot.summary.id
    }

    suspend fun openConversation(serverId: String, threadId: String): StateFlow<ConversationState> {
        val key = ConversationKey(serverId, threadId)
        conversations[key]?.let {
            markThreadViewed(serverId, threadId)
            return it
        }
        return conversationMutex.withLock {
            conversations[key]?.let { return@withLock it }
            val snapshot = resumeConversation(key)
            val state = MutableStateFlow(snapshot.toConversationState(awaitServer(serverId)))
            conversations[key] = state
            state
        }.also { markThreadViewed(serverId, threadId) }
    }

    suspend fun sendMessage(
        serverId: String,
        threadId: String,
        input: List<UserInputPart>,
        options: TurnOptions = TurnOptions(),
    ) {
        require(input.isNotEmpty()) { "消息内容不能为空" }
        val text = input.filterIsInstance<UserInputPart.Text>().joinToString("\n") { it.text }.trim()
        require(text.isNotEmpty() || input.any { it !is UserInputPart.Text }) { "消息内容不能为空" }
        val state = requireConversation(serverId, threadId)
        val activeTurnId = state.value.activeTurnId
        val clientId = UUID.randomUUID().toString()
        val messageTitle = input.conversationTitle()
        state.updateState { current ->
            current.copy(
                title = if (current.title == "新会话") messageTitle.take(72) else current.title,
                isRunning = true,
                errorMessage = null,
            )
        }
        setThreadAttention(serverId, threadId, ThreadAttention.RUNNING)

        try {
            val result = if (activeTurnId == null) {
                api(serverId).startTurn(threadId, clientId, input, options)
            } else {
                api(serverId).steerTurn(threadId, activeTurnId, clientId, input)
            }
            val turnId = if (activeTurnId == null) {
                result.requiredObject("turn").requiredString("id")
            } else {
                result.requiredString("turnId")
            }
            state.updateState { current ->
                current.copy(
                    activeTurnId = turnId,
                    isRunning = true,
                )
            }
        } catch (error: Throwable) {
            state.updateState { current ->
                current.copy(
                    isRunning = false,
                    errorMessage = error.toUserFacingError("发送消息失败"),
                )
            }
            setThreadAttention(serverId, threadId, ThreadAttention.FAILED)
            throw error
        }
    }

    suspend fun sendMessage(serverId: String, threadId: String, message: String) =
        sendMessage(serverId, threadId, listOf(UserInputPart.Text(message)))

    suspend fun interrupt(serverId: String, threadId: String) {
        val state = requireConversation(serverId, threadId)
        val turnId = state.value.activeTurnId ?: return
        api(serverId).interruptTurn(threadId, turnId)
    }

    suspend fun forkConversation(serverId: String, threadId: String, lastTurnId: String? = null): String {
        val snapshot = ProtocolMapper.threadSnapshot(api(serverId).forkThread(threadId, lastTurnId))
        conversations[ConversationKey(serverId, snapshot.summary.id)] =
            MutableStateFlow(snapshot.toConversationState(awaitServer(serverId)))
        bumpThreadRevision(serverId)
        return snapshot.summary.id
    }

    suspend fun renameConversation(serverId: String, threadId: String, name: String) {
        val normalized = name.trim()
        require(normalized.isNotEmpty()) { "会话名称不能为空" }
        api(serverId).request(
            StableRpcMethod.THREAD_NAME_SET,
            buildJsonObject {
                put("threadId", threadId)
                put("name", normalized)
            },
        )
        conversations[ConversationKey(serverId, threadId)]?.updateState { it.copy(title = normalized) }
        bumpThreadRevision(serverId)
    }

    suspend fun archiveConversation(serverId: String, threadId: String) {
        api(serverId).request(
            StableRpcMethod.THREAD_ARCHIVE,
            buildJsonObject { put("threadId", threadId) },
        )
        conversations.remove(ConversationKey(serverId, threadId))
        bumpThreadRevision(serverId)
    }

    suspend fun unarchiveConversation(serverId: String, threadId: String) {
        api(serverId).request(
            StableRpcMethod.THREAD_UNARCHIVE,
            buildJsonObject { put("threadId", threadId) },
        )
        bumpThreadRevision(serverId)
    }

    suspend fun deleteConversation(serverId: String, threadId: String) {
        api(serverId).request(
            StableRpcMethod.THREAD_DELETE,
            buildJsonObject { put("threadId", threadId) },
        )
        conversations.remove(ConversationKey(serverId, threadId))
        removePendingRequests(serverId, threadId)
        bumpThreadRevision(serverId)
    }

    suspend fun compactConversation(serverId: String, threadId: String) {
        api(serverId).request(
            StableRpcMethod.THREAD_COMPACT,
            buildJsonObject { put("threadId", threadId) },
        )
    }

    suspend fun reviewConversation(
        serverId: String,
        threadId: String,
        target: ReviewTarget,
        delivery: ReviewDelivery,
    ): String {
        val response = api(serverId).review(threadId, target, delivery)
        return response.requiredString("reviewThreadId")
    }

    suspend fun getGoal(serverId: String, threadId: String): ThreadGoal? {
        val response = api(serverId).request(
            StableRpcMethod.THREAD_GOAL_GET,
            buildJsonObject { put("threadId", threadId) },
        ).jsonObject
        val goal = (response["goal"] as? JsonObject)?.toThreadGoal()
        conversations[ConversationKey(serverId, threadId)]?.updateState {
            it.copy(goal = goal, goalLoaded = true)
        }
        return goal
    }

    suspend fun setGoal(
        serverId: String,
        threadId: String,
        objective: String,
        status: ThreadGoalStatus = ThreadGoalStatus.ACTIVE,
        tokenBudget: Long? = null,
    ): ThreadGoal {
        require(objective.isNotBlank()) { "目标不能为空" }
        val response = api(serverId).request(
            StableRpcMethod.THREAD_GOAL_SET,
            buildJsonObject {
                put("threadId", threadId)
                put("objective", objective.trim())
                put("status", status.wireValue)
                put("tokenBudget", tokenBudget?.let(::JsonPrimitive) ?: JsonNull)
            },
        ).jsonObject
        return response.requiredObject("goal").toThreadGoal().also { goal ->
            conversations[ConversationKey(serverId, threadId)]?.updateState {
                it.copy(goal = goal, goalLoaded = true)
            }
        }
    }

    suspend fun clearGoal(serverId: String, threadId: String) {
        api(serverId).request(
            StableRpcMethod.THREAD_GOAL_CLEAR,
            buildJsonObject { put("threadId", threadId) },
        )
        conversations[ConversationKey(serverId, threadId)]?.updateState {
            it.copy(goal = null, goalLoaded = true)
        }
    }

    fun closeConversation(serverId: String, threadId: String) {
        val key = ConversationKey(serverId, threadId)
        pendingResumeGoalSnapshots.remove(key)
        applicationScope.launch {
            runSuspendCatching {
                api(serverId).request(
                    StableRpcMethod.THREAD_UNSUBSCRIBE,
                    buildJsonObject { put("threadId", threadId) },
                )
            }
            conversations.remove(key)
            removePendingRequests(serverId, threadId)
        }
    }

    fun clearConversationError(serverId: String, threadId: String) {
        conversations[ConversationKey(serverId, threadId)]?.updateState { it.copy(errorMessage = null) }
        if (mutableThreadAttention.value[serverId]?.get(threadId) == ThreadAttention.FAILED) {
            setThreadAttention(serverId, threadId, null)
        }
    }

    fun respondToApproval(
        serverId: String,
        threadId: String,
        requestKey: String,
        decision: ApprovalDecision,
    ) = respondToServerRequest(
        serverId,
        threadId,
        requestKey,
        buildJsonObject { put("decision", decision.wireValue) },
    )

    fun respondToApprovalWithExecPolicy(
        serverId: String,
        threadId: String,
        requestKey: String,
        amendment: JsonElement,
    ) = respondToServerRequest(
        serverId,
        threadId,
        requestKey,
        buildJsonObject {
            put(
                "decision",
                buildJsonObject {
                    put(
                        "acceptWithExecpolicyAmendment",
                        buildJsonObject { put("execpolicy_amendment", amendment) },
                    )
                },
            )
        },
    )

    fun respondToApprovalWithNetworkPolicy(
        serverId: String,
        threadId: String,
        requestKey: String,
        amendment: JsonElement,
    ) = respondToServerRequest(
        serverId,
        threadId,
        requestKey,
        buildJsonObject {
            put(
                "decision",
                buildJsonObject {
                    put(
                        "applyNetworkPolicyAmendment",
                        buildJsonObject { put("network_policy_amendment", amendment) },
                    )
                },
            )
        },
    )

    fun respondToQuestions(
        serverId: String,
        threadId: String,
        requestKey: String,
        answers: Map<String, List<String>>,
    ) = respondToServerRequest(
        serverId,
        threadId,
        requestKey,
        buildJsonObject {
            put(
                "answers",
                buildJsonObject {
                    answers.forEach { (questionId, values) ->
                        put(
                            questionId,
                            buildJsonObject {
                                put("answers", buildJsonArray { values.forEach { add(JsonPrimitive(it)) } })
                            },
                        )
                    }
                },
            )
        },
    )

    fun respondToMcpElicitation(
        serverId: String,
        threadId: String,
        requestKey: String,
        action: String,
        content: JsonElement? = null,
    ) = respondToServerRequest(
        serverId,
        threadId,
        requestKey,
        buildJsonObject {
            put("action", action)
            put("content", content ?: JsonNull)
            put("_meta", JsonNull)
        },
    )

    fun respondToPermissions(
        serverId: String,
        threadId: String,
        requestKey: String,
        permissions: JsonObject,
        sessionScope: Boolean,
        strictAutoReview: Boolean,
    ) = respondToServerRequest(
        serverId,
        threadId,
        requestKey,
        buildJsonObject {
            require(!sessionScope || !strictAutoReview) {
                "严格自动审查只支持当前回合"
            }
            put("permissions", permissions)
            put("scope", if (sessionScope) "session" else "turn")
            if (strictAutoReview) put("strictAutoReview", true)
        },
    )

    suspend fun models(serverId: String): List<ModelOption> = StableFeatureApi(api(serverId)).models()

    suspend fun modelProviderCapabilities(serverId: String): FeatureActionResult =
        StableFeatureApi(api(serverId)).modelProviderCapabilities()

    suspend fun featureCatalog(
        serverId: String,
        area: FeatureArea,
        cwd: String? = null,
        threadId: String? = null,
        forceRefresh: Boolean = false,
    ): FeatureCatalog = StableFeatureApi(api(serverId)).catalog(area, cwd, threadId, forceRefresh)

    suspend fun executeFeatureAction(serverId: String, action: FeatureAction): FeatureActionResult =
        StableFeatureApi(api(serverId)).execute(action)

    suspend fun account(serverId: String): AccountOverview = StableFeatureApi(api(serverId)).account()

    suspend fun rateLimits(serverId: String): AccountRateLimits? = StableFeatureApi(api(serverId)).rateLimits()

    suspend fun accountUsage(serverId: String): AccountUsage? = StableFeatureApi(api(serverId)).accountUsage()

    suspend fun loginWithApiKey(serverId: String, apiKey: String): LoginChallenge =
        StableFeatureApi(api(serverId)).loginWithApiKey(apiKey)

    suspend fun loginWithChatGpt(serverId: String, deviceCode: Boolean): LoginChallenge =
        StableFeatureApi(api(serverId)).loginWithChatGpt(deviceCode)

    suspend fun cancelLogin(serverId: String, loginId: String) =
        StableFeatureApi(api(serverId)).cancelLogin(loginId)

    suspend fun logout(serverId: String) = StableFeatureApi(api(serverId)).logout()

    suspend fun consumeRateLimitReset(serverId: String, creditId: String?): FeatureActionResult =
        StableFeatureApi(api(serverId)).consumeRateLimitReset(creditId)

    suspend fun sendCreditsNudge(serverId: String, creditType: String): FeatureActionResult =
        StableFeatureApi(api(serverId)).sendCreditsNudge(creditType)

    suspend fun configuration(serverId: String, cwd: String?): ServerConfiguration =
        StableFeatureApi(api(serverId)).configuration(cwd)

    private suspend fun api(serverId: String): AppServerApi = connectionMutex.withLock {
        val existing = connections[serverId]
        if (existing?.status?.value is ConnectionStatus.Connected) return@withLock AppServerApi(existing)
        closeConnectionLocked(serverId)

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
            mutableConnectionStatuses.updateValue(
                serverId,
                ConnectionStatus.Failed(error.toUserFacingError("连接服务器失败")),
            )
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

    private fun closeConnectionLocked(serverId: String) {
        connectionObservers.remove(serverId)?.forEach { it.cancel() }
        connections.remove(serverId)?.close()
        pendingResumeGoalSnapshots.removeIf { it.serverId == serverId }
        clearServerPendingRequests(serverId)
    }

    private suspend fun restoreConversations(
        serverId: String,
        server: ServerProfile,
        api: AppServerApi,
    ) {
        conversations.entries
            .filter { it.key.serverId == serverId }
            .forEach { (key, state) ->
                pendingResumeGoalSnapshots.add(key)
                runSuspendCatching { ProtocolMapper.threadSnapshot(api.resumeThread(key.threadId)) }
                    .onSuccess { snapshot ->
                        state.value = snapshot.toConversationState(server)
                    }
                    .onFailure { error ->
                        pendingResumeGoalSnapshots.remove(key)
                        state.updateState {
                            it.copy(isRunning = false, errorMessage = error.toUserFacingError("恢复会话失败"))
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
                            it.copy(errorMessage = error.toUserFacingError("处理服务器事件失败"))
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
        if (request.method !in SUPPORTED_SERVER_REQUESTS) {
            connection.respondError(request.id, METHOD_NOT_FOUND, "客户端不支持服务端请求：${request.method}")
            return
        }
        val threadId = request.params.requiredString("threadId")
        val conversation = conversations[ConversationKey(serverId, threadId)]
        if (conversation == null) {
            connection.respondError(request.id, INVALID_REQUEST, "会话尚未在客户端打开")
            return
        }
        val requestKey = requestKey(serverId, request.id)
        val pending = when (request.method) {
            "item/commandExecution/requestApproval" -> PendingServerRequest.Approval(
                requestKey = requestKey,
                itemId = request.params.requiredString("itemId"),
                turnId = request.params.requiredString("turnId"),
                type = ApprovalType.COMMAND_EXECUTION,
                reason = request.params.optionalString("reason"),
                command = request.params.optionalString("command"),
                cwd = request.params.optionalString("cwd"),
                environmentId = request.params.optionalString("environmentId"),
                networkHost = (request.params["networkApprovalContext"] as? JsonObject)?.optionalString("host"),
                commandActions = ProtocolMapper.commandActions(request.params["commandActions"]),
                grantRoot = null,
                proposedExecPolicy = ProtocolMapper.execPolicyAmendment(
                    request.params["proposedExecpolicyAmendment"],
                ),
                proposedNetworkPolicies = ProtocolMapper.networkPolicyAmendments(
                    request.params["proposedNetworkPolicyAmendments"],
                ),
            )

            "item/fileChange/requestApproval" -> PendingServerRequest.Approval(
                requestKey = requestKey,
                itemId = request.params.requiredString("itemId"),
                turnId = request.params.requiredString("turnId"),
                type = ApprovalType.FILE_CHANGE,
                reason = request.params.optionalString("reason"),
                command = null,
                cwd = null,
                environmentId = null,
                networkHost = null,
                commandActions = emptyList(),
                grantRoot = request.params.optionalString("grantRoot"),
                proposedExecPolicy = null,
                proposedNetworkPolicies = emptyList(),
            )

            "item/tool/requestUserInput" -> PendingServerRequest.Questions(
                requestKey = requestKey,
                itemId = request.params.requiredString("itemId"),
                turnId = request.params.requiredString("turnId"),
                questions = request.params.requiredArray("questions").map { element ->
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
                autoResolutionMillis = request.params.optionalLong("autoResolutionMs"),
                receivedAtMillis = System.currentTimeMillis(),
            )

            "mcpServer/elicitation/request" -> PendingServerRequest.McpElicitation(
                requestKey = requestKey,
                turnId = request.params.optionalString("turnId"),
                serverName = request.params.requiredString("serverName"),
                mode = request.params.requiredString("mode"),
                message = request.params.requiredString("message"),
                url = request.params.optionalString("url"),
                requestedSchema = request.params["requestedSchema"]?.toString(),
            )

            "item/permissions/requestApproval" -> PendingServerRequest.Permissions(
                requestKey = requestKey,
                itemId = request.params.requiredString("itemId"),
                turnId = request.params.requiredString("turnId"),
                environmentId = request.params.optionalString("environmentId"),
                cwd = request.params.requiredString("cwd"),
                reason = request.params.optionalString("reason"),
                requestedPermissions = ProtocolMapper.requestedPermissions(
                    request.params.requiredObject("permissions"),
                ),
            )

            else -> error("支持的服务端请求未实现：${request.method}")
        }
        pendingRequestRoutes[requestKey] = PendingRequestRoute(request.id, threadId)
        conversation.updateState { current ->
            current.copy(pendingRequests = current.pendingRequests + pending)
        }
        setThreadAttention(serverId, threadId, ThreadAttention.NEEDS_INPUT)
        notifyConversation(
            ConversationNotification(
                serverId = serverId,
                threadId = threadId,
                title = conversation.value.title,
                message = pending.notificationMessage(),
                attention = ThreadAttention.NEEDS_INPUT,
            ),
        )
    }

    private fun handleNotification(serverId: String, method: String, params: JsonObject) {
        when (method) {
            "account/updated", "account/rateLimits/updated", "account/login/completed" -> {
                bumpFeatureRevision(serverId)
                bumpRateLimitRevision(serverId)
            }

            "app/list/updated", "skills/changed", "mcpServer/oauthLogin/completed",
            "mcpServer/startupStatus/updated" -> bumpFeatureRevision(serverId)

            "configWarning" -> addServerNotice(
                serverId,
                listOfNotNull(params.optionalString("summary"), params.optionalString("details")).joinToString("\n"),
            )

            "deprecationNotice" -> addServerNotice(
                serverId,
                listOfNotNull(params.optionalString("summary"), params.optionalString("details")).joinToString("\n"),
            )

            "warning" -> {
                val message = params.requiredString("message")
                val targetThreadId = params.optionalString("threadId")
                if (targetThreadId == null) addServerNotice(serverId, message)
                else conversations[ConversationKey(serverId, targetThreadId)]?.addNotice(message)
            }
        }

        val threadId = params.optionalString("threadId")
        if (
            method.startsWith("thread/") && method != "thread/tokenUsage/updated" &&
            method != "thread/goal/updated" && method != "thread/goal/cleared"
        ) {
            bumpThreadRevision(serverId)
        }
        if (threadId == null) return
        val key = ConversationKey(serverId, threadId)
        val state = conversations[key]
        fun updateNotifiedItem(transform: (ConversationItem) -> ConversationItem) {
            state?.updateItem(
                turnId = params.requiredString("turnId"),
                itemId = params.requiredString("itemId"),
                transform = transform,
            )
        }
        when (method) {
            "thread/started" -> bumpThreadRevision(serverId)

            "thread/status/changed" -> state?.updateState { current ->
                val status = params.requiredObject("status").requiredString("type")
                current.copy(isRunning = status == "active")
            }.also {
                val status = params.requiredObject("status").requiredString("type")
                when (status) {
                    "active" -> setThreadAttention(serverId, threadId, ThreadAttention.RUNNING)
                    "systemError" -> setThreadAttention(serverId, threadId, ThreadAttention.FAILED)
                    else -> Unit
                }
            }

            "thread/name/updated" -> state?.updateState { current ->
                current.copy(title = params.optionalString("threadName") ?: current.title)
            }

            "thread/archived", "thread/deleted", "thread/closed" -> {
                removePendingRequests(serverId, threadId)
                state?.updateState { current ->
                    val message = when (method) {
                        "thread/archived" -> "会话已归档"
                        "thread/deleted" -> "会话已删除"
                        else -> "服务端已关闭会话"
                    }
                    current.copy(
                        isRunning = false,
                        notices = (current.notices + ConversationNotice(
                            UUID.randomUUID().toString(),
                            message,
                        )).takeLast(MAX_NOTICES),
                    )
                }
                pendingResumeGoalSnapshots.remove(key)
                conversations.remove(key)
            }

            "thread/unarchived" -> bumpThreadRevision(serverId)

            "thread/tokenUsage/updated" -> state?.updateState {
                it.copy(tokenUsage = ProtocolMapper.tokenUsage(params))
            }

            "thread/goal/updated" -> {
                val goal = params.requiredObject("goal").toThreadGoal()
                val announce = !pendingResumeGoalSnapshots.remove(key)
                state?.updateState { current -> current.withGoal(goal, announce) }
            }

            "thread/goal/cleared" -> {
                val announce = !pendingResumeGoalSnapshots.remove(key)
                state?.updateState { current -> current.withGoal(null, announce) }
            }

            "turn/started" -> {
                if (state == null) return
                val turn = params.requiredObject("turn")
                state.updateState {
                    it.copy(
                        activeTurnId = turn.requiredString("id"),
                        isRunning = true,
                        plan = null,
                        errorMessage = null,
                    )
                }
                setThreadAttention(serverId, threadId, ThreadAttention.RUNNING)
            }

            "turn/completed" -> {
                if (state == null) return
                val turn = params.requiredObject("turn")
                val errorMessage = (turn["error"] as? JsonObject)?.optionalString("message")
                    ?.let { serverError("任务执行失败", it) }
                state.updateState {
                    it.copy(
                        activeTurnId = null,
                        isRunning = false,
                        pendingRequests = emptyList(),
                        errorMessage = errorMessage,
                    )
                }
                val attention = if (errorMessage == null) ThreadAttention.READY else ThreadAttention.FAILED
                setThreadAttention(serverId, threadId, attention)
                notifyConversation(
                    ConversationNotification(
                        serverId = serverId,
                        threadId = threadId,
                        title = state.value.title,
                        message = if (errorMessage == null) "任务已完成，可以查看结果" else errorMessage.summary,
                        attention = attention,
                    ),
                )
                removePendingRequests(serverId, threadId)
                bumpThreadRevision(serverId)
            }

            "turn/plan/updated" -> state?.updateState { it.copy(plan = ProtocolMapper.turnPlan(params)) }

            "error" -> {
                val error = params.requiredObject("error")
                val message = error.requiredString("message")
                state?.addNotice("服务器报告错误", message)
                if (!(params.optionalBoolean("willRetry") ?: false)) {
                    state?.updateState { it.copy(errorMessage = serverError("服务器报告错误", message)) }
                }
            }

            "guardianWarning" -> state?.addNotice(params.requiredString("message"))

            "model/rerouted" -> state?.addNotice(
                "模型已从 ${params.requiredString("fromModel")} 切换为 ${params.requiredString("toModel")}",
            )

            "model/verification" -> state?.addNotice("模型请求需要额外账号验证")

            "model/safetyBuffering/updated" -> if (params.optionalBoolean("showBufferingUi") == true) {
                state?.addNotice("模型正在进行安全检查")
            }

            "hook/started", "hook/completed" -> state?.addNotice(
                if (method == "hook/started") "Hook 开始执行" else "Hook 执行完成",
            )

            "item/autoApprovalReview/started", "item/autoApprovalReview/completed" -> state?.addNotice(
                if (method.endsWith("started")) "自动审批审查开始" else "自动审批审查完成",
            )

            "item/started", "item/completed" -> {
                val itemPayload = params.requiredObject("item")
                if (method == "item/started" && itemPayload.requiredString("type") == "userMessage") {
                    bumpThreadRevision(serverId)
                }
                if (state == null) return
                val item = ProtocolMapper.conversationItem(
                    item = itemPayload,
                    turnId = params.requiredString("turnId"),
                    isCompleted = method == "item/completed",
                )
                state.updateState { current -> current.copy(items = current.items.upsertConversationItem(item)) }
            }

            "item/agentMessage/delta" -> updateNotifiedItem { item ->
                val message = item as? ConversationItem.AgentMessage
                    ?: throw ProtocolException("agentMessage delta 的目标类型不正确")
                message.copy(text = message.text + params.requiredString("delta"))
            }

            "item/reasoning/summaryTextDelta" -> updateNotifiedItem { item ->
                val reasoning = item as? ConversationItem.Reasoning
                    ?: throw ProtocolException("reasoning summary delta 的目标类型不正确")
                reasoning.copy(summary = reasoning.summary + params.requiredString("delta"))
            }

            "item/reasoning/summaryPartAdded" -> updateNotifiedItem { item ->
                val reasoning = item as? ConversationItem.Reasoning
                    ?: throw ProtocolException("reasoning summary part 的目标类型不正确")
                reasoning.copy(summary = reasoning.summary.trimEnd() + "\n")
            }

            "item/reasoning/textDelta" -> updateNotifiedItem { item ->
                val reasoning = item as? ConversationItem.Reasoning
                    ?: throw ProtocolException("reasoning delta 的目标类型不正确")
                reasoning.copy(details = reasoning.details + params.requiredString("delta"))
            }

            "item/commandExecution/outputDelta" -> updateNotifiedItem { item ->
                val command = item as? ConversationItem.CommandExecution
                    ?: throw ProtocolException("command output delta 的目标类型不正确")
                command.copy(output = command.output + params.requiredString("delta"))
            }

            "item/commandExecution/terminalInteraction" -> updateNotifiedItem { item ->
                val command = item as? ConversationItem.CommandExecution
                    ?: throw ProtocolException("terminal interaction 的目标类型不正确")
                command.copy(output = command.output + params.requiredString("stdin"))
            }

            "item/fileChange/patchUpdated" -> updateNotifiedItem { item ->
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

            "item/plan/delta" -> updateNotifiedItem { item ->
                val plan = item as? ConversationItem.Plan
                    ?: throw ProtocolException("plan delta 的目标类型不正确")
                plan.copy(text = plan.text + params.requiredString("delta"))
            }

            "item/mcpToolCall/progress" -> updateNotifiedItem { item ->
                val activity = item as? ConversationItem.Activity
                    ?: throw ProtocolException("MCP progress 的目标类型不正确")
                activity.copy(details = listOf(activity.details, params.requiredString("message"))
                    .filter(String::isNotBlank).joinToString("\n"))
            }

            "serverRequest/resolved" -> {
                val requestId = params["requestId"] as? JsonPrimitive ?: return
                val key = requestKey(serverId, requestId)
                pendingRequestRoutes.remove(key)
                state?.updateState { current ->
                    current.copy(pendingRequests = current.pendingRequests.filterNot { it.requestKey == key })
                }
                state?.value?.let { current ->
                    setThreadAttention(
                        serverId,
                        threadId,
                        if (current.pendingRequests.isNotEmpty()) ThreadAttention.NEEDS_INPUT
                        else if (current.isRunning) ThreadAttention.RUNNING else null,
                    )
                }
            }
        }
    }

    private fun removePendingRequests(serverId: String, threadId: String) {
        pendingRequestRoutes.entries
            .filter { (key, request) -> key.startsWith("$serverId:") && request.threadId == threadId }
            .forEach { pendingRequestRoutes.remove(it.key) }
        conversations[ConversationKey(serverId, threadId)]?.updateState { it.copy(pendingRequests = emptyList()) }
    }

    private fun clearServerPendingRequests(serverId: String) {
        pendingRequestRoutes.keys
            .filter { it.startsWith("$serverId:") }
            .forEach { pendingRequestRoutes.remove(it) }
        conversations.entries
            .filter { it.key.serverId == serverId }
            .forEach { it.value.updateState { state -> state.copy(pendingRequests = emptyList()) } }
        mutableThreadAttention.update { it - serverId }
    }

    private fun requireConversation(serverId: String, threadId: String): MutableStateFlow<ConversationState> =
        conversations[ConversationKey(serverId, threadId)] ?: error("会话尚未打开")

    private suspend fun resumeConversation(key: ConversationKey): ThreadSnapshot {
        pendingResumeGoalSnapshots.add(key)
        return try {
            ProtocolMapper.threadSnapshot(api(key.serverId).resumeThread(key.threadId))
        } catch (error: Throwable) {
            pendingResumeGoalSnapshots.remove(key)
            throw error
        }
    }

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
        model = model,
        serviceTier = serviceTier,
        reasoningEffort = reasoningEffort,
        approvalPolicy = approvalPolicy,
        approvalsReviewer = approvalsReviewer,
        sandbox = sandbox,
    )

    private fun MutableStateFlow<ConversationState>.updateItem(
        turnId: String,
        itemId: String,
        transform: (ConversationItem) -> ConversationItem,
    ) {
        updateState { current ->
            val items = current.items.replaceConversationItem(turnId, itemId, transform)
                ?: throw ProtocolException("流式事件引用了不存在的条目：$turnId/$itemId")
            current.copy(items = items)
        }
    }

    private fun <T> MutableStateFlow<T>.updateState(transform: (T) -> T) {
        update(transform)
    }

    private fun MutableStateFlow<ConversationState>.addNotice(
        message: String,
        technicalDetails: String? = null,
    ) {
        updateState { current ->
            current.copy(
                notices = (current.notices + ConversationNotice(
                    UUID.randomUUID().toString(),
                    message,
                    technicalDetails,
                ))
                    .takeLast(MAX_NOTICES),
            )
        }
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

    private fun respondToServerRequest(
        serverId: String,
        threadId: String,
        key: String,
        result: JsonObject,
    ) {
        val route = pendingRequestRoutes[key] ?: error("服务端请求已失效")
        check(route.threadId == threadId) { "服务端请求不属于当前会话" }
        val connection = connections[serverId] ?: error("服务器未连接")
        connection.respond(route.id, result)
        pendingRequestRoutes.remove(key)
        val conversation = conversations[ConversationKey(serverId, threadId)] ?: return
        conversation.updateState { current ->
            current.copy(pendingRequests = current.pendingRequests.filterNot { it.requestKey == key })
        }
        val current = conversation.value
        setThreadAttention(
            serverId,
            threadId,
            if (current.pendingRequests.isNotEmpty()) ThreadAttention.NEEDS_INPUT
            else if (current.isRunning) ThreadAttention.RUNNING else null,
        )
    }

    private fun List<UserInputPart>.conversationTitle(): String =
        filterIsInstance<UserInputPart.Text>()
            .firstNotNullOfOrNull { text ->
                text.text.lineSequence().firstOrNull(String::isNotBlank)?.trim()
            }
            ?: firstNotNullOfOrNull { input ->
                when (input) {
                    is UserInputPart.Text -> null
                    is UserInputPart.Image, is UserInputPart.LocalImage -> "图片"
                    is UserInputPart.Skill -> "使用 Skill：${input.name}"
                    is UserInputPart.Mention -> "提及：${input.name}"
                }
            }
            ?: "新消息"

    private fun JsonObject.toThreadGoal() = ThreadGoal(
        objective = requiredString("objective"),
        status = ThreadGoalStatus.entries.singleOrNull { it.wireValue == requiredString("status") }
            ?: throw ProtocolException("未知目标状态：${requiredString("status")}"),
        tokenBudget = optionalLong("tokenBudget"),
        tokensUsed = optionalLong("tokensUsed") ?: 0L,
        timeUsedSeconds = optionalLong("timeUsedSeconds") ?: 0L,
    )

    private fun requestKey(serverId: String, id: JsonPrimitive): String = "$serverId:${id}"

    private fun bumpThreadRevision(serverId: String) {
        mutableThreadRevisions.increment(serverId)
    }

    private fun bumpFeatureRevision(serverId: String) {
        mutableFeatureRevisions.increment(serverId)
    }

    private fun bumpRateLimitRevision(serverId: String) {
        mutableRateLimitRevisions.increment(serverId)
    }

    private fun MutableStateFlow<Map<String, Long>>.increment(serverId: String) {
        update { current -> current + (serverId to ((current[serverId] ?: 0L) + 1L)) }
    }

    private fun addServerNotice(serverId: String, message: String) {
        mutableServerNotices.update { current ->
            val notices = current[serverId].orEmpty() +
                ConversationNotice(UUID.randomUUID().toString(), message)
            current + (serverId to notices.takeLast(MAX_NOTICES))
        }
    }

    private fun setThreadAttention(serverId: String, threadId: String, attention: ThreadAttention?) {
        mutableThreadAttention.update { current ->
            val serverAttention = current[serverId].orEmpty()
            val updated = if (attention == null) serverAttention - threadId
            else serverAttention + (threadId to attention)
            if (updated.isEmpty()) current - serverId else current + (serverId to updated)
        }
    }

    private fun markThreadViewed(serverId: String, threadId: String) {
        val attention = mutableThreadAttention.value[serverId]?.get(threadId)
        if (attention == ThreadAttention.READY || attention == ThreadAttention.FAILED) {
            setThreadAttention(serverId, threadId, null)
        }
    }

    private fun PendingServerRequest.notificationMessage(): String = when (this) {
        is PendingServerRequest.Approval -> if (type == ApprovalType.COMMAND_EXECUTION) {
            "命令等待确认"
        } else {
            "文件修改等待确认"
        }

        is PendingServerRequest.Questions -> "Codex 需要你的回答"
        is PendingServerRequest.McpElicitation -> "$serverName 请求确认"
        is PendingServerRequest.Permissions -> "权限请求等待处理"
    }

    private data class ConversationKey(val serverId: String, val threadId: String)

    private data class PendingRequestRoute(val id: JsonPrimitive, val threadId: String)

    private companion object {
        const val METHOD_NOT_FOUND = -32601
        const val INVALID_REQUEST = -32600
        const val SERVER_LOAD_TIMEOUT_MILLIS = 5_000L
        val SUPPORTED_SERVER_REQUESTS = setOf(
            "item/commandExecution/requestApproval",
            "item/fileChange/requestApproval",
            "item/tool/requestUserInput",
            "mcpServer/elicitation/request",
            "item/permissions/requestApproval",
        )
    }
}
