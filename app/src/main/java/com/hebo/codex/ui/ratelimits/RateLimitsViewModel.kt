package com.hebo.codex.ui.ratelimits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.hebo.codex.domain.AccountRateLimits
import com.hebo.codex.domain.CodexRepository
import com.hebo.codex.domain.UserFacingError
import com.hebo.codex.util.runSuspendCatching
import com.hebo.codex.util.toUserFacingError
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

data class RateLimitsUiState(
    val rateLimits: AccountRateLimits? = null,
    val updatedAt: Long? = null,
    val isRefreshing: Boolean = false,
    val errorMessage: UserFacingError? = null,
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
            ))
        }
        refreshJobs[serverId] = viewModelScope.launch {
            runSuspendCatching { repository.rateLimits(serverId) }
                .onSuccess { rateLimits ->
                    if (refreshGenerations[serverId] == generation) {
                        mutableState.update { current ->
                            current + (serverId to RateLimitsUiState(
                                rateLimits = rateLimits,
                                updatedAt = Instant.now().epochSecond,
                            ))
                        }
                    }
                }
                .onFailure { error ->
                    if (refreshGenerations[serverId] == generation) {
                        mutableState.update { current ->
                            val previous = current.getOrDefault(serverId, RateLimitsUiState())
                            current + (serverId to previous.copy(
                                isRefreshing = false,
                                errorMessage = error.toUserFacingError("无法刷新额度"),
                            ))
                        }
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
