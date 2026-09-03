package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class BatteryRepository(
    private val batteryDao: BatteryDao,
    private val batteryHistoryDao: BatteryHistoryDao? = null
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val backupAdapter = moshi.adapter(BatteryBackupData::class.java)

    private val _batteryLevel = MutableStateFlow(100)
    val batteryLevel: StateFlow<Int> = _batteryLevel.asStateFlow()

    fun updateBatteryLevel(level: Int) {
        _batteryLevel.value = level
    }

    val settings: Flow<SettingsEntity?> = batteryDao.getSettings()
    val appVersion: Flow<AppVersionEntity?> = batteryDao.getAppVersion()
    val allSessions: Flow<List<ChargingSession>> = batteryDao.getAllSessions()
    val allAppConsumption: Flow<List<AppConsumptionEntity>> = batteryDao.getAllAppConsumption()
    val allTrendLogs: Flow<List<BatteryTrendLog>> = batteryDao.getAllTrendLogs()
    val allBatteryEvents: Flow<List<BatteryEvent>> = batteryDao.getAllBatteryEvents()
    val allDischargingSessions: Flow<List<DischargingSession>> = batteryDao.getAllDischargingSessions()
    val allAppActivity: Flow<List<AppActivity>> = batteryDao.getAllAppActivity()
    val allSystemAuditRecords: Flow<List<SystemAuditRecord>> = batteryDao.getAllSystemAuditRecords()

    // --- Battery History & Charging Pattern Analysis Streams ---
    val allBatteryHistory: Flow<List<BatteryHistoryEntity>> =
        batteryHistoryDao?.getAllBatteryHistory() ?: kotlinx.coroutines.flow.flowOf(emptyList())

    val hourlyChargingDistribution: Flow<List<HourlyChargingPattern>> =
        batteryHistoryDao?.getHourlyChargingDistribution() ?: kotlinx.coroutines.flow.flowOf(emptyList())

    val dailyChargingPatterns: Flow<List<DailyChargingPattern>> =
        batteryHistoryDao?.getDailyChargingPatterns() ?: kotlinx.coroutines.flow.flowOf(emptyList())

    val chargingTimeWindowAnalysis: Flow<List<ChargingTimeWindowAnalysis>> =
        batteryHistoryDao?.getChargingTimeWindowAnalysis() ?: kotlinx.coroutines.flow.flowOf(emptyList())

    val batteryHistoryCount: Flow<Int> =
        batteryHistoryDao?.getBatteryHistoryCount() ?: kotlinx.coroutines.flow.flowOf(0)

    fun getRecentBatteryHistory(limit: Int = 100): Flow<List<BatteryHistoryEntity>> {
        return batteryHistoryDao?.getRecentBatteryHistory(limit) ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }

    fun getBatteryHistoryBetween(startTime: Long, endTime: Long): Flow<List<BatteryHistoryEntity>> {
        return batteryHistoryDao?.getBatteryHistoryBetween(startTime, endTime) ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }

    suspend fun insertBatteryHistory(entry: BatteryHistoryEntity): Long {
        return batteryHistoryDao?.insertBatteryHistory(entry) ?: -1L
    }

    suspend fun insertBatteryHistoryBatch(entries: List<BatteryHistoryEntity>): List<Long> {
        return batteryHistoryDao?.insertBatteryHistoryBatch(entries) ?: emptyList()
    }

    suspend fun recordBatterySnapshot(
        level: Int,
        isCharging: Boolean,
        chargingType: String = "NONE",
        temperature: Float = 0.0f,
        voltageMv: Int = 0,
        currentNowMa: Int = 0,
        health: String = "GOOD",
        status: String = if (isCharging) "CHARGING" else "DISCHARGING"
    ): Long {
        val entry = BatteryHistoryEntity(
            timestamp = System.currentTimeMillis(),
            batteryLevel = level.coerceIn(0, 100),
            isCharging = isCharging,
            chargingType = chargingType,
            temperature = temperature,
            voltageMv = voltageMv,
            currentNowMa = currentNowMa,
            batteryHealth = health,
            batteryStatus = status
        )
        return insertBatteryHistory(entry)
    }

    suspend fun deleteOldBatteryHistory(olderThanTimestamp: Long): Int {
        return batteryHistoryDao?.deleteOldHistory(olderThanTimestamp) ?: 0
    }

    suspend fun clearAllBatteryHistory(): Int {
        return batteryHistoryDao?.clearAllHistory() ?: 0
    }

    suspend fun insertSystemAuditRecord(record: SystemAuditRecord): Long {
        return try {
            val id = batteryDao.insertSystemAuditRecord(record)
            com.example.util.LogDatabaseInspector.logInsertionSuccess(record)
            id
        } catch (e: Exception) {
            com.example.util.LogDatabaseInspector.logInsertionFailure(record, e)
            -1L
        }
    }

    suspend fun clearSystemAuditRecords() {
        batteryDao.clearSystemAuditRecords()
    }

    suspend fun insertTrendLog(log: BatteryTrendLog) {
        batteryDao.insertTrendLog(log)
        try {
            val isCharging = log.dischargeRate == 0f || log.currentNow > 15
            val historyEntry = BatteryHistoryEntity(
                timestamp = log.timestamp,
                batteryLevel = log.batteryLevel.coerceIn(0, 100),
                isCharging = isCharging,
                temperature = log.temperature,
                voltageMv = log.voltage,
                currentNowMa = log.currentNow,
                batteryHealth = "GOOD",
                batteryStatus = if (isCharging) "CHARGING" else "DISCHARGING",
                chargingType = if (isCharging) "AC" else "NONE"
            )
            batteryHistoryDao?.insertBatteryHistory(historyEntry)
        } catch (e: Exception) {
            android.util.Log.e("BatteryRepository", "Error auto-duplicating TrendLog to BatteryHistory: ${e.message}")
        }
    }

    companion object {
        private var lastEventTime = 0L
        private var lastEventType = ""
        private var lastEventTitle = ""
        private var lastEventDetails = ""
        private var lastChargingTime = 0L
    }

    suspend fun logBatteryEvent(eventType: String, title: String, details: String, category: String, source: String) {
        android.util.Log.d("BatteryRepository", "logBatteryEvent called: $title")
        val now = System.currentTimeMillis()
        
        // 1. Log Optimization Engine
        val isMagnetic = eventType == "NETRA" || title.contains("Magnetic", ignoreCase = true) || details.contains("Magnetic", ignoreCase = true)
        val isCharging = eventType == "CHARGING" || title.contains("Charger Connected", ignoreCase = true) || title.contains("Charging Started", ignoreCase = true)
        
        if (isCharging) {
            lastChargingTime = now
        }
        
        if (isMagnetic && (now - lastChargingTime < 5000L)) {
            android.util.Log.d("BatteryRepository", "Suppressed magnetic log due to recent charging event (Log Optimization Engine)")
            return
        }
        
        // 2. Window Deduplication
        val isThermal = category.equals("TEMPERATURE", ignoreCase = true) || category.equals("THERMAL", ignoreCase = true) || eventType.equals("THERMAL", ignoreCase = true)
        val isBluetooth = category.equals("BLUETOOTH", ignoreCase = true) || category.equals("BLUETOOTH_DEVICE", ignoreCase = true) || eventType.contains("BLUETOOTH", ignoreCase = true)
        val bypassDeduplication = isThermal || isBluetooth

        if (!bypassDeduplication && eventType == lastEventType && title == lastEventTitle && (now - lastEventTime < 5000L)) {
            android.util.Log.d("BatteryRepository", "Suppressed duplicate log within window: $title")
            return
        }
        
        lastEventTime = now
        lastEventType = eventType
        lastEventTitle = title
        lastEventDetails = details

        val event = BatteryEvent(
            timestamp = now,
            eventType = eventType,
            title = title,
            details = details,
            category = category,
            source = source
        )
        batteryDao.insertBatteryEvent(event)
        com.example.util.LogDatabaseInspector.logInsertionSuccess(event)
        android.util.Log.d("BatteryRepository", "BatteryEvent inserted into DB: $title")
    }

    fun logBatteryEventSync(eventType: String, title: String, details: String, category: String, source: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                logBatteryEvent(eventType, title, details, category, source)
            } catch (e: Exception) {
                android.util.Log.e("BatteryRepository", "Failed to log event sync", e)
            }
        }
    }

    suspend fun insertDischargingSession(session: DischargingSession): Long {
        return batteryDao.insertDischargingSession(session)
    }

    suspend fun startDischargingSession(startTime: Long, startPercentage: Int, temp: Float) {
        val newSession = DischargingSession(
            startTime = startTime,
            startPercentage = startPercentage,
            maxTemperature = temp
        )
        batteryDao.insertDischargingSession(newSession)
    }

    suspend fun endActiveDischargingSession(endTime: Long, endPercentage: Int, screenOnMinutes: Int, standbyMinutes: Int, avgDrainRate: Float) {
        val active = batteryDao.getActiveDischargingSession() ?: return
        batteryDao.updateDischargingSession(active.copy(
            endTime = endTime,
            endPercentage = endPercentage,
            screenOnTimeMinutes = screenOnMinutes,
            standbyTimeMinutes = standbyMinutes,
            avgDrainRate = avgDrainRate
        ))
    }

    suspend fun clearTrendLogs() {
        batteryDao.clearTrendLogs()
    }

    suspend fun getAllSessionsDirect(): List<ChargingSession> {
        return batteryDao.getAllSessionsDirect()
    }

    suspend fun getAllAppConsumptionDirect(): List<AppConsumptionEntity> {
        return batteryDao.getAllAppConsumptionDirect()
    }

    suspend fun saveAppConsumption(apps: List<AppConsumptionEntity>) {
        batteryDao.insertAllAppConsumption(apps)
    }

    suspend fun clearAppConsumption() {
        batteryDao.clearAppConsumption()
    }

    suspend fun getSettingsDirect(): SettingsEntity? {
        return batteryDao.getSettingsDirect()
    }

    suspend fun exportDataToJson(): String? {
        val settings = batteryDao.getSettingsDirect() ?: SettingsEntity()
        val sessions = batteryDao.getAllSessionsDirect()
        val apps = batteryDao.getAllAppConsumptionDirect()
        
        val backupData = BatteryBackupData(settings, sessions, apps)
        return backupAdapter.toJson(backupData)
    }

    suspend fun importDataFromJson(json: String) {
        val backupData = backupAdapter.fromJson(json) ?: return
        insertAllData(backupData.settings, backupData.sessions, backupData.appConsumption)
    }

    suspend fun insertAllData(
        settings: SettingsEntity,
        sessions: List<ChargingSession>,
        apps: List<AppConsumptionEntity>
    ) {
        batteryDao.insertSettings(settings)
        batteryDao.insertAllAppConsumption(apps)
        for (session in sessions) {
            batteryDao.insertSession(session)
        }
    }

    suspend fun getSettingsOrInit(): SettingsEntity {
        val current = batteryDao.getSettingsDirect()
        if (current == null) {
            val defaultSettings = SettingsEntity()
            batteryDao.insertSettings(defaultSettings)
            return defaultSettings
        }
        return current
    }

    suspend fun updateSettings(settings: SettingsEntity) {
        batteryDao.insertSettings(settings)
    }

    private fun formatTime(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat("hh mm ss a", java.util.Locale.US)
        return sdf.format(java.util.Date(timestamp)).lowercase(java.util.Locale.US)
    }

    suspend fun startSession(startTime: Long, startPercentage: Int, chargingType: String, temp: Float, isDischarge: Boolean = false, avgPower: Float = 0f) {
        val currentActive = batteryDao.getActiveSession()
        if (currentActive != null) {
            // End it prematurely if there's an orphaned session
            batteryDao.updateSession(currentActive.copy(
                endTime = startTime, 
                endPercentage = startPercentage,
                screenOnTimeMinutes = 0,
                standbyTimeMinutes = 0,
                endTemperature = temp,
                formattedEndTime = formatTime(startTime),
                totalDurationSeconds = (startTime - currentActive.startTime) / 1000,
                sessionStatus = "INTERRUPTED"
            ))
        }
        val formattedStart = formatTime(startTime)
        val newSession = ChargingSession(
            startTime = startTime,
            startPercentage = startPercentage,
            chargingType = chargingType,
            maxTemperature = temp,
            isDischarge = isDischarge,
            avgPower = avgPower,
            startTemperature = temp,
            formattedStartTime = formattedStart,
            sessionStatus = "ACTIVE",
            createdTimestamp = startTime
        )
        batteryDao.insertSession(newSession)
    }

    suspend fun updateActiveSessionTemperature(temp: Float) {
        val active = batteryDao.getActiveSession() ?: return
        if (temp > active.maxTemperature) {
            batteryDao.updateSession(active.copy(maxTemperature = temp))
        }
    }

    suspend fun endActiveSession(endTime: Long, endPercentage: Int, screenOnMinutes: Int = 0, standbyMinutes: Int = 0, avgPower: Float = 0f, endTemp: Float = 0f) {
        val active = batteryDao.getActiveSession() ?: return
        
        // Define overnight as lasting > 3 hours and ending or running between 11 PM and 6 AM
        val durationMs = endTime - active.startTime
        val isOvernight = !active.isDischarge && durationMs > 3 * 60 * 60 * 1000 // Simple duration heuristic for a continuous charge session
        
        val totalSecs = durationMs / 1000
        val isFull = endPercentage >= 100 || active.startPercentage == 100 || (active.fullChargeTime ?: 0L) > 0L
        val fullTime = active.fullChargeTime ?: if (endPercentage >= 100 && active.startPercentage < 100) endTime else null
        val overchargeSecs = if (fullTime != null) {
            ((endTime - fullTime) / 1000).coerceAtLeast(0L)
        } else {
            0L
        }

        batteryDao.updateSession(active.copy(
            endTime = endTime,
            endPercentage = endPercentage,
            isOvernight = isOvernight,
            screenOnTimeMinutes = screenOnMinutes,
            standbyTimeMinutes = standbyMinutes,
            avgPower = if (avgPower > 0f) avgPower else active.avgPower,
            endTemperature = endTemp,
            formattedEndTime = formatTime(endTime),
            totalDurationSeconds = totalSecs,
            fullChargeTime = fullTime,
            formattedFullChargeTime = fullTime?.let { formatTime(it) },
            overchargingDurationSeconds = overchargeSecs,
            fullyCharged = isFull,
            sessionStatus = "COMPLETED"
        ))
    }

    suspend fun markActiveSessionFullyCharged(timestamp: Long) {
        val active = batteryDao.getActiveSession() ?: return
        if (active.fullChargeTime == null) {
            batteryDao.updateSession(active.copy(
                fullChargeTime = timestamp,
                formattedFullChargeTime = formatTime(timestamp),
                fullyCharged = true
            ))
        }
    }
    
    suspend fun getActiveSession(): ChargingSession? {
        return batteryDao.getActiveSession()
    }

    suspend fun getChargingSession(id: Long): ChargingSession? {
        return batteryDao.getChargingSession(id)
    }

    suspend fun getDischargingSession(id: Long): DischargingSession? {
        return batteryDao.getDischargingSession(id)
    }

    suspend fun clearHistory() {
        batteryDao.clearAllHistory()
    }

    val allMagneticEvents: Flow<List<MagneticEvent>> = batteryDao.getAllMagneticEvents()

    suspend fun insertMagneticEvent(event: MagneticEvent): Long {
        return batteryDao.insertMagneticEvent(event)
    }

    suspend fun clearMagneticEvents() {
        batteryDao.clearMagneticEvents()
    }

    suspend fun deleteMagneticEvent(id: Long) {
        batteryDao.deleteMagneticEvent(id)
    }

    val allBatteryAlerts: Flow<List<BatteryAlert>> = batteryDao.getAllBatteryAlerts()

    suspend fun insertBatteryAlert(alert: BatteryAlert) {
        batteryDao.insertBatteryAlert(alert)
    }

    suspend fun deleteBatteryAlert(alert: BatteryAlert) {
        batteryDao.deleteBatteryAlert(alert)
    }

    val allHealthStatuses: Flow<List<HealthStatusEntity>> = batteryDao.getAllHealthStatuses()

    suspend fun insertHealthStatus(status: HealthStatusEntity) {
        batteryDao.insertHealthStatus(status)
    }

    val allDiagnosticLogs: Flow<List<DiagnosticLogEntity>> = batteryDao.getAllDiagnosticLogs()

    suspend fun insertDiagnosticLog(log: DiagnosticLogEntity) {
        batteryDao.insertDiagnosticLog(log)
    }

    val allRootCauseLogs: Flow<List<RootCauseEntity>> = batteryDao.getAllRootCauseLogs()

    suspend fun insertRootCauseLog(log: RootCauseEntity) {
        batteryDao.insertRootCauseLog(log)
    }

    val allResourceOptimizations: Flow<List<ResourceOptimizerEntity>> = batteryDao.getAllResourceOptimizations()

    suspend fun insertResourceOptimization(optimization: ResourceOptimizerEntity) {
        batteryDao.insertResourceOptimization(optimization)
    }
}

