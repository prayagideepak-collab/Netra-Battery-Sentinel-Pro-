package com.example.engines

import com.example.data.BatteryRepository
import com.example.data.DiagnosticLogEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Internal Diagnostic Logger
 * Records system events, crashes, and recovery history locally.
 */
class InternalDiagnosticLogger(private val repository: BatteryRepository) {
    private val scope = CoroutineScope(Dispatchers.IO)

    fun logEvent(eventType: String, moduleName: String, details: String) {
        scope.launch {
            repository.insertDiagnosticLog(
                DiagnosticLogEntity(
                    eventType = eventType,
                    moduleName = moduleName,
                    details = details
                )
            )
        }
    }
}
