package com.example.engines.notification

import android.content.Context
import android.util.Log
import com.example.engines.coordinator.Engine
import com.example.engines.notification.modules.*

/**
 * NotificationPreferenceEngine (NPE)
 * Single Source of Truth (SSOT) for all notification and voice announcement decisions.
 */
object NotificationPreferenceEngine : Engine {
    private const val TAG = "NotificationPreferenceEngine"

    override val name = "NotificationPreferenceEngine"
    override val priority = 50 // Medium priority

    private var isInitialized = false

    override fun initialize(context: Context) {
        try {
            Log.i(TAG, "Initializing NotificationPreferenceEngine...")
            val appContext = context.applicationContext
            PreferenceManager.initialize(appContext)
            AnnouncementQueue.initialize(appContext)
            isInitialized = true
            Log.i(TAG, "NotificationPreferenceEngine successfully initialized.")
        } catch (e: Exception) {
            Log.e(TAG, "Fault in NPE initialization", e)
        }
    }

    override fun shutdown() {
        try {
            Log.i(TAG, "Shutting down NotificationPreferenceEngine...")
            AnnouncementQueue.shutdown()
            isInitialized = false
        } catch (e: Exception) {
            Log.e(TAG, "Fault in NPE shutdown", e)
        }
    }

    override fun getStatus(): String {
        return try {
            "Active (Managed Events: ${PreferenceManager.getAllPreferences().size})"
        } catch (e: Exception) {
            "Error retrieving status: ${e.message}"
        }
    }

    /**
     * Primary entry point to request a system notification + announcement.
     */
    fun requestNotification(
        context: Context,
        event: NotificationEvent,
        title: String,
        details: String,
        iconResId: Int = android.R.drawable.ic_dialog_info,
        notificationId: Int = System.currentTimeMillis().toInt(),
        source: String = "NPE",
        overridePriority: EventPriority? = null
    ) {
        try {
            val eventData = NotificationEventData(
                event = event,
                title = title,
                details = details,
                iconResId = iconResId,
                notificationId = notificationId,
                source = source,
                overridePriority = overridePriority
            )
            processEvent(context, eventData)
        } catch (e: Exception) {
            Log.e(TAG, "Fault in requestNotification for event: $event", e)
        }
    }

    /**
     * Entry point to request a standalone voice announcement.
     */
    fun requestAnnouncement(context: Context, event: NotificationEvent, text: String) {
        try {
            requestNotification(
                context = context,
                event = event,
                title = event.name,
                details = text,
                source = "VoiceEngine"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Fault in requestAnnouncement for event: $event", e)
        }
    }

    /**
     * Process an event through the Phase 5 Intelligent Event Processing & Decision Engine (IEPDE) pipeline.
     */
    fun processEvent(context: Context, data: NotificationEventData) {
        try {
            val appContext = context.applicationContext
            com.example.engines.iepde.IntelligentEventProcessingEngine.postNotificationEvent(
                context = appContext,
                notificationEvent = data.event,
                title = data.title,
                details = data.details,
                source = data.source,
                overridePriority = data.overridePriority
            )
        } catch (e: Exception) {
            Log.e(TAG, "Fault in processEvent for ${data.event}", e)
        }
    }

    // --- Query & Public API Methods ---

    fun isNotificationEnabled(event: NotificationEvent): Boolean {
        return try {
            val pref = PreferenceManager.getPreference(event)
            pref?.notificationEnabled ?: true
        } catch (e: Exception) {
            true
        }
    }

    fun isAnnouncementEnabled(event: NotificationEvent): Boolean {
        return try {
            val pref = PreferenceManager.getPreference(event)
            pref?.announcementEnabled ?: true
        } catch (e: Exception) {
            true
        }
    }

    fun isAllowed(context: Context, event: NotificationEvent, isAnnouncement: Boolean = false): Boolean {
        return try {
            val priority = PriorityManager.getEventPriority(event)
            if (SafetyOverrideManager.isSafetyOverride(event)) return true

            val pref = PreferenceManager.getPreference(event)
            val prefEnabled = if (isAnnouncement) pref?.announcementEnabled ?: true else pref?.notificationEnabled ?: true
            val supported = CapabilityDetector.isCapabilitySupported(context, event)
            val permitted = PermissionManager.isPermissionGrantedForEvent(context, event)
            val inCooldown = if (isAnnouncement) CooldownManager.isAnnouncementInCooldown(event, priority) else CooldownManager.isNotificationInCooldown(event, priority)

            prefEnabled && supported && permitted && !inCooldown
        } catch (e: Exception) {
            Log.e(TAG, "Fault in isAllowed check", e)
            false
        }
    }

    fun isCritical(event: NotificationEvent): Boolean {
        return try {
            SafetyOverrideManager.isSafetyOverride(event)
        } catch (e: Exception) {
            false
        }
    }

    fun getPriority(event: NotificationEvent, overridePriority: EventPriority? = null): EventPriority {
        return try {
            PriorityManager.getEventPriority(event, overridePriority)
        } catch (e: Exception) {
            EventPriority.INFORMATION
        }
    }

    fun isCooldownActive(event: NotificationEvent, isAnnouncement: Boolean = false): Boolean {
        return try {
            val priority = PriorityManager.getEventPriority(event)
            if (isAnnouncement) CooldownManager.isAnnouncementInCooldown(event, priority)
            else CooldownManager.isNotificationInCooldown(event, priority)
        } catch (e: Exception) {
            false
        }
    }

    fun getPermissionStatus(context: Context, event: NotificationEvent): Boolean {
        return try {
            PermissionManager.isPermissionGrantedForEvent(context, event)
        } catch (e: Exception) {
            false
        }
    }

    fun getCapabilityStatus(context: Context, event: NotificationEvent): Boolean {
        return try {
            CapabilityDetector.isCapabilitySupported(context, event)
        } catch (e: Exception) {
            false
        }
    }

    fun updatePreference(event: NotificationEvent, notifEnabled: Boolean, annEnabled: Boolean): Boolean {
        return try {
            PreferenceManager.updatePreference(event, notifEnabled, annEnabled)
        } catch (e: Exception) {
            Log.e(TAG, "Fault updating preference for $event", e)
            false
        }
    }

    fun restoreDefaults() {
        try {
            PreferenceManager.restoreDefaults()
        } catch (e: Exception) {
            Log.e(TAG, "Fault restoring defaults", e)
        }
    }

    fun getAllPreferences(): List<NotificationPreference> {
        return try {
            PreferenceManager.getAllPreferences()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
