package com.example.battery.engine

import com.example.battery.model.BatterySample
import java.util.ArrayDeque
import kotlin.math.abs

/**
 * In-memory velocity calculation engine for battery rate of change.
 * Maintains rolling samples to calculate % per hour safely and deterministically.
 * Uses linear regression and outlier protection.
 */
class BatteryVelocityEngine(
    private val maxSamples: Int = 10,
    private val minSampleIntervalMs: Long = 10_000L // Minimum 10 seconds between distinct samples
) {
    private val samples = ArrayDeque<BatterySample>()
    private var lastChargingState: Boolean? = null

    @Synchronized
    fun onChargingStateChanged(isCharging: Boolean) {
        if (lastChargingState != null && lastChargingState != isCharging) {
            clear()
        }
        lastChargingState = isCharging
    }

    @Synchronized
    fun addSample(sample: BatterySample): Float? {
        // 1. Validate percentage strictly
        if (sample.percentage !in 0..100) {
            return calculateCurrentVelocity()
        }

        // 2. Validate timestamp
        if (sample.timestamp <= 0L) {
            return calculateCurrentVelocity()
        }

        val lastSample = samples.peekLast()
        if (lastSample != null) {
            // Reject duplicate or backwards timestamps
            if (sample.timestamp <= lastSample.timestamp) {
                return calculateCurrentVelocity()
            }

            val deltaTimeMs = sample.timestamp - lastSample.timestamp
            // Reject interval below minimum threshold
            if (deltaTimeMs < minSampleIntervalMs) {
                return calculateCurrentVelocity()
            }

            // Outlier & jump protection:
            // Check for unrealistic percentage jump in a short time
            val deltaPct = abs(sample.percentage - lastSample.percentage)
            val deltaHours = deltaTimeMs / 3_600_000f
            if (deltaHours > 0f) {
                val instantRate = deltaPct / deltaHours
                // Maximum physically possible rate is ~300%/hr for ultra-fast charging or extreme discharge
                if (instantRate > 300f && deltaPct > 3) {
                    // Reject abnormal sudden jump reading
                    return calculateCurrentVelocity()
                }
            }
        }

        samples.addLast(sample)
        while (samples.size > maxSamples) {
            samples.removeFirst()
        }

        return calculateCurrentVelocity()
    }

    @Synchronized
    fun calculateCurrentVelocity(): Float? {
        if (samples.size < 2) return null

        val first = samples.peekFirst() ?: return null
        val last = samples.peekLast() ?: return null

        val totalDeltaTimeMs = last.timestamp - first.timestamp
        if (totalDeltaTimeMs <= 0L) return null

        // Require at least 10 seconds total span between first and last sample
        if (totalDeltaTimeMs < minSampleIntervalMs) return null

        val sampleList = samples.toList()
        val n = sampleList.size
        if (n < 2) return null

        // Linear regression: y = battery percentage, x = elapsed time in hours from first sample
        val t0 = first.timestamp
        var sumX = 0.0
        var sumY = 0.0
        var sumXY = 0.0
        var sumX2 = 0.0

        for (s in sampleList) {
            val x = (s.timestamp - t0).toDouble() / 3_600_000.0 // hours
            val y = s.percentage.toDouble()
            sumX += x
            sumY += y
            sumXY += (x * y)
            sumX2 += (x * x)
        }

        val denominator = (n * sumX2) - (sumX * sumX)
        if (abs(denominator) < 1e-9) {
            // Fallback to simple endpoint slope if denominator is degenerate
            val totalHours = totalDeltaTimeMs / 3_600_000f
            if (totalHours <= 0f) return null
            val rawSlope = (last.percentage - first.percentage) / totalHours
            if (rawSlope.isNaN() || rawSlope.isInfinite()) return null
            return rawSlope
        }

        val slope = ((n * sumXY) - (sumX * sumY)) / denominator
        val floatSlope = slope.toFloat()

        if (floatSlope.isNaN() || floatSlope.isInfinite()) return null

        return floatSlope
    }

    @Synchronized
    fun clear() {
        samples.clear()
        lastChargingState = null
    }

    @Synchronized
    fun getSampleCount(): Int = samples.size
}

