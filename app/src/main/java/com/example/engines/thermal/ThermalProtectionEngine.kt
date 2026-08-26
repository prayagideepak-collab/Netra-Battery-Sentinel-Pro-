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
    PROTECTION_ENTERING,
    PROTECTED,
    RECOVERY_PENDING,
    RESTORING
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
    val targetThermalBrightness: Int = 25, // ~10% dimming
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
                    targetThermalBrightness = json.optInt("targetThermalBrightness", 25),
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
 * 2. Mandatory pre-action snapshot persisted synchronously before any modification.
 * 3. Hysteresis: Overheat >= 45°C, Safe Recovery <= 40°C.
 * 4. Exact restoration of modified properties with safe concurrent user change detection.
 * 5. Crash and restart safety across process/service recreation.
 * 6. Zero voice announcements.
 */
object ThermalProtectionEngine {
    private const val TAG = "ThermalProtectionEngine"
    private const val PREFS_NAME = "netra_thermal_protection_v2"
    private const val KEY_SESSION_STATE = "thermal_session_state"
    private const val KEY_ACTIVE_SNAPSHOT = "thermal_active_snapshot"
    private const val KEY_LAST_KNOWN_TEMP = "thermal_last_known_temp"

    const val DEFAULT_OVERHEAT_THRESHOLD_C = 45.0f
    const val DEFAULT_RECOVERY_THRESHOLD_C = 40.0f
    const val THERMAL_DIM_BRIGHTNESS = 25 // ~10%

    @Volatile
    private var currentState: ThermalSessionState = ThermalSessionState.NORMAL

    @Volatile
    private var activeSnapshot: ThermalSnapshot? = null

    @Volatile
    private var isInitialized = false

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

