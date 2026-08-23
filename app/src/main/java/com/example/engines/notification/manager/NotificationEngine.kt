package com.example.engines.notification.manager

import android.content.Context
import com.example.engines.notification.NotificationEvent
import com.example.engines.notification.NotificationPreferenceEngine

object NotificationEngine {
    fun notify(context: Context, event: NotificationEvent, title: String, text: String, icon: Int, id: Int) {
        NotificationPreferenceEngine.requestNotification(
            context = context,
            event = event,
            title = title,
            details = text,
            iconResId = icon,
            notificationId = id,
            source = "NotificationEngine"
        )
    }

    fun announce(context: Context, event: NotificationEvent, text: String) {
        NotificationPreferenceEngine.requestAnnouncement(
            context = context,
            event = event,
            text = text
        )
    }
}
