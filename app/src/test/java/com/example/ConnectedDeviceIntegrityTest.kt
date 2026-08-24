package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.devices.DeviceCategory
import com.example.devices.DeviceClassificationEngine
import com.example.service.ConnectedBluetoothDevice
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ConnectedDeviceIntegrityTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testBluetoothDeviceClassification_headphonesAndSpeakers() {
        val btHeadset = ConnectedBluetoothDevice(
            name = "Wireless Earbuds Pro",
            address = "11:22:33:44:55:66",
            batteryLevel = 80,
            deviceType = "Earbuds",
            isCharging = false,
            profile = "A2DP",
            connectionState = "LIVE",
            signalRssi = -65
        )

        val record = DeviceClassificationEngine.classifyBluetoothDevice(btHeadset)
        assertEquals(DeviceCategory.BLUETOOTH, record.primaryCategory)
        assertEquals("Bluetooth Headset / Earbuds", record.deviceType)
        assertEquals(80, record.batteryLevel)
        assertEquals("Bluetooth", record.transport)
        assertEquals("Connected", record.connectionState)
        assertTrue(record.capabilities.contains("HFP Voice"))
    }

    @Test
    fun testBluetoothDeviceClassification_smartwatchWearable() {
        val smartwatch = ConnectedBluetoothDevice(
            name = "Galaxy Watch 6",
            address = "AA:BB:CC:DD:EE:FF",
            batteryLevel = 45,
            deviceType = "Smart Watch",
            isCharging = true,
            profile = "HealthProfile",
            connectionState = "LIVE",
            signalRssi = -55
        )

        val record = DeviceClassificationEngine.classifyBluetoothDevice(smartwatch)
        assertEquals(DeviceCategory.WEARABLES, record.primaryCategory)
        assertEquals("Smartwatch", record.deviceType)
        assertEquals(45, record.batteryLevel)
        assertTrue(record.isCharging)
        assertTrue(record.capabilities.contains("GATT Profile"))
    }

    @Test
    fun testBluetoothBatteryUnavailableHandling() {
        val unknownBatteryDevice = ConnectedBluetoothDevice(
            name = "Generic BT Accessory",
            address = "00:11:22:33:44:55",
            batteryLevel = -1, // Unavailable
            deviceType = "Accessory",
            isCharging = false,
            profile = "Generic",
            connectionState = "LIVE",
            signalRssi = -92 // Weak (< -88)
        )

        val record = DeviceClassificationEngine.classifyBluetoothDevice(unknownBatteryDevice)
        assertEquals(-1, record.batteryLevel)
        assertEquals("Weak", record.signalStrength)
    }

    @Test
    fun testWifiDeviceClassification_routerAndCellular() {
        val wifiRecord = DeviceClassificationEngine.classifyWifiDevice(
            ssid = "HomeNetwork",
            rssi = -50,
            linkSpeedMbps = 433,
            isConnected = true,
            isInternetAvailable = true,
            isMobileData = false
        )

        assertEquals(DeviceCategory.WIFI, wifiRecord.primaryCategory)
        assertEquals("Wi-Fi", wifiRecord.transport)
        assertEquals("Wi-Fi Router / Access Point", wifiRecord.deviceType)
        assertEquals(433, wifiRecord.linkSpeedMbps)
        assertEquals("Excellent", wifiRecord.signalStrength)
        assertEquals(-1, wifiRecord.batteryLevel) // Wi-Fi routers do not expose battery

        val cellularRecord = DeviceClassificationEngine.classifyWifiDevice(
            ssid = "",
            rssi = -70,
            linkSpeedMbps = 0,
            isConnected = true,
            isInternetAvailable = true,
            isMobileData = true
        )

        assertEquals(DeviceCategory.WIFI, cellularRecord.primaryCategory)
        assertEquals("Mobile Data", cellularRecord.transport)
        assertEquals("Cellular Base Station", cellularRecord.deviceType)
    }

    @Test
    fun testDataAndGraphSeparation_noCrossSectionLeakage() {
        val btDevice = ConnectedBluetoothDevice(
            name = "Headphones",
            address = "12:34:56:78:90:AB",
            batteryLevel = 90,
            deviceType = "Headphones",
            isCharging = false,
            profile = "A2DP",
            connectionState = "LIVE",
            signalRssi = -60
        )
        val btRecord = DeviceClassificationEngine.classifyBluetoothDevice(btDevice)
        val wifiRecord = DeviceClassificationEngine.classifyWifiDevice(
            ssid = "OfficeWiFi",
            rssi = -60,
            linkSpeedMbps = 150,
            isConnected = true,
            isInternetAvailable = true,
            isMobileData = false
        )

        // Verify category boundaries
        assertEquals(DeviceCategory.BLUETOOTH, btRecord.primaryCategory)
        assertEquals(DeviceCategory.WIFI, wifiRecord.primaryCategory)

        // Bluetooth has battery, Wi-Fi does not (-1)
        assertNotEquals(-1, btRecord.batteryLevel)
        assertEquals(-1, wifiRecord.batteryLevel)

        // Wi-Fi has linkSpeed, Bluetooth does not (0)
        assertEquals(0, btRecord.linkSpeedMbps)
        assertEquals(150, wifiRecord.linkSpeedMbps)
    }
}
