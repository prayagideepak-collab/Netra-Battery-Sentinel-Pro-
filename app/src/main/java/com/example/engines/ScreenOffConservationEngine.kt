package com.example.engines

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ScreenState {
    SCREEN_ON,
    SCREEN_OFF
}

enum class BackgroundPolicyMode {
    NORMAL,
    CONSERVATION
}

data class ScreenConservationState(
    val screenState: ScreenState = ScreenState.SCREEN_ON,
    val policyMode: BackgroundPolicyMode = BackgroundPolicyMode.NORMAL,
    val pollingCadenceMs: Long = 30000L,
    val syncWindowSec: Long = 60L,
    val isNonCriticalSyncDeferred: Boolean = false,
    val statusSummary: String = "Normal Mode (Screen ON)"
)

object ScreenOffConservationEngine {
    private const val TAG = "ScreenOffConservationEngine"

    private val _engineState = MutableStateFlow(ScreenConservationState())
    val engineState: StateFlow<ScreenConservationState> = _engineState.asStateFlow()

    // Dynamic state preservation (remembers parameters before screen off)
    private var previousPollingCadenceMs: Long = 30000L
    private var previousSyncWindowSec: Long = 60L
    private var wasStateCaptured: Boolean = false

    fun onScreenOff(context: Context, currentPollingCadenceMs: Long = 30000L, currentSyncWindowSec: Long = 60L) {
        if (_engineState.value.screenState == ScreenState.SCREEN_OFF) return

        Log.i(TAG, "Screen State Transition: SCREEN_ON -> SCREEN_OFF")

        // 1. Remember pre-optimization state dynamically
        previousPollingCadenceMs = currentPollingCadenceMs
        previousSyncWindowSec = currentSyncWindowSec
        wasStateCaptured = true

        // 2. Apply Background Conservation Policy:
        // - Lower priority / slower cadence (e.g., 30s -> 120s)
        // - Increased sync window (e.g., 60s -> 900s)
        // - Defer non-critical background syncs
        // - Batch network-heavy ops
        val conservedCadence = (currentPollingCadenceMs * 4).coerceAtLeast(120000L) // 2 mins minimum
        val conservedSyncWindow = (currentSyncWindowSec * 15).coerceAtLeast(900L) // 15 mins minimum

        _engineState.value = ScreenConservationState(
            screenState = ScreenState.SCREEN_OFF,
            policyMode = BackgroundPolicyMode.CONSERVATION,
            pollingCadenceMs = conservedCadence,
            syncWindowSec = conservedSyncWindow,
            isNonCriticalSyncDeferred = true,
            statusSummary = "Automatic Background Conservation Active (Screen OFF)"
        )

        Log.i(TAG, "Background Conservation Engaged: Cadence=${conservedCadence}ms, SyncWindow=${conservedSyncWindow}s, Non-critical sync deferred.")
        
        com.example.util.DiagnosticLogger.logEvent(
            context,
            "SCREEN_OFF_CONSERVATION",
            "Screen OFF Conservation Activated",
            "Automatic background policy switched to CONSERVATION mode. Cadence slowed to ${conservedCadence / 1000}s.",
            0, 0f, 0f, "System"
        )
    }

    fun onScreenOn(context: Context) {
        if (_engineState.value.screenState == ScreenState.SCREEN_ON) return

        Log.i(TAG, "Screen State Transition: SCREEN_OFF -> SCREEN_ON")

        // Restore remembered parameters prior to screen-off
        val restoredCadence = if (wasStateCaptured) previousPollingCadenceMs else 30000L
        val restoredSyncWindow = if (wasStateCaptured) previousSyncWindowSec else 60L

        _engineState.value = ScreenConservationState(
            screenState = ScreenState.SCREEN_ON,
            policyMode = BackgroundPolicyMode.NORMAL,
            pollingCadenceMs = restoredCadence,
            syncWindowSec = restoredSyncWindow,
            isNonCriticalSyncDeferred = false,
            statusSummary = "Normal Operating Mode (Screen ON)"
        )

        Log.i(TAG, "Normal Background Policy Restored: Cadence=${restoredCadence}ms, SyncWindow=${restoredSyncWindow}s")

        com.example.util.DiagnosticLogger.logEvent(
            context,
            "SCREEN_ON_NORMAL",
            "Screen ON Policy Restored",
            "Background policy restored to NORMAL. Cadence reset to ${restoredCadence / 1000}s.",
            0, 0f, 0f, "System"
        )
    }
}
