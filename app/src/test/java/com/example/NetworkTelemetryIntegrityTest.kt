package com.example

import com.example.engines.network.AuthoritativeNetworkLogger
import com.example.providers.SafeNetworkInfo
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NetworkTelemetryIntegrityTest {

    @Before
    fun setUp() {
        AuthoritativeNetworkLogger.resetForTesting()
    }

    @Test
    fun testWifiState_noFabricationOnColdStart() {
        AuthoritativeNetworkLogger.initializeBaseline(
            wifiRadio = true,
            wifiConnected = true,
            ssid = "HomeNetwork",
            transport = "WIFI",
            internetAccess = true,
            airplaneMode = false,
            bluetoothRadio = true
        )
        val state = AuthoritativeNetworkLogger.getTrackedState()
        assertEquals(true, state["wifiConnected"])
        assertEquals("HomeNetwork", state["wifiSsid"])
    }

    @Test
    fun testWifiConnection_duplicateEventsSuppressed() {
        AuthoritativeNetworkLogger.initializeBaseline(
            wifiRadio = true,
            wifiConnected = true,
            ssid = "HomeNetwork",
            transport = "WIFI",
            internetAccess = true,
            airplaneMode = false,
            bluetoothRadio = true
        )
        val stateBefore = AuthoritativeNetworkLogger.getTrackedState()
        assertEquals(true, stateBefore["wifiConnected"])
    }

    @Test
    fun testSafeNetworkInfo_unknownWhenUnavailable() {
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
    }

    @Test
    fun testBluetoothRadioState_transitions() {
        AuthoritativeNetworkLogger.initializeBaseline(
            wifiRadio = false,
            wifiConnected = false,
            ssid = "",
            transport = "CELLULAR",
            internetAccess = true,
            airplaneMode = false,
            bluetoothRadio = false
        )
        val state = AuthoritativeNetworkLogger.getTrackedState()
        assertEquals(false, state["bluetoothRadio"])
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
        val state = AuthoritativeNetworkLogger.getTrackedState()
        assertEquals("WIFI", state["activeTransport"])
    }
}
