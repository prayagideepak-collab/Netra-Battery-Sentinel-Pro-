package com.example.engines.ibrsle

import android.content.Context
import android.util.Log
import com.example.data.BatteryDatabase
import com.example.data.BatteryRepository
import com.example.data.SystemAuditRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

object IbrsleRecoveryManager {
    private const val TAG = "IBRSLE_RecoveryManager"
    private const val RECOVERY_COOLDOWN_MS = 10_000L // 10 seconds cooldown
    private const val MAX_RECOVERIES_IN_WINDOW = 3
    private const val WINDOW_MS = 300_000L // 5 minutes window

    private val recoveryTimestamps = ConcurrentHashMap<String, MutableList<Long>>()

    fun canAttemptRecovery(serviceId: String): Boolean {
        val now = System.currentTimeMillis()
        val list = recoveryTimestamps.getOrPut(serviceId) { mutableListOf() }

        synchronized(list) {
            // Remove timestamps older than window
            list.removeAll { now - it > WINDOW_MS }

            // Check cooldown from last attempt
            val lastAttempt = list.lastOrNull() ?: 0L
            if (now - lastAttempt < RECOVERY_COOLDOWN_MS) {
                Log.w(TAG, "Recovery for $serviceId suppressed: Cooldown active (${now - lastAttempt}ms < ${RECOVERY_COOLDOWN_MS}ms)")
                return false
            }

            // Check max attempts in window
            if (list.size >= MAX_RECOVERIES_IN_WINDOW) {
                Log.e(TAG, "Recovery for $serviceId suppressed: Max attempts ($MAX_RECOVERIES_IN_WINDOW in 5 min) reached")
                return false
            }

            return true
        }
    }

    fun recordRecoveryAttempt(serviceId: String) {
        val list = recoveryTimestamps.getOrPut(serviceId) { mutableListOf() }
        synchronized(list) {
            list.add(System.currentTimeMillis())
        }
    }

    suspend fun logRecoveryEvent(context: Context, serviceId: String, serviceName: String, reason: String, isSuccess: Boolean) {
        withContext(Dispatchers.IO) {
            try {
                val db = BatteryDatabase.getDatabase(context)
                val repo = BatteryRepository(db.batteryDao())

                val resultStr = if (isSuccess) "RECOVERY_SUCCESSFUL" else "RECOVERY_FAILED"
                repo.logBatteryEvent(
                    eventType = "IBRSLE_SERVICE_RECOVERY",
                    title = "Service Recovery: $serviceName",
                    details = "Service [$serviceId] recovery attempt: $resultStr. Reason: $reason",
                    category = "AUDIT",
                    source = "IBRSLE_Engine"
                )

                repo.insertSystemAuditRecord(
                    SystemAuditRecord(
                        timestamp = System.currentTimeMillis(),
                        durationMs = 0L,
                        totalServicesChecked = 1,
                        healthyServices = if (isSuccess) 1 else 0,
                        restartedServices = 1,
                        failedServices = if (isSuccess) 0 else 1,
                        unsupportedComponents = 0,
                        recoveryActions = "Attempted isolated module recovery for $serviceName ($serviceId). Result: $resultStr. Reason: $reason",
                        healthScore = if (isSuccess) 100 else 50
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error logging recovery event for $serviceId", e)
            }
        }
    }
}
