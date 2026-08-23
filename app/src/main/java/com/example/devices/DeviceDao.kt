package com.example.devices

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {
    @Query("SELECT * FROM devices")
    fun getAllDevices(): Flow<List<Device>>

    @Query("SELECT * FROM devices WHERE type = :type AND isConnected = 1")
    fun getConnectedDevices(type: String): Flow<List<Device>>

    @Query("SELECT * FROM devices WHERE type = :type AND isConnected = 0")
    fun getOfflineDevices(type: String): Flow<List<Device>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(device: Device)

    @Query("UPDATE devices SET isConnected = 0, disconnectTime = :time WHERE macAddress = :macAddress")
    suspend fun markDisconnected(macAddress: String, time: Long)

    @Query("UPDATE devices SET isConnected = 1, lastSeen = :time, totalConnectionCount = totalConnectionCount + 1 WHERE macAddress = :macAddress")
    suspend fun markConnected(macAddress: String, time: Long)
}
