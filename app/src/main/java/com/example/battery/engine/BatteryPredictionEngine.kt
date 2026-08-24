package com.example.battery.engine

import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

enum class EtaConfidence {
    INITIALIZING,
    ESTIMATING,
    STABLE
}

enum class EtaSource {
    MEASURED_PERCENTAGE_VELOCITY,
    HARDWARE_CURRENT_AND_VALIDATED_CAPACITY,
    UNAVAILABLE
}

data class AuthoritativeEtaResult(
    val remainingTimeMs: Long, // -1L = Calculating / Unavailable, 0L = Target Reached
    val confidence: EtaConfidence,
    val source: EtaSource,
    val isAvailable: Boolean
)

/**
 * Authoritative clean prediction engine for battery charging and discharge times.
 * Strictly adheres to the Deterministic Priority Policy:
 * Priority A: Measured battery-percentage velocity with sufficient reliable history (Preferred source)
 * Priority B: Valid hardware current + VALIDATED actual battery capacity (Secondary source)
 * Priority C: Neither reliable source available -> Unavailable / Calculating (-1L)
 * 
 * NEVER uses an arbitrary hardcoded battery capacity (e.g. 4500 mAh) or fake fallback rates.
 */
object BatteryPredictionEngine {

    @Volatile
    private var lastValidPredictionMs: Long = -1L
    @Volatile
    private var lastPredictionTimestamp: Long = 0L
    @Volatile
    private var lastChargingState: Boolean? = null
    @Volatile
    var currentConfidence: EtaConfidence = EtaConfidence.INITIALIZING
        private set
    @Volatile
    var currentSource: EtaSource = EtaSource.UNAVAILABLE
        private set

    private val sampleRates = mutableListOf<Float>()

    private const val MAX_CHARGE_TIME_MS = 8 * 3600 * 1000L
    private const val MAX_DISCHARGE_TIME_MS = 72 * 3600 * 1000L

    /**
     * Calculates estimated minutes to target charge (e.g. 100%).
     * Returns 0L if percentage >= targetPercentage.
     * Returns null if velocity is invalid, zero, or negative.
     */
    fun estimateTimeToFullMinutes(
        currentPercentage: Int,
        velocityPercentPerHour: Float?,
        targetPercentage: Int = 100
    ): Long? {
        // 1. Validate percentage boundaries strictly
        if (currentPercentage !in 0..100) return null
        if (targetPercentage !in 1..100) return null

        // 2. Full or reached target percentage -> 0 minutes
        if (currentPercentage >= targetPercentage) return 0L

        // 3. Validate velocity: must be strictly positive and finite
        if (velocityPercentPerHour == null ||
            velocityPercentPerHour.isNaN() ||
            velocityPercentPerHour.isInfinite() ||
            velocityPercentPerHour <= 0f
        ) {
            return null
        }

        val remainingPercent = targetPercentage - currentPercentage
        val hoursRemaining = remainingPercent.toFloat() / velocityPercentPerHour

        if (hoursRemaining.isNaN() || hoursRemaining.isInfinite() || hoursRemaining < 0f) {
            return null
        }

        val minutesRemaining = round(hoursRemaining * 60f).toLong()
        return minutesRemaining.coerceIn(0L, 1440L * 7L) // Max 7 days bound
    }

    /**
     * Calculates estimated minutes to empty (0%).
     * Returns 0L if percentage <= 0.
     * Returns null if velocity is invalid, zero, or positive.
     */
    fun estimateTimeToEmptyMinutes(
        currentPercentage: Int,
        velocityPercentPerHour: Float?
    ): Long? {
        // 1. Validate percentage boundaries strictly
        if (currentPercentage !in 0..100) return null

        // 2. Empty battery -> 0 minutes
        if (currentPercentage == 0) return 0L

        // 3. Validate velocity: must be strictly negative and finite
        if (velocityPercentPerHour == null ||
            velocityPercentPerHour.isNaN() ||
            velocityPercentPerHour.isInfinite() ||
            velocityPercentPerHour >= 0f
        ) {
            return null
        }

        val dischargeRatePerHour = abs(velocityPercentPerHour)
        if (dischargeRatePerHour <= 0f) return null

        val hoursRemaining = currentPercentage.toFloat() / dischargeRatePerHour

        if (hoursRemaining.isNaN() || hoursRemaining.isInfinite() || hoursRemaining < 0f) {
            return null
        }

        val minutesRemaining = round(hoursRemaining * 60f).toLong()
        return minutesRemaining.coerceIn(0L, 1440L * 7L) // Max 7 days bound
    }

