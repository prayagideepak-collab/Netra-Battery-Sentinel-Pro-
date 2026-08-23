package com.example.engines.ibrsle

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat

object IbrsleHealthVerifier {
    private const val TAG = "IBRSLE_HealthVerifier"

    fun checkMissingPermissions(context: Context, spec: RegisteredServiceSpec): List<String> {
        val missing = mutableListOf<String>()
        for (perm in spec.requiredPermissions) {
            if (ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED) {
                missing.add(perm)
            }
        }
        return missing
    }

    fun isServiceRunningInSystem(context: Context, serviceClassName: String): Boolean {
        return try {
            val cls = Class.forName(serviceClassName)
            com.example.providers.SafeServiceHealthProvider.checkServiceHealth(context, cls).isServiceRunning
        } catch (e: Exception) {
            Log.e(TAG, "Error checking running system service cleanly", e)
            false
        }
    }

    fun getMemoryStats(): Pair<Float, Float> {
        val runtime = Runtime.getRuntime()
        val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024f * 1024f)
        val maxMb = runtime.maxMemory() / (1024f * 1024f)
        return Pair(usedMb, maxMb)
    }

    fun calculateHealthScore(statuses: Collection<RuntimeServiceStatus>, memoryUsedMb: Float, memoryMaxMb: Float): Int {
        if (statuses.isEmpty()) return 100

        var penalty = 0
        val total = statuses.size

        for (status in statuses) {
            if (status.currentState == RuntimeServiceState.FAILED) {
                penalty += if (status.spec.isCore) 25 else 10
            } else if (status.currentState == RuntimeServiceState.PAUSED) {
                penalty += 5
            } else if (status.currentState == RuntimeServiceState.STOPPED && status.spec.isCore) {
                penalty += 15
            }
        }

        if (memoryMaxMb > 0) {
            val memRatio = memoryUsedMb / memoryMaxMb
            if (memRatio > 0.85f) {
                penalty += 15
            } else if (memRatio > 0.70f) {
                penalty += 5
            }
        }

        val score = (100 - penalty).coerceIn(0, 100)
        return score
    }
}
