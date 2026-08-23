package com.example.engines.idmse

import android.content.Context
import android.util.Log
import com.example.data.BatteryDatabase
import com.example.data.BatteryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.io.File

object IdmseHealthMonitor {
    private const val TAG = "IDMSE_HealthMonitor"

    data class HealthReport(
        val databaseSizeKb: Long,
        val totalRecordsCount: Long,
        val readSpeedMs: Float,
        val writeSpeedMs: Float,
        val integrityStatus: String,
        val quarantinedCount: Long
    )

    suspend fun checkHealth(context: Context): HealthReport = withContext(Dispatchers.IO) {
        val dbFile = context.getDatabasePath("battery_voice_assistant_db")
        val sizeKb = if (dbFile.exists()) dbFile.length() / 1024 else 0

        val db = BatteryDatabase.getDatabase(context)
        val repo = BatteryRepository(db.batteryDao())

        // 1. Measure Read Speed
        val readStart = System.nanoTime()
        val settings = repo.settings.firstOrNull()
        val readDurationMs = (System.nanoTime() - readStart) / 1_000_000f

        // 2. Measure Write Speed
        val writeStart = System.nanoTime()
        if (settings != null) {
            repo.updateSettings(settings)
        } else {
            repo.getSettingsOrInit()
        }
        val writeDurationMs = (System.nanoTime() - writeStart) / 1_000_000f

        // 3. Estimate Total Record Count
        val eventsCount = repo.allBatteryEvents.firstOrNull()?.size ?: 0
        val sessionsCount = repo.allSessions.firstOrNull()?.size ?: 0
        val dischargingCount = repo.allDischargingSessions.firstOrNull()?.size ?: 0
        val totalRecords = (eventsCount + sessionsCount + dischargingCount).toLong()

        val integrity = if (sizeKb > 50_000) "Warning (Large DB)" else "Optimal"

        HealthReport(
            databaseSizeKb = sizeKb,
            totalRecordsCount = totalRecords,
            readSpeedMs = readDurationMs,
            writeSpeedMs = writeDurationMs,
            integrityStatus = integrity,
            quarantinedCount = 0L
        )
    }
}
