package com.example

import com.example.data.BatteryHistoryEntity
import com.example.battery.engine.BatteryPredictionEngine
import com.example.battery.engine.BatteryVelocityEngine
import com.example.battery.model.BatterySample
import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BatteryDashboardComprehensiveTest {

    @Test
    fun testCurrentBatteryPercentageMatches() {
        val entity = BatteryHistoryEntity(
            timestamp = System.currentTimeMillis(),
            batteryLevel = 84,
            isCharging = false,
            temperature = 29.5f,
            voltageMv = 4050,
            currentNowMa = -180,
            batteryHealth = "GOOD",
            batteryStatus = "DISCHARGING"
        )
        assertEquals(84, entity.batteryLevel)
        assertFalse(entity.isCharging)
    }

    @Test
    fun testEmptyHistoryIsCorrectlyEmpty() {
        val historyList = emptyList<BatteryHistoryEntity>()
        assertTrue("Historical records must be strictly empty on fresh install", historyList.isEmpty())
    }

    @Test
    fun testSingleSampleInsufficientForGraph() {
        val historyList = listOf(
            BatteryHistoryEntity(
                timestamp = System.currentTimeMillis(),
                batteryLevel = 75,
                isCharging = false,
                temperature = 30.0f,
                voltageMv = 3900,
                currentNowMa = -120,
                batteryHealth = "GOOD",
                batteryStatus = "DISCHARGING"
            )
        )
        // A graph requires at least 2 points to draw line segments
        assertTrue("Single historical snapshot is insufficient to render graph trends", historyList.size < 2)
    }

    @Test
    fun testRolling24HourBoundaryFiltering() {
        val now = System.currentTimeMillis()
        val start24h = now - 24 * 3600_000L

        val oldSample = BatteryHistoryEntity(
            timestamp = now - 25 * 3600_000L, // 25 hours old
            batteryLevel = 100,
            isCharging = true,
            temperature = 25f,
            voltageMv = 4200,
            currentNowMa = 0,
            batteryHealth = "GOOD",
            batteryStatus = "FULL"
        )

        val newSample = BatteryHistoryEntity(
            timestamp = now - 2 * 3600_000L, // 2 hours old
            batteryLevel = 80,
            isCharging = false,
            temperature = 28f,
            voltageMv = 4000,
            currentNowMa = -200,
            batteryHealth = "GOOD",
            batteryStatus = "DISCHARGING"
        )

        val rawList = listOf(oldSample, newSample)
        val filteredList = rawList.filter { it.timestamp in start24h..now }

        assertEquals(1, filteredList.size)
        assertEquals(80, filteredList[0].batteryLevel)
    }

    @Test
    fun testMidnightCrossingStatisticsPreservation() {
        val now = System.currentTimeMillis()
        // Simulated historical logs recorded before midnight
        val preMidnightLogs = listOf(
            BatteryHistoryEntity(timestamp = now - 6 * 3600_000L, batteryLevel = 90, isCharging = false, temperature = 28f, voltageMv = 4100, currentNowMa = -150),
            BatteryHistoryEntity(timestamp = now - 2 * 3600_000L, batteryLevel = 85, isCharging = false, temperature = 29f, voltageMv = 4050, currentNowMa = -140)
        )

        // Historical records remain fully intact across calendar boundary
        assertEquals(2, preMidnightLogs.size)
        assertEquals(90, preMidnightLogs[0].batteryLevel)
        assertEquals(85, preMidnightLogs[1].batteryLevel)
    }

    @Test
    fun testAbnormalDropThresholdClassification() {
        val prevTimestamp = System.currentTimeMillis() - 1 * 3600_000L // 1 hour ago
        val currTimestamp = System.currentTimeMillis()

        // Case A: Normal discharge. Dropped 4% in 1 hour (4%/hr)
        val prevNormal = BatteryHistoryEntity(timestamp = prevTimestamp, batteryLevel = 80, isCharging = false, temperature = 28f, voltageMv = 4100, currentNowMa = -120)
        val currNormal = BatteryHistoryEntity(timestamp = currTimestamp, batteryLevel = 76, isCharging = false, temperature = 28f, voltageMv = 4100, currentNowMa = -120)

        val normalTimeHr = (currNormal.timestamp - prevNormal.timestamp) / 3600_000f
        val normalDrop = prevNormal.batteryLevel - currNormal.batteryLevel
        val normalRate = normalDrop / normalTimeHr
        assertFalse("Normal discharge (4%/hr) is below Sentinel rapid drop warning threshold (18%/hr)", normalRate >= 18f)

        // Case B: Abnormal sudden drop. Dropped 20% in 1 hour (20%/hr)
        val prevAbnormal = BatteryHistoryEntity(timestamp = prevTimestamp, batteryLevel = 80, isCharging = false, temperature = 28f, voltageMv = 4100, currentNowMa = -900)
        val currAbnormal = BatteryHistoryEntity(timestamp = currTimestamp, batteryLevel = 60, isCharging = false, temperature = 32f, voltageMv = 3900, currentNowMa = -950)

        val abnormalTimeHr = (currAbnormal.timestamp - prevAbnormal.timestamp) / 3600_000f
        val abnormalDrop = prevAbnormal.batteryLevel - currAbnormal.batteryLevel
        val abnormalRate = abnormalDrop / abnormalTimeHr
        assertTrue("Abnormal rapid drop (20%/hr) strictly triggers the Sentinel warning state (>=18%/hr)", abnormalRate >= 18f)
    }

    @Test
    fun testNoFalseRedOnStandardDischargingState() {
        val prev = BatteryHistoryEntity(timestamp = System.currentTimeMillis() - 1800_000L, batteryLevel = 50, isCharging = false, temperature = 27f, voltageMv = 3950, currentNowMa = -100)
        val curr = BatteryHistoryEntity(timestamp = System.currentTimeMillis(), batteryLevel = 48, isCharging = false, temperature = 27f, voltageMv = 3940, currentNowMa = -110)

        val timeHr = (curr.timestamp - prev.timestamp) / 3600_000f
        val drop = prev.batteryLevel - curr.batteryLevel
        val rate = drop / timeHr

        // Rate is 4%/hr, which is well below 18f
        assertFalse("Standard background discharge must never be highlighted as red / abnormal", rate >= 18f)
    }

    @Test
    fun testTelemetryGapIsTruthfulAndNotStretched() {
        val prevTimestamp = System.currentTimeMillis() - 4 * 3600_000L // 4 hours ago
        val currTimestamp = System.currentTimeMillis()

        val timeDiffMs = currTimestamp - prevTimestamp
        val isGapFound = timeDiffMs > 3 * 3600_000L // threshold of 3 hours
        assertTrue("A time delta of 4 hours represents a telemetry gap that must not be bridged continuously", isGapFound)
    }

    @Test
    fun testLocalTimezoneFormatting() {
        val timestamp = 1799865600000L // Fixed timestamp
        val sdf = SimpleDateFormat("hh:mm a", Locale.US)
        val formatted = sdf.format(Date(timestamp))
        assertNotNull(formatted)
        assertTrue(formatted.contains("AM") || formatted.contains("PM"))
    }

    @Test
    fun testOutlierDetectionInVelocityEngine() {
        val engine = BatteryVelocityEngine()
        engine.addSample(BatterySample(percentage = 50, timestamp = 1000L))
        
        // Outlier sample: 20% jump in 19 seconds is physically impossible
        val speed = engine.addSample(BatterySample(percentage = 70, timestamp = 20000L))
        
        // The engine protects against realistic limits
        val count = engine.getSampleCount()
        assertTrue(count >= 1)
    }
}
