package com.hebo.codex

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.hebo.codex.domain.ConversationNotification
import com.hebo.codex.domain.ThreadAttention

data class ConversationTarget(val serverId: String, val threadId: String)

class ConversationNotificationController(private val context: Context) {
    @Volatile
    private var appInForeground = true

    init {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Codex 任务",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "任务完成、需要输入或执行失败时通知"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun setAppInForeground(value: Boolean) {
        appInForeground = value
    }

    fun notify(event: ConversationNotification) {
        if (appInForeground || !canPostNotifications()) return
        val notificationId = "${event.serverId}:${event.threadId}".hashCode()
        // 异步问题不会阻塞回合，完成通知不能覆盖刚收到的提问提醒
        val notificationTag = if (event.attention == ThreadAttention.NEEDS_INPUT) "questions" else "results"
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_SERVER_ID, event.serverId)
            putExtra(EXTRA_THREAD_ID, event.threadId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(event.title)
            .setContentText(event.message)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(
                if (event.attention == ThreadAttention.NEEDS_INPUT || event.attention == ThreadAttention.FAILED) {
                    NotificationCompat.PRIORITY_HIGH
                } else {
                    NotificationCompat.PRIORITY_DEFAULT
                },
            )
            .build()
        try {
            NotificationManagerCompat.from(context).notify(notificationTag, notificationId, notification)
        } catch (_: SecurityException) {
            // Permission can be revoked between the explicit check and this call
        }
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        const val EXTRA_SERVER_ID = "conversation_server_id"
        const val EXTRA_THREAD_ID = "conversation_thread_id"
        private const val CHANNEL_ID = "codex_tasks"
    }
}
