package com.example

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.example.engines.charging.AutomaticChargingProtectionEngine
import com.example.engines.charging.ChargingAuditEvent
import com.example.engines.charging.ChargingProtectionState
import com.example.engines.thermal.ThermalProtectionEngine
import com.example.engines.thermal.ThermalSessionState
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AutomaticChargingProtectionEngineTest {

    private lateinit var context: Context
    private val recordedAuditEvents = mutableListOf<ChargingAuditEvent>()

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        AutomaticChargingProtectionEngine.resetForTesting(context)
        recordedAuditEvents.clear()
        AutomaticChargingProtectionEngine.onChargingProtectionEventCallback = { event ->
            recordedAuditEvents.add(event)
        }

        // Set baseline system settings
        val resolver = context.contentResolver
        Settings.System.putInt(resolver, Settings.System.SCREEN_OFF_TIMEOUT, 60000) // 1 min baseline
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, 180) // 70% brightness baseline
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
    }

    @After
    fun tearDown() {
        AutomaticChargingProtectionEngine.resetForTesting(context)
        recordedAuditEvents.clear()
    }

    /**
     * Scenario A: Full Lifecycle (Connect -> Capture -> Override -> Disconnect -> Exact Baseline Restoration)
     */
    @Test
    fun testScenarioA_FullLifecycle_Capture_Override_Restore() {
        val resolver = context.contentResolver
        assertEquals(60000, Settings.System.getInt(resolver, Settings.System.SCREEN_OFF_TIMEOUT, -1))
        assertEquals(180, Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, -1))

        // 1. Connect charger
        val state = AutomaticChargingProtectionEngine.processTelemetry(
            context = context,
            isCharging = true,
            batteryLevel = 45,
            temperature = 34.0f,
            chargingType = "AC"
        )
        assertEquals(ChargingProtectionState.CHARGING_PROTECTION_RUNNING, state)
        assertTrue(AutomaticChargingProtectionEngine.isProtectionActive())

        val snapshot = AutomaticChargingProtectionEngine.getActiveSnapshot()
        assertNotNull(snapshot)
        assertEquals(60000, snapshot!!.originalScreenTimeout)
        assertEquals(180, snapshot.originalBrightnessValue)
        assertEquals(Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC, snapshot.originalBrightnessMode)

        // Verify overrides applied
        assertEquals(15000, Settings.System.getInt(resolver, Settings.System.SCREEN_OFF_TIMEOUT, -1))
        assertEquals(26, Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, -1))
        assertEquals(Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL, Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, -1))

        // 2. Disconnect charger
        val restoredState = AutomaticChargingProtectionEngine.processTelemetry(
            context = context,
            isCharging = false,
            batteryLevel = 60,
            temperature = 35.0f
        )
        assertEquals(ChargingProtectionState.NOT_CHARGING, restoredState)
        assertFalse(AutomaticChargingProtectionEngine.isProtectionActive())
        assertNull(AutomaticChargingProtectionEngine.getActiveSnapshot())

        // Verify exact baseline restoration
        assertEquals(60000, Settings.System.getInt(resolver, Settings.System.SCREEN_OFF_TIMEOUT, -1))
        assertEquals(180, Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, -1))
        assertEquals(Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC, Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, -1))
    }

    /**
     * Scenario B: User Manual Brightness Change During Charging (Preserve User Intent)
     */
    @Test
    fun testScenarioB_UserChangesBrightnessDuringCharging_PreserveUserIntent() {
        val resolver = context.contentResolver
        AutomaticChargingProtectionEngine.processTelemetry(context, isCharging = true, batteryLevel = 50, temperature = 35.0f)
        assertEquals(26, Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, -1))

        // User manually changes brightness to 220 during charging
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, 220)

        // Charger disconnects
        AutomaticChargingProtectionEngine.processTelemetry(context, isCharging = false, batteryLevel = 55, temperature = 35.0f)

        // Verify brightness is NOT overwritten to original (180), user's 220 is preserved
        assertEquals(220, Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, -1))
        assertTrue(recordedAuditEvents.any { it.eventType == "RESTORATION_SKIPPED_USER_CHANGED" && it.action.contains("BRIGHTNESS") })
    }

    /**
     * Scenario C: User Manual Screen Timeout Change During Charging (Preserve User Intent)
     */
    @Test
    fun testScenarioC_UserChangesTimeoutDuringCharging_PreserveUserIntent() {
        val resolver = context.contentResolver
        AutomaticChargingProtectionEngine.processTelemetry(context, isCharging = true, batteryLevel = 50, temperature = 35.0f)
        assertEquals(15000, Settings.System.getInt(resolver, Settings.System.SCREEN_OFF_TIMEOUT, -1))

        // User manually changes screen timeout to 300,000 ms (5 min) during charging
        Settings.System.putInt(resolver, Settings.System.SCREEN_OFF_TIMEOUT, 300000)

        // Charger disconnects
        AutomaticChargingProtectionEngine.processTelemetry(context, isCharging = false, batteryLevel = 55, temperature = 35.0f)

        // Verify screen timeout is NOT overwritten to original (60,000), user's 300,000 is preserved
        assertEquals(300000, Settings.System.getInt(resolver, Settings.System.SCREEN_OFF_TIMEOUT, -1))
        assertTrue(recordedAuditEvents.any { it.eventType == "RESTORATION_SKIPPED_USER_CHANGED" && it.action.contains("TIMEOUT") })
    }

    /**
     * Scenario D: Thermal Priority Override During Charging
     */
    @Test
    fun testScenarioD_ThermalPriorityOverChargingProtection() {
        val resolver = context.contentResolver

        // 1. Enter charging (Charging protection sets brightness to 10% = 26)
        AutomaticChargingProtectionEngine.processTelemetry(context, isCharging = true, batteryLevel = 50, temperature = 38.0f)
        assertEquals(26, Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, -1))

        // 2. Temperature spikes to 44°C (Thermal Protection kicks in with 5% = 13 after 2 readings)
        ThermalProtectionEngine.processTemperature(44.0f, context)
        ThermalProtectionEngine.processTemperature(44.0f, context)
        assertTrue(ThermalProtectionEngine.isProtectionActive())
        assertEquals(13, Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, -1))

        // 3. Telemetry while thermal protection is active keeps thermal override
        AutomaticChargingProtectionEngine.processTelemetry(context, isCharging = true, batteryLevel = 55, temperature = 44.0f)
        assertEquals(13, Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, -1))

        // 4. Cools to 39°C (3 readings required to restore) -> Thermal restores -> Device is STILL charging -> Screen returns to charging target (26)
        ThermalProtectionEngine.processTemperature(39.0f, context)
        ThermalProtectionEngine.processTemperature(39.0f, context)
        ThermalProtectionEngine.processTemperature(39.0f, context)
        assertFalse(ThermalProtectionEngine.isProtectionActive())
        // Apply charging protection level
        AutomaticChargingProtectionEngine.processTelemetry(context, isCharging = true, batteryLevel = 60, temperature = 39.0f)

        // 5. Disconnect charger -> Restores pre-charging baseline (180)
        AutomaticChargingProtectionEngine.processTelemetry(context, isCharging = false, batteryLevel = 60, temperature = 38.0f)
        assertEquals(180, Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, -1))
        assertEquals(60000, Settings.System.getInt(resolver, Settings.System.SCREEN_OFF_TIMEOUT, -1))
    }

    /**
     * Scenario E: Disconnect while Thermal Protection is Still Active
     */
    @Test
    fun testScenarioE_DisconnectWhileThermalProtectionActive() {
        val resolver = context.contentResolver
        AutomaticChargingProtectionEngine.processTelemetry(context, isCharging = true, batteryLevel = 50, temperature = 44.0f)
        ThermalProtectionEngine.processTemperature(44.0f, context)
        ThermalProtectionEngine.processTemperature(44.0f, context)
        assertTrue(ThermalProtectionEngine.isProtectionActive())

        // Disconnect charger at 44°C
        AutomaticChargingProtectionEngine.processTelemetry(context, isCharging = false, batteryLevel = 52, temperature = 44.0f)
        // Thermal protection must remain active
        assertTrue(ThermalProtectionEngine.isProtectionActive())
    }

    /**
     * Scenario F: Process Death / Service Restart While Still Charging
     */
    @Test
    fun testScenarioF_ProcessRestart_ResumeExistingSessionWithoutDuplicateSnapshot() {
        val resolver = context.contentResolver
        AutomaticChargingProtectionEngine.processTelemetry(context, isCharging = true, batteryLevel = 50, temperature = 36.0f)
        val initialSessionId = AutomaticChargingProtectionEngine.getActiveSnapshot()!!.chargingSessionId

        // Simulate application restart: clear memory singleton without clearing SharedPreferences
        AutomaticChargingProtectionEngine.initialize(context)

        // Telemetry arrives while still charging
        AutomaticChargingProtectionEngine.processTelemetry(context, isCharging = true, batteryLevel = 55, temperature = 36.5f)

        val resumedSnapshot = AutomaticChargingProtectionEngine.getActiveSnapshot()
        assertNotNull(resumedSnapshot)
        assertEquals(initialSessionId, resumedSnapshot!!.chargingSessionId)
        assertEquals(60000, resumedSnapshot.originalScreenTimeout)
        assertEquals(180, resumedSnapshot.originalBrightnessValue)

        // Disconnect restores original baseline
        AutomaticChargingProtectionEngine.processTelemetry(context, isCharging = false, batteryLevel = 60, temperature = 36.0f)
        assertEquals(60000, Settings.System.getInt(resolver, Settings.System.SCREEN_OFF_TIMEOUT, -1))
        assertEquals(180, Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, -1))
    }

    /**
     * Scenario G: Process Restart When Charger Disconnected During Downtime
     */
    @Test
    fun testScenarioG_ProcessRestart_WhenDisconnectedDuringDowntime() {
        val resolver = context.contentResolver
        AutomaticChargingProtectionEngine.processTelemetry(context, isCharging = true, batteryLevel = 50, temperature = 36.0f)
        assertEquals(15000, Settings.System.getInt(resolver, Settings.System.SCREEN_OFF_TIMEOUT, -1))

        // Save last known as disconnected
        val prefs = context.getSharedPreferences("netra_charging_protection_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("charging_last_known_charging", false).apply()

        // Reinitialize simulates restart
        AutomaticChargingProtectionEngine.resetForTesting(context)
        prefs.edit()
            .putString("charging_protection_state", ChargingProtectionState.CHARGING_PROTECTION_RUNNING.name)
            .putString("charging_active_snapshot", prefs.getString("charging_active_snapshot", null) ?: "")
            .putBoolean("charging_last_known_charging", false)
            .apply()

        // Restores cleanly on disconnect
        AutomaticChargingProtectionEngine.processTelemetry(context, isCharging = false, batteryLevel = 55, temperature = 35.0f)
        assertEquals(ChargingProtectionState.NOT_CHARGING, AutomaticChargingProtectionEngine.getState())
    }

    /**
     * Scenario H: Independent Restoration Verification
     */
    @Test
    fun testScenarioH_IndependentRestoration_PartialHandling() {
        AutomaticChargingProtectionEngine.processTelemetry(context, isCharging = true, batteryLevel = 50, temperature = 36.0f)

        // Execute state restoration
        val success = AutomaticChargingProtectionEngine.restoreDeviceState(context, 55, 36.0f)
        assertTrue(success)
        assertEquals(ChargingProtectionState.NOT_CHARGING, AutomaticChargingProtectionEngine.getState())
    }

    /**
     * Scenario I: Verification of Zero Manual Controls
     */
    @Test
    fun testScenarioI_ZeroManualControls_OnlyAutomaticTransitions() {
        // Must start in NOT_CHARGING
        assertEquals(ChargingProtectionState.NOT_CHARGING, AutomaticChargingProtectionEngine.getState())
        assertFalse(AutomaticChargingProtectionEngine.isProtectionActive())

        // Telemetry triggers state transitions automatically
        AutomaticChargingProtectionEngine.processTelemetry(context, isCharging = true, batteryLevel = 40, temperature = 35.0f)
        assertTrue(AutomaticChargingProtectionEngine.isProtectionActive())

        AutomaticChargingProtectionEngine.processTelemetry(context, isCharging = false, batteryLevel = 42, temperature = 35.0f)
        assertFalse(AutomaticChargingProtectionEngine.isProtectionActive())
        assertEquals(ChargingProtectionState.NOT_CHARGING, AutomaticChargingProtectionEngine.getState())
    }

    /**
     * Scenario J: Complete Audit Events Sequence
     */
    @Test
    fun testScenarioJ_CompleteAuditEventSequence() {
        recordedAuditEvents.clear()

        // Start charging
        AutomaticChargingProtectionEngine.processTelemetry(context, isCharging = true, batteryLevel = 50, temperature = 35.0f)

        val eventTypes = recordedAuditEvents.map { it.eventType }
        assertTrue(eventTypes.contains("CHARGING_PROTECTION_STARTED"))
        assertTrue(eventTypes.contains("CHARGING_STATE_SNAPSHOT_CREATED"))
        assertTrue(eventTypes.contains("SCREEN_TIMEOUT_CHANGED_FOR_CHARGING"))
        assertTrue(eventTypes.contains("BRIGHTNESS_CHANGED_FOR_CHARGING"))
        assertTrue(eventTypes.contains("BACKGROUND_WORKLOAD_REDUCED_FOR_CHARGING"))

        // Disconnect
        AutomaticChargingProtectionEngine.processTelemetry(context, isCharging = false, batteryLevel = 60, temperature = 35.0f)

        val disconnectEventTypes = recordedAuditEvents.map { it.eventType }
        assertTrue(disconnectEventTypes.contains("CHARGING_PROTECTION_RESTORING"))
        assertTrue(disconnectEventTypes.contains("SCREEN_TIMEOUT_RESTORED"))
        assertTrue(disconnectEventTypes.contains("BRIGHTNESS_RESTORED"))
        assertTrue(disconnectEventTypes.contains("BACKGROUND_WORKLOAD_RESTORED"))
        assertTrue(disconnectEventTypes.contains("CHARGING_PROTECTION_RESTORED"))
    }
}
