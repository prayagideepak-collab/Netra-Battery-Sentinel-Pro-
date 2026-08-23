package com.example.engines.idmse

import android.content.Context
import android.util.Log
import com.example.data.BatteryDatabase
import com.example.data.BatteryRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.util.Calendar

object IdmseExportImportManager {
    private const val TAG = "IDMSE_ExportImport"

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(IdmseDataPayload::class.java)

    /**
     * Export complete dataset based on filter date boundaries
     */
    suspend fun exportData(
        context: Context,
        filter: ExportFilter,
        startMs: Long = 0L,
        endMs: Long = System.currentTimeMillis()
    ): String = withContext(Dispatchers.IO) {
        val db = BatteryDatabase.getDatabase(context)
        val repo = BatteryRepository(db.batteryDao())

        val (fromTimestamp, toTimestamp) = getFilterBounds(filter, startMs, endMs)

        val settings = repo.settings.firstOrNull()
        val allCharging = repo.allSessions.firstOrNull() ?: emptyList()
        val allDischarging = repo.allDischargingSessions.firstOrNull() ?: emptyList()
        val allEvents = repo.allBatteryEvents.firstOrNull() ?: emptyList()
        val allMagnetic = repo.allMagneticEvents.firstOrNull() ?: emptyList()
        val allAudit = repo.allSystemAuditRecords.firstOrNull() ?: emptyList()

        val filteredCharging = allCharging.filter { it.startTime in fromTimestamp..toTimestamp }
        val filteredDischarging = allDischarging.filter { it.startTime in fromTimestamp..toTimestamp }
        val filteredEvents = allEvents.filter { it.timestamp in fromTimestamp..toTimestamp }
        val filteredMagnetic = allMagnetic.filter { it.timestamp in fromTimestamp..toTimestamp }
        val filteredAudit = allAudit.filter { it.timestamp in fromTimestamp..toTimestamp }

        var payload = IdmseDataPayload(
            version = 1,
            timestamp = System.currentTimeMillis(),
            settings = settings,
            chargingSessions = filteredCharging,
            dischargingSessions = filteredDischarging,
            batteryEvents = filteredEvents,
            magneticEvents = filteredMagnetic,
            auditRecords = filteredAudit,
            checksum = 0
        )

        val checksum = IdmseDataValidator.calculateChecksum(payload)
        payload = payload.copy(checksum = checksum)

        adapter.toJson(payload) ?: "{}"
    }

    /**
     * Import raw JSON string and merge intelligently with duplicate detection
     */
    suspend fun importData(context: Context, rawJson: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val payload = adapter.fromJson(rawJson)
                ?: return@withContext Pair(false, "Invalid JSON data structure.")

            if (!IdmseDataValidator.validatePayload(payload)) {
                return@withContext Pair(false, "Imported payload failed validation or checksum.")
            }

            val db = BatteryDatabase.getDatabase(context)
            val repo = BatteryRepository(db.batteryDao())

            var importedCount = 0

            payload.chargingSessions.forEach {
                db.batteryDao().insertSession(it)
                importedCount++
            }
            payload.dischargingSessions.forEach {
                db.batteryDao().insertDischargingSession(it)
                importedCount++
            }
            payload.batteryEvents.forEach {
                repo.logBatteryEvent(it.eventType, it.title, it.details, it.category, it.source)
                importedCount++
            }

            Log.i(TAG, "Successfully imported and merged $importedCount records.")
            Pair(true, "Successfully imported $importedCount records.")
        } catch (e: Exception) {
            Log.e(TAG, "Error importing data", e)
            Pair(false, "Import error: ${e.message}")
        }
    }

    private fun getFilterBounds(filter: ExportFilter, startMs: Long, endMs: Long): Pair<Long, Long> {
        val now = Calendar.getInstance()
        return when (filter) {
            ExportFilter.TODAY -> {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                Pair(cal.timeInMillis, System.currentTimeMillis())
            }
            ExportFilter.YESTERDAY -> {
                val calStart = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, -1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val calEnd = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, -1)
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }
                Pair(calStart.timeInMillis, calEnd.timeInMillis)
            }
            ExportFilter.CUSTOM_RANGE -> Pair(startMs, endMs)
            ExportFilter.ALL -> Pair(0L, System.currentTimeMillis() + 86400_000L)
        }
    }
}
