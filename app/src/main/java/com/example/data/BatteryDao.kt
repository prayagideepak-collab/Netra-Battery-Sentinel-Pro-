package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BatteryDao {
    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    fun getSettings(): Flow<SettingsEntity?>

    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettingsDirect(): SettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettings(settings: SettingsEntity)

    @Query("SELECT * FROM charging_sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<ChargingSession>>

    @Query("SELECT * FROM charging_sessions ORDER BY startTime DESC")
    suspend fun getAllSessionsDirect(): List<ChargingSession>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChargingSession): Long

    @Update
    suspend fun updateSession(session: ChargingSession)

    @Query("SELECT * FROM charging_sessions WHERE endTime IS NULL ORDER BY startTime DESC LIMIT 1")
    suspend fun getActiveSession(): ChargingSession?

    @Query("DELETE FROM charging_sessions")
    suspend fun clearAllHistory()

    @Query("SELECT * FROM app_consumption ORDER BY consumedMah DESC")
    fun getAllAppConsumption(): Flow<List<AppConsumptionEntity>>

    @Query("SELECT * FROM app_consumption ORDER BY consumedMah DESC")
    suspend fun getAllAppConsumptionDirect(): List<AppConsumptionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppConsumption(app: AppConsumptionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllAppConsumption(apps: List<AppConsumptionEntity>)

    @Query("DELETE FROM app_consumption")
    suspend fun clearAppConsumption()

    @Query("SELECT * FROM battery_trend_logs ORDER BY timestamp DESC")
    fun getAllTrendLogs(): Flow<List<BatteryTrendLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrendLog(log: BatteryTrendLog)

    @Query("SELECT * FROM battery_events ORDER BY timestamp DESC")
    fun getAllBatteryEvents(): Flow<List<BatteryEvent>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatteryEvent(event: BatteryEvent)

    @Query("DELETE FROM battery_events")
    suspend fun clearBatteryEvents()

    @Query("DELETE FROM battery_trend_logs")
    suspend fun clearTrendLogs()

    @Query("SELECT * FROM app_activity ORDER BY timestamp DESC")
    fun getAllAppActivity(): Flow<List<AppActivity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppActivity(activity: AppActivity)

    @Query("SELECT * FROM discharging_sessions ORDER BY startTime DESC")
    fun getAllDischargingSessions(): Flow<List<DischargingSession>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDischargingSession(session: DischargingSession): Long

    @Update
    suspend fun updateDischargingSession(session: DischargingSession)

    @Query("SELECT * FROM charging_sessions WHERE id = :id")
    suspend fun getChargingSession(id: Long): ChargingSession?

    @Query("SELECT * FROM discharging_sessions WHERE id = :id")
    suspend fun getDischargingSession(id: Long): DischargingSession?

    @Query("SELECT * FROM discharging_sessions WHERE endTime IS NULL ORDER BY startTime DESC LIMIT 1")
    suspend fun getActiveDischargingSession(): DischargingSession?

    @Query("DELETE FROM discharging_sessions")
    suspend fun clearDischargingSessions()

    @Query("DELETE FROM battery_events WHERE timestamp < :timestamp")
    suspend fun clearOldBatteryEvents(timestamp: Long)

    @Query("DELETE FROM discharging_sessions WHERE startTime < :timestamp")
    suspend fun clearOldDischargingSessions(timestamp: Long)

    @Query("DELETE FROM charging_sessions WHERE startTime < :timestamp")
    suspend fun clearOldChargingSessions(timestamp: Long)

    @Query("DELETE FROM app_activity")
    suspend fun clearAppActivity()

    @Query("SELECT * FROM magnetic_events ORDER BY timestamp DESC")
    fun getAllMagneticEvents(): Flow<List<MagneticEvent>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMagneticEvent(event: MagneticEvent): Long

    @Query("DELETE FROM magnetic_events")
    suspend fun clearMagneticEvents()

    @Query("DELETE FROM magnetic_events WHERE id = :id")
    suspend fun deleteMagneticEvent(id: Long)

    @Query("SELECT * FROM system_audit_records ORDER BY timestamp DESC")
    fun getAllSystemAuditRecords(): Flow<List<SystemAuditRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSystemAuditRecord(record: SystemAuditRecord): Long

    @Query("DELETE FROM system_audit_records")
    suspend fun clearSystemAuditRecords()

    @Query("SELECT * FROM battery_alerts")
    fun getAllBatteryAlerts(): Flow<List<BatteryAlert>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatteryAlert(alert: BatteryAlert)

    @Delete
    suspend fun deleteBatteryAlert(alert: BatteryAlert)

    @Query("SELECT * FROM health_status")
    fun getAllHealthStatuses(): Flow<List<HealthStatusEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHealthStatus(status: HealthStatusEntity)

    @Query("SELECT * FROM diagnostic_logs ORDER BY timestamp DESC")
    fun getAllDiagnosticLogs(): Flow<List<DiagnosticLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDiagnosticLog(log: DiagnosticLogEntity)
    @Query("SELECT * FROM root_cause_logs ORDER BY timestamp DESC")
    fun getAllRootCauseLogs(): Flow<List<RootCauseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRootCauseLog(log: RootCauseEntity)
    @Query("SELECT * FROM resource_optimizations ORDER BY timestamp DESC")
    fun getAllResourceOptimizations(): Flow<List<ResourceOptimizerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResourceOptimization(optimization: ResourceOptimizerEntity)

    @Query("SELECT * FROM app_version_table WHERE id = 1 LIMIT 1")
    fun getAppVersion(): Flow<AppVersionEntity?>

    @Query("SELECT * FROM app_version_table WHERE id = 1 LIMIT 1")
    suspend fun getAppVersionDirect(): AppVersionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppVersion(version: AppVersionEntity)

    @Query("SELECT * FROM charging_protection_sessions ORDER BY startTime DESC")
    fun getAllChargingProtectionSessions(): Flow<List<ChargingProtectionSessionEntity>>

    @Query("SELECT * FROM charging_protection_sessions WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getChargingProtectionSession(sessionId: String): ChargingProtectionSessionEntity?

    @Query("SELECT * FROM charging_protection_sessions WHERE endTime IS NULL ORDER BY startTime DESC LIMIT 1")
    suspend fun getActiveChargingProtectionSession(): ChargingProtectionSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChargingProtectionSession(session: ChargingProtectionSessionEntity)

    @Update
    suspend fun updateChargingProtectionSession(session: ChargingProtectionSessionEntity)
}


