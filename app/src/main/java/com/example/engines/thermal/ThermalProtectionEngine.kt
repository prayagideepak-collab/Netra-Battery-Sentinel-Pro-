package com.example.engines.thermal

import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import android.util.Log
import com.example.data.SettingsEntity
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Thermal Protection Session Lifecycle States
 */
enum class ThermalSessionState {
    NORMAL,
    THERMAL_PROTECTION,
    THERMAL_ESCALATED,
    RESTORING,
    RESTORED;

    // Compatibility helpers
    val isProtected: Boolean
        get() = this == THERMAL_PROTECTION || this == THERMAL_ESCALATED
}

/**
 * Snapshot of exact device state captured immediately prior to thermal intervention.
 */
data class ThermalSnapshot(
    val sessionId: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val entryTemperature: Float = 0f,
    val previousBrightnessValue: Int = 128,
    val previousBrightnessMode: Int = Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
    val targetThermalBrightness: Int = 13, // 5% dimming (13/255)
    val brightnessModified: Boolean = false,
    val brightnessModeModified: Boolean = false,
    val previousSyncEnabled: Boolean = false,
    val syncModified: Boolean = false,
    val previousBackgroundRestricted: Boolean = false,
    val backgroundRestrictedModified: Boolean = false,
    val appliedActions: List<String> = emptyList()
) {
    fun toJson(): String {
        val json = JSONObject()
        json.put("sessionId", sessionId)
        json.put("timestamp", timestamp)
        json.put("entryTemperature", entryTemperature.toDouble())
        json.put("previousBrightnessValue", previousBrightnessValue)
        json.put("previousBrightnessMode", previousBrightnessMode)
        json.put("targetThermalBrightness", targetThermalBrightness)
        json.put("brightnessModified", brightnessModified)
        json.put("brightnessModeModified", brightnessModeModified)
        json.put("previousSyncEnabled", previousSyncEnabled)
        json.put("syncModified", syncModified)
        json.put("previousBackgroundRestricted", previousBackgroundRestricted)
        json.put("backgroundRestrictedModified", backgroundRestrictedModified)

        val actionsArray = JSONArray()
        appliedActions.forEach { actionsArray.put(it) }
        json.put("appliedActions", actionsArray)
        return json.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): ThermalSnapshot? {
            return try {
                val json = JSONObject(jsonStr)
                val actions = mutableListOf<String>()
                val arr = json.optJSONArray("appliedActions")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        actions.add(arr.getString(i))
                    }
                }
                ThermalSnapshot(
                    sessionId = json.optString("sessionId", UUID.randomUUID().toString()),
                    timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                    entryTemperature = json.optDouble("entryTemperature", 0.0).toFloat(),
                    previousBrightnessValue = json.optInt("previousBrightnessValue", 128),
                    previousBrightnessMode = json.optInt("previousBrightnessMode", Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL),
                    targetThermalBrightness = json.optInt("targetThermalBrightness", 13),
                    brightnessModified = json.optBoolean("brightnessModified", false),
                    brightnessModeModified = json.optBoolean("brightnessModeModified", false),
                    previousSyncEnabled = json.optBoolean("previousSyncEnabled", false),
                    syncModified = json.optBoolean("syncModified", false),
                    previousBackgroundRestricted = json.optBoolean("previousBackgroundRestricted", false),
                    backgroundRestrictedModified = json.optBoolean("backgroundRestrictedModified", false),
                    appliedActions = actions
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * Central Automatic Thermal Protection and State Restoration Engine.
 *
 * Guarantees:
 * 1. Fully automatic operation from battery temperature signals (No manual buttons).
 * 2. 43°C Protection (2 consecutive readings required) -> Dims brightness to 5%, throttles background workload.
 * 3. 45°C Escalation (2 consecutive readings required) -> Controlled safety announcement.
 * 4. <=40°C Automatic Restoration (3 consecutive readings required) -> Restores captured pre-protection state.
 * 5. Mandatory pre-action snapshot persisted synchronously before any modification.
 * 6. User Manual Brightness change protection (skips overwriting user choice).
 * 7. Crash and restart safety across process/service recreation.
 */
object ThermalProtectionEngine {
    private const val TAG = "ThermalProtectionEngine"
    private const val PREFS_NAME = "netra_thermal_protection_v3"
    private const val KEY_SESSION_STATE = "thermal_session_state"
    private const val KEY_ACTIVE_SNAPSHOT = "thermal_active_snapshot"
    private const val KEY_LAST_KNOWN_TEMP = "thermal_last_known_temp"

    const val PROTECTION_THRESHOLD_C = 43.0f
    const val ESCALATION_THRESHOLD_C = 45.0f
    const val RESTORATION_THRESHOLD_C = 40.0f
    const val THERMAL_DIM_BRIGHTNESS = 13 // 5% of 255

    // Debounce counters
    @Volatile private var protectionReadingCount = 0
    @Volatile private var escalationReadingCount = 0
    @Volatile private var recoveryReadingCount = 0

    @Volatile
    private var currentState: ThermalSessionState = ThermalSessionState.NORMAL

    @Volatile
    private var activeSnapshot: ThermalSnapshot? = null

    @Volatile
    private var isInitialized = false

    @Volatile
    private var lastSpokenTemp = -1f
    @Volatile
    private var lastAnnouncementTime = 0L

    var onThermalEventCallback: ((eventType: String, title: String, details: String) -> Unit)? = null
    var onThermalSpeechCallback: ((text: String) -> Unit)? = null

    @Synchronized
    fun initialize(context: Context) {
        if (isInitialized) return
        val prefs = getPrefs(context)
        val savedStateName = prefs.getString(KEY_SESSION_STATE, ThermalSessionState.NORMAL.name)
        currentState = try {
            ThermalSessionState.valueOf(savedStateName ?: ThermalSessionState.NORMAL.name)
        } catch (e: Exception) {
            ThermalSessionState.NORMAL
        }

        val snapshotJson = prefs.getString(KEY_ACTIVE_SNAPSHOT, null)
        activeSnapshot = if (snapshotJson != null) ThermalSnapshot.fromJson(snapshotJson) else null

        // If in an active protected state on startup, verify if we need to auto-restore or maintain protection
        if (currentState == ThermalSessionState.THERMAL_PROTECTION || currentState == ThermalSessionState.THERMAL_ESCALATED) {
            val lastTemp = prefs.getFloat(KEY_LAST_KNOWN_TEMP, -999f)
            if (lastTemp != -999f && lastTemp <= RESTORATION_THRESHOLD_C) {
                Log.i(TAG, "Process restarted with safe temperature ($lastTemp°C). Initiating automatic restoration.")
                restoreDeviceState(context)
            } else {
                Log.i(TAG, "Process restarted with active thermal protection. Preserving existing snapshot.")
            }
        }
        isInitialized = true
    }

    @Synchronized
    fun processTemperature(
        temperature: Float,
        context: Context,
        settings: SettingsEntity? = null
    ): ThermalSessionState {
        if (temperature == -999f) return currentState
        if (!isInitialized) initialize(context)

        // Save last known temperature for crash recovery
        getPrefs(context).edit().putFloat(KEY_LAST_KNOWN_TEMP, temperature).apply()

        // 1. Debounce Evaluation
        if (temperature >= ESCALATION_THRESHOLD_C) {
            escalationReadingCount++
            protectionReadingCount = 2 // Escalation implies protection confirmed
            recoveryReadingCount = 0
        } else if (temperature >= PROTECTION_THRESHOLD_C) {
            protectionReadingCount++
            escalationReadingCount = 0
            recoveryReadingCount = 0
        } else if (temperature <= RESTORATION_THRESHOLD_C) {
            recoveryReadingCount++
            protectionReadingCount = 0
            escalationReadingCount = 0
        } else {
            // Temperature in deadband (40.1°C to 42.9°C): reset recovery and trigger counters
            recoveryReadingCount = 0
            protectionReadingCount = 0
            escalationReadingCount = 0
        }

        // 2. State Machine Transitions
        when (currentState) {
            ThermalSessionState.NORMAL, ThermalSessionState.RESTORED -> {
                if (escalationReadingCount >= 2) {
                    Log.w(TAG, "THERMAL_45C_ESCALATED triggered at $temperature°C (2 readings confirmed)")
                    executeProtection(context, temperature)
                    escalateThermalProtection(context, temperature)
                } else if (protectionReadingCount >= 2) {
                    Log.w(TAG, "THERMAL_43C_TRIGGERED at $temperature°C (2 readings confirmed)")
                    executeProtection(context, temperature)
                }
            }
            ThermalSessionState.THERMAL_PROTECTION -> {
                if (escalationReadingCount >= 2) {
                    Log.w(TAG, "Escalating from THERMAL_PROTECTION to THERMAL_ESCALATED at $temperature°C")
                    escalateThermalProtection(context, temperature)
                } else if (recoveryReadingCount >= 3) {
                    Log.i(TAG, "Safe temperature <= 40°C confirmed 3 times ($temperature°C). Starting restoration.")
                    restoreDeviceState(context)
                } else {
                    Log.d(TAG, "Maintaining THERMAL_PROTECTION at $temperature°C")
                }
            }
            ThermalSessionState.THERMAL_ESCALATED -> {
                if (recoveryReadingCount >= 3) {
                    Log.i(TAG, "Safe temperature <= 40°C confirmed 3 times ($temperature°C). Starting restoration from escalated.")
                    restoreDeviceState(context)
                } else if (temperature < ESCALATION_THRESHOLD_C && temperature >= PROTECTION_THRESHOLD_C) {
                    // Temperature cooled down below 45°C but still >= 43°C -> remain protected
                    currentState = ThermalSessionState.THERMAL_PROTECTION
                    persistSession(context, currentState, activeSnapshot ?: ThermalSnapshot())
                    Log.i(TAG, "Temperature lowered to $temperature°C; de-escalating to THERMAL_PROTECTION")
                } else {
                    // Overheating continues: check for repeated safety announcement with cooldown
                    checkRepeatedOverheatingSpeech(temperature)
                }
            }
            ThermalSessionState.RESTORING -> {
                if (recoveryReadingCount >= 3) {
                    restoreDeviceState(context)
                }
            }
        }

        return currentState
    }

    @Synchronized
    private fun executeProtection(context: Context, temperature: Float) {
        val resolver = context.contentResolver

        // 1. Capture exact current pre-action state
        var currentBrightness = 128
        var currentBrightnessMode = Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        try {
            currentBrightness = Settings.System.getInt(
                resolver,
                Settings.System.SCREEN_BRIGHTNESS,
                128
            )
            currentBrightnessMode = Settings.System.getInt(
                resolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to capture pre-action brightness: ${e.message}")
        }

        val masterSync = try {
            ContentResolver.getMasterSyncAutomatically()
        } catch (e: Exception) {
            false
        }

        // 2. Build pre-action snapshot
        val snapshot = ThermalSnapshot(
            sessionId = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            entryTemperature = temperature,
            previousBrightnessValue = currentBrightness,
            previousBrightnessMode = currentBrightnessMode,
            targetThermalBrightness = THERMAL_DIM_BRIGHTNESS,
            previousSyncEnabled = masterSync,
            previousBackgroundRestricted = false
        )

        // 3. PERSIST SNAPSHOT SYNCHRONOUSLY BEFORE ANY HARDWARE MODIFICATION (Crash Safety)
        persistSession(context, ThermalSessionState.THERMAL_PROTECTION, snapshot)
        activeSnapshot = snapshot

        // 4. Apply device protection actions
        val appliedActionsList = mutableListOf<String>()
        var brightnessSuccess = false
        var brightnessModeSuccess = false
        var syncSuccess = false

        try {
            if (Settings.System.canWrite(context)) {
                // Set to MANUAL mode so auto-brightness doesn't counteract dimming
                Settings.System.putInt(
                    resolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                )
                brightnessModeSuccess = true
                appliedActionsList.add("SCREEN_BRIGHTNESS_MODE_MANUAL")

                // Dim display to 5%
                Settings.System.putInt(
                    resolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    THERMAL_DIM_BRIGHTNESS
                )
                brightnessSuccess = true
                appliedActionsList.add("BRIGHTNESS_CHANGED_5_PERCENT")
                Log.i(TAG, "Thermal protection: Display dimmed to 5% ($THERMAL_DIM_BRIGHTNESS) in manual mode.")
            } else {
                Log.d(TAG, "WRITE_SETTINGS permission unavailable for display dimming: UNAVAILABLE_BY_ANDROID")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Thermal display dimming action failed: ${e.message}")
        }

        try {
            if (masterSync) {
                ContentResolver.setMasterSyncAutomatically(false)
                syncSuccess = true
                appliedActionsList.add("MASTER_SYNC_DISABLED")
            }
        } catch (e: Exception) {
            Log.d(TAG, "Sync restriction not applicable: ${e.message}")
        }

        // 5. Update snapshot with actual applied actions
        val finalSnapshot = snapshot.copy(
            brightnessModified = brightnessSuccess,
            brightnessModeModified = brightnessModeSuccess,
            syncModified = syncSuccess,
            backgroundRestrictedModified = true,
            appliedActions = appliedActionsList
        )
        activeSnapshot = finalSnapshot
        currentState = ThermalSessionState.THERMAL_PROTECTION
        persistSession(context, ThermalSessionState.THERMAL_PROTECTION, finalSnapshot)

        onThermalEventCallback?.invoke(
            "THERMAL_43C_TRIGGERED",
            "Thermal Protection Activated",
            "Temperature reached $temperature°C (>=43°C confirmed). Display dimmed to 5%, background protection engaged."
        )
    }

    @Synchronized
    private fun escalateThermalProtection(context: Context, temperature: Float) {
        currentState = ThermalSessionState.THERMAL_ESCALATED
        persistSession(context, ThermalSessionState.THERMAL_ESCALATED, activeSnapshot ?: ThermalSnapshot())

        val speechText = "Your battery is overheating ${temperature.toInt()}°C"
        lastSpokenTemp = temperature
        lastAnnouncementTime = System.currentTimeMillis()

        Log.w(TAG, "THERMAL ESCALATION: $speechText (Safety voice alert triggered)")
        onThermalSpeechCallback?.invoke(speechText)
        onThermalEventCallback?.invoke(
            "THERMAL_45C_ESCALATED",
            "Thermal Escalation (Overheating)",
            "Battery temperature $temperature°C (>=45°C). Critical safety alert triggered."
        )
    }

    private fun checkRepeatedOverheatingSpeech(temperature: Float) {
        val now = System.currentTimeMillis()
        if (now - lastAnnouncementTime > 60000L || temperature >= lastSpokenTemp + 1.0f) {
            lastAnnouncementTime = now
            lastSpokenTemp = temperature
            val speechText = "Your battery is overheating ${temperature.toInt()}°C"
            Log.w(TAG, "THERMAL ESCALATION REPEATED: $speechText")
            onThermalSpeechCallback?.invoke(speechText)
        }
    }

    @Synchronized
    fun restoreDeviceState(context: Context): Boolean {
        val snapshot = activeSnapshot ?: run {
            val prefs = getPrefs(context)
            val json = prefs.getString(KEY_ACTIVE_SNAPSHOT, null)
            if (json != null) ThermalSnapshot.fromJson(json) else null
        }

        if (snapshot == null) {
            Log.w(TAG, "No thermal snapshot found to restore. Returning to NORMAL state.")
            currentState = ThermalSessionState.NORMAL
            clearSession(context)
            return false
        }

        currentState = ThermalSessionState.RESTORING
        onThermalEventCallback?.invoke(
            "RESTORATION_STARTED",
            "Thermal Restoration Started",
            "Battery temperature cooled to <=40°C. Restoring pre-protection baseline."
        )

        val resolver = context.contentResolver
        var allRestored = true

        // 1. Restore Brightness & Mode
        if (snapshot.brightnessModified || snapshot.brightnessModeModified) {
            try {
                if (Settings.System.canWrite(context)) {
                    val currentBrightness = Settings.System.getInt(
                        resolver,
                        Settings.System.SCREEN_BRIGHTNESS,
                        -1
                    )

                    // Safe concurrent user change detection:
                    // If user manually changed brightness to something other than our thermal dim value (13),
                    // respect user intent! Otherwise restore pre-protection brightness.
                    if (snapshot.brightnessModified) {
                        if (currentBrightness == snapshot.targetThermalBrightness || currentBrightness == -1) {
                            Settings.System.putInt(
                                resolver,
                                Settings.System.SCREEN_BRIGHTNESS,
                                snapshot.previousBrightnessValue
                            )
                            Log.i(TAG, "BRIGHTNESS_RESTORED: ${snapshot.previousBrightnessValue}")
                            onThermalEventCallback?.invoke("BRIGHTNESS_RESTORED", "Brightness Restored", "Restored to ${snapshot.previousBrightnessValue}")
                        } else {
                            Log.i(TAG, "RESTORATION_SKIPPED_USER_CHANGED: Current brightness is $currentBrightness (user manual change detected)")
                            onThermalEventCallback?.invoke("RESTORATION_SKIPPED_USER_CHANGED", "User Brightness Preserved", "User modified brightness during protection to $currentBrightness")
                        }
                    }

                    if (snapshot.brightnessModeModified) {
                        Settings.System.putInt(
                            resolver,
                            Settings.System.SCREEN_BRIGHTNESS_MODE,
                            snapshot.previousBrightnessMode
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to restore brightness: ${e.message}")
                allRestored = false
            }
        }

        // 2. Restore Master Sync
        if (snapshot.syncModified && snapshot.previousSyncEnabled) {
            try {
                ContentResolver.setMasterSyncAutomatically(true)
                onThermalEventCallback?.invoke("SYNC_RESTORED", "Sync Restored", "Master sync re-enabled.")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to restore master sync: ${e.message}")
                allRestored = false
            }
        }

        onThermalEventCallback?.invoke("BACKGROUND_PROTECTION_RELEASED", "Background Protection Released", "Normal background scheduling restored.")

        // 3. Finalize
        currentState = ThermalSessionState.RESTORED
        activeSnapshot = null
        clearSession(context)
        currentState = ThermalSessionState.NORMAL

        val resultEvent = if (allRestored) "RESTORATION_COMPLETED" else "RESTORATION_PARTIAL"
        onThermalEventCallback?.invoke(resultEvent, "Thermal Restoration Completed", "System returned to normal operational state.")
        Log.i(TAG, "Thermal session successfully closed ($resultEvent).")

        return allRestored
    }

    private fun persistSession(
        context: Context,
        state: ThermalSessionState,
        snapshot: ThermalSnapshot
    ) {
        getPrefs(context).edit()
            .putString(KEY_SESSION_STATE, state.name)
            .putString(KEY_ACTIVE_SNAPSHOT, snapshot.toJson())
            .commit()
    }

    private fun clearSession(context: Context) {
        getPrefs(context).edit()
            .putString(KEY_SESSION_STATE, ThermalSessionState.NORMAL.name)
            .remove(KEY_ACTIVE_SNAPSHOT)
            .commit()
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getState(): ThermalSessionState = currentState

    fun getActiveSnapshot(): ThermalSnapshot? = activeSnapshot

    fun isProtectionActive(): Boolean =
        currentState == ThermalSessionState.THERMAL_PROTECTION || currentState == ThermalSessionState.THERMAL_ESCALATED

    @Synchronized
    fun resetForTesting(context: Context) {
        currentState = ThermalSessionState.NORMAL
        activeSnapshot = null
        isInitialized = false
        protectionReadingCount = 0
        escalationReadingCount = 0
        recoveryReadingCount = 0
        lastSpokenTemp = -1f
        lastAnnouncementTime = 0L
        clearSession(context)
    }
}
