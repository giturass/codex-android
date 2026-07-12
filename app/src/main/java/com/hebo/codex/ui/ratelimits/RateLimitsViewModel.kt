package com.hebo.codex.ui.ratelimits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.hebo.codex.domain.AccountRateLimits
import com.hebo.codex.domain.AccountUsage
import com.hebo.codex.domain.CodexRepository
import com.hebo.codex.domain.UserFacingError
import com.hebo.codex.util.runSuspendCatching
import com.hebo.codex.util.toUserFacingError
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

data class RateLimitsUiState(
    val rateLimits: AccountRateLimits? = null,
    val usage: AccountUsage? = null,
    val updatedAt: Long? = null,
    val isRefreshing: Boolean = false,
    val errorMessage: UserFacingError? = null,
    val usageErrorMessage: UserFacingError? = null,
)

class RateLimitsViewModel(
    private val repository: CodexRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow<Map<String, RateLimitsUiState>>(emptyMap())
    private val refreshJobs = mutableMapOf<String, Job>()
    private val refreshGenerations = mutableMapOf<String, Long>()
    private val observedRevisions = mutableMapOf<String, Long>()

    val state: StateFlow<Map<String, RateLimitsUiState>> = mutableState

    init {
        viewModelScope.launch {
            repository.rateLimitRevisions.collect { revisions ->
                revisions.forEach { (serverId, revision) ->
                    val previous = observedRevisions.put(serverId, revision) ?: 0L
                    if (revision > previous && mutableState.value.containsKey(serverId)) {
                        refresh(serverId)
                    }
                }
            }
        }
    }

    fun refresh(serverId: String) {
        refreshJobs.remove(serverId)?.cancel()
        val generation = (refreshGenerations[serverId] ?: 0L) + 1L
        refreshGenerations[serverId] = generation
        mutableState.update { current ->
            current + (serverId to current.getOrDefault(serverId, RateLimitsUiState()).copy(
                isRefreshing = true,
                errorMessage = null,
                usageErrorMessage = null,
            ))
        }
        refreshJobs[serverId] = viewModelScope.launch {
            val rateLimitsResult = async { runSuspendCatching { repository.rateLimits(serverId) } }
            val usageResult = async { runSuspendCatching { repository.accountUsage(serverId) } }
            val rateLimits = rateLimitsResult.await()
            val usage = usageResult.await()
            if (refreshGenerations[serverId] == generation) {
                mutableState.update { current ->
                    val previous = current.getOrDefault(serverId, RateLimitsUiState())
                    current + (serverId to previous.copy(
                        rateLimits = rateLimits.getOrElse { previous.rateLimits },
                        usage = usage.getOrElse { previous.usage },
                        updatedAt = if (rateLimits.isSuccess) Instant.now().epochSecond else previous.updatedAt,
                        isRefreshing = false,
                        errorMessage = rateLimits.exceptionOrNull()?.toUserFacingError("无法刷新额度"),
                        usageErrorMessage = usage.exceptionOrNull()?.toUserFacingError("无法刷新 Token 用量"),
                    ))
                }
            }
            if (refreshGenerations[serverId] == generation) refreshJobs.remove(serverId)
        }
    }

    override fun onCleared() {
        refreshJobs.values.forEach(Job::cancel)
    }

    companion object {
        fun factory(repository: CodexRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { RateLimitsViewModel(repository) }
        }
    }
}
