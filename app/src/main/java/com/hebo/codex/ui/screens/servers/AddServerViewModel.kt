package com.hebo.codex.ui.screens.servers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.hebo.codex.domain.CodexRepository
import com.hebo.codex.domain.UserFacingError
import com.hebo.codex.util.runSuspendCatching
import com.hebo.codex.util.toUserFacingError
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddServerUiState(
    val isSaving: Boolean = false,
    val errorMessage: UserFacingError? = null,
)

class AddServerViewModel(
    private val repository: CodexRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AddServerUiState())
    private val mutableEvents = Channel<Unit>(Channel.BUFFERED)

    val state: StateFlow<AddServerUiState> = mutableState
    val savedEvents = mutableEvents.receiveAsFlow()

    fun save(name: String, endpoint: String, token: String) {
        if (mutableState.value.isSaving) return
        viewModelScope.launch {
            mutableState.value = AddServerUiState(isSaving = true)
            val result = runSuspendCatching { repository.addServer(name, endpoint, token) }
            val error = result.exceptionOrNull()
            if (error == null) {
                mutableEvents.send(Unit)
            } else {
                mutableState.value = AddServerUiState(errorMessage = error.toUserFacingError("添加服务器失败"))
            }
            mutableState.update { it.copy(isSaving = false) }
        }
    }

    fun clearError() {
        mutableState.update { it.copy(errorMessage = null) }
    }

    companion object {
        fun factory(repository: CodexRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { AddServerViewModel(repository) }
        }
    }
}
