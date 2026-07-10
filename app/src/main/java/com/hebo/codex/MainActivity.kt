package com.hebo.codex

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import com.hebo.codex.ui.CodexApp
import com.hebo.codex.ui.theme.CodexTheme
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

class MainActivity : ComponentActivity() {
    private val notificationTargets = Channel<ConversationTarget>(Channel.BUFFERED)
    private val notificationTargetFlow = notificationTargets.receiveAsFlow()
    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        intent.conversationTarget()?.let(notificationTargets::trySend)
        val container = (application as CodexApplication).container
        setContent {
            CodexTheme {
                CodexApp(container.repository, notificationTargets = notificationTargetFlow)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.conversationTarget()?.let(notificationTargets::trySend)
    }

    override fun onStart() {
        super.onStart()
        (application as CodexApplication).container.notifications.setAppInForeground(true)
    }

    override fun onStop() {
        (application as CodexApplication).container.notifications.setAppInForeground(false)
        super.onStop()
    }
}

private fun Intent.conversationTarget(): ConversationTarget? {
    val serverId = getStringExtra(ConversationNotificationController.EXTRA_SERVER_ID) ?: return null
    val threadId = getStringExtra(ConversationNotificationController.EXTRA_THREAD_ID) ?: return null
    return ConversationTarget(serverId, threadId)
}
