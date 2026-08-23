package com.example.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DiagnosticLogEntry(
    val timestamp: Long,
    val category: String,
    val title: String,
    val details: String,
    val batteryLevel: Int,
    val temperature: Float,
    val voltage: Float,
    val status: String
) {
    val formattedTime: String
        get() = SimpleDateFormat("hh:mm:ss.SSS a", Locale.US).format(Date(timestamp))
}

object DiagnosticLogger {
    private const val TAG = "DiagnosticLogger"
    private const val LOG_DIR_NAME = "diagnostic_logs"
    private const val LOG_FILE_NAME = "battery_system_diagnostics.log"

    private fun getLogFile(context: Context): File {
        val dir = File(context.filesDir, LOG_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val file = File(dir, LOG_FILE_NAME)
        if (!file.exists()) {
            file.createNewFile()
        }
        return file
    }

    /**
     * Logs a system-level battery event directly to local storage.
     * Adheres strictly to the Absolute Truth policy - only logs measured system readings.
     */
    @Synchronized
    fun logEvent(
        context: Context,
        category: String,
        title: String,
        details: String,
        batteryLevel: Int,
        temperature: Float,
        voltage: Float,
        status: String
    ) {
        try {
            val file = getLogFile(context)
            val timestamp = System.currentTimeMillis()
            val timeStr = SimpleDateFormat("hh:mm:ss.SSS a", Locale.US).format(Date(timestamp))

            val line = "[$timeStr] [$category] $title | $details | Level: $batteryLevel% | Temp: ${temperature}°C | Voltage: ${voltage.toInt()}mV | Status: $status"

            PrintWriter(FileWriter(file, true)).use { out ->
                out.println(line)
            }
            Log.d(TAG, "Diagnostic event logged: $line")
        } catch (e: Exception) {
            Log.e(TAG, "Error writing to diagnostic log file", e)
        }
    }

    /**
     * Reads all entries from the local diagnostic log file.
     */
    @Synchronized
    fun readLogs(context: Context): List<DiagnosticLogEntry> {
        val entries = mutableListOf<DiagnosticLogEntry>()
        try {
            val file = getLogFile(context)
            if (!file.exists()) return entries

            val lines = file.readLines()
            for (line in lines) {
                if (line.isBlank()) continue
                // Parse standard formatted line:
                // [2026-07-25 13:00:15] [CATEGORY] Title | Details | Level: 78% | Temp: 35.0°C | Voltage: 3820mV | Status: Charging
                try {
                    val dateEnd = line.indexOf(']')
                    if (dateEnd <= 1) continue
                    val dateStr = line.substring(1, dateEnd)
                    val sdf = SimpleDateFormat("hh:mm:ss.SSS a", Locale.US)
                    val time = sdf.parse(dateStr)?.time ?: System.currentTimeMillis()

                    val catStart = line.indexOf('[', dateEnd)
                    val catEnd = line.indexOf(']', catStart)
                    val category = if (catStart != -1 && catEnd != -1) line.substring(catStart + 1, catEnd) else "SYSTEM"

                    val rest = line.substring(catEnd + 1).trim()
                    val parts = rest.split("|").map { it.trim() }

                    val title = if (parts.isNotEmpty()) parts[0] else "System Event"
                    val details = if (parts.size > 1) parts[1] else ""

                    var level = 0
                    var temp = 0f
                    var volt = 0f
                    var stat = "UNKNOWN"

                    for (p in parts) {
                        if (p.startsWith("Level:")) {
                            level = p.replace("Level:", "").replace("%", "").trim().toIntOrNull() ?: 0
                        } else if (p.startsWith("Temp:")) {
                            temp = p.replace("Temp:", "").replace("°C", "").trim().toFloatOrNull() ?: 0f
                        } else if (p.startsWith("Voltage:")) {
                            volt = p.replace("Voltage:", "").replace("mV", "").trim().toFloatOrNull() ?: 0f
                        } else if (p.startsWith("Status:")) {
                            stat = p.replace("Status:", "").trim()
                        }
                    }

                    entries.add(
                        DiagnosticLogEntry(
                            timestamp = time,
                            category = category,
                            title = title,
                            details = details,
                            batteryLevel = level,
                            temperature = temp,
                            voltage = volt,
                            status = stat
                        )
                    )
                } catch (pe: Exception) {
                    // Fallback entry if custom format
                    entries.add(
                        DiagnosticLogEntry(
                            timestamp = System.currentTimeMillis(),
                            category = "RAW",
                            title = "System Event",
                            details = line,
                            batteryLevel = 0,
                            temperature = 0f,
                            voltage = 0f,
                            status = "RAW"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading diagnostic log file", e)
        }
        return entries.reversed() // newest first
    }

    /**
     * Retrieves raw text from log file for export or troubleshooting.
     */
    @Synchronized
    fun getLogText(context: Context): String {
        return try {
            val file = getLogFile(context)
            if (file.exists()) file.readText() else "No diagnostic logs available."
        } catch (e: Exception) {
            "Error reading diagnostic log text: ${e.message}"
        }
    }

    /**
     * Clears all diagnostic logs.
     */
    @Synchronized
    fun clearLogs(context: Context) {
        try {
            val file = getLogFile(context)
            if (file.exists()) {
                file.writeText("")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing diagnostic log file", e)
        }
    }

    /**
     * Prunes log lines older than maximum timestamp.
     */
    @Synchronized
    fun pruneOldLogs(context: Context, cutoffTimeMs: Long) {
        try {
            val file = getLogFile(context)
            if (!file.exists()) return
            val logs = readLogs(context).filter { it.timestamp >= cutoffTimeMs }
            file.writeText("")
            logs.reversed().forEach { log ->
                logEvent(
                    context,
                    log.category,
                    log.title,
                    log.details,
                    log.batteryLevel,
                    log.temperature,
                    log.voltage,
                    log.status
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error pruning diagnostic log file", e)
        }
    }
}
