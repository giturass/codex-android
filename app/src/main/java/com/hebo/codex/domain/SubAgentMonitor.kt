package com.hebo.codex.domain

import com.hebo.codex.util.runSuspendCatching
import com.hebo.codex.util.toUserFacingError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class SubAgentRuntimeStatus {
    WAITING_APPROVAL, WAITING_INPUT, ERRORED, RUNNING, IDLE, COMPLETED, INTERRUPTED, NOT_LOADED,
}

data class SubAgentSummary(
    val threadId: String,
    val name: String,
    val role: String?,
    val status: SubAgentRuntimeStatus,
)

data class SubAgentsState(
    val agents: List<SubAgentSummary> = emptyList(),
    val error: UserFacingError? = null,
    val isStale: Boolean = true,
)

internal sealed interface SubAgentEvent {
    data class Started(val thread: ThreadSummary) : SubAgentEvent
    data class StatusChanged(
        val threadId: String,
        val status: ThreadRuntimeStatus,
        val flags: Set<ThreadActiveFlag>,
    ) : SubAgentEvent
    data class Removed(val threadId: String, val deleted: Boolean) : SubAgentEvent
    data class Discover(val threadId: String) : SubAgentEvent
}

internal interface SubAgentSource {
    suspend fun listDescendants(): List<ThreadSummary>
    suspend fun latestTurnStatus(threadId: String): ConversationTurnStatus?
}

internal fun ThreadSummary.subAgentSummary(lastTurnStatus: ConversationTurnStatus?): SubAgentSummary =
    SubAgentSummary(
        threadId = id,
        // 路径与昵称是可选的显示元数据，线程 ID 是协议保证存在的身份
        name = agentPath ?: agentNickname ?: id,
        role = agentRole,
        status = when (status) {
            ThreadRuntimeStatus.ACTIVE -> when {
                ThreadActiveFlag.WAITING_APPROVAL in activeFlags -> SubAgentRuntimeStatus.WAITING_APPROVAL
                ThreadActiveFlag.WAITING_INPUT in activeFlags -> SubAgentRuntimeStatus.WAITING_INPUT
                else -> SubAgentRuntimeStatus.RUNNING
            }
            ThreadRuntimeStatus.SYSTEM_ERROR -> SubAgentRuntimeStatus.ERRORED
            ThreadRuntimeStatus.NOT_LOADED -> SubAgentRuntimeStatus.NOT_LOADED
            ThreadRuntimeStatus.IDLE -> when (lastTurnStatus) {
                ConversationTurnStatus.COMPLETED -> SubAgentRuntimeStatus.COMPLETED
                ConversationTurnStatus.INTERRUPTED -> SubAgentRuntimeStatus.INTERRUPTED
                ConversationTurnStatus.FAILED -> SubAgentRuntimeStatus.ERRORED
                ConversationTurnStatus.IN_PROGRESS, null -> SubAgentRuntimeStatus.IDLE
            }
        },
    )

