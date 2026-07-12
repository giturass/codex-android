package com.hebo.codex.ui.components

import com.hebo.codex.domain.ConnectionStatus
import com.hebo.codex.domain.UserFacingError
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerSelectorTest {
    @Test
    fun serverSettingsOnlyEnabledWhileConnected() {
        assertTrue(isServerSettingsEnabled(ConnectionStatus.Connected))
        assertFalse(isServerSettingsEnabled(ConnectionStatus.Connecting))
        assertFalse(isServerSettingsEnabled(ConnectionStatus.Disconnected))
        assertFalse(isServerSettingsEnabled(ConnectionStatus.Failed(UserFacingError("连接失败"))))
    }
}
