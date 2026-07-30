package com.hebo.codex.domain

import android.util.Log
import com.hebo.codex.data.local.ServerSettings
import com.hebo.codex.data.local.ServerStore
import com.hebo.codex.data.remote.AppServerApi
import com.hebo.codex.data.remote.AppServerClientFactory
import com.hebo.codex.data.remote.AppServerConnection
import com.hebo.codex.data.remote.ProtocolMapper
import com.hebo.codex.data.remote.ProtocolException
import com.hebo.codex.data.remote.ServerEvent
import com.hebo.codex.data.remote.StableFeatureApi
import com.hebo.codex.data.remote.StableRpcMethod
import com.hebo.codex.data.remote.ThreadSnapshot
import com.hebo.codex.data.remote.optionalString
import com.hebo.codex.data.remote.requiredArray
import com.hebo.codex.data.remote.requiredObject
import com.hebo.codex.data.remote.requiredString
import com.hebo.codex.util.runSuspendCatching
import com.hebo.codex.util.toUserFacingError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class CodexRepository(
    private val serverStore: ServerStore,
    private val clientFactory: AppServerClientFactory,
    private val applicationScope: CoroutineScope,
    private val notifyConversation: (ConversationNotification) -> Unit = {},
) {
    private val conversationMutex = Mutex()
    private val conversations = ConcurrentHashMap<ConversationKey, MutableStateFlow<ConversationState>>()
    private val conversationLeases = ConversationLeaseRegistry<ConversationKey>()
    private val pendingResumeGoalSnapshots = ConcurrentHashMap.newKeySet<ConversationKey>()
    private val pendingItemUpdates = ConcurrentHashMap<ItemRoute, MutableList<PendingItemUpdate>>()
    private val pendingRequestRoutes = ConcurrentHashMap<String, PendingRequestRoute>()
    private val conversationEventBuffers = ConcurrentHashMap<ConversationKey, ConversationEventBuffer>()
    private val historyPagingMutexes = ConcurrentHashMap<ConversationKey, Mutex>()
    private val mutableConnectionStatuses = MutableStateFlow<Map<String, ConnectionStatus>>(emptyMap())
    private val mutableThreadRevisions = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val mutableFeatureRevisions = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val mutableRateLimitRevisions = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val mutableServerNotices = MutableStateFlow<Map<String, List<ConversationNotice>>>(emptyMap())
    private val threadAttentionRevision = AtomicLong()
    private val mutableThreadAttention = MutableStateFlow<Map<String, Map<String, ThreadAttentionState>>>(emptyMap())
    private val connections = ConnectionCoordinator(
        scope = applicationScope,
        isAvailable = { managed ->
            managed.connection.status.value is ConnectionStatus.Connected
        },
        openConnection = ::establishConnection,
        closeConnection = ::closeManagedConnection,
        onConnecting = { serverId ->
            clearConnectionState(serverId)
            mutableConnectionStatuses.updateValue(serverId, ConnectionStatus.Connecting)
        },
        onConnected = { serverId, managed ->
            managed.observers = observeConnection(serverId, managed)
            mutableConnectionStatuses.updateValue(serverId, ConnectionStatus.Connected)
        },
        onFailed = { serverId, error ->
            mutableConnectionStatuses.updateValue(
                serverId,
                ConnectionStatus.Failed(error.toUserFacingError("连接服务器失败")),
            )
        },
        onCancelled = { serverId ->
            mutableConnectionStatuses.updateValue(serverId, ConnectionStatus.Disconnected)
        },
    )

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
    val threadAttention: StateFlow<Map<String, Map<String, ThreadAttentionState>>> = mutableThreadAttention

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

        connections.close(serverId)
        clearConnectionState(serverId)
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
        val previousServerId = serverStore.settings.first().activeServerId
        serverStore.selectServer(serverId)
        if (previousServerId != null && previousServerId != serverId) {
            connections.cancelAttempt(previousServerId)
        }
        connect(serverId)
    }

    suspend fun removeServer(serverId: String) {
        connections.close(serverId)
        clearConnectionState(serverId)
        conversations.keys.filter { it.serverId == serverId }.forEach { conversations.remove(it) }
        pendingItemUpdates.keys.removeIf { it.serverId == serverId }
        conversationEventBuffers.keys.removeIf { it.serverId == serverId }
        historyPagingMutexes.keys.removeIf { it.serverId == serverId }
        serverStore.removeServer(serverId)
    }

    suspend fun connect(serverId: String) {
        api(serverId, retryFailed = true)
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
        val snapshot = ProtocolMapper.threadMetadata(api(serverId).startThread(normalizedCwd, options))
        val profile = awaitServer(serverId)
        val state = snapshot.toConversationState(profile)
        conversations[ConversationKey(serverId, snapshot.summary.id)] = MutableStateFlow(state)
        return snapshot.summary.id
    }

    suspend fun openConversation(serverId: String, threadId: String): StateFlow<ConversationState> {
        val key = ConversationKey(serverId, threadId)
        val conversation = conversationMutex.withLock {
            conversationLeases.acquire(key)
            try {
                val existing = conversations[key]
                if (existing != null) {
                    refreshConversation(key, existing)
                    existing
                } else {
                    val profile = awaitServer(serverId)
                    val created = MutableStateFlow(
                        ConversationState(
                            serverId = profile.id,
                            serverName = profile.name,
                            threadId = threadId,
                            title = "正在加载会话",
                            cwd = "",
                        ),
                    )
                    conversations[key] = created
                    try {
                        refreshConversation(key, created, failOnError = true)
                        created
                    } catch (error: Throwable) {
                        conversations.remove(key, created)
                        throw error
                    }
                }
            } catch (error: Throwable) {
                conversationLeases.release(key)
                throw error
            }
        }
        markThreadViewed(serverId, threadId)
        return conversation
    }

    suspend fun loadOlderConversationTurns(serverId: String, threadId: String): Boolean {
        val key = ConversationKey(serverId, threadId)
        val state = requireConversation(serverId, threadId)
        val pagingMutex = historyPagingMutexes.computeIfAbsent(key) { Mutex() }
        return pagingMutex.withLock {
            val cursor = state.value.history.olderTurnsCursor ?: return@withLock false
            state.updateState { current ->
                current.copy(
                    history = current.history.copy(
                        isLoadingOlderTurns = true,
                        olderTurnsError = null,
                    ),
                )
            }
            try {
                val api = api(serverId)
                val page = ProtocolMapper.threadTurnPage(api.listThreadTurns(threadId, cursor))
                if (page.nextCursor != null && page.nextCursor == cursor) {
                    throw ProtocolException("thread/turns/list 返回了未推进的游标")
                }
                val hydratedTurns = hydrateTurns(api, threadId, page.data).asReversed()
                var inserted = false
                state.updateState { current ->
                    if (current.history.olderTurnsCursor != cursor) return@updateState current
                    val loadedTurnIds = current.history.turns.mapTo(mutableSetOf(), ConversationTurn::id)
                    val olderTurns = hydratedTurns.filter { it.id !in loadedTurnIds }
                    inserted = olderTurns.isNotEmpty()
                    current.copy(
                        history = current.history.copy(
                            turns = olderTurns + current.history.turns,
                            olderTurnsCursor = page.nextCursor,
                            isLoadingOlderTurns = false,
                            olderTurnsError = null,
                        ),
                    )
                }
                inserted
            } catch (error: CancellationException) {
                state.updateState { current ->
                    current.copy(
                        history = current.history.copy(isLoadingOlderTurns = false),
                    )
                }
                throw error
            } catch (error: Throwable) {
                state.updateState { current ->
                    if (current.history.olderTurnsCursor != cursor) {
                        current
                    } else {
                        current.copy(
                            history = current.history.copy(
                                isLoadingOlderTurns = false,
                                olderTurnsError = error.toUserFacingError("无法加载更早内容"),
                            ),
                        )
                    }
                }
                false
            }
        }
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
                current.reduceConversationEvent(ConversationEvent.TurnStarted(turnId))
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

    suspend fun runShellCommand(serverId: String, threadId: String, command: String) {
        val normalized = command.trim()
        require(normalized.isNotEmpty()) { "Shell 命令不能为空" }
        val state = requireConversation(serverId, threadId)
        require(!state.value.isRunning) { "当前任务结束后才能执行 Shell 命令" }
        state.updateState { current -> current.copy(isRunning = true, errorMessage = null) }
        setThreadAttention(serverId, threadId, ThreadAttention.RUNNING)
        try {
            api(serverId).runShellCommand(threadId, normalized)
        } catch (error: Throwable) {
            state.updateState { current ->
                if (current.activeTurnId == null) {
                    current.copy(
                        isRunning = false,
                        errorMessage = error.toUserFacingError("执行 Shell 命令失败"),
                    )
                } else {
                    current
                }
            }
            setThreadAttention(serverId, threadId, ThreadAttention.FAILED)
            throw error
        }
    }

    suspend fun interrupt(serverId: String, threadId: String) {
        val state = requireConversation(serverId, threadId)
        val turnId = state.value.activeTurnId ?: return
        api(serverId).interruptTurn(threadId, turnId)
    }

    suspend fun forkConversation(serverId: String, threadId: String, lastTurnId: String? = null): String {
        val snapshot = ProtocolMapper.threadMetadata(api(serverId).forkThread(threadId, lastTurnId))
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
        val key = ConversationKey(serverId, threadId)
        conversations.remove(key)
        removePendingItemUpdates(key)
        conversationEventBuffers.remove(key)
        historyPagingMutexes.remove(key)
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
        val key = ConversationKey(serverId, threadId)
        conversations.remove(key)
        removePendingItemUpdates(key)
        conversationEventBuffers.remove(key)
        historyPagingMutexes.remove(key)
        removePendingRequests(serverId, threadId)
        bumpThreadRevision(serverId)
    }

    suspend fun compactConversation(serverId: String, threadId: String) {
        api(serverId).request(
            StableRpcMethod.THREAD_COMPACT,
            buildJsonObject { put("threadId", threadId) },
        )
    }

    suspend fun getRemoteFileMetadata(serverId: String, path: String): RemoteFileMetadata =
        api(serverId).getFileMetadata(path)

    suspend fun readRemoteFilePreview(serverId: String, path: String): ByteArray =
        api(serverId).readFilePreview(path)

    suspend fun downloadRemoteFile(serverId: String, path: String, output: OutputStream): Long =
        api(serverId).downloadFile(path, output)

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
        val goal = (response["goal"] as? JsonObject)?.let(ProtocolMapper::threadGoal)
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
        return ProtocolMapper.threadGoal(response.requiredObject("goal")).also { goal ->
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

    fun releaseConversation(serverId: String, threadId: String) {
        val key = ConversationKey(serverId, threadId)
        applicationScope.launch {
            conversationMutex.withLock {
                conversationLeases.release(key)
                releaseConversationIfUnusedLocked(key)
            }
        }
    }

    private fun releaseConversationIfUnused(key: ConversationKey) {
        applicationScope.launch {
            conversationMutex.withLock {
                releaseConversationIfUnusedLocked(key)
            }
        }
    }

    private suspend fun releaseConversationIfUnusedLocked(key: ConversationKey) {
        if (conversationLeases.isHeld(key)) return
        val conversation = conversations[key] ?: return
        if (conversation.value.requiresRuntimeRetention) return
        runSuspendCatching {
            api(key.serverId).request(
                StableRpcMethod.THREAD_UNSUBSCRIBE,
                buildJsonObject { put("threadId", key.threadId) },
            )
        }
        conversations.remove(key, conversation)
        pendingResumeGoalSnapshots.remove(key)
        removePendingItemUpdates(key)
        conversationEventBuffers.remove(key)
        historyPagingMutexes.remove(key)
        removePendingRequests(key.serverId, key.threadId)
    }

    fun clearConversationError(serverId: String, threadId: String) {
        conversations[ConversationKey(serverId, threadId)]?.updateState { it.copy(errorMessage = null) }
        if (mutableThreadAttention.value[serverId]?.get(threadId)?.attention == ThreadAttention.FAILED) {
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

    private suspend fun api(
        serverId: String,
        retryFailed: Boolean = false,
    ): AppServerApi = AppServerApi(connections.connection(serverId, retryFailed).connection)

    private suspend fun establishConnection(serverId: String): ManagedConnection {
        val server = awaitServer(serverId)
        val connection = clientFactory.create(
            endpoint = server.webSocketUrl,
            token = serverStore.readToken(serverId),
            parentContext = applicationScope.coroutineContext,
        )
        try {
            connection.connect()
            val api = AppServerApi(connection)
            restoreConversations(serverId, server, api)
            check(connection.status.value is ConnectionStatus.Connected) { "服务器连接已中断" }
            return ManagedConnection(connection)
        } catch (error: CancellationException) {
            connection.abort()
            throw error
        } catch (error: Throwable) {
            connection.abort()
            throw error
        }
    }

    private suspend fun testConnection(endpoint: String, token: String) {
        val connection = clientFactory.create(endpoint, token, applicationScope.coroutineContext)
        try {
            connection.connect()
            connection.close()
        } catch (error: CancellationException) {
            connection.abort()
            throw error
        } catch (error: Throwable) {
            connection.abort()
            throw error
        }
    }

    private fun closeManagedConnection(managed: ManagedConnection) {
        managed.observers.forEach(Job::cancel)
        managed.connection.close()
    }

    private fun clearConnectionState(serverId: String) {
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
                runSuspendCatching { resumeConversation(key, api) }
                    .onSuccess { snapshot ->
                        state.updateState { current ->
                            current.withRefreshedConversation(snapshot.toConversationState(server))
                        }
                        removeRestoredItemUpdates(key, state.value)
                    }
                    .onFailure { error ->
                        pendingResumeGoalSnapshots.remove(key)
                        state.updateState {
                            it.copy(
                                activeItemKeys = emptySet(),
                                isRunning = false,
                                errorMessage = error.toUserFacingError("恢复会话失败"),
                            )
                        }
                    }
            }
    }

    private fun observeConnection(
        serverId: String,
        managed: ManagedConnection,
    ): List<Job> {
        val connection = managed.connection
        val statusObserver = applicationScope.launch {
            connection.status.collect { status ->
                if (connections.isCurrent(serverId, managed)) {
                    mutableConnectionStatuses.updateValue(serverId, status)
                }
            }
        }
        val eventObserver = applicationScope.launch {
            connection.events.collect { event ->
                if (!connections.isCurrent(serverId, managed)) return@collect
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
        return listOf(statusObserver, eventObserver)
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
        val pending = ProtocolMapper.pendingServerRequest(
            method = request.method,
            params = request.params,
            requestKey = requestKey,
            receivedAtMillis = System.currentTimeMillis(),
        )
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
        val notification = ProtocolMapper.notification(serverId, method, params)
        if (notification.bumpFeatureRevision) bumpFeatureRevision(serverId)
        if (notification.bumpRateLimitRevision) bumpRateLimitRevision(serverId)
        if (notification.bumpThreadRevision) bumpThreadRevision(serverId)
        notification.serverNotice?.let { addServerNotice(serverId, it) }

        val threadId = notification.threadId ?: return
        val event = notification.conversationEvent ?: return
        val key = ConversationKey(serverId, threadId)
        if (tryBufferConversationEvent(key, event)) return
        applyConversationEvent(serverId, threadId, event)
    }

    private fun applyConversationEvent(
        serverId: String,
        threadId: String,
        event: ConversationEvent,
    ) {
        val key = ConversationKey(serverId, threadId)
        val state = conversations[key]
        when (event) {
            is ConversationEvent.StatusChanged -> {
                state?.updateState { current ->
                    current.reduceConversationEvent(event)
                }
                when (event.status) {
                    ConversationRunStatus.ACTIVE ->
                        setThreadAttention(serverId, threadId, ThreadAttention.RUNNING)

                    ConversationRunStatus.SYSTEM_ERROR ->
                        setThreadAttention(serverId, threadId, ThreadAttention.FAILED)

                    ConversationRunStatus.INACTIVE -> Unit
                }
            }

            is ConversationEvent.Closed -> {
                removePendingRequests(serverId, threadId)
                state?.updateState { current ->
                    current.reduceConversationEvent(event)
                }
                pendingResumeGoalSnapshots.remove(key)
                removePendingItemUpdates(key)
                conversationEventBuffers.remove(key)
                historyPagingMutexes.remove(key)
                conversations.remove(key)
            }

            is ConversationEvent.GoalChanged -> {
                val announce = !pendingResumeGoalSnapshots.remove(key)
                state?.updateState { current ->
                    current.reduceConversationEvent(event, announceGoal = announce)
                }
            }

            is ConversationEvent.TurnStarted -> {
                if (state == null) return
                state.updateState { current ->
                    current.reduceConversationEvent(event)
                }
                setThreadAttention(serverId, threadId, ThreadAttention.RUNNING)
            }

            is ConversationEvent.TurnCompleted -> {
                if (state == null) return
                val completedTurnItems = state.value.items.filter { it.turnId == event.turnId }
                val shellItem = completedTurnItems.singleOrNull() as? ConversationItem.UserShellCommand
                val shellFailed = shellItem?.status == ActivityStatus.FAILED ||
                    shellItem?.status == ActivityStatus.DECLINED
                state.updateState { current ->
                    current.reduceConversationEvent(event)
                }
                val completionAttention = if (event.error == null && !shellFailed) {
                    ThreadAttention.READY
                } else {
                    ThreadAttention.FAILED
                }
                setThreadAttention(
                    serverId,
                    threadId,
                    completedThreadAttention(
                        hasError = event.error != null || shellFailed,
                        isViewed = conversationLeases.isHeld(key),
                    ),
                )
                notifyConversation(
                    ConversationNotification(
                        serverId = serverId,
                        threadId = threadId,
                        title = state.value.title,
                        message = when {
                            event.error != null -> event.error.summary
                            shellItem == null -> "任务已完成，可以查看结果"
                            shellFailed -> "Shell 命令执行失败，可以查看输出"
                            else -> "Shell 命令已完成，可以查看输出"
                        },
                        attention = completionAttention,
                    ),
                )
                removePendingRequests(serverId, threadId)
                removePendingItemUpdates(key, event.turnId)
                releaseConversationIfUnused(key)
            }

            is ConversationEvent.ItemUpserted -> {
                if (state == null) return
                state.updateState { current ->
                    current.reduceConversationEvent(event)
                }
                val route = ItemRoute(serverId, threadId, event.item.turnId, event.item.id)
                if (event.isActive) replayPendingItemUpdates(state, route)
                else pendingItemUpdates.remove(route)
            }

            is ConversationEvent.ItemChanged -> {
                if (state == null) return
                val update = PendingItemUpdate(event.method, event.markActive, event.update)
                if (!state.updateItem(event.turnId, event.itemId, event.markActive, event.update::apply)) {
                    val route = ItemRoute(serverId, threadId, event.turnId, event.itemId)
                    var firstPendingUpdate = false
                    pendingItemUpdates.compute(route) { _, current ->
                        firstPendingUpdate = current == null
                        current.orEmpty().toMutableList().apply { add(update) }
                    }
                    if (firstPendingUpdate) {
                        val current = state.value
                        Log.w(
                            LOG_TAG,
                            "暂存缺少基础条目的流式事件：method=${event.method}, " +
                                "turnId=${event.turnId}, itemId=${event.itemId}, " +
                                "activeTurnId=${current.activeTurnId}, itemCount=${current.items.size}",
                        )
                    }
                }
            }

            is ConversationEvent.RequestResolved -> {
                pendingRequestRoutes.remove(event.requestKey)
                state?.updateState { current ->
                    current.reduceConversationEvent(event)
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

            else -> state?.updateState { current ->
                current.reduceConversationEvent(event)
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

    private suspend fun resumeConversation(key: ConversationKey): ThreadSnapshot =
        resumeConversation(key, api(key.serverId))

    private suspend fun resumeConversation(
        key: ConversationKey,
        api: AppServerApi,
    ): ThreadSnapshot {
        pendingResumeGoalSnapshots.add(key)
        return try {
            val snapshot = ProtocolMapper.resumedThreadSnapshot(api.resumeThread(key.threadId))
            snapshot.copy(
                turns = hydrateTurns(api, key.threadId, snapshot.turns).asReversed(),
            )
        } catch (error: Throwable) {
            pendingResumeGoalSnapshots.remove(key)
            throw error
        }
    }

    private suspend fun refreshConversation(
        key: ConversationKey,
        state: MutableStateFlow<ConversationState>,
        failOnError: Boolean = false,
    ) {
        val buffer = beginConversationEventBuffer(key)
        try {
            val snapshot = resumeConversation(key)
            val server = awaitServer(key.serverId)
            completeConversationRefresh(key, state, buffer) { current ->
                current.withRefreshedConversation(snapshot.toConversationState(server))
            }
            removeRestoredItemUpdates(key, state.value)
        } catch (error: CancellationException) {
            completeConversationRefresh(key, state, buffer) { it }
            throw error
        } catch (error: Throwable) {
            completeConversationRefresh(key, state, buffer) { current ->
                current.copy(errorMessage = error.toUserFacingError("刷新会话失败"))
            }
            if (failOnError) throw error
        }
    }

    private suspend fun hydrateTurns(
        api: AppServerApi,
        threadId: String,
        turns: List<ConversationTurn>,
    ): List<ConversationTurn> = coroutineScope {
        val semaphore = Semaphore(TURN_HYDRATION_CONCURRENCY)
        turns.map { turn ->
            async {
                semaphore.withPermit {
                    turn.copy(items = loadAllTurnItems(api, threadId, turn.id))
                }
            }
        }.awaitAll()
    }

    private suspend fun loadAllTurnItems(
        api: AppServerApi,
        threadId: String,
        turnId: String,
    ): List<ConversationItem> {
        val items = mutableListOf<ConversationItem>()
        var cursor: String? = null
        do {
            val page = ProtocolMapper.threadItemPage(
                response = api.listThreadItems(threadId, turnId, cursor),
                expectedTurnId = turnId,
            )
            items += page.data
            if (page.nextCursor != null && page.nextCursor == cursor) {
                throw ProtocolException("thread/items/list 返回了未推进的游标")
            }
            cursor = page.nextCursor
        } while (cursor != null)
        return items
    }

    private fun beginConversationEventBuffer(key: ConversationKey): ConversationEventBuffer {
        val buffer = ConversationEventBuffer()
        check(conversationEventBuffers.putIfAbsent(key, buffer) == null) {
            "会话正在恢复：${key.threadId}"
        }
        return buffer
    }

    private fun completeConversationRefresh(
        key: ConversationKey,
        state: MutableStateFlow<ConversationState>,
        buffer: ConversationEventBuffer,
        transform: (ConversationState) -> ConversationState,
    ) {
        synchronized(buffer) {
            state.updateState(transform)
            buffer.events.forEach { event ->
                applyConversationEvent(key.serverId, key.threadId, event)
            }
            buffer.accepting = false
            conversationEventBuffers.remove(key, buffer)
        }
    }

    private fun tryBufferConversationEvent(
        key: ConversationKey,
        event: ConversationEvent,
    ): Boolean {
        val buffer = conversationEventBuffers[key] ?: return false
        synchronized(buffer) {
            if (!buffer.accepting) return false
            buffer.events += event
            return true
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
    ): ConversationState {
        return ConversationState(
            serverId = profile.id,
            serverName = profile.name,
            threadId = summary.id,
            title = summary.title,
            cwd = summary.cwd,
            history = ConversationHistory(
                turns = turns,
                olderTurnsCursor = olderTurnsCursor,
            ),
            activeTurnId = turns.lastOrNull { it.status == ConversationTurnStatus.IN_PROGRESS }?.id,
            isRunning = summary.status == ThreadRuntimeStatus.ACTIVE,
            model = model,
            serviceTier = serviceTier,
            reasoningEffort = reasoningEffort,
            approvalPolicy = approvalPolicy,
            approvalsReviewer = approvalsReviewer,
            sandbox = sandbox,
        )
    }

    private fun MutableStateFlow<ConversationState>.updateItem(
        turnId: String,
        itemId: String,
        markActive: Boolean = false,
        transform: (ConversationItem) -> ConversationItem,
    ): Boolean {
        var found = false
        updateState { current ->
            val updated = current.withUpdatedConversationItem(turnId, itemId, markActive, transform)
            found = updated != null
            updated ?: current
        }
        return found
    }

    private fun replayPendingItemUpdates(
        state: MutableStateFlow<ConversationState>,
        route: ItemRoute,
    ) {
        pendingItemUpdates.remove(route)?.forEach { update ->
            check(state.updateItem(route.turnId, route.itemId, update.markActive, update.update::apply)) {
                "无法重放流式事件：${update.method} ${route.turnId}/${route.itemId}"
            }
        }
    }

    private fun removeRestoredItemUpdates(key: ConversationKey, state: ConversationState) {
        val restoredKeys = state.items.mapTo(mutableSetOf(), ConversationItem::key)
        pendingItemUpdates.keys.removeIf { route ->
            route.serverId == key.serverId && route.threadId == key.threadId &&
                ConversationItemKey(route.turnId, route.itemId) in restoredKeys
        }
    }

    private fun removePendingItemUpdates(key: ConversationKey, turnId: String? = null) {
        pendingItemUpdates.keys.removeIf { route ->
            route.serverId == key.serverId && route.threadId == key.threadId &&
                (turnId == null || route.turnId == turnId)
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

    private fun respondToServerRequest(
        serverId: String,
        threadId: String,
        key: String,
        result: JsonObject,
    ) {
        val route = pendingRequestRoutes[key] ?: error("服务端请求已失效")
        check(route.threadId == threadId) { "服务端请求不属于当前会话" }
        val connection = connections.current(serverId)?.connection ?: error("服务器未连接")
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
            else serverAttention + (threadId to ThreadAttentionState(attention, threadAttentionRevision.incrementAndGet()))
            if (updated.isEmpty()) current - serverId else current + (serverId to updated)
        }
    }

    private fun markThreadViewed(serverId: String, threadId: String) {
        val attention = mutableThreadAttention.value[serverId]?.get(threadId)?.attention
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

    private data class ItemRoute(
        val serverId: String,
        val threadId: String,
        val turnId: String,
        val itemId: String,
    )

    private data class PendingItemUpdate(
        val method: String,
        val markActive: Boolean,
        val update: ConversationItemUpdate,
    )

    private data class PendingRequestRoute(val id: JsonPrimitive, val threadId: String)

    private class ConversationEventBuffer {
        val events = mutableListOf<ConversationEvent>()
        var accepting = true
    }

    private class ManagedConnection(
        val connection: AppServerConnection,
        var observers: List<Job> = emptyList(),
    )

    private companion object {
        const val LOG_TAG = "CodexRepository"
        const val METHOD_NOT_FOUND = -32601
        const val INVALID_REQUEST = -32600
        const val SERVER_LOAD_TIMEOUT_MILLIS = 5_000L
        const val TURN_HYDRATION_CONCURRENCY = 4
        val SUPPORTED_SERVER_REQUESTS = setOf(
            "item/commandExecution/requestApproval",
            "item/fileChange/requestApproval",
            "item/tool/requestUserInput",
            "mcpServer/elicitation/request",
            "item/permissions/requestApproval",
        )
    }
}

internal fun completedThreadAttention(
    hasError: Boolean,
    isViewed: Boolean,
): ThreadAttention? = if (isViewed) {
    null
} else if (hasError) {
    ThreadAttention.FAILED
} else {
    ThreadAttention.READY
}
