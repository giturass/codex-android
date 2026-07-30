package com.hebo.codex.ui.screens.servers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerEditorUiStateTest {
    @Test
    fun addingServerRequiresToken() {
        val state = ServerEditorUiState(
            isEditing = false,
            name = "Office Server",
            endpoint = "wss://codex.example.com",
        )

        assertFalse(state.canSave)
        assertTrue(state.copy(token = "secret").canSave)
    }

    @Test
    fun editingServerUsesLoadedToken() {
        val state = ServerEditorUiState(
            isEditing = true,
            name = "Office Server",
            endpoint = "wss://codex.example.com",
            isReady = true,
            isLoading = false,
        )

        assertFalse(state.canSave)
        assertTrue(state.copy(token = "stored-secret").canSave)
    }

    @Test
    fun editingServerCannotSaveBeforeProfileLoads() {
        val state = ServerEditorUiState(
            isEditing = true,
            name = "Office Server",
            endpoint = "wss://codex.example.com",
        )

        assertFalse(state.canSave)
    }

    @Test
    fun testingConnectionDoesNotRequireServerName() {
        val state = ServerEditorUiState(
            isEditing = false,
            endpoint = "wss://codex.example.com",
            token = "secret",
        )

        assertTrue(state.canTest)
        assertFalse(state.canSave)
    }

    @Test
    fun testingAndSavingAreMutuallyExclusive() {
        val ready = ServerEditorUiState(
            isEditing = false,
            name = "Office Server",
            endpoint = "wss://codex.example.com",
            token = "secret",
        )

        assertFalse(ready.copy(isTesting = true).canSave)
        assertFalse(ready.copy(isSaving = true).canTest)
    }
}
