package com.example.battery.engine

import kotlin.math.abs

/**
 * Clean prediction engine for charging and discharge times.
 * Only returns estimates when valid velocity is present.
 */
object BatteryPredictionEngine {

    /**
     * Calculates estimated minutes to full charge (100%).
     * Returns null if velocity is invalid or not actively charging.
     */
    fun estimateTimeToFullMinutes(
        currentPercentage: Int,
        velocityPercentPerHour: Float?
    ): Long? {
        if (currentPercentage < 0 || currentPercentage >= 100) return 0L
        if (velocityPercentPerHour == null || velocityPercentPerHour <= 0.1f) return null

        val remainingPercent = 100 - currentPercentage
        val hoursRemaining = remainingPercent / velocityPercentPerHour
        val minutesRemaining = (hoursRemaining * 60f).toLong()

        return minutesRemaining.coerceAtLeast(0L)
    }

    /**
     * Calculates estimated minutes to empty (0%).
     * Returns null if velocity is invalid or not discharging.
     */
    fun estimateTimeToEmptyMinutes(
        currentPercentage: Int,
        velocityPercentPerHour: Float?
    ): Long? {
        if (currentPercentage <= 0) return 0L
        if (velocityPercentPerHour == null || velocityPercentPerHour >= -0.1f) return null

        val dischargeRatePerHour = abs(velocityPercentPerHour)
        val hoursRemaining = currentPercentage / dischargeRatePerHour
        val minutesRemaining = (hoursRemaining * 60f).toLong()

        return minutesRemaining.coerceAtLeast(0L)
    }
}
