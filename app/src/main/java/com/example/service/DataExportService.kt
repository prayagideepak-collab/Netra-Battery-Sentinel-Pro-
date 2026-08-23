package com.example.service

import com.example.data.BatteryRepository
import com.example.data.ChargingSession
import com.example.data.DischargingSession
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class DataExportService(private val repository: BatteryRepository) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    
    suspend fun exportChargingSessionToJson(sessionId: Long): String? {
        val session = repository.getChargingSession(sessionId)
        return if (session != null) moshi.adapter(ChargingSession::class.java).toJson(session) else null
    }

    suspend fun exportDischargingSessionToJson(sessionId: Long): String? {
        val session = repository.getDischargingSession(sessionId)
        return if (session != null) moshi.adapter(DischargingSession::class.java).toJson(session) else null
    }
}
