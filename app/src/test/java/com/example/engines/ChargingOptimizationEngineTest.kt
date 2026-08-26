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
class ChargingOptimizationEngineTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockDatabase: AppDatabase

    private lateinit var engine: ChargingOptimizationEngine

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        engine = ChargingOptimizationEngine(mockContext, mockDatabase)
    }

    @Test
    fun testNotChargingState() {
        engine.updateChargingState(
            chargePercent = 50,
            isCharging = false,
            chargingPowerMilliamps = 0,
            temperature = 35f,
            voltage = 4200,
            health = 80
        )
        assertEquals(ChargingState.NotCharging, engine.chargingState.value)
        assertEquals("Not Charging", engine.getChargingStatusText())
    }

    @Test
    fun testChargingState() {
        engine.updateChargingState(
            chargePercent = 50,
            isCharging = true,
            chargingPowerMilliamps = 2000,
            temperature = 35f,
            voltage = 5000,
            health = 80
        )
        assertEquals(ChargingState.Charging, engine.chargingState.value)
        assertEquals("Charging", engine.getChargingStatusText())
    }

    @Test
    fun testFullBatteryState() {
        engine.updateChargingState(
            chargePercent = 100,
            isCharging = true,
            chargingPowerMilliamps = 0,
            temperature = 35f,
            voltage = 4200,
            health = 80
        )
        assertEquals(ChargingState.Full, engine.chargingState.value)
        assertEquals("Fully Charged", engine.getChargingStatusText())
    }

    @Test
    fun testThermallyLimitedState() {
        engine.updateChargingState(
            chargePercent = 50,
            isCharging = true,
            chargingPowerMilliamps = 1000,
            temperature = 45f,
            voltage = 5000,
            health = 80
        )
        assertEquals(ChargingState.ThermallyLimited, engine.chargingState.value)
        assertEquals("Charging (Thermally Limited)", engine.getChargingStatusText())
        assertEquals(true, engine.chargingMetrics.value.thermallyLimited)
    }

    @Test
    fun testOptimizedChargingState() {
        engine.updateChargingState(
            chargePercent = 85,
            isCharging = true,
            chargingPowerMilliamps = 500,  // Low power = optimization
            temperature = 35f,
            voltage = 5000,
            health = 80
        )
        assertEquals(ChargingState.Optimized, engine.chargingState.value)
        assertEquals("Optimized Charging", engine.getChargingStatusText())
    }

    @Test
    fun testChargingMetricsCalculation() {
        engine.updateChargingState(
            chargePercent = 50,
            isCharging = true,
            chargingPowerMilliamps = 2000,
            temperature = 35f,
            voltage = 5000,
            health = 80
        )
        val metrics = engine.chargingMetrics.value
        assertEquals(50, metrics.currentChargePercent)
        assertEquals(true, metrics.chargingState == ChargingState.Charging)
        assertTrue(metrics.estimatedTimeMinutes >= 0)
    }

    @Test
    fun testChargingStatusColors() {
        engine.updateChargingState(
            chargePercent = 50,
            isCharging = false,
            chargingPowerMilliamps = 0,
            temperature = 35f,
            voltage = 4200,
            health = 80
        )
        assertTrue(engine.getChargingStatusColor() > 0)
    }

    @Test
    fun testResetSessions() {
        engine.updateChargingState(
            chargePercent = 50,
            isCharging = true,
            chargingPowerMilliamps = 2000,
            temperature = 35f,
            voltage = 5000,
            health = 80
        )
        assertEquals(true, engine.sessionActive.value)

        engine.resetSessions()
        assertEquals(false, engine.sessionActive.value)
    }
}
