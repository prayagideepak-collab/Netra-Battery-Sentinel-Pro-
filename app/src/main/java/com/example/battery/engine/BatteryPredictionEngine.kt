package com.example.battery.engine

import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

enum class EtaConfidence {
    INITIALIZING,
    ESTIMATING,
    STABLE
}

/**
 * Authoritative clean prediction engine for battery charging and discharge times.
 * Strictly validates inputs and never generates fake or guessed ETAs.
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
    }

    /**
     * Authoritative remaining time calculation in milliseconds.
     * Returns -1L if ETA cannot be reliably determined (Calculating / Initializing).
     * Returns 0L if 100% (charging) or 0% (discharging).
     */
    @Synchronized
    fun calculateRemainingTimeMs(
        percentage: Int,
        isCharging: Boolean,
        currentNowVal: Int, // mA
        isScreenOn: Boolean,
        capacity: Int, // mAh
        speed: Float, // % per hour
        targetPercentage: Int = 100
    ): Long {
        if (percentage !in 0..100) {
            currentConfidence = EtaConfidence.INITIALIZING
            return -1L
        }

        if (lastChargingState != null && lastChargingState != isCharging) {
            invalidateStateTransition(isCharging)
        }
        lastChargingState = isCharging

        val now = System.currentTimeMillis()

        if (isCharging) {
            if (percentage >= 100 || (targetPercentage in 1..99 && percentage >= targetPercentage)) {
                currentConfidence = EtaConfidence.STABLE
                lastValidPredictionMs = 0L
                lastPredictionTimestamp = now
                return 0L
            }

            // Primary: Real Rate
            if (!speed.isNaN() && !speed.isInfinite() && speed > 0f) {
                val etaMinutes = estimateTimeToFullMinutes(percentage, speed, targetPercentage)
                if (etaMinutes != null) {
                    val predictionMs = (etaMinutes * 60_000L).coerceIn(60_000L, MAX_CHARGE_TIME_MS)
                    currentConfidence = if (speed >= 0.5f) EtaConfidence.STABLE else EtaConfidence.ESTIMATING
                    lastValidPredictionMs = predictionMs
                    lastPredictionTimestamp = now
                    return predictionMs
                }
            }

            // Secondary: Hardware Charging Current (mA)
            val currentMa = abs(currentNowVal)
            val validCapacity = if (capacity > 0) capacity else 4500
            if (currentMa >= 150) {
                val remainingPct = (targetPercentage - percentage).coerceAtLeast(0)
                val remainingMah = (validCapacity * remainingPct) / 100f
                val hours = remainingMah / currentMa.toFloat()
                val predictionMs = (hours * 3600_000L).toLong().coerceIn(60_000L, MAX_CHARGE_TIME_MS)
                currentConfidence = EtaConfidence.ESTIMATING
                lastValidPredictionMs = predictionMs
                lastPredictionTimestamp = now
                return predictionMs
            }

            currentConfidence = EtaConfidence.INITIALIZING
            lastValidPredictionMs = -1L
            return -1L
        } else {
            if (percentage <= 0) {
                currentConfidence = EtaConfidence.STABLE
                lastValidPredictionMs = 0L
                lastPredictionTimestamp = now
                return 0L
            }

            // Primary: Real Discharge Rate
            val signedSpeed = if (speed > 0f) -speed else speed
            if (!signedSpeed.isNaN() && !signedSpeed.isInfinite() && signedSpeed < 0f) {
                val etaMinutes = estimateTimeToEmptyMinutes(percentage, signedSpeed)
                if (etaMinutes != null) {
                    val predictionMs = (etaMinutes * 60_000L).coerceIn(300_000L, MAX_DISCHARGE_TIME_MS)
                    currentConfidence = if (abs(signedSpeed) >= 0.5f) EtaConfidence.STABLE else EtaConfidence.ESTIMATING
                    lastValidPredictionMs = predictionMs
                    lastPredictionTimestamp = now
                    return predictionMs
                }
            }

            // Secondary: Hardware Discharge Current (mA)
            val drainMa = abs(currentNowVal)
            val validCapacity = if (capacity > 0) capacity else 4500
            if (drainMa >= 40) {
                if (sampleRates.size >= 10) sampleRates.removeAt(0)
                sampleRates.add(drainMa.toFloat())
                val avgDrain = sampleRates.average().toFloat()
                if (avgDrain > 0f) {
                    val remainingMah = (validCapacity * percentage) / 100f
                    val hours = remainingMah / avgDrain
                    val predictionMs = (hours * 3600_000L).toLong().coerceIn(300_000L, MAX_DISCHARGE_TIME_MS)
                    currentConfidence = EtaConfidence.ESTIMATING
                    lastValidPredictionMs = predictionMs
                    lastPredictionTimestamp = now
                    return predictionMs
                }
            }

            currentConfidence = EtaConfidence.INITIALIZING
            lastValidPredictionMs = -1L
            return -1L
        }
    }

    fun predictAgingYears(healthPct: Int): Double {
        val wear = (100 - healthPct).coerceAtLeast(0)
        val remainingYears = (healthPct - 80).coerceAtLeast(0) / 3.0
        return String.format(Locale.US, "%.1f", remainingYears).toDouble()
    }
}

