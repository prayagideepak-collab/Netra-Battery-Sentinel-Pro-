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
        // Single and double readings < 43°C (e.g. 42.9°C) -> No thermal protection
        ThermalProtectionEngine.processTemperature(42.5f, context, defaultSettings)
        val state = ThermalProtectionEngine.processTemperature(42.9f, context, defaultSettings)
        assertEquals(ThermalSessionState.NORMAL, state)
        assertFalse(ThermalProtectionEngine.isProtectionActive())
        assertNull(ThermalProtectionEngine.getActiveSnapshot())
    }

    @Test
    fun test02_ProtectionActivation_43C_Debounce() {
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 185)
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)

        // Reading 1: 43.0°C -> Debouncing, not yet active
        val state1 = ThermalProtectionEngine.processTemperature(43.0f, context, defaultSettings)
        assertEquals(ThermalSessionState.NORMAL, state1)
        assertFalse(ThermalProtectionEngine.isProtectionActive())

        // Reading 2: 43.2°C -> Confirmed (2 readings) -> Activates THERMAL_PROTECTION
        val state2 = ThermalProtectionEngine.processTemperature(43.2f, context, defaultSettings)
        assertEquals(ThermalSessionState.THERMAL_PROTECTION, state2)
        assertTrue(ThermalProtectionEngine.isProtectionActive())

        val snapshot = ThermalProtectionEngine.getActiveSnapshot()
        assertNotNull(snapshot)
        assertEquals(43.2f, snapshot!!.entryTemperature, 0.01f)
        assertEquals(185, snapshot.previousBrightnessValue)
        assertEquals(Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC, snapshot.previousBrightnessMode)
    }

    @Test
    fun test03_EscalationActivation_45C_Debounce() {
        var speechTriggered: String? = null
        ThermalProtectionEngine.onThermalSpeechCallback = { text -> speechTriggered = text }

        // Reading 1: 45.0°C
        ThermalProtectionEngine.processTemperature(45.0f, context, defaultSettings)
        assertNull(speechTriggered)

        // Reading 2: 45.5°C -> Confirmed -> Escalates to THERMAL_ESCALATED and speaks
        val state = ThermalProtectionEngine.processTemperature(45.5f, context, defaultSettings)
        assertEquals(ThermalSessionState.THERMAL_ESCALATED, state)
        assertTrue(ThermalProtectionEngine.isProtectionActive())
        assertNotNull(speechTriggered)
        assertTrue(speechTriggered!!.contains("overheating"))
    }

    @Test
    fun test04_Recovery_AutomaticRestoration_40C_Debounce() {
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 175)
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)

        // Trigger protection (2 readings)
        ThermalProtectionEngine.processTemperature(43.5f, context, defaultSettings)
        ThermalProtectionEngine.processTemperature(44.0f, context, defaultSettings)
        assertTrue(ThermalProtectionEngine.isProtectionActive())

        // Recovery reading 1: 40.0°C (Not restored yet)
        ThermalProtectionEngine.processTemperature(40.0f, context, defaultSettings)
        assertTrue(ThermalProtectionEngine.isProtectionActive())

        // Recovery reading 2: 39.8°C (Not restored yet)
        ThermalProtectionEngine.processTemperature(39.8f, context, defaultSettings)
        assertTrue(ThermalProtectionEngine.isProtectionActive())

        // Recovery reading 3: 39.5°C (Confirmed 3 readings <= 40°C -> Restores state)
        val recoveryState = ThermalProtectionEngine.processTemperature(39.5f, context, defaultSettings)
        assertEquals(ThermalSessionState.NORMAL, recoveryState)
        assertFalse(ThermalProtectionEngine.isProtectionActive())
        assertNull(ThermalProtectionEngine.getActiveSnapshot())

        // Restored brightness and mode check
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

        ThermalProtectionEngine.processTemperature(43.5f, context, defaultSettings)
        ThermalProtectionEngine.processTemperature(43.5f, context, defaultSettings)
        assertTrue(ThermalProtectionEngine.isProtectionActive())

        // Recover (3 readings)
        ThermalProtectionEngine.processTemperature(39.5f, context, defaultSettings)
        ThermalProtectionEngine.processTemperature(39.5f, context, defaultSettings)
        ThermalProtectionEngine.processTemperature(39.5f, context, defaultSettings)
        assertEquals(ThermalSessionState.NORMAL, ThermalProtectionEngine.getState())

        val restored = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, -1)
        assertEquals(userBrightness, restored)
    }

    @Test
    fun test06_UserManualBrightnessChange_SkipsOverwrite() {
        // User starts at 200
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 200)
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)

        ThermalProtectionEngine.processTemperature(43.5f, context, defaultSettings)
        ThermalProtectionEngine.processTemperature(43.5f, context, defaultSettings)
        assertTrue(ThermalProtectionEngine.isProtectionActive())

        // User manually changes brightness to 120 during protection
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 120)

        // Recover
        ThermalProtectionEngine.processTemperature(39.0f, context, defaultSettings)
        ThermalProtectionEngine.processTemperature(39.0f, context, defaultSettings)
        ThermalProtectionEngine.processTemperature(39.0f, context, defaultSettings)

        // Must NOT overwrite user's 120 with previous 200!
        val currentBrightness = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, -1)
        assertEquals(120, currentBrightness)
    }

    @Test
    fun test07_BackgroundProcessingRestoration() {
        ContentResolver.setMasterSyncAutomatically(true)
        assertTrue(ContentResolver.getMasterSyncAutomatically())

        ThermalProtectionEngine.processTemperature(45.5f, context, defaultSettings)
        ThermalProtectionEngine.processTemperature(45.5f, context, defaultSettings)
        val snapshot = ThermalProtectionEngine.getActiveSnapshot()
        assertNotNull(snapshot)
        assertTrue(snapshot!!.previousSyncEnabled)

        // Recovery (3 readings)
        ThermalProtectionEngine.processTemperature(39.0f, context, defaultSettings)
        ThermalProtectionEngine.processTemperature(39.0f, context, defaultSettings)
        ThermalProtectionEngine.processTemperature(39.0f, context, defaultSettings)
        assertTrue(ContentResolver.getMasterSyncAutomatically())
    }

    @Test
    fun test08_ProcessRestartSafety() {
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 204)
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)

        ThermalProtectionEngine.processTemperature(45.8f, context, defaultSettings)
        ThermalProtectionEngine.processTemperature(45.8f, context, defaultSettings)
        assertTrue(ThermalProtectionEngine.isProtectionActive())

        // Re-initialize from disk to simulate restart
        val reflectionField = ThermalProtectionEngine::class.java.getDeclaredField("isInitialized")
        reflectionField.isAccessible = true
        reflectionField.set(ThermalProtectionEngine, false)

        ThermalProtectionEngine.initialize(context)
        assertTrue(ThermalProtectionEngine.isProtectionActive())

        val recoveredSnapshot = ThermalProtectionEngine.getActiveSnapshot()
        assertNotNull(recoveredSnapshot)
        assertEquals(204, recoveredSnapshot!!.previousBrightnessValue)

        // Safe temperature arrives (3 readings)
        ThermalProtectionEngine.processTemperature(39.8f, context, defaultSettings)
        ThermalProtectionEngine.processTemperature(39.8f, context, defaultSettings)
        ThermalProtectionEngine.processTemperature(39.8f, context, defaultSettings)
        assertEquals(ThermalSessionState.NORMAL, ThermalProtectionEngine.getState())

        val restoredBrightness = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, -1)
        assertEquals(204, restoredBrightness)
    }

    @Test
    fun test09_SnapshotSerialization() {
        val snapshot = ThermalSnapshot(
            sessionId = "test-session-partial",
            timestamp = System.currentTimeMillis(),
            entryTemperature = 45.5f,
            previousBrightnessValue = 180,
            previousBrightnessMode = Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
            targetThermalBrightness = 13,
            brightnessModified = true,
            brightnessModeModified = false,
            previousSyncEnabled = true,
            syncModified = false,
            appliedActions = listOf("SCREEN_BRIGHTNESS_DIM")
        )

        val jsonStr = snapshot.toJson()
        val deserialized = ThermalSnapshot.fromJson(jsonStr)
        assertNotNull(deserialized)
        assertTrue(deserialized!!.brightnessModified)
        assertEquals(13, deserialized.targetThermalBrightness)
    }
}
