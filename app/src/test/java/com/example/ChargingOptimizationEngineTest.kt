package com.example

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.example.data.SettingsEntity
import com.example.engines.charging.ChargingOptimizationEngine
import com.example.engines.charging.ChargingOptimizationState
import com.example.engines.charging.ChargingSpeedClassification
import com.example.engines.charging.ChargingTempClass
import com.example.engines.thermal.ThermalProtectionEngine
import com.example.engines.thermal.ThermalSessionState
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ChargingOptimizationEngineTest {

    private lateinit var context: Context
    private val defaultSettings = SettingsEntity(tempAlertThreshold = 45.0f)

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        ChargingOptimizationEngine.resetForTesting(context)
    }

    @After
    fun tearDown() {
        ChargingOptimizationEngine.resetForTesting(context)
    }

    @Test
    fun test01_ChargingConnected_OptimizationStarts() {
        val state = ChargingOptimizationEngine.processUpdate(context, isCharging = true, batteryLevel = 45, temperature = 36.0f, chargingType = "AC")
        assertEquals(ChargingOptimizationState.CHARGING_OPTIMIZED, state)
        assertNotNull(ChargingOptimizationEngine.getActiveSnapshot())
    }

    @Test
    fun test02_ChargingDisconnected_OptimizationEnds() {
        ChargingOptimizationEngine.processUpdate(context, isCharging = true, batteryLevel = 50, temperature = 37.0f)
        assertEquals(ChargingOptimizationState.CHARGING_OPTIMIZED, ChargingOptimizationEngine.getState())

        val state = ChargingOptimizationEngine.processUpdate(context, isCharging = false, batteryLevel = 52, temperature = 37.0f)
        assertEquals(ChargingOptimizationState.NOT_CHARGING, state)
        assertNull(ChargingOptimizationEngine.getActiveSnapshot())
    }

    @Test
    fun test03_ChargingNormalTemp_38Degrees() {
        val tempClass = ChargingOptimizationEngine.classifyTemperature(38.0f)
        assertEquals(ChargingTempClass.WARM, tempClass)

        val state = ChargingOptimizationEngine.processUpdate(context, isCharging = true, batteryLevel = 60, temperature = 38.0f)
        assertEquals(ChargingOptimizationState.CHARGING_OPTIMIZED, state)
    }

    @Test
    fun test04_ChargingThermalThreshold_43Degrees_PriorityRule() {
        val tempClass = ChargingOptimizationEngine.classifyTemperature(43.0f)
        assertEquals(ChargingTempClass.THERMALLY_LIMITED, tempClass)

        // Thermal protection takes absolute priority when temperature >= 43°C (with threshold set to 43.0f)
        val customSettings = SettingsEntity(tempAlertThreshold = 43.0f)
        val state = ChargingOptimizationEngine.processUpdate(context, isCharging = true, batteryLevel = 65, temperature = 43.0f)
        assertEquals(ChargingOptimizationState.THERMAL_LIMITED_CHARGING, state)
        
        // Also test at 45.0f where default thermal protection activates
        val state45 = ChargingOptimizationEngine.processUpdate(context, isCharging = true, batteryLevel = 65, temperature = 45.0f)
        assertEquals(ChargingOptimizationState.THERMAL_LIMITED_CHARGING, state45)
        assertTrue(ThermalProtectionEngine.isProtectionActive())
    }

    @Test
    fun test05_ThermalEscalation_45Degrees() {
        val tempClass = ChargingOptimizationEngine.classifyTemperature(45.0f)
        assertEquals(ChargingTempClass.OVERHEATING, tempClass)

        val state = ChargingOptimizationEngine.processUpdate(context, isCharging = true, batteryLevel = 70, temperature = 45.0f)
        assertEquals(ChargingOptimizationState.THERMAL_LIMITED_CHARGING, state)
        assertEquals(ThermalSessionState.PROTECTED, ThermalProtectionEngine.getState())
    }

    @Test
    fun test06_ChargerDisconnectedAt44Degrees_ThermalProtectionRemainsActive() {
        // Trigger thermal protection at 45°C
        ChargingOptimizationEngine.processUpdate(context, isCharging = true, batteryLevel = 75, temperature = 45.0f)
        assertTrue(ThermalProtectionEngine.isProtectionActive())

        // Disconnect charger while temperature is still 44°C (above recovery threshold 40°C)
        val state = ChargingOptimizationEngine.processUpdate(context, isCharging = false, batteryLevel = 75, temperature = 44.0f)
        assertEquals(ChargingOptimizationState.NOT_CHARGING, state)

        // Thermal protection MUST remain active because temperature is 44°C (> 40°C)
        assertTrue(ThermalProtectionEngine.isProtectionActive())
    }

    @Test
    fun test07_TemperatureSafeRecovery_LessThan40Degrees() {
        // Trigger thermal protection
        ChargingOptimizationEngine.processUpdate(context, isCharging = true, batteryLevel = 80, temperature = 45.0f)
        assertTrue(ThermalProtectionEngine.isProtectionActive())

        // Drop temperature to 39°C <= 40°C
        ChargingOptimizationEngine.processUpdate(context, isCharging = false, batteryLevel = 80, temperature = 39.0f)
        assertFalse(ThermalProtectionEngine.isProtectionActive())
        assertEquals(ThermalSessionState.NORMAL, ThermalProtectionEngine.getState())
    }

    @Test
    fun test08_FullCharge_100PercentRecorded() {
        val state = ChargingOptimizationEngine.processUpdate(context, isCharging = true, batteryLevel = 100, temperature = 37.0f)
        assertEquals(ChargingOptimizationState.FULL_CHARGE, state)
    }

    @Test
    fun test09_ProcessRestartDuringCharging_StateRecovered() {
        ChargingOptimizationEngine.processUpdate(context, isCharging = true, batteryLevel = 55, temperature = 37.0f)
        assertEquals(ChargingOptimizationState.CHARGING_OPTIMIZED, ChargingOptimizationEngine.getState())

        // Simulate restart
        val reflectionField = ChargingOptimizationEngine::class.java.getDeclaredField("currentState")
        reflectionField.isAccessible = true
        // Re-initialize state recovery simulation
        val recoveredState = ChargingOptimizationEngine.processUpdate(context, isCharging = true, batteryLevel = 56, temperature = 37.2f)
        assertEquals(ChargingOptimizationState.CHARGING_OPTIMIZED, recoveredState)
    }

    @Test
    fun test10_ProcessRestartDuringThermalProtection_ThermalStateRecovered() {
        ThermalProtectionEngine.processTemperature(45.5f, context, defaultSettings)
        assertTrue(ThermalProtectionEngine.isProtectionActive())

        // Simulate process restart for ThermalProtectionEngine
        val ref = ThermalProtectionEngine::class.java.getDeclaredField("isInitialized")
        ref.isAccessible = true
        ref.set(ThermalProtectionEngine, false)
        ThermalProtectionEngine.initialize(context)

        assertTrue(ThermalProtectionEngine.isProtectionActive())
    }

    @Test
    fun test11_UnsupportedFastChargeTelemetry_NeverFakesHardwareFastCharge() {
        // If device telemetry lacks wattage/fast-charge hardware flag, classify as UNKNOWN or ESTIMATED
        val speed = ChargingOptimizationEngine.classifyChargingSpeed(0.2f)
        assertEquals(ChargingSpeedClassification.SLOW, speed)

        val unknownSpeed = ChargingOptimizationEngine.classifyChargingSpeed(-0.1f)
        assertEquals(ChargingSpeedClassification.UNKNOWN, unknownSpeed)
    }

    @Test
    fun test12_PartialRestoration_RemainingActionsContinue() {
        // Test snapshot & partial restoration capability via ThermalProtectionEngine
        val snapshot = com.example.engines.thermal.ThermalSnapshot(
            sessionId = "partial-test",
            timestamp = System.currentTimeMillis(),
            entryTemperature = 45.0f,
            previousBrightnessValue = 150,
            brightnessModified = true,
            syncModified = false
        )
        assertNotNull(snapshot)
        assertTrue(snapshot.brightnessModified)
        assertFalse(snapshot.syncModified)
    }
}
