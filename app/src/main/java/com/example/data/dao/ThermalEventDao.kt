package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.data.entity.ThermalEvent
import kotlinx.coroutines.flow.Flow

@Dao
interface ThermalEventDao {
    @Insert
    suspend fun insertEvent(event: ThermalEvent): Long

    @Query("SELECT * FROM thermal_events ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentEvents(limit: Int): Flow<List<ThermalEvent>>

    @Query("SELECT * FROM thermal_events WHERE eventType = :type ORDER BY timestamp DESC LIMIT :limit")
    fun getEventsByType(type: String, limit: Int): Flow<List<ThermalEvent>>

    @Query("SELECT * FROM thermal_events WHERE timestamp >= :startTime ORDER BY timestamp DESC")
    fun getEventsSince(startTime: Long): Flow<List<ThermalEvent>>

    @Query("DELETE FROM thermal_events WHERE timestamp < :cutoffTime")
    suspend fun deleteOldEvents(cutoffTime: Long)
}
