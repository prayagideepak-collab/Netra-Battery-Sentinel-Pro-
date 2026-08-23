package com.example.engines.bus

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object EventBus {
    private val _events = MutableSharedFlow<SystemEvent>(replay = 1)
    val events = _events.asSharedFlow()

    suspend fun post(event: SystemEvent) {
        _events.emit(event)
    }
}

sealed class SystemEvent {
    data class BatteryUpdated(val level: Int) : SystemEvent()
    data class ThermalUpdated(val temp: Int) : SystemEvent()
    data class ChargerStateChanged(val isConnected: Boolean) : SystemEvent()
    data class BluetoothStateChanged(val isConnected: Boolean) : SystemEvent()
}
