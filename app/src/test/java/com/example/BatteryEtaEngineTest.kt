package com.example

import com.example.battery.engine.BatteryCapacityEngine
import com.example.battery.engine.BatteryPredictionEngine
import com.example.battery.engine.BatteryVelocityEngine
import com.example.battery.engine.EtaConfidence
import com.example.battery.engine.EtaSource
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
            capacity = null,
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
            capacity = null,
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

    // Test U: Fallback without verified capacity returns Unavailable/Calculating (-1L)
    @Test
    fun testU_FallbackWithoutVerifiedCapacity_ReturnsCalculating() {
        val res = BatteryPredictionEngine.calculateAuthoritativeEta(
            percentage = 50,
            isCharging = true,
            currentNowVal = 1000, // 1000mA hardware current
            isScreenOn = true,
            capacity = null, // Capacity not verified / unavailable
            speed = 0f, // Velocity not yet established
            targetPercentage = 100
        )
        assertEquals(-1L, res.remainingTimeMs)
        assertEquals(EtaSource.UNAVAILABLE, res.source)
        assertFalse(res.isAvailable)
    }

    // Test V: Fallback with validated real capacity produces honest ETA
    @Test
    fun testV_FallbackWithValidatedCapacity_ProducesHonestEta() {
        val res = BatteryPredictionEngine.calculateAuthoritativeEta(
            percentage = 50,
            isCharging = true,
            currentNowVal = 2500, // 2500mA verified charging current
            isScreenOn = false,
            capacity = 5000, // 5000 mAh validated device capacity
            speed = 0f, // Velocity not yet established
            targetPercentage = 100
        )
        // 50% of 5000mAh = 2500mAh remaining. At 2500mA -> 1 hour = 3600_000 ms
        assertEquals(3600_000L, res.remainingTimeMs)
        assertEquals(EtaSource.HARDWARE_CURRENT_AND_VALIDATED_CAPACITY, res.source)
        assertEquals(EtaConfidence.ESTIMATING, res.confidence)
        assertTrue(res.isAvailable)
    }

    // Test W: Velocity-based ETA preferred over hardware-current fallback
    @Test
    fun testW_VelocityPreferredOverHardwareCurrent() {
        val res = BatteryPredictionEngine.calculateAuthoritativeEta(
            percentage = 50,
            isCharging = true,
            currentNowVal = 5000, // Hardware current might suggest 30 min
            isScreenOn = true,
            capacity = 5000,
            speed = 10.0f, // Measured rate: 10%/hr -> 5 hours = 18,000,000 ms
            targetPercentage = 100
        )
        // Must prioritize measured percentage velocity (Priority A)
        assertEquals(18_000_000L, res.remainingTimeMs)
        assertEquals(EtaSource.MEASURED_PERCENTAGE_VELOCITY, res.source)
        assertEquals(EtaConfidence.STABLE, res.confidence)
    }

    // Test X: Hardware current available but below minimum threshold (<150mA for charging)
    @Test
    fun testX_HardwareCurrentBelowMinimumThreshold() {
        val res = BatteryPredictionEngine.calculateAuthoritativeEta(
            percentage = 50,
            isCharging = true,
            currentNowVal = 80, // Low current, below 150mA threshold
            isScreenOn = true,
            capacity = 5000,
            speed = 0f,
            targetPercentage = 100
        )
        assertEquals(-1L, res.remainingTimeMs)
        assertEquals(EtaSource.UNAVAILABLE, res.source)
    }

    // Test Y: Plausible capacity range validation (e.g. 100mAh vs 5000mAh vs 50,000mAh)
    @Test
    fun testY_PlausibleCapacityRangeValidation() {
        assertFalse("100mAh is below smartphone threshold", BatteryCapacityEngine.isValidCapacity(100))
        assertFalse("50000mAh is above smartphone threshold", BatteryCapacityEngine.isValidCapacity(50000))
        assertFalse("Null is invalid", BatteryCapacityEngine.isValidCapacity(null))
        assertTrue("5000mAh is valid", BatteryCapacityEngine.isValidCapacity(5000))
        assertTrue("4500mAh is valid", BatteryCapacityEngine.isValidCapacity(4500))
    }

    // Test Z: Zero remaining charge time at target percentage
    @Test
    fun testZ_ZeroRemainingAtTargetPercentage() {
        val res80 = BatteryPredictionEngine.calculateAuthoritativeEta(
            percentage = 80,
            isCharging = true,
            currentNowVal = 2000,
            isScreenOn = true,
            capacity = 5000,
            speed = 20f,
            targetPercentage = 80
        )
        assertEquals(0L, res80.remainingTimeMs)

        val res100 = BatteryPredictionEngine.calculateAuthoritativeEta(
            percentage = 100,
            isCharging = true,
            currentNowVal = 500,
            isScreenOn = true,
            capacity = 5000,
            speed = 0f,
            targetPercentage = 100
        )
        assertEquals(0L, res100.remainingTimeMs)
    }

    // Test AA: Zero remaining discharge time at 0%
    @Test
    fun testAA_ZeroRemainingDischargeAtZeroPercent() {
        val res = BatteryPredictionEngine.calculateAuthoritativeEta(
            percentage = 0,
            isCharging = false,
            currentNowVal = -300,
            isScreenOn = true,
            capacity = 5000,
            speed = -5f,
            targetPercentage = 100
        )
        assertEquals(0L, res.remainingTimeMs)
        assertEquals(EtaConfidence.STABLE, res.confidence)
    }

    // Test AB: Target percentage boundary safety (e.g. Target > 100% or Target < 1%)
    @Test
    fun testAB_TargetPercentageBoundarySafety() {
        val invalidTargetOver = BatteryPredictionEngine.calculateAuthoritativeEta(
            percentage = 50,
            isCharging = true,
            currentNowVal = 2000,
            isScreenOn = true,
            capacity = 5000,
            speed = 20f,
            targetPercentage = 105
        )
        assertEquals(-1L, invalidTargetOver.remainingTimeMs)

        val invalidTargetUnder = BatteryPredictionEngine.calculateAuthoritativeEta(
            percentage = 50,
            isCharging = true,
            currentNowVal = 2000,
            isScreenOn = true,
            capacity = 5000,
            speed = 20f,
            targetPercentage = 0
        )
        assertEquals(-1L, invalidTargetUnder.remainingTimeMs)
    }

    // Test AC: Out-of-bounds percentage safety
    @Test
    fun testAC_OutOfBoundsPercentageSafety() {
        val neg = BatteryPredictionEngine.calculateAuthoritativeEta(
            percentage = -1,
            isCharging = true,
            currentNowVal = 2000,
            isScreenOn = true,
            capacity = 5000,
            speed = 20f
        )
        assertEquals(-1L, neg.remainingTimeMs)

        val over100 = BatteryPredictionEngine.calculateAuthoritativeEta(
            percentage = 101,
            isCharging = true,
            currentNowVal = 2000,
            isScreenOn = true,
            capacity = 5000,
            speed = 20f
        )
        assertEquals(-1L, over100.remainingTimeMs)
    }

    // Test AD: Discharge fallback with validated capacity
    @Test
    fun testAD_DischargeFallbackWithValidatedCapacity() {
        val res = BatteryPredictionEngine.calculateAuthoritativeEta(
            percentage = 50,
            isCharging = false,
            currentNowVal = -500, // 500mA drain
            isScreenOn = true,
            capacity = 5000, // 5000mAh validated
            speed = 0f
        )
        // 50% of 5000mAh = 2500mAh. At 500mA drain -> 5 hours = 18,000,000 ms
        assertEquals(18_000_000L, res.remainingTimeMs)
        assertEquals(EtaSource.HARDWARE_CURRENT_AND_VALIDATED_CAPACITY, res.source)
        assertEquals(EtaConfidence.ESTIMATING, res.confidence)
    }

    // Test AE: Discharge fallback without capacity returns calculating
    @Test
    fun testAE_DischargeFallbackWithoutCapacity_ReturnsCalculating() {
        val res = BatteryPredictionEngine.calculateAuthoritativeEta(
            percentage = 50,
            isCharging = false,
            currentNowVal = -500,
            isScreenOn = true,
            capacity = null,
            speed = 0f
        )
        assertEquals(-1L, res.remainingTimeMs)
        assertEquals(EtaSource.UNAVAILABLE, res.source)
    }

    // Test AF: Maximum bounds clamping (Charge max 8h, Discharge max 72h)
    @Test
    fun testAF_MaximumBoundsClamping() {
        // Very slow charge velocity: 0.1%/hr -> 50% / 0.1% = 500 hours -> Clamped to 8 hours
        val chargeClamped = BatteryPredictionEngine.calculateAuthoritativeEta(
            percentage = 50,
            isCharging = true,
            currentNowVal = 0,
            isScreenOn = false,
            capacity = null,
            speed = 0.1f
        )
        assertEquals(8 * 3600 * 1000L, chargeClamped.remainingTimeMs)

        // Very slow discharge velocity: 0.01%/hr -> 50% / 0.01% = 5000 hours -> Clamped to 72 hours
        val dischargeClamped = BatteryPredictionEngine.calculateAuthoritativeEta(
            percentage = 50,
            isCharging = false,
            currentNowVal = 0,
            isScreenOn = false,
            capacity = null,
            speed = -0.01f
        )
        assertEquals(72 * 3600 * 1000L, dischargeClamped.remainingTimeMs)
    }

    // Test AG: Linear regression slope accuracy over multiple samples
    @Test
    fun testAG_LinearRegressionAccuracy() {
        val engine = BatteryVelocityEngine(maxSamples = 5, minSampleIntervalMs = 10_000L)
        val t0 = 10000000L
        engine.addSample(BatterySample(50, t0))
        engine.addSample(BatterySample(52, t0 + 600_000L)) // 10 min: +2%
        engine.addSample(BatterySample(54, t0 + 1200_000L)) // 20 min: +4%
        val v = engine.calculateCurrentVelocity()
        assertNotNull(v)
        // Rate: 4% in 20 min (1/3 hr) = 12%/hr
        assertEquals(12.0f, v!!, 0.2f)
    }

    // Test AH: Rapid successive state toggling stability
    @Test
    fun testAH_RapidStateTogglingStability() {
        for (i in 0..10) {
            val isCharging = i % 2 == 0
            BatteryPredictionEngine.invalidateStateTransition(isCharging)
            val res = BatteryPredictionEngine.calculateAuthoritativeEta(
                percentage = 50,
                isCharging = isCharging,
                currentNowVal = 0,
                isScreenOn = true,
                capacity = 5000,
                speed = 0f
            )
            assertEquals(-1L, res.remainingTimeMs)
            assertEquals(EtaConfidence.INITIALIZING, res.confidence)
        }
    }
}
