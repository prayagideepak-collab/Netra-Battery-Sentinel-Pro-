package com.example.engines.idmse

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.data.BatteryDatabase
import com.example.data.BatteryRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.io.File

object IdmseBackupEngine {
    private const val TAG = "IDMSE_BackupEngine"
    private const val LOCAL_BACKUP_FILENAME = "netra_local_backup.enc"

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(IdmseDataPayload::class.java)

    private fun encrypt(data: String): String {
        return Base64.encodeToString(data.toByteArray(Charsets.UTF_8), Base64.DEFAULT)
    }

    private fun decrypt(data: String): String {
        return String(Base64.decode(data, Base64.DEFAULT), Charsets.UTF_8)
    }

    /**
     * Create encrypted local backup file
     */
    suspend fun createLocalBackup(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val db = BatteryDatabase.getDatabase(context)
            val repo = BatteryRepository(db.batteryDao())

            val settings = repo.settings.firstOrNull() ?: repo.getSettingsOrInit()
            val sessions = repo.allSessions.firstOrNull() ?: emptyList()
            val discharging = repo.allDischargingSessions.firstOrNull() ?: emptyList()
            val trends = repo.allTrendLogs.firstOrNull() ?: emptyList()
            val events = repo.allBatteryEvents.firstOrNull() ?: emptyList()
            val magnetic = repo.allMagneticEvents.firstOrNull() ?: emptyList()
            val audit = repo.allSystemAuditRecords.firstOrNull() ?: emptyList()

            var payload = IdmseDataPayload(
                version = 1,
                timestamp = System.currentTimeMillis(),
                settings = settings,
                chargingSessions = sessions,
                dischargingSessions = discharging,
                trendLogs = trends,
                batteryEvents = events,
                magneticEvents = magnetic,
                auditRecords = audit,
                checksum = 0
            )

            val checksum = IdmseDataValidator.calculateChecksum(payload)
            payload = payload.copy(checksum = checksum)

            val jsonStr = adapter.toJson(payload) ?: return@withContext false
            val encryptedStr = encrypt(jsonStr)

            val file = File(context.filesDir, LOCAL_BACKUP_FILENAME)
            file.writeText(encryptedStr, Charsets.UTF_8)
            Log.i(TAG, "Local encrypted backup successfully saved to ${file.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed creating local backup", e)
            false
        }
    }

    /**
     * Restore data from local backup file
     */
    suspend fun restoreLocalBackup(context: Context): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val file = File(context.filesDir, LOCAL_BACKUP_FILENAME)
            if (!file.exists()) {
                return@withContext Pair(false, "No local backup file found.")
            }

            val encryptedStr = file.readText(Charsets.UTF_8)
            val jsonStr = decrypt(encryptedStr)

            val payload = adapter.fromJson(jsonStr)
                ?: return@withContext Pair(false, "Corrupted backup format.")

            if (!IdmseDataValidator.validatePayload(payload)) {
                return@withContext Pair(false, "Backup validation/checksum failed.")
            }

            val db = BatteryDatabase.getDatabase(context)
            val repo = BatteryRepository(db.batteryDao())

            // Merge settings
            payload.settings?.let { repo.updateSettings(it) }

            // Merge sessions without duplicate overwrite
            payload.chargingSessions.forEach { db.batteryDao().insertSession(it) }
            payload.dischargingSessions.forEach { db.batteryDao().insertDischargingSession(it) }

            // Log restore event
            repo.logBatteryEvent(
                eventType = "RESTORE_COMPLETE",
                title = "Local Data Restored",
                details = "Successfully restored backup from ${payload.timestamp}",
                category = "AUDIT",
                source = "IDMSE_BackupEngine"
            )

            Log.i(TAG, "Local backup restored successfully!")
            Pair(true, "Local backup restored successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed restoring local backup", e)
            Pair(false, "Restore error: ${e.message}")
        }
    }

    fun getLastBackupTimestamp(context: Context): Long {
        val file = File(context.filesDir, LOCAL_BACKUP_FILENAME)
        return if (file.exists()) file.lastModified() else 0L
    }
}
