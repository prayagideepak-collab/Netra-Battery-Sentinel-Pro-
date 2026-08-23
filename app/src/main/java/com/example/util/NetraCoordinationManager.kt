package com.example.util

import android.content.Context
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicLong

/**
 * Netra Coordination Manager (NCS) implementing Smart Announcement Arbitration,
 * Shared Event IDs, 5-second Announcement Locks, Primary Ownership rules,
 * and Cross-App Sync state for Netra Battery Sentinel & Netra Sensor Hub.
 */
object NetraCoordinationManager {
    private val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
    private val counter = AtomicLong(1000)
    
    // Announcement lock timestamp
    private var lastAnnouncementTime = 0L
    private const val ANNOUNCEMENT_LOCK_DURATION_MS = 5000L // 5 seconds lock

    /**
     * Generates a standardized shared event ID (e.g. DRV-20260728-00154)
     */
    fun generateEventId(category: String): String {
        val dateStr = dateFormat.format(Date())
        val seq = counter.incrementAndGet()
        val prefix = category.take(3).uppercase()
        return "$prefix-$dateStr-$seq"
    }

    /**
     * Checks whether an announcement can be played or if it should be suppressed
     * according to arbitration rules:
     * - 5-second announcement lock
     * - Bluetooth connection requirement (if bluetooth required)
     * - Priority conflicts
     */
    fun arbitrateAnnouncement(
        context: Context,
        category: String,
        isBluetoothConnected: Boolean,
        announcementsEnabled: Int, // 1 for enabled, 0 for disabled
        isInCall: Boolean,
        isDndActive: Boolean,
        isHigherPriorityActive: Boolean
    ): Pair<Boolean, String> {
        val now = System.currentTimeMillis()

        // 1. User disabled announcements
        if (announcementsEnabled <= 0) {
            return Pair(false, "User disabled announcements")
        }

        // 2. Phone in call
        if (isInCall) {
            return Pair(false, "Phone in call")
        }

        // 3. Do Not Disturb restrictions
        if (isDndActive) {
            return Pair(false, "Do Not Disturb restrictions")
        }

        // 4. Higher-priority safety announcement active
        if (isHigherPriorityActive) {
            return Pair(false, "Higher-priority safety announcement active")
        }

        // 5. 5-Second Announcement Lock
        if (now - lastAnnouncementTime < ANNOUNCEMENT_LOCK_DURATION_MS) {
            return Pair(false, "5-Second Announcement Lock active (Duplicate / Rapid suppression)")
        }

        // 6. Bluetooth restriction for driving/announcements
        if ((category.equals("DRIVING", true) || category.equals("BLUETOOTH", true)) && !isBluetoothConnected) {
            return Pair(false, "Bluetooth not connected")
        }

        // Passed all checks! Acquire lock
        lastAnnouncementTime = now
        return Pair(true, "Announcement Allowed")
    }

    /**
     * Logs announcement attempt with full audit trail via LoggingManager
     */
    fun requestAnnouncement(
        context: Context,
        title: String,
        details: String,
        category: String,
        isBluetoothConnected: Boolean,
        announcementsEnabled: Int,
        isInCall: Boolean,
        isDndActive: Boolean,
        isHigherPriorityActive: Boolean
    ): Boolean {
        val eventId = generateEventId(category)
        val (allowed, reason) = arbitrateAnnouncement(
            context = context,
            category = category,
            isBluetoothConnected = isBluetoothConnected,
            announcementsEnabled = announcementsEnabled,
            isInCall = isInCall,
            isDndActive = isDndActive,
            isHigherPriorityActive = isHigherPriorityActive
        )

        LoggingManager.logAnnouncement(
            context = context,
            title = "$title [$eventId]",
            details = details,
            played = allowed,
            reason = if (!allowed) reason else null,
            source = "NetraCoordinationManager"
        )

        return allowed
    }
}