        // If in an active protected state on startup, check if we need to auto-restore or maintain protection
        if (currentState == ThermalSessionState.PROTECTED || currentState == ThermalSessionState.PROTECTION_ENTERING) {
            val lastTemp = prefs.getFloat(KEY_LAST_KNOWN_TEMP, -999f)
            if (lastTemp != -999f && lastTemp <= DEFAULT_RECOVERY_THRESHOLD_C) {
                Log.i(TAG, "Process restarted with safe temperature ($lastTemp°C). Initiating automatic restoration.")
                restoreDeviceState(context)
            } else {
                Log.i(TAG, "Process restarted with active thermal protection. Preserving existing snapshot.")
                currentState = ThermalSessionState.PROTECTED
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

        val overheatThreshold = settings?.tempAlertThreshold ?: DEFAULT_OVERHEAT_THRESHOLD_C
        val recoveryThreshold = minOf(DEFAULT_RECOVERY_THRESHOLD_C, overheatThreshold - 5.0f)

        // Save last known temperature for crash recovery
        getPrefs(context).edit().putFloat(KEY_LAST_KNOWN_TEMP, temperature).apply()

        when (currentState) {
            ThermalSessionState.NORMAL -> {
                if (temperature >= overheatThreshold) {
                    Log.w(TAG, "Overheat condition detected: $temperature°C >= $overheatThreshold°C. Entering thermal protection.")
                    executeProtection(context, temperature)
                }
            }
            ThermalSessionState.PROTECTED, ThermalSessionState.PROTECTION_ENTERING -> {
                if (temperature <= recoveryThreshold) {
                    Log.i(TAG, "Safe recovery condition confirmed: $temperature°C <= $recoveryThreshold°C. Initiating automatic restoration.")
                    restoreDeviceState(context)
                } else {
                    // Temperature remains above recovery threshold (e.g. 42°C, 44.9°C).
                    // Idempotent: Do NOT re-capture snapshot or re-modify settings.
                    Log.d(TAG, "Maintaining thermal protection at $temperature°C (Safe threshold <= $recoveryThreshold°C)")
                }
            }
            ThermalSessionState.RECOVERY_PENDING, ThermalSessionState.RESTORING -> {
                // Restoration in progress or finalizing
                if (temperature <= recoveryThreshold) {
                    restoreDeviceState(context)
                }
            }
        }

        return currentState
    }

    @Synchronized
    private fun executeProtection(context: Context, temperature: Float) {
        currentState = ThermalSessionState.PROTECTION_ENTERING

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

        // 2. Build initial pre-action snapshot
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

        // 3. PERSIST SNAPSHOT SYNCHRONOUSLY BEFORE ANY HARDWARE MODIFICATION (Crash-Safety)
        persistSession(context, ThermalSessionState.PROTECTED, snapshot)
        activeSnapshot = snapshot

        // 4. Apply device protection actions with individual failure tracking
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

                // Dim display to thermal level (~10%)
                Settings.System.putInt(
                    resolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    THERMAL_DIM_BRIGHTNESS
                )
                brightnessSuccess = true
                appliedActionsList.add("SCREEN_BRIGHTNESS_DIM")
                Log.i(TAG, "Thermal protection: Display dimmed to $THERMAL_DIM_BRIGHTNESS in manual mode.")
            } else {
                Log.d(TAG, "WRITE_SETTINGS permission unavailable for display dimming.")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Thermal display dimming action failed: ${e.message}")
        }

        try {
            if (masterSync) {
                ContentResolver.setMasterSyncAutomatically(false)
                syncSuccess = true
                appliedActionsList.add("MASTER_SYNC_DISABLED")
                Log.i(TAG, "Thermal protection: Master sync suspended.")
            }
        } catch (e: Exception) {
            Log.d(TAG, "Sync restriction not applicable: ${e.message}")
        }

        // 5. Update snapshot with actual applied actions and persist
        val finalSnapshot = snapshot.copy(
            brightnessModified = brightnessSuccess,
            brightnessModeModified = brightnessModeSuccess,
            syncModified = syncSuccess,
            backgroundRestrictedModified = true,
            appliedActions = appliedActionsList
        )
        activeSnapshot = finalSnapshot
        currentState = ThermalSessionState.PROTECTED
        persistSession(context, ThermalSessionState.PROTECTED, finalSnapshot)

        Log.w(TAG, "Thermal Protection Active (Session: ${finalSnapshot.sessionId}, Temp: $temperature°C, Actions: ${appliedActionsList.size})")
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
        Log.i(TAG, "Executing exact state restoration for session ${snapshot.sessionId}")

        val resolver = context.contentResolver
        var allRestored = true

        // 1. Restore Brightness & Mode (Exact Restoration + Safe Concurrent User Change Check)
        if (snapshot.brightnessModified || snapshot.brightnessModeModified) {
            try {
                if (Settings.System.canWrite(context)) {
                    val currentBrightness = Settings.System.getInt(
                        resolver,
                        Settings.System.SCREEN_BRIGHTNESS,
                        -1
                    )

                    // If user manually changed brightness to something other than our thermal dim value (25),
                    // respect user intent or restore pre-thermal baseline safely.
                    // If current brightness is still at our thermal dim level (25), restore original brightness value!
                    if (snapshot.brightnessModified) {
                        if (currentBrightness == snapshot.targetThermalBrightness || currentBrightness == -1) {
                            Settings.System.putInt(
                                resolver,
                                Settings.System.SCREEN_BRIGHTNESS,
                                snapshot.previousBrightnessValue
                            )
                            Log.i(TAG, "Restored pre-thermal brightness: ${snapshot.previousBrightnessValue}")
                        } else {
                            Log.i(TAG, "Preserving user manual post-protection brightness: $currentBrightness")
                        }
                    }

                    // Restore brightness mode (Auto vs Manual)
                    if (snapshot.brightnessModeModified) {
                        Settings.System.putInt(
                            resolver,
                            Settings.System.SCREEN_BRIGHTNESS_MODE,
                            snapshot.previousBrightnessMode
                        )
                        Log.i(TAG, "Restored pre-thermal brightness mode: ${snapshot.previousBrightnessMode}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to restore brightness: ${e.message}")
                allRestored = false
            }
        }

        // 2. Restore Master Sync if changed by thermal protection
        if (snapshot.syncModified && snapshot.previousSyncEnabled) {
            try {
                ContentResolver.setMasterSyncAutomatically(true)
                Log.i(TAG, "Restored pre-thermal master sync: ON")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to restore master sync: ${e.message}")
                allRestored = false
            }
        }

        // 3. Complete thermal session & transition to NORMAL
        currentState = ThermalSessionState.NORMAL
        activeSnapshot = null
        clearSession(context)
        Log.i(TAG, "Thermal session successfully closed. System returned to NORMAL state.")

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
            .commit() // Synchronous flush to disk for crash safety
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
        currentState == ThermalSessionState.PROTECTED || currentState == ThermalSessionState.PROTECTION_ENTERING

    /**
     * Testing hook to inject state or reset between isolated test runs.
     */
    @Synchronized
    fun resetForTesting(context: Context) {
        currentState = ThermalSessionState.NORMAL
        activeSnapshot = null
        isInitialized = false
        clearSession(context)
    }
}
