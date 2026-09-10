package com.example.service

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.example.engines.notification.EventPriority
import com.example.engines.notification.NotificationEvent
import com.example.engines.notification.modules.AnnouncementQueue

object BluetoothBatteryAnnouncementEngine {
    private const val TAG = "BtBatteryAnnouncement"
    private val THRESHOLDS = listOf(10, 20, 30, 40, 50, 60, 70, 80, 90, 100)

    private fun routeAudio(context: Context, isAudioDevice: Boolean) {
        val appContext = context.applicationContext
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val hasBtAudioOutput = outputs.any { 
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || 
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || 
            it.type == AudioDeviceInfo.TYPE_BLE_HEADSET || 
            it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER 
        }
        val forcePhoneSpeaker = !isAudioDevice || !hasBtAudioOutput

        if (forcePhoneSpeaker) {
            audioManager.isSpeakerphoneOn = true
        } else {
            audioManager.isSpeakerphoneOn = false
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val btDev = outputs.find { 
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || 
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || 
                        it.type == AudioDeviceInfo.TYPE_BLE_HEADSET || 
                        it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER 
                    }
                    if (btDev != null) {
                        audioManager.setCommunicationDevice(btDev)
                    }
                } else {
                    @Suppress("DEPRECATION")
                    audioManager.isBluetoothScoOn = true
                    @Suppress("DEPRECATION")
                    audioManager.startBluetoothSco()
                }
            } catch (e: Exception) {}
        }
    }

    fun onDeviceConnected(context: Context, address: String, name: String, batteryLevel: Int, deviceType: String, isAudioDevice: Boolean) {
        val appContext = context.applicationContext
        if (AnnouncementPolicyChecker.shouldSkipAnnouncement(appContext)) return

        val prefs = appContext.getSharedPreferences("bt_battery_announcement_prefs", Context.MODE_PRIVATE)
        val connectionKey = "connected_session_$address"
        val alreadyAnnounced = prefs.getBoolean(connectionKey, false)

        if (!alreadyAnnounced) {
            prefs.edit()
                .putBoolean(connectionKey, true)
                .remove("disconnected_session_$address")
                .apply()

            routeAudio(appContext, isAudioDevice)

            val text = if (batteryLevel >= 0) {
                "$name connected. Battery level is $batteryLevel percent."
            } else {
                "$name connected."
            }
            Log.i(TAG, "Announcing BT Connection: $text")
            AnnouncementQueue.enqueue(
                appContext,
                NotificationEvent.BLUETOOTH_CONNECTED,
                EventPriority.INFORMATION,
                text
            )
        }
    }

    fun onDeviceDisconnected(context: Context, address: String, name: String) {
        val appContext = context.applicationContext
        if (AnnouncementPolicyChecker.shouldSkipAnnouncement(appContext)) return

        val prefs = appContext.getSharedPreferences("bt_battery_announcement_prefs", Context.MODE_PRIVATE)
        val disconnectKey = "disconnected_session_$address"
        val alreadyAnnounced = prefs.getBoolean(disconnectKey, false)

        if (!alreadyAnnounced) {
            prefs.edit()
                .putBoolean(disconnectKey, true)
                .remove("connected_session_$address")
                .remove("threshold_$address")
                .apply()

            val text = "$name disconnected."
            Log.i(TAG, "Announcing BT Disconnection: $text")
            AnnouncementQueue.enqueue(
                appContext,
                NotificationEvent.BLUETOOTH_DISCONNECTED,
                EventPriority.INFORMATION,
                text
            )
        }
    }

    fun onBatteryLevelChanged(context: Context, address: String, name: String, batteryLevel: Int, isAudioDevice: Boolean) {
        val appContext = context.applicationContext
        if (AnnouncementPolicyChecker.shouldSkipAnnouncement(appContext)) return
        if (batteryLevel < 0) return

        val prefs = appContext.getSharedPreferences("bt_battery_announcement_prefs", Context.MODE_PRIVATE)
        val thresholdKey = "threshold_$address"
        val previousBracket = prefs.getInt(thresholdKey, -1)
        val currentBracket = THRESHOLDS.lastOrNull { batteryLevel >= it } ?: 10

        if (previousBracket == -1) {
            prefs.edit().putInt(thresholdKey, currentBracket).apply()
        } else if (currentBracket != previousBracket) {
            prefs.edit().putInt(thresholdKey, currentBracket).apply()
            routeAudio(appContext, isAudioDevice)

            val text = "$name battery is $currentBracket percent."
            Log.i(TAG, "Announcing BT Battery Threshold Crossing: $text")
            AnnouncementQueue.enqueue(
                appContext,
                NotificationEvent.BLUETOOTH_LOW_BATTERY,
                EventPriority.WARNING,
                text
            )
        }
    }
}