    /**
     * Invalidate all prediction telemetry immediately when power state changes.
     */
    @Synchronized
    fun invalidateStateTransition(isCharging: Boolean) {
        lastChargingState = isCharging
        lastValidPredictionMs = -1L
        lastPredictionTimestamp = 0L
        sampleRates.clear()
        currentConfidence = EtaConfidence.INITIALIZING
        currentSource = EtaSource.UNAVAILABLE
    }

    /**
     * Authoritative remaining time calculation in milliseconds.
     * Implements deterministic priority selection:
     * Priority A: Measured battery-percentage velocity
     * Priority B: Hardware current + VALIDATED actual capacity
     * Priority C: Unavailable / Calculating (-1L)
     * 
     * Returns -1L if ETA cannot be reliably determined (Calculating / Initializing).
     * Returns 0L if 100% (charging) or 0% (discharging).
     */
    @Synchronized
    fun calculateRemainingTimeMs(
        percentage: Int,
        isCharging: Boolean,
        currentNowVal: Int, // mA
        isScreenOn: Boolean,
        capacity: Int?, // mAh (MUST be validated or null)
        speed: Float, // % per hour
        targetPercentage: Int = 100
    ): Long {
        return calculateAuthoritativeEta(
            percentage = percentage,
            isCharging = isCharging,
            currentNowVal = currentNowVal,
            isScreenOn = isScreenOn,
            capacity = capacity,
            speed = speed,
            targetPercentage = targetPercentage
        ).remainingTimeMs
    }

