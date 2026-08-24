package com.example.battery.engine

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.battery.model.ChargingClassificationResult
import com.example.battery.model.ChargingConfidence
import com.example.battery.model.ChargingState
import com.example.battery.model.ChargingTelemetryInput
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Netra Battery Sentinel Pro — Authoritative Charging Classification Engine
 *
 * Implements deterministic, evidence-based charging type classification:
 * 1. SLOW CHARGING
 * 2. NORMAL CHARGING
 * 3. FAST CHARGING
 * 4. MAINTENANCE / NEAR-FULL CHARGING
 * 5. INITIALIZING / INSUFFICIENT DATA
 *
 * ZERO-FABRICATION POLICY:
 * - Never assumes an arbitrary 15%/hr fallback baseline.
 * - Never declares Fast/Normal/Slow without verifiable telemetry.
 * - Distinguishes between Charger Input Power, Net Battery Gain, and Device Load.
 * - Accounts for Thermal Throttling and Near-Full Tapering.
 */
object ChargingClassificationEngine {
    private const val TAG = "ChargingEngine"
    private const val PREFS_NAME = "netra_charging_learned_baselines"

    // Source-aware learned baselines (e.g., "AC" -> [30.0f, 32.5f], "USB" -> [9.5f])
    private val sourceBaselines = ConcurrentHashMap<String, MutableList<Float>>()

    // Session state
    @Volatile
    private var lastObservedTimestampMs: Long = 0L

    @Volatile
    private var currentPowerSource: String = "None"

    @Volatile
    private var lastStableState: ChargingState = ChargingState.INITIALIZING

    private val hysteresisWindow = mutableListOf<ChargingState>()
    private const val HYSTERESIS_DEPTH = 3

