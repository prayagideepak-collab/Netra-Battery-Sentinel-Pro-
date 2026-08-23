package com.example.engines

import com.example.data.BatteryRepository
import com.example.data.RootCauseEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Root Cause Analyzer
 * Captures diagnostics and identifies probable cause.
 */
class RootCauseAnalyzer(private val repository: BatteryRepository) {
    private val scope = CoroutineScope(Dispatchers.IO)

    fun analyzeAndLog(
        moduleName: String,
        failureType: String,
        threadDump: String,
        exception: String
    ) {
        scope.launch {
            val rootCause = identifyRootCause(failureType, exception)
            repository.insertRootCauseLog(
                RootCauseEntity(
                    moduleName = moduleName,
                    failureType = failureType,
                    rootCause = rootCause,
                    threadDump = threadDump,
                    exception = exception,
                    memorySnapshot = "N/A", // To be filled
                    cpuSnapshot = "N/A", // To be filled
                    recommendedRecovery = "Level 1: Reset", // To be smarter
                    recoveryExecuted = "Pending",
                    recoveryResult = "Pending"
                )
            )
        }
    }

    private fun identifyRootCause(failureType: String, exception: String): String {
        return when {
            failureType.contains("Timeout") -> "System Unresponsive"
            exception.contains("OutOfMemory") -> "Memory Leak"
            else -> "Unknown"
        }
    }
}