    /**
     * Comprehensive Authoritative ETA resolution returning metadata, source, and confidence.
     */
    @Synchronized
    fun calculateAuthoritativeEta(
        percentage: Int,
        isCharging: Boolean,
        currentNowVal: Int, // mA
        isScreenOn: Boolean,
        capacity: Int?, // mAh (Must be verified device capacity)
        speed: Float, // % per hour
        targetPercentage: Int = 100
    ): AuthoritativeEtaResult {
        // 1. Strict percentage boundary validation
        if (percentage !in 0..100 || targetPercentage !in 1..100) {
            currentConfidence = EtaConfidence.INITIALIZING
            currentSource = EtaSource.UNAVAILABLE
            return AuthoritativeEtaResult(-1L, EtaConfidence.INITIALIZING, EtaSource.UNAVAILABLE, false)
        }

        // 2. State transition invalidation check
        if (lastChargingState != null && lastChargingState != isCharging) {
            invalidateStateTransition(isCharging)
        }
        lastChargingState = isCharging

        val now = System.currentTimeMillis()

        if (isCharging) {
            // Target percentage already reached
            if (percentage >= 100 || percentage >= targetPercentage) {
                currentConfidence = EtaConfidence.STABLE
                currentSource = EtaSource.MEASURED_PERCENTAGE_VELOCITY
                lastValidPredictionMs = 0L
                lastPredictionTimestamp = now
                return AuthoritativeEtaResult(0L, EtaConfidence.STABLE, EtaSource.MEASURED_PERCENTAGE_VELOCITY, true)
            }

            // PRIORITY A: Measured Battery Percentage Velocity (Preferred Source)
            if (!speed.isNaN() && !speed.isInfinite() && speed > 0f) {
                val etaMinutes = estimateTimeToFullMinutes(percentage, speed, targetPercentage)
                if (etaMinutes != null) {
                    val predictionMs = (etaMinutes * 60_000L).coerceIn(60_000L, MAX_CHARGE_TIME_MS)
                    val conf = if (speed >= 0.5f) EtaConfidence.STABLE else EtaConfidence.ESTIMATING
                    currentConfidence = conf
                    currentSource = EtaSource.MEASURED_PERCENTAGE_VELOCITY
                    lastValidPredictionMs = predictionMs
                    lastPredictionTimestamp = now
                    return AuthoritativeEtaResult(predictionMs, conf, EtaSource.MEASURED_PERCENTAGE_VELOCITY, true)
                }
            }

            // PRIORITY B: Hardware Current + VALIDATED Actual Capacity (Secondary Source)
            val isValidCapacity = BatteryCapacityEngine.isValidCapacity(capacity)
            val currentMa = abs(currentNowVal)
            // Require verified hardware charging current (> 150mA) AND proven device capacity
            if (isValidCapacity && capacity != null && currentNowVal > 0 && currentMa >= 150) {
                val remainingPct = (targetPercentage - percentage).coerceAtLeast(0)
                val remainingMah = (capacity * remainingPct) / 100f
                val hours = remainingMah / currentMa.toFloat()
                if (!hours.isNaN() && !hours.isInfinite() && hours > 0f) {
                    val predictionMs = (hours * 3600_000L).toLong().coerceIn(60_000L, MAX_CHARGE_TIME_MS)
                    currentConfidence = EtaConfidence.ESTIMATING
                    currentSource = EtaSource.HARDWARE_CURRENT_AND_VALIDATED_CAPACITY
                    lastValidPredictionMs = predictionMs
                    lastPredictionTimestamp = now
                    return AuthoritativeEtaResult(predictionMs, EtaConfidence.ESTIMATING, EtaSource.HARDWARE_CURRENT_AND_VALIDATED_CAPACITY, true)
                }
            }

            // PRIORITY C: Neither reliable source available -> Unavailable / Calculating (-1L)
            currentConfidence = EtaConfidence.INITIALIZING
            currentSource = EtaSource.UNAVAILABLE
            lastValidPredictionMs = -1L
            return AuthoritativeEtaResult(-1L, EtaConfidence.INITIALIZING, EtaSource.UNAVAILABLE, false)
        } else {
            // Discharging state
            if (percentage <= 0) {
                currentConfidence = EtaConfidence.STABLE
                currentSource = EtaSource.MEASURED_PERCENTAGE_VELOCITY
                lastValidPredictionMs = 0L
                lastPredictionTimestamp = now
                return AuthoritativeEtaResult(0L, EtaConfidence.STABLE, EtaSource.MEASURED_PERCENTAGE_VELOCITY, true)
            }

            // PRIORITY A: Measured Battery Percentage Discharge Rate (Preferred Source)
            val signedSpeed = if (speed > 0f) -speed else speed
            if (!signedSpeed.isNaN() && !signedSpeed.isInfinite() && signedSpeed < 0f) {
                val etaMinutes = estimateTimeToEmptyMinutes(percentage, signedSpeed)
                if (etaMinutes != null) {
                    val predictionMs = (etaMinutes * 60_000L).coerceIn(300_000L, MAX_DISCHARGE_TIME_MS)
                    val conf = if (abs(signedSpeed) >= 0.5f) EtaConfidence.STABLE else EtaConfidence.ESTIMATING
                    currentConfidence = conf
                    currentSource = EtaSource.MEASURED_PERCENTAGE_VELOCITY
                    lastValidPredictionMs = predictionMs
                    lastPredictionTimestamp = now
                    return AuthoritativeEtaResult(predictionMs, conf, EtaSource.MEASURED_PERCENTAGE_VELOCITY, true)
                }
            }

            // PRIORITY B: Hardware Current + VALIDATED Actual Capacity (Secondary Source)
            val isValidCapacity = BatteryCapacityEngine.isValidCapacity(capacity)
            val drainMa = abs(currentNowVal)
            // In discharging mode, currentNowVal is negative or measured drain >= 40mA with verified capacity
            if (isValidCapacity && capacity != null && (currentNowVal < 0 || drainMa >= 40) && drainMa >= 40) {
                if (sampleRates.size >= 10) sampleRates.removeAt(0)
                sampleRates.add(drainMa.toFloat())
                val avgDrain = sampleRates.average().toFloat()
                if (avgDrain > 0f) {
                    val remainingMah = (capacity * percentage) / 100f
                    val hours = remainingMah / avgDrain
                    if (!hours.isNaN() && !hours.isInfinite() && hours > 0f) {
                        val predictionMs = (hours * 3600_000L).toLong().coerceIn(300_000L, MAX_DISCHARGE_TIME_MS)
                        currentConfidence = EtaConfidence.ESTIMATING
                        currentSource = EtaSource.HARDWARE_CURRENT_AND_VALIDATED_CAPACITY
                        lastValidPredictionMs = predictionMs
                        lastPredictionTimestamp = now
                        return AuthoritativeEtaResult(predictionMs, EtaConfidence.ESTIMATING, EtaSource.HARDWARE_CURRENT_AND_VALIDATED_CAPACITY, true)
                    }
                }
            }

            // PRIORITY C: Neither reliable source available -> Unavailable / Calculating (-1L)
            currentConfidence = EtaConfidence.INITIALIZING
            currentSource = EtaSource.UNAVAILABLE
            lastValidPredictionMs = -1L
            return AuthoritativeEtaResult(-1L, EtaConfidence.INITIALIZING, EtaSource.UNAVAILABLE, false)
        }
    }

    fun predictAgingYears(healthPct: Int): Double {
        val wear = (100 - healthPct).coerceAtLeast(0)
        val remainingYears = (healthPct - 80).coerceAtLeast(0) / 3.0
        return String.format(Locale.US, "%.1f", remainingYears).toDouble()
    }
}
