package com.example.engines.deepsleep

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.data.SettingsEntity
import java.util.Calendar
import java.util.Locale

/**
 * Netra Deep Sleep Mode Engine
 *
 * Implements authoritative behavior-policy mode for Nighttime Deep Sleep:
 * - Default Schedule: 8:00 PM (20:00) -> 7:00 AM (07:00) (Configurable in Settings)
 *
 * Suppression Policy when Deep Sleep is ACTIVE:
 * 🔴 Standard Voice Announcements: OFF (Suppressed)
 * 🔴 Full-battery Alarm (Sound & Speech): OFF (Suppressed at 04:00 AM and throughout night window)
 * 🔴 Charger Connect / Disconnect Spoken Alerts: OFF (Suppressed)
 * 🔴 General Battery Milestone Alerts: OFF (Suppressed)
 * 🔴 Other Configurable Non-Critical Announcements: OFF (Suppressed)
 * 🟢 Thermal Safety Warnings (>= 45°C): PERMANENT ON 🔒 (Never suppressed under any condition)
 * 🟢 Thermal Protection Mode (>= 43°C): ALWAYS OPERATIONAL 🔒
 * 🟢 Safe Background Telemetry & Data Collection: ACTIVE
 */
object DeepSleepEngine {
    private const val TAG = "DeepSleepEngine"
    private const val PREFS_NAME = "netra_deep_sleep_state_prefs"
    private const val KEY_IS_ACTIVE = "deep_sleep_is_active"
    private const val KEY_ACTIVATION_TIMESTAMP = "deep_sleep_activation_timestamp"
    private const val KEY_DEACTIVATION_TIMESTAMP = "deep_sleep_deactivation_timestamp"
    private const val KEY_LAST_STATE_CHANGE = "deep_sleep_last_state_change"

    enum class DeepSleepStatus {
        ACTIVE,
        SCHEDULED,
        INACTIVE
    }

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
     * Supports standard formats e.g., "08:00 PM", "8:00 PM", "20:00", "07:00 AM", "7:00 AM".
     * Overnight logic: If start > end, currentTime is in window if currentTime >= start OR currentTime < end.
     */
    fun isTimeInWindow(
        startTimeStr: String,
        endTimeStr: String,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): Boolean {
        return try {
            val cal = Calendar.getInstance().apply { timeInMillis = currentTimeMillis }
            val currentMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

            val startMinutes = parseTimeToMinutes(startTimeStr, defaultMinutes = 20 * 60) // 8:00 PM (20:00)
            val endMinutes = parseTimeToMinutes(endTimeStr, defaultMinutes = 7 * 60)      // 7:00 AM (07:00)

            if (startMinutes < endMinutes) {
                // Same-day window (e.g., 08:00 AM to 05:00 PM)
                currentMinutes in startMinutes until endMinutes
            } else if (startMinutes > endMinutes) {
                // Overnight window crossing midnight (e.g., 08:00 PM (1200) to 07:00 AM (420))
                currentMinutes >= startMinutes || currentMinutes < endMinutes
            } else {
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error calculating time window, falling back to 8PM-7AM", e)
            val cal = Calendar.getInstance().apply { timeInMillis = currentTimeMillis }
            val h = cal.get(Calendar.HOUR_OF_DAY)
            h >= 20 || h < 7
        }
    }

    /**
     * Converts a time string (e.g. "08:00 PM", "8:00 PM", "20:00", "07:00 AM", "7:00 AM") into minutes from midnight (0..1439).
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
     * Determines whether an announcement or alarm should be suppressed under Deep Sleep.
     * CRITICAL THERMAL SAFETY EVENTS ARE IMMUNE TO SUPPRESSION and will ALWAYS return false.
     */
    fun isAnnouncementSuppressed(
        isThermalSafety: Boolean,
        settings: SettingsEntity,
        currentTimeMillis: Long = System.currentTimeMillis(),
        announcementText: String = "",
        category: String = ""
    ): Boolean {
        if (isThermalSafety) {
            // Thermal safety warnings (>= 45°C overheat) can NEVER be suppressed under any condition
            return false
        }
        if (!isDeepSleepActive(settings, currentTimeMillis)) {
            return false
        }

        val textLower = announcementText.lowercase(Locale.US)
        val catLower = category.lowercase(Locale.US)

        return when {
            catLower.contains("charger") || textLower.contains("charger") || textLower.contains("connected") || textLower.contains("disconnected") -> {
                !settings.deepSleepChargerVoiceEnabled
            }
            textLower.contains("full") || textLower.contains("100") -> {
                !settings.deepSleepFullChargeVoiceEnabled
            }
            catLower.contains("milestone") || textLower.contains("percent") || textLower.contains("c ") || textLower.contains("d ") -> {
                !settings.deepSleepMilestonesEnabled
            }
            else -> {
                !settings.deepSleepStandardVoiceEnabled
            }
        }
    }

    /**
     * Gets the display status of Nighttime Deep Sleep for UI and logging.
     */
    fun getStatus(
        settings: SettingsEntity,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): DeepSleepStatus {
        if (!settings.deepSleepModeEnabled) return DeepSleepStatus.INACTIVE
        return if (isTimeInWindow(settings.deepSleepStartTime, settings.deepSleepEndTime, currentTimeMillis)) {
            DeepSleepStatus.ACTIVE
        } else {
            DeepSleepStatus.SCHEDULED
        }
    }

    /**
     * Persists current runtime state to disk to survive activity destruction, process restart, and system events.
     */
    fun updateRuntimeState(context: Context, settings: SettingsEntity) {
        try {
            val now = System.currentTimeMillis()
            val currentlyActive = isDeepSleepActive(settings, now)
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val wasActive = prefs.getBoolean(KEY_IS_ACTIVE, false)

            if (currentlyActive != wasActive) {
                val editor = prefs.edit()
                    .putBoolean(KEY_IS_ACTIVE, currentlyActive)
                    .putLong(KEY_LAST_STATE_CHANGE, now)

                if (currentlyActive) {
                    editor.putLong(KEY_ACTIVATION_TIMESTAMP, now)
                    Log.i(TAG, "NIGHT_SLEEP_ACTIVATED at timestamp $now (Schedule: ${settings.deepSleepStartTime} to ${settings.deepSleepEndTime})")
                } else {
                    editor.putLong(KEY_DEACTIVATION_TIMESTAMP, now)
                    Log.i(TAG, "NIGHT_SLEEP_DEACTIVATED at timestamp $now")
                }
                editor.apply()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error persisting Deep Sleep runtime state", e)
        }
    }
}