    /**
     * Initializes engine and loads stored historical baselines.
     */
    fun init(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val allEntries = prefs.all
            for ((key, value) in allEntries) {
                if (key.startsWith("baseline_") && value is String && value.isNotBlank()) {
                    val source = key.removePrefix("baseline_")
                    val rates = value.split(",").mapNotNull { it.trim().toFloatOrNull() }.filter { it in 3.0f..120.0f }
                    if (rates.isNotEmpty()) {
                        sourceBaselines[source] = rates.toMutableList()
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load charging baselines", e)
        }
    }

    /**
     * Clears all session states, hysteresis buffers, and resets timestamp tracking.
     */
    @Synchronized
    fun clearSession() {
        lastObservedTimestampMs = 0L
        currentPowerSource = "None"
        lastStableState = ChargingState.INITIALIZING
        hysteresisWindow.clear()
    }

    /**
     * Notifies engine of charger connect/disconnect or source change.
     */
    @Synchronized
    fun onChargingStateChanged(isCharging: Boolean, newPowerSource: String = "None") {
        if (!isCharging || newPowerSource != currentPowerSource) {
            clearSession()
            currentPowerSource = if (isCharging) newPowerSource else "None"
        }
    }

    /**
     * Records a completed valid charging session to update the source-specific learned baseline.
     * Rejects discharging data or implausible rates.
     */
    @Synchronized
    fun recordSessionCompletion(
        context: Context?,
        powerSource: String,
        measuredAvgRatePctHr: Float
    ) {
        // Only record valid charging sessions (3.0% to 120.0%/hr)
        if (measuredAvgRatePctHr !in 3.0f..120.0f || powerSource.isBlank() || powerSource == "None" || powerSource == "Disconnected") {
            return
        }

        val list = sourceBaselines.getOrPut(powerSource) { mutableListOf() }
        list.add(measuredAvgRatePctHr)
        if (list.size > 20) {
            list.removeAt(0)
        }

        if (context != null) {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit()
                    .putString("baseline_$powerSource", list.joinToString(","))
                    .commit()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to persist charging baseline for $powerSource", e)
            }
        }
    }

    /**
     * Resets internal state and optionally clears persisted baselines for testing.
     */
    @Synchronized
    fun resetAllForTesting(context: Context? = null) {
        clearSession()
        sourceBaselines.clear()
        if (context != null) {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().clear().commit()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    /**
     * Returns learned baseline for the specified power source, or null if no history.
     */
    fun getLearnedBaseline(powerSource: String): Float? {
        val list = sourceBaselines[powerSource]
        return if (!list.isNullOrEmpty()) list.average().toFloat() else null
    }

    /**
     * Authoritative classification entrypoint.
     */
    @Synchronized
    fun classify(input: ChargingTelemetryInput): ChargingClassificationResult {
        val now = if (input.timestampMs > 0) input.timestampMs else System.currentTimeMillis()

        // 1. Not Charging Guard
        if (!input.isCharging) {
            clearSession()
            return ChargingClassificationResult(
                state = ChargingState.NOT_CHARGING,
                confidence = ChargingConfidence.STABLE,
                powerSource = "Disconnected",
                inputPowerW = null,
                currentMa = null,
                voltageV = null,
                netBatteryGainPctPerHr = null,
                explanation = "Charger disconnected",
                deviceLearnedBaselinePctPerHr = null,
                timestampMs = now
            )
        }

        // 2. Source Change Detection
        if (input.powerSource != currentPowerSource && currentPowerSource != "None") {
            clearSession()
        }
        currentPowerSource = input.powerSource

        // 3. Outlier Protection: Timestamps
        if (lastObservedTimestampMs > 0L) {
            if (now < lastObservedTimestampMs) {
                // Backward timestamp detected -> Outlier rejection
                return createFallbackResult(
                    lastStableState,
                    input,
                    "Rejected backward timestamp ($now < $lastObservedTimestampMs)",
                    now
                )
            }
            if (now == lastObservedTimestampMs && hysteresisWindow.isNotEmpty()) {
                // Duplicate timestamp -> Return previous stable result without state change
                return createFallbackResult(
                    lastStableState,
                    input,
                    "Duplicate timestamp reading",
                    now
                )
            }
        }
        lastObservedTimestampMs = now

        // 4. Outlier Protection: Battery Percentage
        val pct = input.batteryPercentage
        if (pct != null && (pct < 0 || pct > 100)) {
            return createFallbackResult(
                ChargingState.INSUFFICIENT_DATA,
                input,
                "Rejected out-of-bounds battery percentage ($pct%)",
                now
            )
        }

        // 5. Telemetry Sanitization
        val rawCurrent = input.currentNowMa
        val validCurrentMa: Int? = if (rawCurrent != null && abs(rawCurrent) in 10..15000) abs(rawCurrent) else null

        val rawVoltage = input.voltageMv
        val validVoltageMv: Int? = if (rawVoltage != null && rawVoltage in 2500..5000) rawVoltage else null
        val validVoltageV: Float? = validVoltageMv?.let { it / 1000f }

        // Power calculation with strict unit verification
        val validPowerWatt: Float? = when {
            input.powerWatt != null && !input.powerWatt.isNaN() && !input.powerWatt.isInfinite() && input.powerWatt > 0.05f && input.powerWatt < 200.0f -> {
                input.powerWatt
            }
            validCurrentMa != null && validVoltageV != null -> {
                // Power (W) = V (Volts) * I (Amps) = validVoltageV * (validCurrentMa / 1000f)
                (validVoltageV * validCurrentMa) / 1000f
            }
            else -> null
        }

        val rawVelocity = input.measuredVelocityPctPerHr
        val validVelocityPctPerHr: Float? = if (rawVelocity != null && !rawVelocity.isNaN() && !rawVelocity.isInfinite() && rawVelocity >= 0f && rawVelocity <= 150f) {
            rawVelocity
        } else null

        val rawTemp = input.temperatureCelsius
        val validTemp: Float? = if (rawTemp != null && !rawTemp.isNaN() && !rawTemp.isInfinite() && rawTemp in -30f..100f) rawTemp else null

        // 6. Thermal Limiting Context
        val isThermalThrottling = validTemp != null && (validTemp >= 41.0f || (validTemp >= 39.0f && input.temperatureTrend == "RISING"))

        // 7. Device Load Context (Screen ON with positive input power but reduced net gain)
        val isDeviceLoadHeavy = input.isScreenOn && validPowerWatt != null && validPowerWatt >= 4.5f && (validVelocityPctPerHr != null && validVelocityPctPerHr < 6.0f)

        // 8. Initializing Window Guard (< 15 seconds with low/no velocity and power)
        if (input.sessionDurationSeconds < 15 && (validVelocityPctPerHr == null || validVelocityPctPerHr <= 0.1f) && (validPowerWatt == null || validPowerWatt <= 0.1f)) {
            val candidateState = ChargingState.INITIALIZING
            lastStableState = candidateState
            return ChargingClassificationResult(
                state = candidateState,
                confidence = ChargingConfidence.INITIALIZING,
                powerSource = input.powerSource,
                inputPowerW = validPowerWatt,
                currentMa = validCurrentMa,
                voltageV = validVoltageV,
                netBatteryGainPctPerHr = validVelocityPctPerHr,
                isThermalLimited = isThermalThrottling,
                isLoadLimited = isDeviceLoadHeavy,
                explanation = "Charging — Initializing & stabilizing telemetry...",
                deviceLearnedBaselinePctPerHr = getLearnedBaseline(input.powerSource),
                timestampMs = now
            )
        }

        // 9. Insufficient Telemetry Check (Zero Physical Signals)
        val hasPhysicalTelemetry = (validPowerWatt != null && validPowerWatt > 0.1f) || (validCurrentMa != null && validCurrentMa >= 50) || (validVelocityPctPerHr != null && validVelocityPctPerHr >= 0.2f)
        if (!hasPhysicalTelemetry) {
            val candidateState = if (input.sessionDurationSeconds < 15) ChargingState.INITIALIZING else ChargingState.INSUFFICIENT_DATA
            lastStableState = candidateState
            return ChargingClassificationResult(
                state = candidateState,
                confidence = ChargingConfidence.INITIALIZING,
                powerSource = input.powerSource,
                inputPowerW = null,
                currentMa = null,
                voltageV = validVoltageV,
                netBatteryGainPctPerHr = null,
                isThermalLimited = isThermalThrottling,
                isLoadLimited = isDeviceLoadHeavy,
                explanation = if (candidateState == ChargingState.INITIALIZING) "Charging — Gathering initial telemetry..." else "Charging — Insufficient telemetry (No current/voltage/rate readable)",
                deviceLearnedBaselinePctPerHr = getLearnedBaseline(input.powerSource),
                timestampMs = now
            )
        }

        // 10. Maintenance / Near-Full Tapering (Condition F)
        // High SoC (>= 80%) where charging current naturally tapers down
        val isNearFullSoC = pct != null && pct >= 80
        val isTaperingSlope = validVelocityPctPerHr != null && validVelocityPctPerHr in 0.0f..3.0f
        val isLowCurrentTapering = validCurrentMa != null && validCurrentMa in 10..400
        val isNearFullCondition = (isNearFullSoC && (isTaperingSlope || isLowCurrentTapering)) || (pct != null && pct >= 95 && (validVelocityPctPerHr == null || validVelocityPctPerHr <= 1.0f))

        if (isNearFullCondition) {
            val rawCandidate = ChargingState.MAINTENANCE
            val resolvedState = applyHysteresis(rawCandidate)
            lastStableState = resolvedState
            return ChargingClassificationResult(
                state = resolvedState,
                confidence = ChargingConfidence.STABLE,
                powerSource = input.powerSource,
                inputPowerW = validPowerWatt,
                currentMa = validCurrentMa,
                voltageV = validVoltageV,
                netBatteryGainPctPerHr = validVelocityPctPerHr,
                isThermalLimited = isThermalThrottling,
                isLoadLimited = isDeviceLoadHeavy,
                isNearFullTapering = true,
                explanation = "Maintenance / Near-Full — Natural current tapering at high state-of-charge (${pct ?: 0}%)",
                deviceLearnedBaselinePctPerHr = getLearnedBaseline(input.powerSource),
                timestampMs = now
            )
        }

        // 11. Multi-Signal Decision Engine
        val learnedBaseline = getLearnedBaseline(input.powerSource)
        val rawCandidateState: ChargingState
        val explanationText: String

        if (learnedBaseline != null) {
            // Learned Device-Specific Baseline Path
            val normalLowerBound = learnedBaseline * 0.65f
            val fastThreshold = learnedBaseline * 1.35f

            val isElevatedRate = validVelocityPctPerHr != null && validVelocityPctPerHr >= fastThreshold
            val isHighPower = validPowerWatt != null && validPowerWatt >= 15.0f

            if (isElevatedRate || (validVelocityPctPerHr != null && validVelocityPctPerHr >= learnedBaseline && isHighPower)) {
                rawCandidateState = ChargingState.FAST
                explanationText = "Fast Charging — Rate (${validVelocityPctPerHr?.let { String.format(Locale.US, "%.1f", it) } ?: "High"}%/h) elevated above device learned baseline (${String.format(Locale.US, "%.1f", learnedBaseline)}%/h)"
            } else if (validVelocityPctPerHr != null && validVelocityPctPerHr < normalLowerBound && input.sessionDurationSeconds >= 15) {
                if (isDeviceLoadHeavy) {
                    rawCandidateState = ChargingState.NORMAL
                    explanationText = "Normal Charging — Device active load (${String.format(Locale.US, "%.1f", validPowerWatt ?: 0f)}W input) reducing net rate below baseline"
                } else if (isThermalThrottling) {
                    rawCandidateState = ChargingState.SLOW
                    explanationText = "Slow Charging — Thermal Throttling active (${validTemp?.let { String.format(Locale.US, "%.1f", it) }}°C)"
                } else {
                    rawCandidateState = ChargingState.SLOW
                    explanationText = "Slow Charging — Rate (${String.format(Locale.US, "%.1f", validVelocityPctPerHr)}%/h) below device learned baseline (${String.format(Locale.US, "%.1f", learnedBaseline)}%/h)"
                }
            } else {
                rawCandidateState = ChargingState.NORMAL
                explanationText = "Normal Charging — Operating within device learned baseline (${String.format(Locale.US, "%.1f", learnedBaseline)}%/h)"
            }
        } else {
            // Dynamic Hardware Telemetry Hierarchy Path (Zero Guessing / No Fake 15%/hr fallback)
            val isFastPower = validPowerWatt != null && validPowerWatt >= 15.0f
            val isFastVelocity = validVelocityPctPerHr != null && validVelocityPctPerHr >= 25.0f
            val isSlowPower = validPowerWatt != null && validPowerWatt < 4.5f
            val isSlowVelocity = validVelocityPctPerHr != null && validVelocityPctPerHr < 7.0f

            if (isFastPower || isFastVelocity) {
                rawCandidateState = ChargingState.FAST
                val powerStr = validPowerWatt?.let { "${String.format(Locale.US, "%.1f", it)}W" } ?: "${validVelocityPctPerHr}%/h"
                explanationText = "Fast Charging — Validated hardware telemetry ($powerStr)"
            } else if (isDeviceLoadHeavy) {
                // High current/power but screen is on consuming power
                rawCandidateState = ChargingState.NORMAL
                explanationText = "Normal Charging — Hardware input (${String.format(Locale.US, "%.1f", validPowerWatt ?: 0f)}W) with active screen consumption"
            } else if (isThermalThrottling && (isSlowPower || isSlowVelocity)) {
                rawCandidateState = ChargingState.SLOW
                explanationText = "Slow Charging — Thermal limitation active (${validTemp?.let { String.format(Locale.US, "%.1f", it) }}°C)"
            } else if ((isSlowPower && (validVelocityPctPerHr == null || isSlowVelocity)) || (isSlowVelocity && validPowerWatt == null)) {
                if (input.sessionDurationSeconds >= 15) {
                    rawCandidateState = ChargingState.SLOW
                    val metric = validPowerWatt?.let { "${String.format(Locale.US, "%.1f", it)}W" } ?: "${validVelocityPctPerHr}%/h"
                    explanationText = "Slow Charging — Low sustained charging throughput ($metric)"
                } else {
                    rawCandidateState = ChargingState.INITIALIZING
                    explanationText = "Charging — Initializing telemetry..."
                }
            } else {
                rawCandidateState = ChargingState.NORMAL
                val powerStr = validPowerWatt?.let { "${String.format(Locale.US, "%.1f", it)}W" } ?: "${validVelocityPctPerHr}%/h"
                explanationText = "Normal Charging — Standard throughput ($powerStr)"
            }
        }

        // 12. Apply Hysteresis (Anti-Flapping Protection)
        val resolvedState = applyHysteresis(rawCandidateState)
        lastStableState = resolvedState

        val confidence = when {
            input.sessionDurationSeconds < 20 -> ChargingConfidence.LOW_SAMPLES
            learnedBaseline != null -> ChargingConfidence.STABLE
            else -> ChargingConfidence.ESTIMATING
        }

        return ChargingClassificationResult(
            state = resolvedState,
            confidence = confidence,
            powerSource = input.powerSource,
            inputPowerW = validPowerWatt,
            currentMa = validCurrentMa,
            voltageV = validVoltageV,
            netBatteryGainPctPerHr = validVelocityPctPerHr,
            isThermalLimited = isThermalThrottling,
            isLoadLimited = isDeviceLoadHeavy,
            isNearFullTapering = false,
            explanation = explanationText,
            deviceLearnedBaselinePctPerHr = learnedBaseline,
            timestampMs = now
        )
    }

    private fun applyHysteresis(candidate: ChargingState): ChargingState {
        // Immediate transition for boundary states
        if (candidate == ChargingState.NOT_CHARGING || candidate == ChargingState.INITIALIZING || candidate == ChargingState.MAINTENANCE) {
            hysteresisWindow.clear()
            hysteresisWindow.add(candidate)
            return candidate
        }

        hysteresisWindow.add(candidate)
        if (hysteresisWindow.size > HYSTERESIS_DEPTH) {
            hysteresisWindow.removeAt(0)
        }

        // If all or majority in recent window agree, transition to candidate
        val candidateCount = hysteresisWindow.count { it == candidate }
        return if (candidateCount >= 2) {
            candidate
        } else {
            // Retain previous stable state if fluctuation is transient
            if (lastStableState.isCharging && lastStableState != ChargingState.INITIALIZING) {
                lastStableState
            } else {
                candidate
            }
        }
    }

    private fun createFallbackResult(
        state: ChargingState,
        input: ChargingTelemetryInput,
        reason: String,
        timestamp: Long
    ): ChargingClassificationResult {
        return ChargingClassificationResult(
            state = state,
            confidence = ChargingConfidence.LOW_SAMPLES,
            powerSource = input.powerSource,
            inputPowerW = input.powerWatt,
            currentMa = input.currentNowMa?.let { abs(it) },
            voltageV = input.voltageMv?.let { it / 1000f },
            netBatteryGainPctPerHr = input.measuredVelocityPctPerHr,
            isThermalLimited = false,
            isLoadLimited = false,
            explanation = reason,
            deviceLearnedBaselinePctPerHr = getLearnedBaseline(input.powerSource),
            timestampMs = timestamp
        )
    }
}
