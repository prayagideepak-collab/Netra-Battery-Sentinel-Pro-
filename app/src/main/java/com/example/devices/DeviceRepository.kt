package com.example.devices

import kotlinx.coroutines.flow.Flow

class DeviceRepository(private val deviceDao: DeviceDao) {
    val allDevices: Flow<List<Device>> = deviceDao.getAllDevices()
    
    fun getConnected(type: String) = deviceDao.getConnectedDevices(type)
    fun getOffline(type: String) = deviceDao.getOfflineDevices(type)
    
    suspend fun insertOrUpdate(device: Device) = deviceDao.insertOrUpdate(device)
    suspend fun markDisconnected(mac: String, time: Long) = deviceDao.markDisconnected(mac, time)
    suspend fun markConnected(mac: String, time: Long) = deviceDao.markConnected(mac, time)
}
