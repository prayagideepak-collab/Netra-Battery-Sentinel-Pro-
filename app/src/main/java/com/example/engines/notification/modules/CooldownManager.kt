package com.example.engines.notification.modules

import com.example.engines.notification.EventPriority
import com.example.engines.notification.NotificationEvent
import java.util.concurrent.ConcurrentHashMap

object CooldownManager {
    private val lastNotificationTimes = ConcurrentHashMap<NotificationEvent, Long>()
    private val lastAnnouncementTimes = ConcurrentHashMap<NotificationEvent, Long>()

    fun getCooldownDurationMs(event: NotificationEvent, priority: EventPriority): Long {
        return when (priority) {
            EventPriority.EMERGENCY -> 0L // No cooldown for safety emergency
            EventPriority.CRITICAL -> 60_000L // 1 minute
            EventPriority.WARNING -> 3 * 60_000L // 3 minutes
            EventPriority.INFORMATION -> 15_000L // 15 seconds
            EventPriority.BACKGROUND -> 5 * 60_000L // 5 minutes
        }
    }

    fun isNotificationInCooldown(event: NotificationEvent, priority: EventPriority): Boolean {
        if (priority == EventPriority.EMERGENCY) return false
        val now = System.currentTimeMillis()
        val last = lastNotificationTimes[event] ?: 0L
        val cd = getCooldownDurationMs(event, priority)
        return (now - last) < cd
    }

    fun isAnnouncementInCooldown(event: NotificationEvent, priority: EventPriority): Boolean {
        if (priority == EventPriority.EMERGENCY) return false
        val now = System.currentTimeMillis()
        val last = lastAnnouncementTimes[event] ?: 0L
        val cd = getCooldownDurationMs(event, priority)
        return (now - last) < cd
    }

    fun recordNotificationSent(event: NotificationEvent) {
        lastNotificationTimes[event] = System.currentTimeMillis()
    }

    fun recordAnnouncementSent(event: NotificationEvent) {
        lastAnnouncementTimes[event] = System.currentTimeMillis()
    }

    fun resetCooldown(event: NotificationEvent) {
        lastNotificationTimes.remove(event)
        lastAnnouncementTimes.remove(event)
    }
}
