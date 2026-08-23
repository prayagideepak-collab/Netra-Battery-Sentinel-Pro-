package com.example.battery.model

data class BatterySample(
    val percentage: Int,
    val timestamp: Long = System.currentTimeMillis()
)

sealed interface BatteryEvent {
    data class PowerConnected(val timestamp: Long = System.currentTimeMillis()) : BatteryEvent
    data class PowerDisconnected(val timestamp: Long = System.currentTimeMillis()) : BatteryEvent
    data class BatteryLevelChanged(
        val oldLevel: Int?,
        val newLevel: Int,
        val timestamp: Long = System.currentTimeMillis()
    ) : BatteryEvent
    data class ChargingStateChanged(
        val isCharging: Boolean,
        val timestamp: Long = System.currentTimeMillis()
    ) : BatteryEvent
    data class BatteryLow(
        val level: Int,
        val timestamp: Long = System.currentTimeMillis()
    ) : BatteryEvent
    data class BatteryOkay(
        val level: Int,
        val timestamp: Long = System.currentTimeMillis()
    ) : BatteryEvent
    data class PowerSaveModeChanged(
        val isPowerSaveMode: Boolean,
        val timestamp: Long = System.currentTimeMillis()
    ) : BatteryEvent
}
