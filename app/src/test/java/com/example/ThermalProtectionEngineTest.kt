package com.example

import android.content.ContentResolver
import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.example.data.SettingsEntity
import com.example.engines.thermal.ThermalProtectionEngine
import com.example.engines.thermal.ThermalSessionState
import com.example.engines.thermal.ThermalSnapshot
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ThermalProtectionEngineTest {

    private lateinit var context: Context
    private val defaultSettings = SettingsEntity(tempAlertThreshold = 45.0f)

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        ThermalProtectionEngine.resetForTesting(context)
    }

    @After
    fun tearDown() {
        ThermalProtectionEngine.resetForTesting(context)
    }

    @Test
    fun test01_NormalToBelowThreshold_NoProtection() {
        // Temperature < threshold (e.g. 44.9°C) -> No thermal protection
        val state = ThermalProtectionEngine.processTemperature(44.9f, context, defaultSettings)
        assertEquals(ThermalSessionState.NORMAL, state)
        assertFalse(ThermalProtectionEngine.isProtectionActive())
        assertNull(ThermalProtectionEngine.getActiveSnapshot())
    }

    @Test
    fun test02_OverheatActivation_SnapshotCreatedAndProtected() {
        // Set an arbitrary initial brightness
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 185)
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)

        // Temperature reaches overheat threshold (45.0°C)
        val state = ThermalProtectionEngine.processTemperature(45.0f, context, defaultSettings)
        assertEquals(ThermalSessionState.PROTECTED, state)
        assertTrue(ThermalProtectionEngine.isProtectionActive())

        val snapshot = ThermalProtectionEngine.getActiveSnapshot()
        assertNotNull(snapshot)
        assertEquals(45.0f, snapshot!!.entryTemperature, 0.01f)
        assertEquals(185, snapshot.previousBrightnessValue)
        assertEquals(Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC, snapshot.previousBrightnessMode)
    }

    @Test
    fun test03_NoRepeatedSnapshot_Idempotence() {
        // Set initial user brightness = 190
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 190)
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)

        // Overheat trigger
        ThermalProtectionEngine.processTemperature(45.5f, context, defaultSettings)
        val originalSnapshot = ThermalProtectionEngine.getActiveSnapshot()
        assertNotNull(originalSnapshot)
        val originalSessionId = originalSnapshot!!.sessionId
        val originalTimestamp = originalSnapshot.timestamp

        // Subsequent higher temperature readings during the same thermal session
        val state2 = ThermalProtectionEngine.processTemperature(47.0f, context, defaultSettings)
        val state3 = ThermalProtectionEngine.processTemperature(46.2f, context, defaultSettings)

        assertEquals(ThermalSessionState.PROTECTED, state2)
        assertEquals(ThermalSessionState.PROTECTED, state3)

        // Snapshot must remain identical and NOT be overwritten
        val currentSnapshot = ThermalProtectionEngine.getActiveSnapshot()
        assertNotNull(currentSnapshot)
        assertEquals(originalSessionId, currentSnapshot!!.sessionId)
        assertEquals(originalTimestamp, currentSnapshot.timestamp)
        assertEquals(190, currentSnapshot.previousBrightnessValue)
    }

    @Test
    fun test04_Recovery_AutomaticRestoration() {
        // Initial brightness = 175, Auto Mode
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 175)
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)

        // Overheat
        ThermalProtectionEngine.processTemperature(45.0f, context, defaultSettings)
        assertTrue(ThermalProtectionEngine.isProtectionActive())

        // Temperature drops to safe recovery threshold (40.0°C)
        val recoveryState = ThermalProtectionEngine.processTemperature(40.0f, context, defaultSettings)
        assertEquals(ThermalSessionState.NORMAL, recoveryState)
        assertFalse(ThermalProtectionEngine.isProtectionActive())
        assertNull(ThermalProtectionEngine.getActiveSnapshot())

        // Check restored brightness
        val restoredBrightness = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, -1)
        val restoredMode = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, -1)
        assertEquals(175, restoredBrightness)
        assertEquals(Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC, restoredMode)
    }

    @Test
    fun test05_ExactBrightnessRestoration_ArbitraryUserValue() {
        val userBrightness = 217
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, userBrightness)
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)

        ThermalProtectionEngine.processTemperature(46.0f, context, defaultSettings)
        assertTrue(ThermalProtectionEngine.isProtectionActive())

        // Recover
        ThermalProtectionEngine.processTemperature(39.5f, context, defaultSettings)
        assertEquals(ThermalSessionState.NORMAL, ThermalProtectionEngine.getState())

        val restored = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, -1)
        assertEquals(userBrightness, restored)
    }

    @Test
    fun test06_AutoBrightnessRestoration_BothModes() {
        // Case A: Initial mode was Auto ON
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 160)
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)

        ThermalProtectionEngine.processTemperature(45.0f, context, defaultSettings)
        ThermalProtectionEngine.processTemperature(39.0f, context, defaultSettings)
        assertEquals(Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC, Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, -1))

        // Reset for Case B
        ThermalProtectionEngine.resetForTesting(context)

        // Case B: Initial mode was Manual OFF
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 110)
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)

        ThermalProtectionEngine.processTemperature(45.0f, context, defaultSettings)
        ThermalProtectionEngine.processTemperature(39.0f, context, defaultSettings)
        assertEquals(Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL, Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, -1))
    }

    @Test
    fun test07_BackgroundProcessingRestoration() {
        // Test master sync snapshot and restoration
        ContentResolver.setMasterSyncAutomatically(true)
        assertTrue(ContentResolver.getMasterSyncAutomatically())

        ThermalProtectionEngine.processTemperature(45.5f, context, defaultSettings)
        val snapshot = ThermalProtectionEngine.getActiveSnapshot()
        assertNotNull(snapshot)
        assertTrue(snapshot!!.previousSyncEnabled)

        // Recovery
        ThermalProtectionEngine.processTemperature(39.0f, context, defaultSettings)
        assertTrue(ContentResolver.getMasterSyncAutomatically())
    }

    @Test
    fun test08_ProcessRestartSafety() {
        // User state before overheat
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 204)
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)

        // Thermal event triggers
        ThermalProtectionEngine.processTemperature(45.8f, context, defaultSettings)
        assertTrue(ThermalProtectionEngine.isProtectionActive())

        // SIMULATE PROCESS RESTART: re-initialize engine from disk
        val reflectionField = ThermalProtectionEngine::class.java.getDeclaredField("isInitialized")
        reflectionField.isAccessible = true
        reflectionField.set(ThermalProtectionEngine, false)

        ThermalProtectionEngine.initialize(context)

        // Verify session survived the process restart
        assertTrue(ThermalProtectionEngine.isProtectionActive())
        val recoveredSnapshot = ThermalProtectionEngine.getActiveSnapshot()
        assertNotNull(recoveredSnapshot)
        assertEquals(204, recoveredSnapshot!!.previousBrightnessValue)

        // Now safe temperature arrives
        ThermalProtectionEngine.processTemperature(39.8f, context, defaultSettings)
        assertEquals(ThermalSessionState.NORMAL, ThermalProtectionEngine.getState())

        val restoredBrightness = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, -1)
        assertEquals(204, restoredBrightness)
    }

    @Test
    fun test09_MultipleThermalCycles() {
        // Cycle 1: Brightness 150
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 150)
        ThermalProtectionEngine.processTemperature(45.0f, context, defaultSettings)
        val snapshot1 = ThermalProtectionEngine.getActiveSnapshot()
        assertNotNull(snapshot1)
        assertEquals(150, snapshot1!!.previousBrightnessValue)

        // Cycle 1 Recovery
        ThermalProtectionEngine.processTemperature(40.0f, context, defaultSettings)
        assertEquals(ThermalSessionState.NORMAL, ThermalProtectionEngine.getState())

        // User adjusts brightness between events to 230
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 230)

        // Cycle 2: Temperature overheats again
        ThermalProtectionEngine.processTemperature(45.2f, context, defaultSettings)
        val snapshot2 = ThermalProtectionEngine.getActiveSnapshot()
        assertNotNull(snapshot2)
        assertNotEquals(snapshot1.sessionId, snapshot2!!.sessionId)
        assertEquals(230, snapshot2.previousBrightnessValue)

        // Cycle 2 Recovery
        ThermalProtectionEngine.processTemperature(39.0f, context, defaultSettings)
        assertEquals(ThermalSessionState.NORMAL, ThermalProtectionEngine.getState())
        assertEquals(230, Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, -1))
    }

    @Test
    fun test10_PartialActionFailure() {
        // Verify snapshot serialization & partial action handling
        val snapshot = ThermalSnapshot(
            sessionId = "test-session-partial",
            timestamp = System.currentTimeMillis(),
            entryTemperature = 45.5f,
            previousBrightnessValue = 180,
            previousBrightnessMode = Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
            targetThermalBrightness = 25,
            brightnessModified = true,
            brightnessModeModified = false,
            previousSyncEnabled = true,
            syncModified = false, // Sync action failed/skipped
            appliedActions = listOf("SCREEN_BRIGHTNESS_DIM")
        )

        val jsonStr = snapshot.toJson()
        val deserialized = ThermalSnapshot.fromJson(jsonStr)
        assertNotNull(deserialized)
        assertTrue(deserialized!!.brightnessModified)
        assertFalse(deserialized.syncModified)
        assertEquals(1, deserialized.appliedActions.size)
        assertEquals("SCREEN_BRIGHTNESS_DIM", deserialized.appliedActions[0])
    }

    @Test
    fun test11_Hysteresis_NoRapidOscillation() {
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 195)

        // Overheat trigger at 45.0°C
        ThermalProtectionEngine.processTemperature(45.0f, context, defaultSettings)
        assertTrue(ThermalProtectionEngine.isProtectionActive())

        // Small drops around 44.9°C, 43.5°C, 42.0°C, 40.1°C must NOT deactivate protection
        ThermalProtectionEngine.processTemperature(44.9f, context, defaultSettings)
        assertTrue(ThermalProtectionEngine.isProtectionActive())

        ThermalProtectionEngine.processTemperature(43.5f, context, defaultSettings)
        assertTrue(ThermalProtectionEngine.isProtectionActive())

        ThermalProtectionEngine.processTemperature(41.0f, context, defaultSettings)
        assertTrue(ThermalProtectionEngine.isProtectionActive())

        ThermalProtectionEngine.processTemperature(40.1f, context, defaultSettings)
        assertTrue(ThermalProtectionEngine.isProtectionActive())

        // Only when dropping to <= 40.0°C does it restore
        ThermalProtectionEngine.processTemperature(40.0f, context, defaultSettings)
        assertFalse(ThermalProtectionEngine.isProtectionActive())
        assertEquals(ThermalSessionState.NORMAL, ThermalProtectionEngine.getState())
    }

    @Test
    fun test12_SafeConcurrentAdjustmentHandling() {
        // User pre-event brightness = 140
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 140)
        ThermalProtectionEngine.processTemperature(45.0f, context, defaultSettings)

        // While hot, user deliberately manually changes brightness to 80 (not 25)
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 80)

        // Recovery occurs
        ThermalProtectionEngine.processTemperature(39.0f, context, defaultSettings)
        assertEquals(ThermalSessionState.NORMAL, ThermalProtectionEngine.getState())

        // Manual adjustment made by user during hot state (80) is preserved safely
        val finalBrightness = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, -1)
        assertEquals(80, finalBrightness)
    }
}
