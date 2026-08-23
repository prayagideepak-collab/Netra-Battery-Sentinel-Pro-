package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * BatteryHistoryDao
 * Data Access Object for recording and querying battery history and charging patterns.
 */
@Dao
interface BatteryHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatteryHistory(entry: BatteryHistoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatteryHistoryBatch(entries: List<BatteryHistoryEntity>): List<Long>

    @Query("SELECT * FROM battery_history ORDER BY timestamp DESC")
    fun getAllBatteryHistory(): Flow<List<BatteryHistoryEntity>>

    @Query("SELECT * FROM battery_history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentBatteryHistory(limit: Int): Flow<List<BatteryHistoryEntity>>

    @Query("SELECT * FROM battery_history WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp ASC")
    fun getBatteryHistoryBetween(startTime: Long, endTime: Long): Flow<List<BatteryHistoryEntity>>

    @Query("SELECT * FROM battery_history WHERE isCharging = 1 ORDER BY timestamp DESC")
    fun getChargingHistory(): Flow<List<BatteryHistoryEntity>>

    @Query("SELECT * FROM battery_history WHERE isCharging = 0 ORDER BY timestamp DESC")
    fun getDischargingHistory(): Flow<List<BatteryHistoryEntity>>

    @Query("SELECT * FROM battery_history WHERE hourOfDay = :hour ORDER BY timestamp DESC")
    fun getHistoryForHour(hour: Int): Flow<List<BatteryHistoryEntity>>

    @Query("""
        SELECT 
            hourOfDay,
            AVG(batteryLevel) AS avgLevel,
            MIN(batteryLevel) AS minLevel,
            MAX(batteryLevel) AS maxLevel,
            COUNT(*) AS sampleCount,
            SUM(CASE WHEN isCharging = 1 THEN 1 ELSE 0 END) AS chargingSampleCount
        FROM battery_history
        GROUP BY hourOfDay
        ORDER BY hourOfDay ASC
    """)
    fun getHourlyChargingDistribution(): Flow<List<HourlyChargingPattern>>

    @Query("""
        SELECT 
            dayOfWeek,
            AVG(batteryLevel) AS avgLevel,
            SUM(CASE WHEN isCharging = 1 THEN 1 ELSE 0 END) AS chargingSampleCount,
            COUNT(*) AS totalSampleCount
        FROM battery_history
        GROUP BY dayOfWeek
        ORDER BY dayOfWeek ASC
    """)
    fun getDailyChargingPatterns(): Flow<List<DailyChargingPattern>>

    @Query("""
        SELECT 
            hourOfDay,
            (CAST(SUM(CASE WHEN isCharging = 1 THEN 1 ELSE 0 END) AS FLOAT) / CAST(COUNT(*) AS FLOAT)) * 100.0 AS chargingFrequencyPercent,
            AVG(temperature) AS avgTemperature,
            AVG(voltageMv) AS avgVoltageMv
        FROM battery_history
        GROUP BY hourOfDay
        ORDER BY hourOfDay ASC
    """)
    fun getChargingTimeWindowAnalysis(): Flow<List<ChargingTimeWindowAnalysis>>

    @Query("SELECT COUNT(*) FROM battery_history")
    fun getBatteryHistoryCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM battery_history")
    suspend fun getBatteryHistoryCountDirect(): Int

    @Query("DELETE FROM battery_history WHERE timestamp < :olderThanTimestamp")
    suspend fun deleteOldHistory(olderThanTimestamp: Long): Int

    @Query("DELETE FROM battery_history")
    suspend fun clearAllHistory(): Int
}
