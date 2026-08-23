package com.example.engines.charging

import android.content.Context
import android.content.SharedPreferences
import android.os.BatteryManager
import android.util.Log
import java.util.Locale

/**
 * Netra Deterministic & Evidence-Based Charging Engine
 *
 * Implements a 3-Layer Telemetry Architecture:
 * - Layer 1: Physical Input (V, I, P = V x I, Plug Type)
 * - Layer 2: Battery Response (Delta Charge Counter / Delta Time, Delta Battery% / Delta Time)
 * - Layer 3: Phone Load & Thermal Throttling Context (Screen ON/OFF, thermal elevation)
 *
 * Implements 5 Evidence-Based Charging States:
 * 1. CHARGING - INITIALIZING: Charger connected, collecting stable samples (0-few secs)
 * 2. SLOW CHARGING (or Slow Charging - Thermal Limited): Measured rate significantly below device learned baseline
 * 3. NORMAL CHARGING: Battery increasing within learned baseline operating band
 * 4. FAST CHARGING: Measured sustained rate substantially above learned baseline supported by physical input
 * 5. MAINTENANCE / NET-ZERO: Plugged in, but battery slope approx 0 or charge counter stable (not discharging, not gaining)
 *
 * Anti-Guess Rule: If telemetry is missing/unsupported, yields "Charging — Insufficient telemetry".
 */

enum class EvidenceChargingState(val displayName: String) {
    INITIALIZING("Charging — Calculating rate..."),
    SLOW("Slow Charging"),
    SLOW_THERMAL_LIMITED("Slow Charging — Thermal Limited"),
    SLOW_LOAD_LIMITED("Slow Charging — High Device Load"),
    NORMAL("Normal Charging"),
    FAST("Fast Charging"),
    MAINTENANCE("Maintenance / Net-Zero"),
    INSUFFICIENT_TELEMETRY("Charging — Insufficient telemetry"),
    NOT_CHARGING("Discharging")
}

data class EvidenceAssessment(
    val state: EvidenceChargingState,
    val measuredRatePctPerHr: Float,
    val inputPowerW: Float?,
    val currentMa: Int?,
    val voltageV: Float?,
    val powerSource: String, // AC, USB, Wireless, Dock
    val explanation: String,
    val isThermalThrottlingDetected: Boolean = false,
    val isHeavyLoadDetected: Boolean = false,
    val deviceBaselineRatePctPerHr: Float
)

object DeterministicChargingEngine {
    private const val TAG = "DeterministicCharging"
    private const val PREFS_NAME = "netra_device_charging_baseline_prefs"
    private const val KEY_SESSION_RATES = "observed_session_rates"
    private const val KEY_LEARNED_BASELINE = "learned_baseline_pct_hr"

    // Default conservative fallback baseline if no sessions yet recorded
    private const val DEFAULT_NORMAL_BASELINE_PCT_HR = 15.0f

    @Volatile
    private var learnedBaselinePctHr: Float = DEFAULT_NORMAL_BASELINE_PCT_HR

    private val recentSessionRates = mutableListOf<Float>()

    fun init(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            learnedBaselinePctHr = prefs.getFloat(KEY_LEARNED_BASELINE, DEFAULT_NORMAL_BASELINE_PCT_HR)
            val savedRatesStr = prefs.getString(KEY_SESSION_RATES, "") ?: ""
            if (savedRatesStr.isNotBlank()) {
                recentSessionRates.clear()
                savedRatesStr.split(",").mapNotNull { it.trim().toFloatOrNull() }.forEach {
                    recentSessionRates.add(it)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load charging baseline prefs", e)
        }
    }

    /**
     * Records a completed valid charging session to update the device-specific baseline.
     */
    @Synchronized
    fun recordSessionCompletion(context: Context, measuredAvgRatePctHr: Float) {
        if (measuredAvgRatePctHr in 3.0f..100.0f) {
            recentSessionRates.add(measuredAvgRatePctHr)
            if (recentSessionRates.size > 20) {
                recentSessionRates.removeAt(0)
            }
            learnedBaselinePctHr = recentSessionRates.average().toFloat()
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit()
                    .putFloat(KEY_LEARNED_BASELINE, learnedBaselinePctHr)
                    .putString(KEY_SESSION_RATES, recentSessionRates.joinToString(","))
                    .apply()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to save charging baseline", e)
            }
        }
    }

