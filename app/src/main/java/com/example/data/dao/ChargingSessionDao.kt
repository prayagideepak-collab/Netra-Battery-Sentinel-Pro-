package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.data.entity.ChargingSession
import kotlinx.coroutines.flow.Flow

@Dao
interface ChargingSessionDao {
    @Insert
    suspend fun insertSession(session: ChargingSession): Long

    @Update
    suspend fun updateSession(session: ChargingSession)

    @Query("SELECT * FROM charging_sessions WHERE id = :sessionId")
    fun getSessionById(sessionId: Int): Flow<ChargingSession?>

    @Query("SELECT * FROM charging_sessions WHERE completed = 1 ORDER BY startTime DESC LIMIT :limit")
    fun getCompletedSessions(limit: Int): Flow<List<ChargingSession>>

    @Query("SELECT * FROM charging_sessions WHERE completed = 0")
    fun getActiveSessions(): Flow<List<ChargingSession>>

    @Query("SELECT AVG(endChargePercent - startChargePercent) FROM charging_sessions WHERE completed = 1")
    fun getAverageChargePerSession(): Flow<Float?>

    @Query("DELETE FROM charging_sessions WHERE completed = 1 AND startTime < :cutoffTime")
    suspend fun deleteOldSessions(cutoffTime: Long)
}
