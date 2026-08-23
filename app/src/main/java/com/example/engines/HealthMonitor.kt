package com.example.engines

import android.content.Context
import com.example.data.BatteryRepository
import com.example.data.HealthStatusEntity
import kotlinx.coroutines.*
import java.lang.Runtime

/**
 * Health Monitor Engine
 * Monitors system health and persists status to Room.
 */
class HealthMonitor(
    private val context: Context,
    private val repository: BatteryRepository
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun startMonitoring() {
        scope.launch {
            while (isActive) {
                val status = checkHealth()
                repository.insertHealthStatus(status)
                delay(60000) // Poll every minute
            }
        }
    }

    private fun checkHealth(): HealthStatusEntity {
        val runtime = Runtime.getRuntime()
        val usedMem = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memoryInfo = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        
        val threadHealth = if (Thread.currentThread().isAlive) "OK" else "ISSUE"
        
        return HealthStatusEntity(
            moduleName = "CoreSystem",
            status = if (memoryInfo.lowMemory) "Low Memory" else "Alive",
            memoryUsageMb = usedMem,
            threadHealth = threadHealth
        )
    }
}
