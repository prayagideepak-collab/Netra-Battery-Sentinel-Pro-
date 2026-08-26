package com.example.engines

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.AppDatabase
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class ThermalProtectionEngineTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockDatabase: AppDatabase

    private lateinit var engine: ThermalProtectionEngine

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        engine = ThermalProtectionEngine(mockContext, mockDatabase)
    }

    @Test
    fun testNormalStateAt35Celsius() {
        engine.updateThermalState(35f)
        assertEquals(ThermalState.Normal, engine.thermalState.value)
        assertEquals(0, engine.getBrightnessReduction())
    }

    @Test
    fun testProtectionStateAt43Celsius() {
        engine.updateThermalState(43f)
        assertEquals(ThermalState.Protection, engine.thermalState.value)
        assertEquals(5, engine.getBrightnessReduction())
    }

    @Test
    fun testEscalatedStateAt45Celsius() {
        engine.updateThermalState(45f)
        assertEquals(ThermalState.Escalated, engine.thermalState.value)
        assertEquals(5, engine.getBrightnessReduction())
    }

    @Test
    fun testRestorationTriggerAt40Celsius() {
        engine.updateThermalState(45f)
        assertEquals(ThermalState.Escalated, engine.thermalState.value)

        engine.updateThermalState(40f)
        assertEquals(ThermalState.Restoring, engine.thermalState.value)
    }

    @Test
    fun testBrightnessReductionDuring Protection() {
        engine.updateThermalState(40f)
        assertEquals(false, engine.shouldReduceBrightness())

        engine.updateThermalState(43f)
        assertEquals(true, engine.shouldReduceBrightness())
        assertEquals(5, engine.getBrightnessReduction())
    }

    @Test
    fun testThermalStatusText() {
        engine.updateThermalState(35f)
        assertEquals("Normal", engine.getThermalStatusText())

        engine.updateThermalState(43f)
        assertEquals("Thermal Protection Active", engine.getThermalStatusText())

        engine.updateThermalState(45f)
        assertEquals("Thermal Escalation", engine.getThermalStatusText())
    }

    @Test
    fun testThermalStatusColors() {
        engine.updateThermalState(35f)
        assertTrue(engine.getThermalStatusColor() > 0)  // Green for Normal

        engine.updateThermalState(43f)
        assertTrue(engine.getThermalStatusColor() > 0)  // Amber for Protection

        engine.updateThermalState(45f)
        assertTrue(engine.getThermalStatusColor() > 0)  // Red-Orange for Escalated
    }

    @Test
    fun testResetThermalState() {
        engine.updateThermalState(45f)
        assertEquals(ThermalState.Escalated, engine.thermalState.value)

        engine.resetThermalState()
        assertEquals(ThermalState.Normal, engine.thermalState.value)
        assertEquals(false, engine.shouldReduceBrightness())
    }
}
