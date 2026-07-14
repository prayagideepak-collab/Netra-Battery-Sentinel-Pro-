package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

class BatteryRepository(private val batteryDao: BatteryDao) {
    val settings: Flow<SettingsEntity?> = batteryDao.getSettings()
    val allSessions: Flow<List<ChargingSession>> = batteryDao.getAllSessions()

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

    suspend fun startSession(startTime: Long, startPercentage: Int, chargingType: String, temp: Float) {
        val currentActive = batteryDao.getActiveSession()
        if (currentActive != null) {
            // End it prematurely if there's an orphaned session
            batteryDao.updateSession(currentActive.copy(endTime = startTime, endPercentage = startPercentage))
        }
        val newSession = ChargingSession(
            startTime = startTime,
            startPercentage = startPercentage,
            chargingType = chargingType,
            maxTemperature = temp
        )
        batteryDao.insertSession(newSession)
    }

    suspend fun updateActiveSessionTemperature(temp: Float) {
        val active = batteryDao.getActiveSession() ?: return
        if (temp > active.maxTemperature) {
            batteryDao.updateSession(active.copy(maxTemperature = temp))
        }
    }

    suspend fun endActiveSession(endTime: Long, endPercentage: Int) {
        val active = batteryDao.getActiveSession() ?: return
        
        // Define overnight as lasting > 3 hours and ending or running between 11 PM and 6 AM
        val durationMs = endTime - active.startTime
        val isOvernight = durationMs > 3 * 60 * 60 * 1000 // Simple duration heuristic for a continuous charge session
        
        batteryDao.updateSession(active.copy(
            endTime = endTime,
            endPercentage = endPercentage,
            isOvernight = isOvernight
        ))
    }
    
    suspend fun getActiveSession(): ChargingSession? {
        return batteryDao.getActiveSession()
    }

    suspend fun clearHistory() {
        batteryDao.clearAllHistory()
    }
}
