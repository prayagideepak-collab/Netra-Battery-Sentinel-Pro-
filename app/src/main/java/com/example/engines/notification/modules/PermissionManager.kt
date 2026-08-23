package com.example.engines.notification.modules

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.engines.notification.NotificationEvent

object PermissionManager {

    fun isNotificationPermissionGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun isBluetoothPermissionGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun isLocationPermissionGranted(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isPermissionGrantedForEvent(context: Context, event: NotificationEvent): Boolean {
        return when (event) {
            NotificationEvent.BLUETOOTH_CONNECTED,
            NotificationEvent.BLUETOOTH_DISCONNECTED,
            NotificationEvent.BLUETOOTH_LOW_BATTERY -> isBluetoothPermissionGranted(context)

            NotificationEvent.WEATHER_GOVERNMENT,
            NotificationEvent.WEATHER_EXTREME -> isLocationPermissionGranted(context)

            else -> isNotificationPermissionGranted(context)
        }
    }
}
