package com.example.engines.notification.modules

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.engines.notification.EventPriority
import com.example.engines.notification.NotificationEventData
import com.example.util.LoggingManager

object NotificationDispatcher {
    private const val TAG = "NPE_Dispatcher"
    private const val CHANNEL_ID = "netra_sentinel_channel"
    private const val CHANNEL_NAME = "Netra Sentinel Alerts"

    fun dispatch(
        context: Context,
        data: NotificationEventData,
        shouldNotify: Boolean,
        shouldAnnounce: Boolean,
        priority: EventPriority
    ) {
        val appContext = context.applicationContext

        // RULE: Logs & History MUST always be recorded regardless of user UI preferences!
        try {
            LoggingManager.logEvent(
                context = appContext,
                category = data.event.name,
                title = data.title,
                details = data.details,
                source = data.source,
                eventType = "NOTIFICATION",
                status = priority.name
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to record mandatory audit log for event ${data.event}", e)
        }

        // Post System Notification if permitted
        if (shouldNotify) {
            try {
                postSystemNotification(appContext, data, priority)
                CooldownManager.recordNotificationSent(data.event)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to post status bar notification for ${data.event}", e)
            }
        } else {
            Log.d(TAG, "Notification skipped for ${data.event} per user pref / cooldown.")
        }

        // Enqueue Voice Announcement if permitted
        if (shouldAnnounce) {
            try {
                AnnouncementQueue.enqueue(appContext, data.event, priority, data.title)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to enqueue voice announcement for ${data.event}", e)
            }
        } else {
            Log.d(TAG, "Announcement skipped for ${data.event} per user pref / cooldown.")
        }
    }

    private fun postSystemNotification(context: Context, data: NotificationEventData, priority: EventPriority) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create Channel if Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = when (priority) {
                EventPriority.EMERGENCY, EventPriority.CRITICAL -> NotificationManager.IMPORTANCE_HIGH
                EventPriority.WARNING -> NotificationManager.IMPORTANCE_DEFAULT
                else -> NotificationManager.IMPORTANCE_LOW
            }
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = "Netra Sentinel System and Battery Notifications"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val compatPriority = when (priority) {
            EventPriority.EMERGENCY, EventPriority.CRITICAL -> NotificationCompat.PRIORITY_MAX
            EventPriority.WARNING -> NotificationCompat.PRIORITY_HIGH
            EventPriority.INFORMATION -> NotificationCompat.PRIORITY_DEFAULT
            EventPriority.BACKGROUND -> NotificationCompat.PRIORITY_LOW
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(data.title)
            .setContentText(data.details)
            .setSmallIcon(data.iconResId)
            .setPriority(compatPriority)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(data.notificationId, notification)
        Log.i(TAG, "Posted system notification [ID: ${data.notificationId}] for event: ${data.event.name}")
    }
}