    /**
     * Evaluates charging behaviour without guesswork.
     *
     * @param isCharging true if plugged and charging intent active
     * @param sessionDurationSeconds duration since charger connection
     * @param measuredRatePctPerHr delta % / delta time or charge counter rate
     * @param powerWatt physical input power (V x I) if available
     * @param currentMa hardware current reading in mA
     * @param voltageMv hardware voltage reading in mV
     * @param temperatureCelsius current battery temperature
     * @param temperatureTrend thermal trend (RISING, STABLE, FALLING)
     * @param isScreenOn whether display is currently consuming power
     * @param powerSource extra plugged category (AC, USB, Wireless, Dock)
     */
    fun evaluate(
        isCharging: Boolean,
        sessionDurationSeconds: Long,
        measuredRatePctPerHr: Float,
        powerWatt: Float,
        currentMa: Int,
        voltageMv: Int,
        temperatureCelsius: Float,
        temperatureTrend: String,
        isScreenOn: Boolean,
        powerSource: String
    ): EvidenceAssessment {
        if (!isCharging) {
            return EvidenceAssessment(
                state = EvidenceChargingState.NOT_CHARGING,
                measuredRatePctPerHr = 0f,
                inputPowerW = null,
                currentMa = null,
                voltageV = null,
                powerSource = "Disconnected",
                explanation = "Charger disconnected",
                deviceBaselineRatePctPerHr = learnedBaselinePctHr
            )
        }

        val absCurrent = Math.abs(currentMa)
        val validVoltage = if (voltageMv > 0) voltageMv / 1000f else null
        val validPower = if (powerWatt > 0.05f) {
            powerWatt
        } else if (absCurrent > 0 && validVoltage != null) {
            (absCurrent * validVoltage) / 1000f
        } else {
            null
        }

        // 1. CHARGING — INITIALIZING (0 to ~15-30s until first stable slope or readings available)
        if (sessionDurationSeconds < 15 && measuredRatePctPerHr <= 0.1f && (validPower == null || validPower <= 0.1f)) {
            return EvidenceAssessment(
                state = EvidenceChargingState.INITIALIZING,
                measuredRatePctPerHr = 0f,
                inputPowerW = validPower,
                currentMa = if (absCurrent > 0) absCurrent else null,
                voltageV = validVoltage,
                powerSource = powerSource,
                explanation = "Charging — Calculating charging rate...",
                deviceBaselineRatePctPerHr = learnedBaselinePctHr
            )
        }

        // Anti-Guess Rule: If no slope AND no current/voltage/power is readable from the OS
        val hasValidTelemetry = (measuredRatePctPerHr >= 0.2f) || (validPower != null && validPower > 0.1f) || (absCurrent > 50)
        if (!hasValidTelemetry) {
            return EvidenceAssessment(
                state = EvidenceChargingState.INSUFFICIENT_TELEMETRY,
                measuredRatePctPerHr = 0f,
                inputPowerW = null,
                currentMa = null,
                voltageV = validVoltage,
                powerSource = powerSource,
                explanation = "Charging — Insufficient telemetry (OS did not expose current/charge counters)",
                deviceBaselineRatePctPerHr = learnedBaselinePctHr
            )
        }

        // 5. MAINTENANCE / NET-ZERO
        // If connected, but slope is approximately 0 (±0.4%/h) and current is minimal (< 120mA) or near zero
        val isNetZeroSlope = measuredRatePctPerHr in 0.0f..0.4f
        val isLowCurrent = absCurrent in 0..120
        if (sessionDurationSeconds >= 30 && isNetZeroSlope && (validPower == null || validPower < 1.0f || isLowCurrent)) {
            return EvidenceAssessment(
                state = EvidenceChargingState.MAINTENANCE,
                measuredRatePctPerHr = measuredRatePctPerHr,
                inputPowerW = validPower,
                currentMa = if (absCurrent > 0) absCurrent else null,
                voltageV = validVoltage,
                powerSource = powerSource,
                explanation = "Maintenance / Net-Zero — Sustained charge slope near 0% under current power state",
                deviceBaselineRatePctPerHr = learnedBaselinePctHr
            )
        }

        // Baseline comparison parameters
        val baseline = learnedBaselinePctHr.coerceAtLeast(6.0f)
        val normalLowerBound = baseline * 0.65f  // e.g. if baseline is 15, lower is ~9.7%/h
        val fastThreshold = baseline * 1.35f     // e.g. if baseline is 15, fast is > 20.2%/h

        // Thermal limitation evidence check
        val isThermalElevated = temperatureCelsius >= 41.0f || (temperatureCelsius >= 39.0f && temperatureTrend == "RISING")
        // High load context check
        val isHighLoad = isScreenOn && (validPower != null && validPower >= 5.0f && measuredRatePctPerHr < normalLowerBound)

        // 4. FAST CHARGING
        // Fast if measured rate is substantially above baseline AND telemetry supports it (elevated power/current)
        val isSlopeFast = measuredRatePctPerHr >= fastThreshold
        val isPowerFast = validPower != null && validPower >= 15.0f
        if (isSlopeFast || (measuredRatePctPerHr >= baseline && isPowerFast)) {
            return EvidenceAssessment(
                state = EvidenceChargingState.FAST,
                measuredRatePctPerHr = measuredRatePctPerHr,
                inputPowerW = validPower,
                currentMa = if (absCurrent > 0) absCurrent else null,
                voltageV = validVoltage,
                powerSource = powerSource,
                explanation = "Fast Charging — Measured charging rate (${String.format(Locale.US, "%.1f", measuredRatePctPerHr)}%/h) elevated above device baseline (${String.format(Locale.US, "%.1f", baseline)}%/h)",
                deviceBaselineRatePctPerHr = baseline
            )
        }

        // 2. SLOW CHARGING
        // Slow if measured rate is significantly below device baseline
        if (measuredRatePctPerHr < normalLowerBound && sessionDurationSeconds >= 20) {
            val state = when {
                isThermalElevated -> EvidenceChargingState.SLOW_THERMAL_LIMITED
                isHighLoad -> EvidenceChargingState.SLOW_LOAD_LIMITED
                else -> EvidenceChargingState.SLOW
            }
            val reasonText = when {
                isThermalElevated -> "Slow Charging — Thermal Limited (Battery temp: ${String.format(Locale.US, "%.1f", temperatureCelsius)}°C)"
                isHighLoad -> "Slow Charging — Effective charging reduced by high device/screen load"
                else -> "Slow Charging — Measured rate (${String.format(Locale.US, "%.1f", measuredRatePctPerHr)}%/h) below device baseline (${String.format(Locale.US, "%.1f", baseline)}%/h)"
            }
            return EvidenceAssessment(
                state = state,
                measuredRatePctPerHr = measuredRatePctPerHr,
                inputPowerW = validPower,
                currentMa = if (absCurrent > 0) absCurrent else null,
                voltageV = validVoltage,
                powerSource = powerSource,
                explanation = reasonText,
                isThermalThrottlingDetected = isThermalElevated,
                isHeavyLoadDetected = isHighLoad,
                deviceBaselineRatePctPerHr = baseline
            )
        }

        // 3. NORMAL CHARGING (Default stable operating band)
        return EvidenceAssessment(
            state = EvidenceChargingState.NORMAL,
            measuredRatePctPerHr = measuredRatePctPerHr,
            inputPowerW = validPower,
            currentMa = if (absCurrent > 0) absCurrent else null,
            voltageV = validVoltage,
            powerSource = powerSource,
            explanation = "Normal Charging — Operating within learned device baseline band (${String.format(Locale.US, "%.1f", baseline)}%/h)",
            deviceBaselineRatePctPerHr = baseline
        )
    }
}
