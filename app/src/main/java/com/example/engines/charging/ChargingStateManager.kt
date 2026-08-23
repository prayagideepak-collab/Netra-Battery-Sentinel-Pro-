package com.example.engines.charging

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ChargingMode {
    NOT_CHARGING,
    FAST,
    NORMAL,
    SLOW,
    DATA_TRANSFER
}

object ChargingStateManager {
    private val _currentMode = MutableStateFlow(ChargingMode.NOT_CHARGING)
    val currentMode: StateFlow<ChargingMode> = _currentMode.asStateFlow()

    private val _isDataTransferActive = MutableStateFlow(false)
    val isDataTransferActive: StateFlow<Boolean> = _isDataTransferActive.asStateFlow()

    fun updateChargingMode(mode: ChargingMode) {
        _currentMode.value = mode
    }

    fun setDataTransferActive(active: Boolean) {
        _isDataTransferActive.value = active
        if (active) {
            _currentMode.value = ChargingMode.DATA_TRANSFER
        }
    }
}
