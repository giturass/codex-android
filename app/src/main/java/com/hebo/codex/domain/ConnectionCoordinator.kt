package com.hebo.codex.domain

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class ConnectionCoordinator<K : Any, V : Any>(
    private val scope: CoroutineScope,
    private val isAvailable: (V) -> Boolean,
    private val openConnection: suspend (K) -> V,
    private val closeConnection: (V) -> Unit,
    private val onConnecting: (K) -> Unit = {},
    private val onConnected: (K, V) -> Unit = { _, _ -> },
    private val onFailed: (K, Throwable) -> Unit = { _, _ -> },
    private val onCancelled: (K) -> Unit = {},
) {
    private val attemptScope = CoroutineScope(
        scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]),
    )
    private val entries = ConcurrentHashMap<K, Entry<V>>()

    suspend fun connection(key: K, retryFailed: Boolean = false): V {
        val entry = entries.computeIfAbsent(key) { Entry() }
        var current: V? = null
        var obsolete: V? = null
        var cachedFailure: Throwable? = null
        val attempt = entry.mutex.withLock {
            entry.connection?.let { connection ->
                if (isAvailable(connection)) {
                    current = connection
                    return@withLock null
                }
                entry.connection = null
                obsolete = connection
            }
            entry.attempt?.let { return@withLock it }
            if (!retryFailed) {
                entry.failure?.let {
                    cachedFailure = it
                    return@withLock null
                }
            }

            entry.failure = null
            val generation = ++entry.generation
            onConnecting(key)
            attemptScope.async(start = CoroutineStart.LAZY) {
                runAttempt(key, entry, generation)
            }.also {
                entry.attempt = it
                it.start()
            }
        }

        obsolete?.let(closeConnection)
        current?.let { return it }
        cachedFailure?.let { throw it }
        return requireNotNull(attempt).await()
    }

    suspend fun cancelAttempt(key: K) {
        val entry = entries[key] ?: return
        val attempt = entry.mutex.withLock {
            val currentAttempt = entry.attempt ?: return@withLock null
            ++entry.generation
            entry.attempt = null
            entry.failure = null
            onCancelled(key)
            currentAttempt
        }
        attempt?.cancel()
    }

    suspend fun close(key: K) {
        val entry = entries[key] ?: return
        var connection: V? = null
        val attempt = entry.mutex.withLock {
            ++entry.generation
            connection = entry.connection
            entry.connection = null
            entry.failure = null
            val currentAttempt = entry.attempt
            entry.attempt = null
            onCancelled(key)
            currentAttempt
        }
        attempt?.cancel()
        connection?.let(closeConnection)
    }

    fun current(key: K): V? = entries[key]?.connection

    suspend fun isCurrent(key: K, connection: V): Boolean {
        val entry = entries[key] ?: return false
        return entry.mutex.withLock { entry.connection === connection }
    }

    private suspend fun runAttempt(
        key: K,
        entry: Entry<V>,
        generation: Long,
    ): V {
        var candidate: V? = null
        try {
            val connection = openConnection(key)
            candidate = connection
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            val accepted = entry.mutex.withLock {
                if (entry.generation != generation) {
                    false
                } else {
                    entry.connection = connection
                    entry.failure = null
                    entry.attempt = null
                    onConnected(key, connection)
                    true
                }
            }
            if (!accepted) throw CancellationException("连接尝试已失效")
            return connection
        } catch (error: CancellationException) {
            candidate?.let(closeConnection)
            withContext(NonCancellable) {
                entry.mutex.withLock {
                    if (entry.generation == generation) {
                        if (entry.connection === candidate) entry.connection = null
                        entry.attempt = null
                        entry.failure = null
                        onCancelled(key)
                    }
                }
            }
            throw error
        } catch (error: Throwable) {
            candidate?.let(closeConnection)
            withContext(NonCancellable) {
                entry.mutex.withLock {
                    if (entry.generation == generation) {
                        if (entry.connection === candidate) entry.connection = null
                        entry.attempt = null
                        entry.failure = error
                        onFailed(key, error)
                    }
                }
            }
            throw error
        }
    }

    private class Entry<V : Any> {
        val mutex = Mutex()
        var generation = 0L

        @Volatile
        var connection: V? = null

        var attempt: Deferred<V>? = null
        var failure: Throwable? = null
    }
}