/** 所有状态变更由同一邮箱串行处理，网络请求并发执行且不阻塞实时通知 */
internal class SubAgentMonitor(
    parentScope: CoroutineScope,
    private val parentThreadId: String,
    private val source: SubAgentSource,
) {
    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]))
    private val messages = Channel<Message>(Channel.UNLIMITED)
    private val mutableState = MutableStateFlow(SubAgentsState())
    val state: StateFlow<SubAgentsState> = mutableState
    private val agents = linkedMapOf<String, ObservedAgent>()
    private val errors = mutableMapOf<String, UserFacingError>()
    private val discovered = mutableSetOf<String>()
    private var connected = false
    private var snapshotCurrent = false
    private var deleted = false
    private var generation = 0L
    private var refreshJob: Job? = null
    private var refreshAgain = false
    private val bufferedEvents = mutableListOf<SubAgentEvent>()
    private val turnJobs = mutableMapOf<String, Job>()

    init {
        scope.launch {
            for (message in messages) {
                when (message) {
                    is Message.Connection -> {
                        connected = message.connected
                        snapshotCurrent = false
                        generation++
                        refreshJob?.cancel()
                        refreshJob = null
                        turnJobs.values.forEach(Job::cancel)
                        turnJobs.clear()
                        bufferedEvents.clear()
                        discovered.clear()
                        refreshAgain = false
                        // 临时会话不在 thread/list 中，断连后无法确认其状态
                        if (!connected) agents.values.filter { it.thread.ephemeral }
                            .map { it.thread.id }.forEach(::remove)
                        if (connected && !deleted) refreshSnapshot()
                    }
                    Message.Refresh -> if (connected && !deleted) refreshSnapshot()
                    is Message.Event -> if (connected && !deleted) {
                        if (refreshJob != null) bufferedEvents += message.event
                        apply(message.event, discover = true)
                    }
                    is Message.Snapshot -> if (message.generation == generation && !deleted) {
                        refreshJob = null
                        message.result.fold(
                            onSuccess = { snapshot ->
                                snapshotCurrent = true
                                val ids = snapshot.mapTo(mutableSetOf(), ThreadSummary::id)
                                agents.values.filter { it.thread.id !in ids && !it.thread.ephemeral }
                                    .map { it.thread.id }.forEach(::remove)
                                snapshot.forEach(::upsert)
                                errors.remove(parentThreadId)
                                // 查询期间已应用的通知再次重放，防止旧快照覆盖新状态
                                bufferedEvents.forEach { apply(it, discover = false) }
                            },
                            onFailure = { errors[parentThreadId] = it.toUserFacingError("无法同步子 Agent 状态") },
                        )
                        bufferedEvents.clear()
                        if (refreshAgain) {
                            refreshAgain = false
                            refreshSnapshot()
                        }
                    }
                    is Message.LastTurn -> if (message.generation == generation && agents[message.id] === message.agent) {
                        turnJobs.remove(message.id)
                        message.result.fold(
                            onSuccess = { status ->
                                agents[message.id] = message.agent.copy(lastTurnStatus = status)
                                errors.remove(message.id)
                            },
                            onFailure = { errors[message.id] = it.toUserFacingError("无法同步部分子 Agent 状态") },
                        )
                    }
                }
                mutableState.value = SubAgentsState(
                    agents = agents.values.map { it.thread.subAgentSummary(it.lastTurnStatus) },
                    error = errors.values.firstOrNull(),
                    isStale = !connected || !snapshotCurrent,
                )
            }
        }
    }

    fun setConnected(connected: Boolean) { messages.trySend(Message.Connection(connected)) }
    fun accept(event: SubAgentEvent) { messages.trySend(Message.Event(event)) }
    fun refresh() { messages.trySend(Message.Refresh) }
    fun close() {
        messages.close()
        scope.cancel()
    }

    private fun refreshSnapshot() {
        if (refreshJob != null) {
            refreshAgain = true
            return
        }
        bufferedEvents.clear()
        val revision = generation
        refreshJob = scope.launch {
            messages.send(Message.Snapshot(revision, runSuspendCatching { source.listDescendants() }))
        }
    }

    private fun apply(event: SubAgentEvent, discover: Boolean) {
        when (event) {
            is SubAgentEvent.Started -> {
                val thread = event.thread
                if (thread.parentThreadId == parentThreadId || thread.parentThreadId in agents) upsert(thread)
                if (discover && thread.parentThreadId != null) refreshSnapshot()
            }
            is SubAgentEvent.StatusChanged -> {
                val agent = agents[event.threadId]
                if (agent != null) {
                    upsert(agent.thread.copy(status = event.status, activeFlags = event.flags))
                } else if (discover && event.status == ThreadRuntimeStatus.ACTIVE &&
                    event.threadId != parentThreadId && discovered.add(event.threadId)
                ) {
                    refreshSnapshot()
                }
            }
            is SubAgentEvent.Removed -> if (event.threadId == parentThreadId && event.deleted) {
                deleted = true
                agents.keys.toList().forEach(::remove)
                errors.clear()
                refreshJob?.cancel()
                refreshJob = null
            } else if (event.deleted) {
                remove(event.threadId)
            } else {
                agents[event.threadId]?.let {
                    upsert(it.thread.copy(status = ThreadRuntimeStatus.NOT_LOADED, activeFlags = emptySet()))
                }
            }
            is SubAgentEvent.Discover -> if (discover && (event.threadId == parentThreadId || event.threadId in agents)) {
                refreshSnapshot()
            }
        }
    }

    private fun upsert(thread: ThreadSummary) {
        val previous = agents[thread.id]
        val agent = ObservedAgent(
            thread,
            lastTurnStatus = previous?.lastTurnStatus?.takeIf {
                thread.status == ThreadRuntimeStatus.IDLE && previous.thread.status == ThreadRuntimeStatus.IDLE
            },
        )
        agents[thread.id] = agent
        errors.remove(thread.id)
        turnJobs.remove(thread.id)?.cancel()
        // 临时会话不支持分页历史，idle 本身不代表任务已完成
        if (thread.status == ThreadRuntimeStatus.IDLE && !thread.ephemeral) {
            val revision = generation
            turnJobs[thread.id] = scope.launch {
                messages.send(Message.LastTurn(
                    revision, thread.id, agent, runSuspendCatching { source.latestTurnStatus(thread.id) },
                ))
            }
        }
    }

    private fun remove(id: String) {
        agents.remove(id)
        errors.remove(id)
        turnJobs.remove(id)?.cancel()
    }

    private data class ObservedAgent(val thread: ThreadSummary, val lastTurnStatus: ConversationTurnStatus? = null)
    private sealed interface Message {
        data class Connection(val connected: Boolean) : Message
        data object Refresh : Message
        data class Event(val event: SubAgentEvent) : Message
        data class Snapshot(val generation: Long, val result: Result<List<ThreadSummary>>) : Message
        data class LastTurn(
            val generation: Long,
            val id: String,
            val agent: ObservedAgent,
            val result: Result<ConversationTurnStatus?>,
        ) : Message
    }
}
