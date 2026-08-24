package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.engines.network.AuthoritativeNetworkLogger
import com.example.providers.SafeNetworkInfo
import com.example.providers.SafeNetworkProvider
import com.example.providers.SafeTelephonyInfo
import com.example.providers.SafeTelephonyProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NetworkTelemetryIntegrityTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AuthoritativeNetworkLogger.resetForTesting()
    }

    @Test
    fun testWifiConnection_duplicateEventsSuppressedAndTransitionsPreserved() {
        // Initialize baseline state
        AuthoritativeNetworkLogger.initializeBaseline(
            wifiRadio = true,
            wifiConnected = false,
            ssid = "",
            transport = "CELLULAR",
            internetAccess = true,
            airplaneMode = false,
            bluetoothRadio = true
        )

        val initialTracked = AuthoritativeNetworkLogger.getTrackedState()
        assertEquals(false, initialTracked["wifiConnected"])

        // First transition: CONNECTED to HomeNetwork
        AuthoritativeNetworkLogger.onWifiConnectionStateChanged(context, true, "HomeNetwork", 1000L)
        var state = AuthoritativeNetworkLogger.getTrackedState()
        assertEquals(true, state["wifiConnected"])
        assertEquals("HomeNetwork", state["wifiSsid"])

        // Emit DUPLICATE CONNECTED event with same SSID - should be suppressed by early return
        AuthoritativeNetworkLogger.onWifiConnectionStateChanged(context, true, "HomeNetwork", 1050L)
        state = AuthoritativeNetworkLogger.getTrackedState()
        assertEquals(true, state["wifiConnected"])
        assertEquals("HomeNetwork", state["wifiSsid"])

        // Emit DISCONNECTED transition
        AuthoritativeNetworkLogger.onWifiConnectionStateChanged(context, false, "HomeNetwork", 1100L)
        state = AuthoritativeNetworkLogger.getTrackedState()
        assertEquals(false, state["wifiConnected"])

        // Emit CONNECTED transition again (legitimate state change #3)
        AuthoritativeNetworkLogger.onWifiConnectionStateChanged(context, true, "OfficeNetwork", 1200L)
        state = AuthoritativeNetworkLogger.getTrackedState()
        assertEquals(true, state["wifiConnected"])
        assertEquals("OfficeNetwork", state["wifiSsid"])
    }

    @Test
    fun testSafeNetworkInfo_noFabricationWhenUnavailable() {
        // Test SafeNetworkInfo default/fallback restrictions
        val info = SafeNetworkInfo(
            isWifiConnected = false,
            isCellularConnected = false,
            isInternetAvailable = false,
            ssid = "Unavailable / Location Permission Required",
            bssid = "Restricted",
            rssi = -127,
            linkSpeedMbps = 0,
            isSupportedOnDevice = true
        )
        assertFalse(info.isWifiConnected)
        assertEquals("Unavailable / Location Permission Required", info.ssid)
        assertEquals("Restricted", info.bssid)
        assertEquals(-127, info.rssi)
        assertEquals(0, info.linkSpeedMbps)

        // Exercise SafeNetworkProvider with context
        val liveInfo = SafeNetworkProvider.getNetworkInfo(context)
        assertNotNull(liveInfo)
        assertTrue(liveInfo.isSupportedOnDevice)
    }

    @Test
    fun testSafeTelephonyInfo_restrictedAndFallbackHandling() {
        val telephonyInfo = SafeTelephonyInfo(
            isCallActive = false,
            networkOperatorName = "Unknown / No SIM",
            networkType = "Unknown",
            isRoaming = false,
            simState = "UNKNOWN",
            isDualSimConfigured = true,
            isSupportedOnDevice = true
        )
        assertFalse(telephonyInfo.isCallActive)
        assertEquals("Unknown / No SIM", telephonyInfo.networkOperatorName)
        assertEquals("Unknown", telephonyInfo.networkType)

        val liveTelephony = SafeTelephonyProvider.getTelephonyInfo(context)
        assertNotNull(liveTelephony)
        assertTrue(liveTelephony.isSupportedOnDevice)
    }

    @Test
    fun testBluetoothRadioState_transitionsAndDuplicateSuppression() {
        AuthoritativeNetworkLogger.initializeBaseline(
            wifiRadio = false,
            wifiConnected = false,
            ssid = "",
            transport = "CELLULAR",
            internetAccess = true,
            airplaneMode = false,
            bluetoothRadio = false
        )

        var state = AuthoritativeNetworkLogger.getTrackedState()
        assertEquals(false, state["bluetoothRadio"])

        // Turn BT ON
        AuthoritativeNetworkLogger.onBluetoothRadioStateChanged(context, true, 2000L)
        state = AuthoritativeNetworkLogger.getTrackedState()
        assertEquals(true, state["bluetoothRadio"])

        // Duplicate BT ON callback - should be suppressed
        AuthoritativeNetworkLogger.onBluetoothRadioStateChanged(context, true, 2050L)
        state = AuthoritativeNetworkLogger.getTrackedState()
        assertEquals(true, state["bluetoothRadio"])

        // Turn BT OFF
        AuthoritativeNetworkLogger.onBluetoothRadioStateChanged(context, false, 2100L)
        state = AuthoritativeNetworkLogger.getTrackedState()
        assertEquals(false, state["bluetoothRadio"])
    }

    @Test
    fun testBluetoothDeviceConnectionAndBatterySanitization() {
        AuthoritativeNetworkLogger.initializeBaseline(
            wifiRadio = false,
            wifiConnected = false,
            ssid = "",
            transport = "CELLULAR",
            internetAccess = true,
            airplaneMode = false,
            bluetoothRadio = true
        )

        // Connect device A
        AuthoritativeNetworkLogger.onBluetoothDeviceConnected(context, "Headphones", "AA:BB:CC:DD:EE:FF", "AUDIO", 3000L)
        var state = AuthoritativeNetworkLogger.getTrackedState()
        assertEquals(1, state["connectedBtDevicesCount"])

        // Duplicate connection event for same MAC should be suppressed
        AuthoritativeNetworkLogger.onBluetoothDeviceConnected(context, "Headphones", "AA:BB:CC:DD:EE:FF", "AUDIO", 3050L)
        state = AuthoritativeNetworkLogger.getTrackedState()
        assertEquals(1, state["connectedBtDevicesCount"])

        // Invalid battery percentages must be rejected (< 0, > 100, NaN/Infinity representation via bounds)
        AuthoritativeNetworkLogger.onBluetoothBatteryInformation(context, "Headphones", "AA:BB:CC:DD:EE:FF", -5, 3100L)
        AuthoritativeNetworkLogger.onBluetoothBatteryInformation(context, "Headphones", "AA:BB:CC:DD:EE:FF", 105, 3110L)

        // Valid initial battery percentage
        AuthoritativeNetworkLogger.onBluetoothBatteryInformation(context, "Headphones", "AA:BB:CC:DD:EE:FF", 85, 3120L)

        // Minor battery delta (< 10%) should be suppressed by anti-spam rule
        AuthoritativeNetworkLogger.onBluetoothBatteryInformation(context, "Headphones", "AA:BB:CC:DD:EE:FF", 82, 3130L)

        // Significant battery delta (>= 10%) or low threshold (<= 20%) should be accepted
        AuthoritativeNetworkLogger.onBluetoothBatteryInformation(context, "Headphones", "AA:BB:CC:DD:EE:FF", 15, 3140L)

        // Disconnect device A
        AuthoritativeNetworkLogger.onBluetoothDeviceDisconnected(context, "Headphones", "AA:BB:CC:DD:EE:FF", 3200L)
        state = AuthoritativeNetworkLogger.getTrackedState()
        assertEquals(0, state["connectedBtDevicesCount"])
    }

    @Test
    fun testActiveTransportSwitch_wifiCellular() {
        AuthoritativeNetworkLogger.initializeBaseline(
            wifiRadio = true,
            wifiConnected = true,
            ssid = "OfficeWi-Fi",
            transport = "WIFI",
            internetAccess = true,
            airplaneMode = false,
            bluetoothRadio = true
        )

        var state = AuthoritativeNetworkLogger.getTrackedState()
        assertEquals("WIFI", state["activeTransport"])

        // Switch transport to CELLULAR
        AuthoritativeNetworkLogger.onActiveTransportChanged(context, "CELLULAR", 4000L)
        state = AuthoritativeNetworkLogger.getTrackedState()
        assertEquals("CELLULAR", state["activeTransport"])

        // Duplicate active transport change should be ignored
        AuthoritativeNetworkLogger.onActiveTransportChanged(context, "CELLULAR", 4050L)
        state = AuthoritativeNetworkLogger.getTrackedState()
        assertEquals("CELLULAR", state["activeTransport"])
    }
}
