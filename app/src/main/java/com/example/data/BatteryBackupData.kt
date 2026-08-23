package com.example.data

import com.squareup.moshi.JsonClass
import com.example.data.ChargingSession
import com.example.data.AppConsumptionEntity
import com.example.data.SettingsEntity

@JsonClass(generateAdapter = true)
data class BatteryBackupData(
    val settings: SettingsEntity,
    val sessions: List<ChargingSession>,
    val appConsumption: List<AppConsumptionEntity>
)
