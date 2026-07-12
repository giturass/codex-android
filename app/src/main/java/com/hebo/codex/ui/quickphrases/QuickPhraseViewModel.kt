package com.hebo.codex.ui.quickphrases

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.hebo.codex.domain.QuickPhrase
import com.hebo.codex.domain.QuickPhraseRepository
import com.hebo.codex.domain.UserFacingError
import com.hebo.codex.util.runSuspendCatching
import com.hebo.codex.util.toUserFacingError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class QuickPhraseUiState(
    val phrases: List<QuickPhrase> = emptyList(),
    val error: UserFacingError? = null,
)

class QuickPhraseViewModel(
    private val repository: QuickPhraseRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(QuickPhraseUiState())
    val state: StateFlow<QuickPhraseUiState> = mutableState

    init {
        viewModelScope.launch {
            repository.phrases
                .catch { error -> showError(error, "无法读取快捷短语") }
                .collect { phrases -> mutableState.update { it.copy(phrases = phrases) } }
        }
    }

    fun add(content: String) {
        mutate("无法添加快捷短语") { repository.add(content) }
    }

    fun update(id: String, content: String) {
        mutate("无法更新快捷短语") { repository.update(id, content) }
    }

    fun delete(id: String) {
        mutate("无法删除快捷短语") { repository.delete(id) }
    }

    fun moveUp(id: String) {
        mutate("无法移动快捷短语") { repository.move(id, -1) }
    }

    fun moveDown(id: String) {
        mutate("无法移动快捷短语") { repository.move(id, 1) }
    }

    fun clearError() {
        mutableState.update { it.copy(error = null) }
    }

    private fun mutate(summary: String, action: suspend () -> Unit) {
        viewModelScope.launch {
            runSuspendCatching(action).onFailure { showError(it, summary) }
        }
    }

    private fun showError(error: Throwable, summary: String) {
        mutableState.update { it.copy(error = error.toUserFacingError(summary)) }
    }

    companion object {
        fun factory(repository: QuickPhraseRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { QuickPhraseViewModel(repository) }
        }
    }
}
