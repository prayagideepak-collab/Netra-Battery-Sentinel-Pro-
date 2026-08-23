package com.example.engines.notification.modules

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.example.engines.notification.NotificationEvent

object CapabilityDetector {

    fun isCapabilitySupported(context: Context, event: NotificationEvent): Boolean {
        val pm = context.packageManager
        return when (event) {
            NotificationEvent.BLUETOOTH_CONNECTED,
            NotificationEvent.BLUETOOTH_DISCONNECTED,
            NotificationEvent.BLUETOOTH_LOW_BATTERY -> pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)

            NotificationEvent.MAGNETIC_NORMAL,
            NotificationEvent.MAGNETIC_CRITICAL,
            NotificationEvent.MAGNETIC_EMERGENCY -> pm.hasSystemFeature(PackageManager.FEATURE_SENSOR_COMPASS) or pm.hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER)

            NotificationEvent.BATTERY_CRITICAL_FAILURE -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP

            else -> true
        }
    }
}
