package com.hebo.codex.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.hebo.codex.domain.ApprovalDecision
import com.hebo.codex.domain.CodexRepository
import com.hebo.codex.domain.ConversationState
import com.hebo.codex.util.runSuspendCatching
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val conversation: ConversationState? = null,
    val isLoading: Boolean = true,
    val screenError: String? = null,
)

class ChatViewModel(
    private val repository: CodexRepository,
    private val serverId: String,
    private val threadId: String,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = mutableState

    init {
        viewModelScope.launch {
            try {
                repository.openConversation(serverId, threadId).collectLatest { value ->
                    mutableState.value = ChatUiState(conversation = value, isLoading = false)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                mutableState.value = ChatUiState(
                    isLoading = false,
                    screenError = error.message ?: "无法打开会话",
                )
            }
        }
    }

    fun send(message: String) {
        viewModelScope.launch {
            runSuspendCatching { repository.sendMessage(serverId, threadId, message) }
                .onFailure(::showError)
        }
    }

    fun interrupt() {
        viewModelScope.launch {
            runSuspendCatching { repository.interrupt(serverId, threadId) }
                .onFailure(::showError)
        }
    }

    fun respondToApproval(requestKey: String, decision: ApprovalDecision) {
        runCatching {
            repository.respondToApproval(serverId, threadId, requestKey, decision)
        }.onFailure(::showError)
    }

    fun clearError() {
        repository.clearConversationError(serverId, threadId)
        mutableState.update { it.copy(screenError = null) }
    }

    private fun showError(error: Throwable) {
        mutableState.update { it.copy(screenError = error.message ?: "操作失败") }
    }

    companion object {
        fun factory(
            repository: CodexRepository,
            serverId: String,
            threadId: String,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { ChatViewModel(repository, serverId, threadId) }
        }
    }
}
