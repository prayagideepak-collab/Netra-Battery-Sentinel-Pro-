package com.example.engines.deepsleep

import android.content.Context
import android.util.Log
import com.example.data.SettingsEntity
import java.util.Calendar
import java.util.Locale

/**
 * Netra Deep Sleep Mode Engine
 *
 * Implements the authoritative behavior-policy mode for Deep Sleep:
 * - Default Schedule: 9:00 PM (21:00) -> 6:00 AM (06:00) (User configurable in Settings -> Deep Sleep)
 *
 * Suppression Policy when Deep Sleep is ACTIVE:
 * 🔴 Standard Voice Announcements: OFF (Suppressed)
 * 🔴 Full-charge Voice Announcement: OFF (Suppressed)
 * 🔴 Charger Connect / Disconnect Spoken Alerts: OFF (Suppressed)
 * 🔴 General Battery Milestone Alerts: OFF (Suppressed)
 * 🔴 Other Configurable Non-Critical Announcements: OFF (Suppressed)
 * 🟢 Thermal Safety Warnings: PERMANENT ON 🔒 (Never suppressed, permanently active)
 * 🟢 Temperature Safety Limit Violations: ALWAYS ALLOWED 🔒
 * 🟢 Telemetry & Data Collection: ACTIVE
 * 🟢 Critical Safety Processing: ACTIVE
 */
object DeepSleepEngine {
    private const val TAG = "DeepSleepEngine"

    /**
     * Checks if Deep Sleep Mode is currently active based on user settings and system time.
     */
    fun isDeepSleepActive(
        settings: SettingsEntity,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (!settings.deepSleepModeEnabled) return false
        return isTimeInWindow(settings.deepSleepStartTime, settings.deepSleepEndTime, currentTimeMillis)
    }

    /**
     * Helper to evaluate whether a given timestamp falls within [startTimeStr, endTimeStr].
     * Supports standard formats e.g., "09:00 PM", "9:00 PM", "21:00", "06:00 AM".
     */
    fun isTimeInWindow(
        startTimeStr: String,
        endTimeStr: String,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): Boolean {
        return try {
            val cal = Calendar.getInstance().apply { timeInMillis = currentTimeMillis }
            val currentMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

            val startMinutes = parseTimeToMinutes(startTimeStr, defaultMinutes = 21 * 60) // 9:00 PM
            val endMinutes = parseTimeToMinutes(endTimeStr, defaultMinutes = 6 * 60)     // 6:00 AM

            if (startMinutes <= endMinutes) {
                // Same-day window (e.g., 01:00 PM to 05:00 PM)
                currentMinutes in startMinutes until endMinutes
            } else {
                // Overnight window (e.g., 09:00 PM (1260) to 06:00 AM (360))
                currentMinutes >= startMinutes || currentMinutes < endMinutes
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error calculating time window, falling back to 9PM-6AM", e)
            val cal = Calendar.getInstance().apply { timeInMillis = currentTimeMillis }
            val h = cal.get(Calendar.HOUR_OF_DAY)
            h >= 21 || h < 6
        }
    }

    /**
     * Converts a time string (e.g. "09:00 PM", "9:30 AM", "21:00") into minutes from midnight (0..1439).
     */
    fun parseTimeToMinutes(timeStr: String, defaultMinutes: Int): Int {
        return try {
            val cleaned = timeStr.trim().uppercase(Locale.US)
            val isPm = cleaned.contains("PM")
            val isAm = cleaned.contains("AM")
            val digits = cleaned.replace("AM", "").replace("PM", "").trim()
            val parts = digits.split(":")
            var hour = parts[0].trim().toInt()
            val minute = if (parts.size > 1) parts[1].trim().toInt() else 0
            if (isPm && hour < 12) hour += 12
            if (isAm && hour == 12) hour = 0
            (hour * 60 + minute) % (24 * 60)
        } catch (e: Exception) {
            defaultMinutes
        }
    }

    /**
     * Determines whether an announcement should be suppressed under Deep Sleep.
     * Thermal safety events are IMMUNE to suppression and will ALWAYS return false (never suppressed).
     */
    fun isAnnouncementSuppressed(
        isThermalSafety: Boolean,
        settings: SettingsEntity,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (isThermalSafety) {
            // Thermal safety warnings can NEVER be suppressed under any condition
            return false
        }
        return isDeepSleepActive(settings, currentTimeMillis)
    }
}
