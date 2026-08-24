package com.example

import com.example.battery.engine.BatteryPredictionEngine
import com.example.battery.engine.BatteryVelocityEngine
import com.example.battery.model.BatterySample
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BatteryEtaEngineTest {

    // Test A: 50% battery, +10%/hour charging
    @Test
    fun testA_50Percent_Charging_10PercentPerHour() {
        val result = BatteryPredictionEngine.estimateTimeToFullMinutes(50, 10.0f)
        assertEquals(300L, result) // (100 - 50) / 10 * 60 = 300 minutes (5 hours)
    }

    // Test B: 80% battery, +20%/hour charging
    @Test
    fun testB_80Percent_Charging_20PercentPerHour() {
        val result = BatteryPredictionEngine.estimateTimeToFullMinutes(80, 20.0f)
        assertEquals(60L, result) // (100 - 80) / 20 * 60 = 60 minutes (1 hour)
    }

    // Test C: 99% battery, positive charging velocity (+30%/hr)
    @Test
    fun testC_99Percent_PositiveChargingVelocity() {
        val result = BatteryPredictionEngine.estimateTimeToFullMinutes(99, 30.0f)
        assertEquals(2L, result) // (100 - 99) / 30 * 60 = 2 minutes
    }

    // Test D: 100% battery (charging state)
    @Test
    fun testD_100PercentBattery() {
        val result = BatteryPredictionEngine.estimateTimeToFullMinutes(100, 15.0f)
        assertEquals(0L, result) // Reached full charge
    }

    // Test E: 50% battery, -10%/hour discharge
    @Test
    fun testE_50Percent_Discharge_10PercentPerHour() {
        val result = BatteryPredictionEngine.estimateTimeToEmptyMinutes(50, -10.0f)
        assertEquals(300L, result) // 50 / 10 * 60 = 300 minutes (5 hours)
    }

    // Test F: 20% battery, -5%/hour discharge
    @Test
    fun testF_20Percent_Discharge_5PercentPerHour() {
        val result = BatteryPredictionEngine.estimateTimeToEmptyMinutes(20, -5.0f)
        assertEquals(240L, result) // 20 / 5 * 60 = 240 minutes (4 hours)
    }

    // Test G: 0% battery (discharging state)
    @Test
    fun testG_0PercentBattery() {
        val result = BatteryPredictionEngine.estimateTimeToEmptyMinutes(0, -8.0f)
        assertEquals(0L, result) // Empty battery reached
    }

    // Test H: null velocity
    @Test
    fun testH_NullVelocity() {
        assertNull(BatteryPredictionEngine.estimateTimeToFullMinutes(50, null))
        assertNull(BatteryPredictionEngine.estimateTimeToEmptyMinutes(50, null))
    }

    // Test I: zero velocity
    @Test
    fun testI_ZeroVelocity() {
        assertNull(BatteryPredictionEngine.estimateTimeToFullMinutes(50, 0.0f))
        assertNull(BatteryPredictionEngine.estimateTimeToEmptyMinutes(50, 0.0f))
    }

    // Test J: wrong-sign velocity
    @Test
    fun testJ_WrongSignVelocity() {
        // Charging ETA with negative velocity -> null
        assertNull(BatteryPredictionEngine.estimateTimeToFullMinutes(50, -15.0f))
        // Discharging ETA with positive velocity -> null
        assertNull(BatteryPredictionEngine.estimateTimeToEmptyMinutes(50, 15.0f))
    }

    // Test K: negative battery percentage (< 0)
    @Test
    fun testK_NegativeBatteryPercentage() {
        assertNull(BatteryPredictionEngine.estimateTimeToFullMinutes(-5, 10.0f))
        assertNull(BatteryPredictionEngine.estimateTimeToEmptyMinutes(-5, -10.0f))
    }

    // Test L: >100% battery percentage
    @Test
    fun testL_Over100BatteryPercentage() {
        assertNull(BatteryPredictionEngine.estimateTimeToFullMinutes(105, 10.0f))
        assertNull(BatteryPredictionEngine.estimateTimeToEmptyMinutes(105, -10.0f))
    }

    // Test M: duplicate timestamps
    @Test
    fun testM_DuplicateTimestamps() {
        val engine = BatteryVelocityEngine(maxSamples = 5, minSampleIntervalMs = 1000L)
        val now = 1000000L
        engine.addSample(BatterySample(50, now))
        // Duplicate timestamp
        val vDuplicate = engine.addSample(BatterySample(51, now))
        assertNull("Duplicate timestamp must be rejected without creating invalid velocity", vDuplicate)
    }

    // Test N: decreasing timestamp
    @Test
    fun testN_DecreasingTimestamp() {
        val engine = BatteryVelocityEngine(maxSamples = 5, minSampleIntervalMs = 1000L)
        val now = 1000000L
        engine.addSample(BatterySample(50, now))
        // Decreasing timestamp (backwards in time)
        val vBackwards = engine.addSample(BatterySample(51, now - 5000L))
        assertNull("Decreasing timestamp must be rejected", vBackwards)
    }

    // Test O: sudden unrealistic percentage jump
    @Test
    fun testO_SuddenUnrealisticPercentageJump() {
        val engine = BatteryVelocityEngine(maxSamples = 5, minSampleIntervalMs = 1000L)
        val now = 1000000L
        engine.addSample(BatterySample(20, now))
        // Jump from 20% to 90% in 5 seconds (50,400%/hr -> physically impossible)
        val vJump = engine.addSample(BatterySample(90, now + 5000L))
        assertNull("Sudden unrealistic percentage jump must be rejected as an anomaly", vJump)
    }

    // Test P: insufficient samples (< 2 valid samples)
    @Test
    fun testP_InsufficientSamples() {
        val engine = BatteryVelocityEngine(maxSamples = 5, minSampleIntervalMs = 1000L)
        val v = engine.addSample(BatterySample(50, 1000000L))
        assertNull("Single sample has insufficient data to determine rate", v)
    }

    // Test Q: restart/state restoration
    @Test
    fun testQ_StateRestorationAndClear() {
        val engine = BatteryVelocityEngine(maxSamples = 5, minSampleIntervalMs = 1000L)
        val now = 1000000L
        engine.addSample(BatterySample(50, now))
        engine.addSample(BatterySample(60, now + 3600000L))
        assertNotNull(engine.calculateCurrentVelocity())

        // Clear simulates restart
        engine.clear()
        assertNull("After clear/restart, velocity must be null until fresh data arrives", engine.calculateCurrentVelocity())
    }

    // Test R: charger connect transition
    @Test
    fun testR_ChargerConnectTransition() {
        val engine = BatteryVelocityEngine(maxSamples = 5, minSampleIntervalMs = 1000L)
        val now = 1000000L
        engine.onChargingStateChanged(false)
        engine.addSample(BatterySample(50, now))
        engine.addSample(BatterySample(48, now + 1800000L))

        // State transition: Charger connected
        engine.onChargingStateChanged(true)
        assertNull("On charger connect, previous discharge history must be cleared", engine.calculateCurrentVelocity())

        BatteryPredictionEngine.invalidateStateTransition(isCharging = true)
        val remainingMs = BatteryPredictionEngine.calculateRemainingTimeMs(
            percentage = 50,
            isCharging = true,
            currentNowVal = 0,
            isScreenOn = true,
            capacity = 4500,
            speed = 0f,
            targetPercentage = 100
        )
        assertEquals(-1L, remainingMs) // Must return calculating (-1L)
    }

    // Test S: charger disconnect transition
    @Test
    fun testS_ChargerDisconnectTransition() {
        val engine = BatteryVelocityEngine(maxSamples = 5, minSampleIntervalMs = 1000L)
        val now = 1000000L
        engine.onChargingStateChanged(true)
        engine.addSample(BatterySample(50, now))
        engine.addSample(BatterySample(70, now + 1800000L))

        // State transition: Charger disconnected
        engine.onChargingStateChanged(false)
        assertNull("On charger disconnect, previous charging history must be cleared", engine.calculateCurrentVelocity())

        BatteryPredictionEngine.invalidateStateTransition(isCharging = false)
        val remainingMs = BatteryPredictionEngine.calculateRemainingTimeMs(
            percentage = 70,
            isCharging = false,
            currentNowVal = 0,
            isScreenOn = true,
            capacity = 4500,
            speed = 0f,
            targetPercentage = 100
        )
        assertEquals(-1L, remainingMs) // Must return calculating (-1L)
    }

    // Test T: NaN / Infinity protection
    @Test
    fun testT_NaNAndInfinityProtection() {
        assertNull(BatteryPredictionEngine.estimateTimeToFullMinutes(50, Float.NaN))
        assertNull(BatteryPredictionEngine.estimateTimeToFullMinutes(50, Float.POSITIVE_INFINITY))
        assertNull(BatteryPredictionEngine.estimateTimeToFullMinutes(50, Float.NEGATIVE_INFINITY))

        assertNull(BatteryPredictionEngine.estimateTimeToEmptyMinutes(50, Float.NaN))
        assertNull(BatteryPredictionEngine.estimateTimeToEmptyMinutes(50, Float.POSITIVE_INFINITY))
        assertNull(BatteryPredictionEngine.estimateTimeToEmptyMinutes(50, Float.NEGATIVE_INFINITY))
    }
}
