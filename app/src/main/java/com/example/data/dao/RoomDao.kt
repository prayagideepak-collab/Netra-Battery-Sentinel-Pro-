package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.data.entity.Room
import kotlinx.coroutines.flow.Flow

@Dao
interface RoomDao {
    @Insert
    suspend fun insertRoom(room: Room): Long

    @Update
    suspend fun updateRoom(room: Room)

    @Query("SELECT * FROM rooms WHERE id = :roomId")
    fun getRoomById(roomId: Int): Flow<Room?>

    @Query("SELECT * FROM rooms ORDER BY lastUpdated DESC")
    fun getAllRooms(): Flow<List<Room>>

    @Query("SELECT AVG(averageTemperature) FROM rooms")
    fun getAverageTemperature(): Flow<Float>
}
