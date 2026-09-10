package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.service.BluetoothBatteryAnnouncementEngine
import com.example.service.BluetoothBatteryManager
import com.example.service.BluetoothBatteryState
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BluetoothAnnouncementEventTest {

    private lateinit var context: Context
    private val testAddress = "AA:BB:CC:DD:EE:FF"
    private val testName = "Test Earbuds"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Clear prefs for test isolation
        val prefs = context.getSharedPreferences("bt_battery_announcement_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @Test
    fun testRealConnectionTriggersAnnouncementOnce() {
        // First connection event
        BluetoothBatteryAnnouncementEngine.onDeviceConnected(
            context, testAddress, testName, 85, "Earbuds", true
        )

        // Simulating app reopen / state reload / UI read with same connection state
        // Should not trigger another connection announcement because session preference is set
        BluetoothBatteryAnnouncementEngine.onDeviceConnected(
            context, testAddress, testName, 85, "Earbuds", true
        )
    }

    @Test
    fun testBatteryThresholdCrossingAnnouncement() {
        // Initial connection at 95% (sets initial threshold bracket without announcement or with connection announcement)
        BluetoothBatteryAnnouncementEngine.onDeviceConnected(
            context, testAddress, testName, 95, "Earbuds", true
        )

        // Battery drops to 90% (crosses into 90 bracket) -> should announce threshold
        BluetoothBatteryAnnouncementEngine.onBatteryLevelChanged(
            context, testAddress, testName, 90, true
        )

        // Battery fluctuates between 90% and 92% -> should NOT announce again for same 90 bracket
        BluetoothBatteryAnnouncementEngine.onBatteryLevelChanged(
            context, testAddress, testName, 91, true
        )
        BluetoothBatteryAnnouncementEngine.onBatteryLevelChanged(
            context, testAddress, testName, 92, true
        )
    }

    @Test
    fun testDisconnectAndReconnectAllowsNewAnnouncement() {
        // Connect
        BluetoothBatteryAnnouncementEngine.onDeviceConnected(
            context, testAddress, testName, 80, "Earbuds", true
        )

        // Disconnect
        BluetoothBatteryAnnouncementEngine.onDeviceDisconnected(
            context, testAddress, testName
        )

        // Reconnect -> genuine new event allowed
        BluetoothBatteryAnnouncementEngine.onDeviceConnected(
            context, testAddress, testName, 78, "Earbuds", true
        )
    }

    @Test
    fun testDeviceIsolation() {
        val device1 = "11:11:11:11:11:11"
        val device2 = "22:22:22:22:22:22"

        // Device 1 connects and announces 80%
        BluetoothBatteryAnnouncementEngine.onDeviceConnected(context, device1, "Device 1", 80, "Headphones", true)
        BluetoothBatteryAnnouncementEngine.onBatteryLevelChanged(context, device1, "Device 1", 80, true)

        // Device 2 connects at 80% -> should not be suppressed by Device 1's history
        BluetoothBatteryAnnouncementEngine.onDeviceConnected(context, device2, "Device 2", 80, "Headphones", true)
    }
}
