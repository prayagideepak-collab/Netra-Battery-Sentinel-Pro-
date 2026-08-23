package com.example.providers

import android.app.ActivityManager
import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

data class SafeServiceHealthInfo(
    val isServiceRunning: Boolean = false,
    val isForegroundServiceAllowed: Boolean = true,
    val isSupportedOnDevice: Boolean = true,
    val statusMessage: String = "Service Operational"
)

object SafeServiceHealthProvider {
    private const val TAG = "SafeServiceHealthProvider"

    fun checkServiceHealth(context: Context, serviceClass: Class<*>): SafeServiceHealthInfo {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            if (am == null) {
                return SafeServiceHealthInfo(
                    isServiceRunning = false,
                    isSupportedOnDevice = false,
                    statusMessage = "ActivityManager service unavailable"
                )
            }

            @Suppress("DEPRECATION")
            val runningServices = try {
                am.getRunningServices(100)
            } catch (e: SecurityException) {
                Log.w(TAG, "getRunningServices restricted by system security policy")
                null
            } catch (e: Exception) {
                null
            }

            val isRunning = runningServices?.any { it.service.className == serviceClass.name } == true

            SafeServiceHealthInfo(
                isServiceRunning = isRunning,
                isForegroundServiceAllowed = true,
                isSupportedOnDevice = true,
                statusMessage = if (isRunning) "Service Running Normally" else "Service Stopped / Idle"
            )
        } catch (e: Exception) {
            Log.w(TAG, "SafeServiceHealthProvider checkServiceHealth trapped exception: ${e.message}")
            SafeServiceHealthInfo(
                isServiceRunning = false,
                isSupportedOnDevice = false,
                statusMessage = "Service health check isolated due to exception: ${e.javaClass.simpleName}"
            )
        }
    }

    fun safeStartForegroundService(context: Context, intent: Intent): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    context.startForegroundService(intent)
                    true
                } catch (e: Exception) {
                    Log.w(TAG, "startForegroundService failed or restricted (app in background), falling back or deferring: ${e.message}")
                    try {
                        context.startService(intent)
                        true
                    } catch (ex: Exception) {
                        Log.w(TAG, "startService restricted (app in background): ${ex.message}")
                        false
                    }
                }
            } else {
                context.startService(intent)
                true
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Android security restriction prevented startService: ${e.message}")
            false
        } catch (e: Exception) {
            Log.w(TAG, "startService exception isolated (background restriction): ${e.message}")
            false
        }
    }

    fun safeServiceStartForeground(
        service: Service,
        notificationId: Int,
        notification: Notification,
        foregroundServiceType: Int = 0
    ): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && foregroundServiceType != 0) {
                service.startForeground(notificationId, notification, foregroundServiceType)
            } else {
                service.startForeground(notificationId, notification)
            }
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException in startForeground: ${e.message}. Attempting fallback...")
            try {
                service.startForeground(notificationId, notification)
                true
            } catch (ex: Exception) {
                Log.e(TAG, "Fallback startForeground failed: ${ex.message}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception in startForeground: ${e.message}. Attempting fallback...")
            try {
                service.startForeground(notificationId, notification)
                true
            } catch (ex: Exception) {
                Log.e(TAG, "Fallback startForeground failed: ${ex.message}")
                false
            }
        }
    }
}
