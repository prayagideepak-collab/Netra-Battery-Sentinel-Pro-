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
}
