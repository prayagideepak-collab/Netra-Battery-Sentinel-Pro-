package com.example.engines

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.example.engines.coordinator.Engine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

enum class IdleSaverState {
    ACTIVE,
    SCREEN_OFF_PENDING,
    IDLE_CONFIRMED,
    BATTERY_SAVER_ALREADY_ON,
    BATTERY_SAVER_ENABLED_BY_NETRA
}

data class IdleSaverStatus(
    val isEnabled: Boolean = true,
    val state: IdleSaverState = IdleSaverState.ACTIVE,
    val netraTookOwnership: Boolean = false,
    val lastActionTimestamp: Long = 0L,
    val summary: String = "Idle Battery-Saver Sentinel Ready"
)

/**
 * IdleBatterySaverEngine (Capability A)
 * Implements idle battery-saver control with screen-off triggers, 12-minute grace/delay window,
 * revalidation of power/screen state, strict ownership tracking (distinguishing pre-existing vs Netra-enabled),
 * unlock restoration, and persistent state recovery across reboot/process death.
 */
object IdleBatterySaverEngine : Engine {
    private const val TAG = "IdleBatterySaverEngine"
    override val name = "IdleBatterySaverEngine"
    override val priority = 15

    private val isInitialized = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var countdownJob: Job? = null

    private const val PREFS_NAME = "netra_idle_saver_prefs"
    private const val KEY_ENABLED = "idle_saver_enabled"
    private const val KEY_NETRA_OWNED = "netra_took_ownership"

    private val _statusFlow = MutableStateFlow(IdleSaverStatus())
    val statusFlow: StateFlow<IdleSaverStatus> = _statusFlow.asStateFlow()

    private var storedContext: Context? = null

    override fun initialize(context: Context) {
        if (isInitialized.getAndSet(true)) return
        storedContext = context.applicationContext
        Log.i(TAG, "Initializing IdleBatterySaverEngine...")

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean(KEY_ENABLED, true)
        val netraOwned = prefs.getBoolean(KEY_NETRA_OWNED, false)

        _statusFlow.value = IdleSaverStatus(
            isEnabled = enabled,
            netraTookOwnership = netraOwned,
            summary = if (enabled) "Idle Battery-Saver Active" else "Idle Battery-Saver Disabled"
        )
    }

    override fun shutdown() {
        Log.i(TAG, "Shutting down IdleBatterySaverEngine...")
        countdownJob?.cancel()
        countdownJob = null
        isInitialized.set(false)
    }

    override fun getStatus(): String {
        return "IdleSaver: State=${_statusFlow.value.state}, Owned=${_statusFlow.value.netraTookOwnership}"
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        _statusFlow.value = _statusFlow.value.copy(isEnabled = enabled, summary = if (enabled) "Active" else "Disabled")
    }

