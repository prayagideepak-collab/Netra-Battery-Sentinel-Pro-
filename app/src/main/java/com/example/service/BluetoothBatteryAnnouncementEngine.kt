package com.example.service

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.util.Log
import com.example.engines.notification.EventPriority
import com.example.engines.notification.NotificationEvent
import com.example.engines.notification.modules.AnnouncementQueue

object BluetoothBatteryAnnouncementEngine {
    private const val TAG = "BtBatteryAnnouncement"
    
    private val connectedAnnouncedAddresses = mutableSetOf<String>()
    private val lastThresholdMap = mutableMapOf<String, Int>()
    private val THRESHOLDS = listOf(10, 20, 30, 40, 50, 60, 70, 80, 90, 100)

    fun processDevices(context: Context, devices: List<ConnectedBluetoothDevice>) {
        val states = mutableMapOf<String, BluetoothBatteryState>()
        for (dev in devices) {
            states[dev.address] = BluetoothBatteryState(
                name = dev.name,
                address = dev.address,
                batteryLevel = dev.batteryLevel,
                isConnected = dev.connectionState == "LIVE",
                deviceType = dev.deviceType,
                isAudioDevice = dev.deviceType in listOf("Earbuds", "Headphones", "Speaker") || dev.profile.contains("A2DP")
            )
        }
        processDevices(context, states)
    }

    fun processDevices(context: Context, btStates: Map<String, BluetoothBatteryState>) {
        val appContext = context.applicationContext
        
        // 1. Check AnnouncementPolicyChecker
        if (AnnouncementPolicyChecker.shouldSkipAnnouncement(appContext)) {
            return
        }

        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val outputs = audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS) ?: emptyArray()
        
        // Inspect AudioManager.getDevices(GET_DEVICES_OUTPUTS) list to verify Bluetooth audio output routing
        val hasBtAudioOutput = outputs.any { 
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || 
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || 
            it.type == AudioDeviceInfo.TYPE_BLE_HEADSET || 
            it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER 
        }

        val activeAddresses = mutableSetOf<String>()

        for ((address, state) in btStates) {
            if (!state.isConnected) continue
            activeAddresses.add(address)

            val batteryLevel = state.batteryLevel
            // Fallback mechanism: if connected Bluetooth device is classified as a non-audio device (e.g., smartwatch) or no BT audio output route exists, play announcement through phone speaker
            val forcePhoneSpeaker = !state.isAudioDevice || !hasBtAudioOutput

            // 1. Connection Announcement
            if (!connectedAnnouncedAddresses.contains(address)) {
                connectedAnnouncedAddresses.add(address)
                if (batteryLevel >= 0) {
                    val connectionText = "${state.name} connected. Battery level is $batteryLevel percent."
                    Log.i(TAG, "Announcing BT Connection: $connectionText (ForcePhoneSpeaker: $forcePhoneSpeaker)")
                    AnnouncementQueue.enqueue(
                        appContext,
                        NotificationEvent.BLUETOOTH_CONNECTED,
                        EventPriority.INFORMATION,
                        connectionText
                    )
                    val matchedThreshold = THRESHOLDS.lastOrNull { batteryLevel >= it } ?: 10
                    lastThresholdMap[address] = matchedThreshold
                }
            }

            // 2. Threshold-Crossing Announcement
            if (batteryLevel >= 0) {
                val previousThreshold = lastThresholdMap[address] ?: run {
                    val initialT = THRESHOLDS.lastOrNull { batteryLevel >= it } ?: 10
                    lastThresholdMap[address] = initialT
                    initialT
                }

                for (t in THRESHOLDS) {
                    if (batteryLevel == t && previousThreshold != t) {
                        lastThresholdMap[address] = t
                        val thresholdText = "${state.name} battery is $t percent."
                        Log.i(TAG, "Announcing BT Battery Threshold: $thresholdText (ForcePhoneSpeaker: $forcePhoneSpeaker)")
                        AnnouncementQueue.enqueue(
                            appContext,
                            NotificationEvent.BLUETOOTH_LOW_BATTERY,
                            EventPriority.WARNING,
                            thresholdText
                        )
                        break
                    }
                }
                
                val currentBracket = THRESHOLDS.lastOrNull { batteryLevel >= it }
                if (currentBracket != null && currentBracket != previousThreshold) {
                    if (currentBracket < previousThreshold || currentBracket > previousThreshold) {
                        lastThresholdMap[address] = currentBracket
                        val thresholdText = "${state.name} battery is $currentBracket percent."
                        Log.i(TAG, "Announcing BT Battery Bracket: $thresholdText")
                        AnnouncementQueue.enqueue(
                            appContext,
                            NotificationEvent.BLUETOOTH_LOW_BATTERY,
                            EventPriority.WARNING,
                            thresholdText
                        )
                    }
                }
            }
        }

        // Clean up disconnected devices
        val disconnected = connectedAnnouncedAddresses.filter { !activeAddresses.contains(it) }
        for (addr in disconnected) {
            connectedAnnouncedAddresses.remove(addr)
            lastThresholdMap.remove(addr)
        }
    }
}
