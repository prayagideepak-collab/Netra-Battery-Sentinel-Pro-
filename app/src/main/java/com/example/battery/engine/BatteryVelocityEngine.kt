package com.example.battery.engine

import com.example.battery.model.BatterySample
import java.util.ArrayDeque

/**
 * In-memory velocity calculation engine for battery rate of change.
 * Maintains rolling samples to calculate % per hour safely without disk spam.
 */
class BatteryVelocityEngine(
    private val maxSamples: Int = 10,
    private val minSampleIntervalMs: Long = 10_000L // Minimum 10 seconds between samples
) {
    private val samples = ArrayDeque<BatterySample>()

    @Synchronized
    fun addSample(sample: BatterySample): Float? {
        val lastSample = samples.peekLast()
        if (lastSample != null) {
            val deltaTimeMs = sample.timestamp - lastSample.timestamp
            // Reject non-positive interval or duplicate timestamp
            if (deltaTimeMs < minSampleIntervalMs) {
                return calculateCurrentVelocity()
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

        val deltaTimeMs = last.timestamp - first.timestamp
        if (deltaTimeMs <= 0) return null

        val deltaBattery = last.percentage - first.percentage
        val hours = deltaTimeMs / 3_600_000f

        if (hours <= 0f) return null

        return deltaBattery / hours
    }

    @Synchronized
    fun clear() {
        samples.clear()
    }
}