    fun onScreenOff(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_ENABLED, true)) return

        Log.i(TAG, "Screen OFF detected. Starting 12-minute idle countdown for Battery Saver activation...")
        countdownJob?.cancel()

        _statusFlow.value = _statusFlow.value.copy(
            state = IdleSaverState.SCREEN_OFF_PENDING,
            summary = "Screen OFF: Waiting 12 min idle grace window"
        )

        countdownJob = scope.launch {
            // 12 minutes = 720,000 ms (For robust testing/execution, verify conditions every minute or wait 12 mins)
            // Let's wait 12 minutes (720,000 ms), with periodic checks every 60 seconds.
            val totalDelayMs = 720000L
            val intervalMs = 60000L
            var elapsedMs = 0L

            while (elapsedMs < totalDelayMs) {
                delay(intervalMs)
                elapsedMs += intervalMs

                // Revalidation check during window:
                // If screen turned on, or charging connected, cancel pending action.
                val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                val isInteractive = try { pm?.isInteractive == true } catch (e: Exception) { true }
                if (isInteractive) {
                    Log.i(TAG, "Idle countdown cancelled: screen became active during 12m window.")
                    _statusFlow.value = _statusFlow.value.copy(state = IdleSaverState.ACTIVE, summary = "Cancelled: Screen became active")
                    return@launch
                }
            }

            // Re-check conditions after 12 minutes:
            // 1. Still screen off / inactive
            // 2. Not externally powered
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val stillInteractive = try { pm?.isInteractive == true } catch (e: Exception) { false }
            val isCharging = try {
                val batteryStatus = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
                val status = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
                status == android.os.BatteryManager.BATTERY_STATUS_CHARGING || status == android.os.BatteryManager.BATTERY_STATUS_FULL
            } catch (e: Exception) {
                false
            }

            if (!stillInteractive && !isCharging) {
                // Check current Battery Saver state
                val isAlreadyOn = try { pm?.isPowerSaveMode == true } catch (e: Exception) { false }

                if (isAlreadyOn) {
                    Log.i(TAG, "Device already in Battery Saver mode before action. Preserving pre-existing state (Ownership: ALREADY_ON).")
                    prefs.edit().putBoolean(KEY_NETRA_OWNED, false).apply()
                    _statusFlow.value = _statusFlow.value.copy(
                        state = IdleSaverState.BATTERY_SAVER_ALREADY_ON,
                        netraTookOwnership = false,
                        lastActionTimestamp = System.currentTimeMillis(),
                        summary = "Battery Saver was already ON. Preserved."
                    )
                } else {
                    // Battery Saver is OFF. Netra turns it ON.
                    Log.i(TAG, "Device idle & unpowered for 12m. Enabling Battery Saver (Ownership: NETRA_ENABLED).")
                    val enabledSuccess = try {
                        // Note: PowerManager.setPowerSaveModeEnabled requires system permission or is restricted on API 21+.
                        // If restricted/unavailable, we record UNSUPPORTED / fallback gracefully without faking success.
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            // On non-rooted consumer devices, setPowerSaveModeEnabled is restricted to system apps.
                            // We attempt reflection or direct API call if permitted, or record result.
                            val method = PowerManager::class.java.getMethod("setPowerSaveModeEnabled", Boolean::class.javaPrimitiveType)
                            method.invoke(pm, true) as? Boolean ?: true
                        } else {
                            false
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Battery Saver programmatic toggle restricted by Android OS policy: ${e.message}")
                        false
                    }

                    if (enabledSuccess || (pm?.isPowerSaveMode == true)) {
                        prefs.edit().putBoolean(KEY_NETRA_OWNED, true).apply()
                        _statusFlow.value = _statusFlow.value.copy(
                            state = IdleSaverState.BATTERY_SAVER_ENABLED_BY_NETRA,
                            netraTookOwnership = true,
                            lastActionTimestamp = System.currentTimeMillis(),
                            summary = "Battery Saver enabled by Netra due to 12m idle."
                        )
                        com.example.util.DiagnosticLogger.logEvent(
                            context, "IDLE_BATTERY_SAVER_ENABLED",
                            "Battery Saver Enabled (Idle 12m)",
                            "Netra successfully enabled Battery Saver after 12 minutes of screen-off inactivity.",
                            0, 0f, 0f, "IdleBatterySaverEngine"
                        )
                    } else {
                        Log.w(TAG, "Battery Saver operation UNSUPPORTED by OS policy / restricted API.")
                        _statusFlow.value = _statusFlow.value.copy(
                            state = IdleSaverState.IDLE_CONFIRMED,
                            netraTookOwnership = false,
                            summary = "Battery Saver control UNSUPPORTED / restricted by OS."
                        )
                        com.example.util.DiagnosticLogger.logEvent(
                            context, "IDLE_BATTERY_SAVER_UNSUPPORTED",
                            "Battery Saver Control Unsupported",
                            "OS policy restricted programmatic activation of Battery Saver. Recorded as UNSUPPORTED.",
                            0, 0f, 0f, "IdleBatterySaverEngine"
                        )
                    }
                }
            } else {
                Log.i(TAG, "Idle countdown finished, but device is now interactive or charging. Skipping action.")
                _statusFlow.value = _statusFlow.value.copy(state = IdleSaverState.ACTIVE, summary = "Skipped: Device active or charging")
            }
        }
    }

    fun onScreenOnOrUnlocked(context: Context) {
        countdownJob?.cancel()
        countdownJob = null

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val netraOwned = prefs.getBoolean(KEY_NETRA_OWNED, false)

        Log.i(TAG, "Screen ON / User Unlock detected. Evaluating Battery Saver ownership (Netra Owned: $netraOwned)...")

        if (netraOwned) {
            // Netra turned it ON, so Netra must revert it to OFF.
            try {
                val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                val disabledSuccess = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        val method = PowerManager::class.java.getMethod("setPowerSaveModeEnabled", Boolean::class.javaPrimitiveType)
                        method.invoke(pm, false) as? Boolean ?: true
                    } else {
                        false
                    }
                } catch (e: Exception) {
                    false
                }

                if (disabledSuccess || (pm?.isPowerSaveMode == false)) {
                    Log.i(TAG, "Battery Saver restored to OFF because Netra originally enabled it.")
                    com.example.util.DiagnosticLogger.logEvent(
                        context, "IDLE_BATTERY_SAVER_RESTORED",
                        "Battery Saver Restored to OFF",
                        "User unlocked device. Netra restored Battery Saver to OFF as it owned the prior state change.",
                        0, 0f, 0f, "IdleBatterySaverEngine"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring Battery Saver state on unlock", e)
            }
            prefs.edit().putBoolean(KEY_NETRA_OWNED, false).apply()
        } else {
            Log.i(TAG, "Battery Saver was ALREADY ON before Netra action or not owned by Netra. Preserving user's original state (DO NOT TURN OFF).")
        }

        _statusFlow.value = _statusFlow.value.copy(
            state = IdleSaverState.ACTIVE,
            netraTookOwnership = false,
            summary = "Active (Unlocked)"
        )
    }
}
