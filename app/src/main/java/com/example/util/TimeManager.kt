package com.example.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Netra Ecosystem Time Manager & Centralized Time Engine
 * Standardizes all running time, countdown, ETA, and duration formats.
 * Made with ❤️ by Prayagi Ji
 */
object TimeManager {

    /**
     * Formats current system time dynamically using the device's actual timezone.
     * Format: DD MMM YYYY, hh:mm:ss.SS a
     * Example: "15 Aug 2026, 10:42:31.25 PM"
     * AM/PM is mandatory.
     */
    fun formatCurrentClock(timeMs: Long = System.currentTimeMillis()): String {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timeMs
        
        // Date part: "dd MMM yyyy"
        val dateSdf = SimpleDateFormat("dd MMM yyyy", Locale.US)
        val dateStr = dateSdf.format(calendar.time)
        
        // Time part: "hh:mm:ss"
        val timeSdf = SimpleDateFormat("hh:mm:ss", Locale.US)
        val timeStr = timeSdf.format(calendar.time)
        
        // AM/PM marker (mandatory)
        val amPmSdf = SimpleDateFormat("a", Locale.US)
        val amPmStr = amPmSdf.format(calendar.time)
        
        return "$dateStr, $timeStr $amPmStr"
    }

    /**
     * Converts duration in milliseconds into standard duration format without milliseconds.
     * Less than 24 hours: HH:mm:ss
     * 24 hours or more: d HH:mm:ss (e.g. "1d 02:23:22")
     * AM/PM is never appended.
     */
    fun formatDurationMs(ms: Long): String {
        val totalMs = if (ms < 0L) 0L else ms
        val totalSeconds = totalMs / 1000L
        
        val totalMinutes = totalSeconds / 60L
        val totalHours = totalMinutes / 60L
        
        val seconds = totalSeconds % 60L
        val minutes = totalMinutes % 60L
        
        return if (totalHours >= 24) {
            val days = totalHours / 24L
            val hours = totalHours % 24L
            String.format(
                Locale.US,
                "%dd %02d:%02d:%02d",
                days,
                hours,
                minutes,
                seconds
            )
        } else {
            val hours = totalHours
            String.format(
                Locale.US,
                "%02d:%02d:%02d",
                hours,
                minutes,
                seconds
            )
        }
    }

    /**
     * Converts total seconds into standard duration format with hundredths of seconds.
     */
    fun formatDurationSeconds(totalSeconds: Long): String {
        return formatDurationMs(totalSeconds * 1000L)
    }

    /**
     * Converts minutes into standard duration format with hundredths of seconds.
     */
    fun formatMinutes(minutes: Int): String {
        return formatDurationMs(minutes.toLong() * 60L * 1000L)
    }

    /**
     * Calculates charging ETA in milliseconds. Returns -1L if unavailable.
     */
    fun calculateChargingEtaMs(percentage: Int, timeTo100Min: Int): Long {
        if (percentage >= 100) return 0L
        if (timeTo100Min > 0) {
            return timeTo100Min * 60L * 1000L
        }
        return -1L // Signal: Calculating / Insufficient Telemetry
    }

    /**
     * Calculates discharge remaining time in milliseconds. Returns -1L if unavailable.
     */
    fun calculateDischargeRemainingMs(percentage: Int, speed: Float): Long {
        if (percentage <= 0) return 0L
        val dischargeMinutes = com.example.battery.engine.BatteryPredictionEngine.estimateTimeToEmptyMinutes(
            currentPercentage = percentage,
            velocityPercentPerHour = if (speed > 0f) -speed else speed
        )
        return dischargeMinutes?.let { it * 60L * 1000L } ?: -1L
    }

    /**
     * Timezone-safe start of local calendar day (00:00:00.000)
     */
    fun getStartOfLocalDay(timeMs: Long = System.currentTimeMillis()): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timeMs
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    /**
     * Timezone-safe end of local calendar day (23:59:59.999)
     */
    fun getEndOfLocalDay(timeMs: Long = System.currentTimeMillis()): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timeMs
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        return calendar.timeInMillis
    }

    /**
     * Timezone-safe start of next local calendar day (00:00:00.000 next day)
     */
    fun getStartOfNextLocalDay(timeMs: Long = System.currentTimeMillis()): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timeMs
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        return calendar.timeInMillis
    }

    /**
     * Formats calendar date in readable form (e.g. "03 Sep 2026")
     */
    fun formatCalendarDate(timeMs: Long): String {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timeMs
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.US)
        return sdf.format(calendar.time)
    }

    /**
     * Checks if given timestamp falls on the local calendar day of today
     */
    fun isToday(timeMs: Long): Boolean {
        return getStartOfLocalDay(System.currentTimeMillis()) == getStartOfLocalDay(timeMs)
    }

    /**
     * Moves timestamp by day offset (+1 or -1) in local timezone
     */
    fun addDaysToTimestamp(timeMs: Long, days: Int): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timeMs
        calendar.add(Calendar.DAY_OF_YEAR, days)
        return getStartOfLocalDay(calendar.timeInMillis)
    }
}

