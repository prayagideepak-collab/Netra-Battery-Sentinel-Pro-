package com.example.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

/**
 * NetraDeviceAdminReceiver
 * Provides Device Administrator integration recognized by Android OS in system settings.
 * Grants safe coordination of battery telemetry and deep sleep state observation.
 */
class NetraDeviceAdminReceiver : DeviceAdminReceiver() {
    
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Netra Device Administrator enabled in system settings")
        try {
            Toast.makeText(context, "Netra Device Protection Activated", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.w(TAG, "Could not show toast", e)
        }
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.i(TAG, "Netra Device Administrator disabled in system settings")
        try {
            Toast.makeText(context, "Netra Device Protection Deactivated", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.w(TAG, "Could not show toast", e)
        }
    }

    companion object {
        private const val TAG = "NetraDeviceAdmin"
    }
}
