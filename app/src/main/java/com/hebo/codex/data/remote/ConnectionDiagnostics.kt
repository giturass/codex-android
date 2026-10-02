package com.hebo.codex.data.remote

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException

/** 只输出本地生成的诊断字段，异常消息和远端数据不能进入生产日志 */
internal class ConnectionDiagnostics(
    private val writeWarning: (String) -> Unit,
    private val context: () -> String = { "" },
    private val nanoTime: () -> Long = System::nanoTime,
) {
    private val connectionId = nextConnectionId.incrementAndGet()
    private val startedAt = nanoTime()
    private val terminalReported = AtomicBoolean()
    private val lastWarnings = ConcurrentHashMap<String, Long>()

    @Volatile
    private var phase = Phase("created", startedAt)

    @Volatile
    var serverId: String? = null

    @Volatile
    var restoreCompleted = 0

    @Volatile
    var restoreTotal = 0

    fun setPhase(name: String) {
        phase = Phase(name, nanoTime())
    }

    fun failure(reason: String, error: Throwable? = null, fields: String = "") {
        if (error is CancellationException || !terminalReported.compareAndSet(false, true)) return
        emit(reason, error, fields)
    }

    fun warning(
        reason: String,
        error: Throwable? = null,
        fields: String = "",
        rateLimitKey: String = reason,
    ) {
        if (error is CancellationException || terminalReported.get()) return
        val now = nanoTime()
        var shouldReport = false
        val errorKey = generateSequence(error) { it.cause }.take(4).joinToString(">") {
            it.javaClass.name + if (it is RpcException) ":${it.code}" else ""
        }
        lastWarnings.compute("$rateLimitKey:$errorKey") { _, previous ->
            if (previous == null || now - previous >= WARNING_INTERVAL_NANOS) {
                shouldReport = true
                now
            } else {
                previous
            }
        }
        if (shouldReport) emit(reason, error, fields)
    }

    private fun emit(reason: String, error: Throwable?, fields: String) {
        val now = nanoTime()
        val currentPhase = phase
        try {
            writeWarning(buildString {
                append("connection=$connectionId reason=$reason phase=${currentPhase.name}")
                serverId?.let { append(" server=$it") }
                append(" elapsed_ms=${(now - startedAt) / 1_000_000}")
                append(" phase_elapsed_ms=${(now - currentPhase.startedAt) / 1_000_000}")
                append(" restore_completed=$restoreCompleted restore_total=$restoreTotal")
                context().takeIf(String::isNotEmpty)?.let { append(" $it") }
                if (fields.isNotEmpty()) append(" $fields")
                if (error != null) {
                    // Throwable.message、RPC data 和 JSON 解析堆栈可能包含凭据或会话正文
                    val causes = generateSequence(error) { it.cause }.take(4).toList()
                    append(" error_types=${causes.joinToString(">") { it.javaClass.simpleName }}")
                    causes.filterIsInstance<RpcException>().firstOrNull()?.let {
                        append(" rpc_code=${it.code}")
                    }
                }
            })
        } catch (_: Exception) {
            // 日志写入失败不能阻断待处理请求的结束和 Socket 清理
        }
    }

    private data class Phase(val name: String, val startedAt: Long)

    private companion object {
        val nextConnectionId = AtomicLong()
        const val WARNING_INTERVAL_NANOS = 60_000_000_000L
    }
}
