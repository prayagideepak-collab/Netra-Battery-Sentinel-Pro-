package com.example.engines.idmse

import com.example.data.*

enum class SyncState {
    SYNCED,
    SYNC_PENDING,
    SYNC_FAILED
}

enum class SyncType {
    LIVE_SYNC,
    BATCH_SYNC,
    MANUAL_SYNC,
    RECOVERY_SYNC,
    UPDATE_SYNC
}

enum class DataCategory {
    BATTERY,
    SENSOR,
    LOGS,
    HISTORY,
    USER_SETTINGS,
    AI_DATA
}

enum class ExportFilter {
    TODAY,
    YESTERDAY,
    CUSTOM_RANGE,
    ALL
}

data class IdmseMetrics(
    val syncState: SyncState = SyncState.SYNCED,
    val lastSyncTimestamp: Long = System.currentTimeMillis(),
    val databaseSizeKb: Long = 0,
    val totalRecordsCount: Long = 0,
    val lastBackupTimestamp: Long = 0,
    val corruptedRecordsQuarantined: Long = 0,
    val migrationsExecuted: Int = 0,
    val integrityStatus: String = "Optimal",
    val readSpeedMs: Float = 1.2f,
    val writeSpeedMs: Float = 2.4f,
    val isCloudBackupEnabled: Boolean = false
)

data class IdmseDataPayload(
    val version: Int = 1,
    val timestamp: Long = System.currentTimeMillis(),
    val settings: SettingsEntity? = null,
    val chargingSessions: List<ChargingSession> = emptyList(),
    val dischargingSessions: List<DischargingSession> = emptyList(),
    val trendLogs: List<BatteryTrendLog> = emptyList(),
    val batteryEvents: List<BatteryEvent> = emptyList(),
    val magneticEvents: List<MagneticEvent> = emptyList(),
    val auditRecords: List<SystemAuditRecord> = emptyList(),
    val checksum: Int = 0
)
