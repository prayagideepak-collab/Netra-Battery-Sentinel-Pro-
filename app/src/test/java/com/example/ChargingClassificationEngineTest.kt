package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.battery.engine.ChargingClassificationEngine
import com.example.battery.model.ChargingClassificationResult
import com.example.battery.model.ChargingConfidence
import com.example.battery.model.ChargingState
import com.example.battery.model.ChargingTelemetryInput
import com.example.engines.ChargingEngine
import com.example.engines.charging.DeterministicChargingEngine
import com.example.engines.charging.EvidenceChargingState
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ChargingClassificationEngineTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        ChargingClassificationEngine.resetAllForTesting(context)
    }

    // A. Charger disconnected
    @Test
    fun testA_ChargerDisconnected() {
        val result = ChargingClassificationEngine.classify(
            ChargingTelemetryInput(
                isCharging = false,
                powerSource = "None",
                timestampMs = 1000L
            )
        )
        assertEquals(ChargingState.NOT_CHARGING, result.state)
        assertEquals("Discharging", result.displayName)
        assertNull(result.inputPowerW)
    }

    // B. Charger connected, insufficient samples
    @Test
    fun testB_ChargerConnected_InsufficientSamples() {
        val result = ChargingClassificationEngine.classify(
            ChargingTelemetryInput(
                isCharging = true,
                powerSource = "AC",
                sessionDurationSeconds = 5L,
                measuredVelocityPctPerHr = 0f,
                powerWatt = 0f,
                timestampMs = 1000L
            )
        )
        assertEquals(ChargingState.INITIALIZING, result.state)
        assertEquals(ChargingConfidence.INITIALIZING, result.confidence)
    }

    // C. Valid slow charging telemetry
    @Test
    fun testC_ValidSlowChargingTelemetry() {
        // Slow charger (e.g. low current 400mA at 5V -> 2W, rate 4%/h)
        val result = ChargingClassificationEngine.classify(
            ChargingTelemetryInput(
                isCharging = true,
                powerSource = "USB",
                currentNowMa = 400,
                voltageMv = 5000,
                powerWatt = 2.0f,
                measuredVelocityPctPerHr = 4.0f,
                sessionDurationSeconds = 30L,
                timestampMs = 1000L
            )
        )
        assertEquals(ChargingState.SLOW, result.state)
        assertEquals("Slow Charging", result.displayName)
    }

    // D. Valid normal charging telemetry
    @Test
    fun testD_ValidNormalChargingTelemetry() {
        // Standard normal charger (e.g. 1500mA at 5V -> 7.5W, rate 16%/h)
        val result = ChargingClassificationEngine.classify(
            ChargingTelemetryInput(
                isCharging = true,
                powerSource = "AC",
                currentNowMa = 1500,
                voltageMv = 5000,
                powerWatt = 7.5f,
                measuredVelocityPctPerHr = 16.0f,
                sessionDurationSeconds = 45L,
                timestampMs = 1000L
            )
        )
        assertEquals(ChargingState.NORMAL, result.state)
        assertEquals("Normal Charging", result.displayName)
    }

    // E. Valid fast charging telemetry
    @Test
    fun testE_ValidFastChargingTelemetry() {
        // Fast charger (e.g. 4000mA at 5V / 9V -> 25W, rate 35%/h)
        val result = ChargingClassificationEngine.classify(
            ChargingTelemetryInput(
                isCharging = true,
                powerSource = "AC",
                currentNowMa = 4000,
                voltageMv = 9000.coerceAtMost(5000), // bounded
                powerWatt = 25.0f,
                measuredVelocityPctPerHr = 35.0f,
                sessionDurationSeconds = 60L,
                timestampMs = 1000L
            )
        )
        assertEquals(ChargingState.FAST, result.state)
        assertEquals("Fast Charging", result.displayName)
    }

    // F. Near-full tapering
    @Test
    fun testF_NearFullTapering() {
        // Battery at 92% with natural current drop to 250mA and 1.5%/h slope
        val result = ChargingClassificationEngine.classify(
            ChargingTelemetryInput(
                isCharging = true,
                powerSource = "AC",
                currentNowMa = 250,
                voltageMv = 4350,
                powerWatt = 1.08f,
                batteryPercentage = 92,
                measuredVelocityPctPerHr = 1.5f,
                sessionDurationSeconds = 120L,
                timestampMs = 1000L
            )
        )
        assertEquals(ChargingState.MAINTENANCE, result.state)
        assertTrue(result.isNearFullTapering)
        assertEquals("Maintenance / Near-Full", result.displayName)
    }

    // G. High current + high phone consumption (Screen ON)
    @Test
    fun testG_HighCurrent_HighPhoneConsumption_NotFalselySlow() {
        // Charger delivers 12W (high input power), but screen is ON with heavy load so net gain is 3.5%/h
        val result = ChargingClassificationEngine.classify(
            ChargingTelemetryInput(
                isCharging = true,
                powerSource = "AC",
                currentNowMa = 2500,
                voltageMv = 5000,
                powerWatt = 12.5f,
                batteryPercentage = 45,
                measuredVelocityPctPerHr = 3.5f,
                isScreenOn = true,
                sessionDurationSeconds = 60L,
                timestampMs = 1000L
            )
        )
        assertTrue(result.isLoadLimited)
        // Must NOT falsely accuse the charger of being Slow
        assertNotEquals(ChargingState.SLOW, result.state)
        assertEquals(ChargingState.NORMAL, result.state)
    }

    // H. Thermal throttling distinction
    @Test
    fun testH_ThermalThrottlingDistinction() {
        // High temp 42.5°C throttling current down
        val result = ChargingClassificationEngine.classify(
            ChargingTelemetryInput(
                isCharging = true,
                powerSource = "AC",
                currentNowMa = 600,
                voltageMv = 4200,
                powerWatt = 2.5f,
                batteryPercentage = 50,
                measuredVelocityPctPerHr = 5.0f,
                temperatureCelsius = 42.5f,
                temperatureTrend = "RISING",
                sessionDurationSeconds = 60L,
                timestampMs = 1000L
            )
        )
        assertTrue(result.isThermalLimited)
        assertTrue(result.explanation.contains("Thermal"))
    }

    // I. Invalid current
    @Test
    fun testI_InvalidCurrent() {
        val result = ChargingClassificationEngine.classify(
            ChargingTelemetryInput(
                isCharging = true,
                powerSource = "AC",
                currentNowMa = -1, // invalid
                voltageMv = 4000,
                powerWatt = null,
                measuredVelocityPctPerHr = null,
                sessionDurationSeconds = 30L,
                timestampMs = 1000L
            )
        )
        assertEquals(ChargingState.INSUFFICIENT_DATA, result.state)
        assertNull(result.currentMa)
    }

    // J. Invalid voltage
    @Test
    fun testJ_InvalidVoltage() {
        val result = ChargingClassificationEngine.classify(
            ChargingTelemetryInput(
                isCharging = true,
                powerSource = "AC",
                currentNowMa = null,
                voltageMv = -1, // invalid
                powerWatt = null,
                measuredVelocityPctPerHr = null,
                sessionDurationSeconds = 30L,
                timestampMs = 1000L
            )
        )
        assertEquals(ChargingState.INSUFFICIENT_DATA, result.state)
        assertNull(result.voltageV)
    }

    // K. Invalid power
    @Test
    fun testK_InvalidPower() {
        val result = ChargingClassificationEngine.classify(
            ChargingTelemetryInput(
                isCharging = true,
                powerSource = "AC",
                currentNowMa = null,
                voltageMv = null,
                powerWatt = -5.0f, // invalid negative power
                measuredVelocityPctPerHr = null,
                sessionDurationSeconds = 30L,
                timestampMs = 1000L
            )
        )
        assertEquals(ChargingState.INSUFFICIENT_DATA, result.state)
        assertNull(result.inputPowerW)
    }

    // L. NaN Protection
    @Test
    fun testL_NaNProtection() {
        val result = ChargingClassificationEngine.classify(
            ChargingTelemetryInput(
                isCharging = true,
                powerSource = "AC",
                powerWatt = Float.NaN,
                measuredVelocityPctPerHr = Float.NaN,
                temperatureCelsius = Float.NaN,
                sessionDurationSeconds = 30L,
                timestampMs = 1000L
            )
        )
        assertEquals(ChargingState.INSUFFICIENT_DATA, result.state)
        assertNull(result.inputPowerW)
        assertNull(result.netBatteryGainPctPerHr)
    }

    // M. Infinity Protection
    @Test
    fun testM_InfinityProtection() {
        val result = ChargingClassificationEngine.classify(
            ChargingTelemetryInput(
                isCharging = true,
                powerSource = "AC",
                powerWatt = Float.POSITIVE_INFINITY,
                measuredVelocityPctPerHr = Float.POSITIVE_INFINITY,
                temperatureCelsius = Float.POSITIVE_INFINITY,
                sessionDurationSeconds = 30L,
                timestampMs = 1000L
            )
        )
        assertEquals(ChargingState.INSUFFICIENT_DATA, result.state)
        assertNull(result.inputPowerW)
        assertNull(result.netBatteryGainPctPerHr)
    }

    // N. Duplicate timestamps
    @Test
    fun testN_DuplicateTimestamps() {
        val r1 = ChargingClassificationEngine.classify(
            ChargingTelemetryInput(
                isCharging = true,
                powerSource = "AC",
                currentNowMa = 2000,
                voltageMv = 5000,
                powerWatt = 10.0f,
                measuredVelocityPctPerHr = 20.0f,
                sessionDurationSeconds = 30L,
                timestampMs = 5000L
            )
        )
        val r2 = ChargingClassificationEngine.classify(
            ChargingTelemetryInput(
                isCharging = true,
                powerSource = "AC",
                currentNowMa = 2000,
                voltageMv = 5000,
                powerWatt = 10.0f,
                measuredVelocityPctPerHr = 20.0f,
                sessionDurationSeconds = 30L,
                timestampMs = 5000L // duplicate timestamp
            )
        )
        assertEquals(r1.state, r2.state)
    }

    // O. Backward timestamps
    @Test
    fun testO_BackwardTimestamps() {
        ChargingClassificationEngine.classify(
            ChargingTelemetryInput(
                isCharging = true,
                powerSource = "AC",
                currentNowMa = 2000,
                voltageMv = 5000,
                powerWatt = 10.0f,
                measuredVelocityPctPerHr = 20.0f,
                sessionDurationSeconds = 30L,
                timestampMs = 6000L
            )
        )
        val backwardResult = ChargingClassificationEngine.classify(
            ChargingTelemetryInput(
                isCharging = true,
                powerSource = "AC",
                currentNowMa = 2000,
                voltageMv = 5000,
                powerWatt = 10.0f,
                measuredVelocityPctPerHr = 20.0f,
                sessionDurationSeconds = 30L,
                timestampMs = 4000L // backward timestamp
            )
        )
        assertTrue(backwardResult.explanation.contains("backward timestamp"))
    }

    // P. Charger disconnect transition
    @Test
    fun testP_ChargerDisconnectTransition() {
        ChargingClassificationEngine.classify(
            ChargingTelemetryInput(
                isCharging = true,
                powerSource = "AC",
                currentNowMa = 3000,
                voltageMv = 5000,
                powerWatt = 15.0f,
                sessionDurationSeconds = 60L,
                timestampMs = 1000L
            )
        )
        val disconnectResult = ChargingClassificationEngine.classify(
            ChargingTelemetryInput(
                isCharging = false,
                powerSource = "None",
                timestampMs = 2000L
            )
        )
        assertEquals(ChargingState.NOT_CHARGING, disconnectResult.state)
        assertEquals("Disconnected", disconnectResult.powerSource)
    }

    // Q. Charger reconnect
    @Test
    fun testQ_ChargerReconnect() {
        ChargingClassificationEngine.onChargingStateChanged(false)
        ChargingClassificationEngine.onChargingStateChanged(true, "AC")
        val reconnectResult = ChargingClassificationEngine.classify(
            ChargingTelemetryInput(
                isCharging = true,
                powerSource = "AC",
                sessionDurationSeconds = 1L,
                timestampMs = 3000L
            )
        )
        assertEquals(ChargingState.INITIALIZING, reconnectResult.state)
    }

    // R. Charger source change
    @Test
    fun testR_ChargerSourceChange() {
        ChargingClassificationEngine.classify(
            ChargingTelemetryInput(
                isCharging = true,
                powerSource = "AC",
                currentNowMa = 3000,
                voltageMv = 5000,
                powerWatt = 15.0f,
                sessionDurationSeconds = 60L,
                timestampMs = 1000L
            )
        )
        val sourceChangeResult = ChargingClassificationEngine.classify(
            ChargingTelemetryInput(
                isCharging = true,
                powerSource = "USB", // Switched from AC to USB
                sessionDurationSeconds = 1L,
                timestampMs = 2000L
            )
        )
        assertEquals(ChargingState.INITIALIZING, sourceChangeResult.state)
        assertEquals("USB", sourceChangeResult.powerSource)
    }

    // S. Rapid telemetry fluctuation / Hysteresis
    @Test
    fun testS_RapidTelemetryFluctuation_Hysteresis() {
        // Establish Normal state with 2 consecutive readings
        ChargingClassificationEngine.classify(
            ChargingTelemetryInput(
                isCharging = true,
                powerSource = "AC",
                currentNowMa = 1500,
                voltageMv = 5000,
                powerWatt = 7.5f,
                measuredVelocityPctPerHr = 15.0f,
                sessionDurationSeconds = 30L,
                timestampMs = 1000L
            )
        )
        val rNormal = ChargingClassificationEngine.classify(
            ChargingTelemetryInput(
                isCharging = true,
                powerSource = "AC",
                currentNowMa = 1500,
                voltageMv = 5000,
                powerWatt = 7.5f,
                measuredVelocityPctPerHr = 15.0f,
                sessionDurationSeconds = 31L,
                timestampMs = 2000L
            )
        )
        assertEquals(ChargingState.NORMAL, rNormal.state)

        // Single brief spike of Fast telemetry
        val rSpike = ChargingClassificationEngine.classify(
            ChargingTelemetryInput(
                isCharging = true,
                powerSource = "AC",
                currentNowMa = 4000,
                voltageMv = 5000,
                powerWatt = 20.0f,
                measuredVelocityPctPerHr = 30.0f,
                sessionDurationSeconds = 32L,
                timestampMs = 3000L
            )
        )
        // Hysteresis dampens single noisy spike, keeping state stable
        assertEquals(ChargingState.NORMAL, rSpike.state)
    }

    // T. No historical baseline
    @Test
    fun testT_NoHistoricalBaseline_UsesHardwareSignals() {
        assertNull(ChargingClassificationEngine.getLearnedBaseline("AC"))
        val result = ChargingClassificationEngine.classify(
            ChargingTelemetryInput(
                isCharging = true,
                powerSource = "AC",
                currentNowMa = 3500,
                voltageMv = 5000,
                powerWatt = 17.5f,
                sessionDurationSeconds = 40L,
                timestampMs = 1000L
            )
        )
        // Correctly derives FAST from physical input without assuming 15%/hr fallback
        assertEquals(ChargingState.FAST, result.state)
        assertNull(result.deviceLearnedBaselinePctPerHr)
    }

    // U. Historical charging baseline
    @Test
    fun testU_HistoricalChargingBaseline() {
        // Record 3 valid completed charging sessions on AC
        ChargingClassificationEngine.recordSessionCompletion(context, "AC", 28.0f)
        ChargingClassificationEngine.recordSessionCompletion(context, "AC", 30.0f)
        ChargingClassificationEngine.recordSessionCompletion(context, "AC", 32.0f)

        val baseline = ChargingClassificationEngine.getLearnedBaseline("AC")
        assertNotNull(baseline)
        assertEquals(30.0f, baseline!!, 0.1f)

        // Session evaluating against learned baseline of 30%/h:
        // 12%/h is < normalLowerBound (30 * 0.65 = 19.5%/h) -> SLOW
        val result = ChargingClassificationEngine.classify(
            ChargingTelemetryInput(
                isCharging = true,
                powerSource = "AC",
                currentNowMa = 600,
                voltageMv = 5000,
                powerWatt = 3.0f,
                measuredVelocityPctPerHr = 12.0f,
                sessionDurationSeconds = 45L,
                timestampMs = 1000L
            )
        )
        assertEquals(ChargingState.SLOW, result.state)
    }

    // V. Discharge data contamination protection
    @Test
    fun testV_DischargeDataContaminationProtection() {
        val initialBaseline = ChargingClassificationEngine.getLearnedBaseline("USB")
        assertNull(initialBaseline)

        // Attempt to pass negative/discharging rate
        ChargingClassificationEngine.recordSessionCompletion(context, "USB", -12.5f)
        ChargingClassificationEngine.recordSessionCompletion(context, "USB", 0.0f)
        ChargingClassificationEngine.recordSessionCompletion(context, "USB", 1.5f) // below min 3%

        // Baseline must remain clean and unaffected
        assertNull(ChargingClassificationEngine.getLearnedBaseline("USB"))
    }

    // W. Multiple charging sessions / Source Isolation
    @Test
    fun testW_MultipleSessions_SourceIsolation() {
        ChargingClassificationEngine.recordSessionCompletion(context, "AC", 40.0f)
        ChargingClassificationEngine.recordSessionCompletion(context, "USB", 10.0f)

        assertEquals(40.0f, ChargingClassificationEngine.getLearnedBaseline("AC")!!, 0.1f)
        assertEquals(10.0f, ChargingClassificationEngine.getLearnedBaseline("USB")!!, 0.1f)
    }

    // X. UI Authoritative result equality
    @Test
    fun testX_UiAuthoritativeResultEquality() {
        val result = ChargingClassificationEngine.classify(
            ChargingTelemetryInput(
                isCharging = true,
                powerSource = "AC",
                currentNowMa = 3000,
                voltageMv = 5000,
                powerWatt = 15.0f,
                measuredVelocityPctPerHr = 28.0f,
                sessionDurationSeconds = 50L,
                timestampMs = 1000L
            )
        )
        val uiName = ChargingEngine.classifyChargingType(
            isCharging = true,
            powerWatt = 15.0f,
            currentNowMa = 3000,
            voltageMv = 5000,
            sessionDurationSeconds = 50L,
            measuredRatePctPerHr = 28.0f,
            powerSource = "AC"
        )
        assertEquals(result.displayName, uiName)
    }

    // Y. Duplicate calculation audit
    @Test
    fun testY_DeterministicChargingEngineDelegatesDirectly() {
        val assessment = DeterministicChargingEngine.evaluate(
            isCharging = true,
            sessionDurationSeconds = 50L,
            measuredRatePctPerHr = 28.0f,
            powerWatt = 15.0f,
            currentMa = 3000,
            voltageMv = 5000,
            temperatureCelsius = 30f,
            temperatureTrend = "STABLE",
            isScreenOn = false,
            powerSource = "AC"
        )
        assertEquals(EvidenceChargingState.FAST, assessment.state)
        assertEquals("Fast Charging", assessment.state.displayName)
    }

    // Z. Hardcoded baseline audit
    @Test
    fun testZ_ZeroHardcoded15PercentFallback() {
        ChargingClassificationEngine.clearSession()
        // Ensure no baseline exists by default
        val baseline = ChargingClassificationEngine.getLearnedBaseline("AC")
        assertNull(baseline)
    }

    // AA. Charging -> Discharging transition
    @Test
    fun testAA_ChargingToDischargingTransition() {
        // Establish Fast charging
        ChargingClassificationEngine.classify(
            ChargingTelemetryInput(
                isCharging = true,
                powerSource = "AC",
                currentNowMa = 3000,
                voltageMv = 5000,
                powerWatt = 15.0f,
                sessionDurationSeconds = 60L,
                timestampMs = 1000L
            )
        )
        // Transition to discharging
        val result = ChargingClassificationEngine.classify(
            ChargingTelemetryInput(
                isCharging = false,
                powerSource = "None",
                timestampMs = 2000L
            )
        )
        assertEquals(ChargingState.NOT_CHARGING, result.state)
        assertEquals("Discharging", result.displayName)
        assertNull(result.inputPowerW)
    }

    // AB. Discharging -> Charging transition
    @Test
    fun testAB_DischargingToChargingTransition() {
        // Start in discharging
        ChargingClassificationEngine.classify(
            ChargingTelemetryInput(
                isCharging = false,
                powerSource = "None",
                timestampMs = 1000L
            )
        )
        // Charger plugged in with 0 duration and no velocity
        val result = ChargingClassificationEngine.classify(
            ChargingTelemetryInput(
                isCharging = true,
                powerSource = "AC",
                sessionDurationSeconds = 0L,
                timestampMs = 2000L
            )
        )
        assertEquals(ChargingState.INITIALIZING, result.state)
        assertEquals(ChargingConfidence.INITIALIZING, result.confidence)
    }

    // AC. Wireless -> Wired transition
    @Test
    fun testAC_WirelessToWiredTransition() {
        // Record separate baselines
        ChargingClassificationEngine.recordSessionCompletion(context, "Wireless", 15.0f)
        ChargingClassificationEngine.recordSessionCompletion(context, "AC", 45.0f)

        // Charging on wireless
        val rWireless = ChargingClassificationEngine.classify(
            ChargingTelemetryInput(
                isCharging = true,
                powerSource = "Wireless",
                currentNowMa = 1000,
                voltageMv = 5000,
                powerWatt = 5.0f,
                measuredVelocityPctPerHr = 15.0f,
                sessionDurationSeconds = 40L,
                timestampMs = 1000L
            )
        )
        assertEquals(ChargingState.NORMAL, rWireless.state)
        assertEquals(15.0f, rWireless.deviceLearnedBaselinePctPerHr!!, 0.1f)

        // Switch to AC wired
        val rWired = ChargingClassificationEngine.classify(
            ChargingTelemetryInput(
                isCharging = true,
                powerSource = "AC",
                currentNowMa = 3500,
                voltageMv = 5000,
                powerWatt = 17.5f,
                measuredVelocityPctPerHr = 45.0f,
                sessionDurationSeconds = 40L,
                timestampMs = 2000L
            )
        )
        assertEquals("AC", rWired.powerSource)
        assertEquals(45.0f, rWired.deviceLearnedBaselinePctPerHr!!, 0.1f)
    }

    // AD. Wired -> Wireless transition
    @Test
    fun testAD_WiredToWirelessTransition() {
        ChargingClassificationEngine.recordSessionCompletion(context, "Wireless", 14.0f)

        val rWired = ChargingClassificationEngine.classify(
            ChargingTelemetryInput(
                isCharging = true,
                powerSource = "USB",
                currentNowMa = 500,
                voltageMv = 5000,
                powerWatt = 2.5f,
                sessionDurationSeconds = 30L,
                timestampMs = 1000L
            )
        )
        assertEquals("USB", rWired.powerSource)

        val rWireless = ChargingClassificationEngine.classify(
            ChargingTelemetryInput(
                isCharging = true,
                powerSource = "Wireless",
                sessionDurationSeconds = 2L,
                timestampMs = 2000L
            )
        )
        assertEquals("Wireless", rWireless.powerSource)
        assertEquals(ChargingState.INITIALIZING, rWireless.state)
    }

    // AE. Application restart during charging
    @Test
    fun testAE_ApplicationRestartDuringCharging() {
        // Record baseline before restart
        ChargingClassificationEngine.recordSessionCompletion(context, "AC", 20.0f)

        // Simulate app restart: clear in-memory state and reload from prefs
        ChargingClassificationEngine.clearSession()
        ChargingClassificationEngine.init(context)

        assertEquals(20.0f, ChargingClassificationEngine.getLearnedBaseline("AC")!!, 0.1f)
        val result = ChargingClassificationEngine.classify(
            ChargingTelemetryInput(
                isCharging = true,
                powerSource = "AC",
                currentNowMa = 1500,
                voltageMv = 5000,
                powerWatt = 7.5f,
                measuredVelocityPctPerHr = 20.0f,
                sessionDurationSeconds = 40L,
                timestampMs = 1000L
            )
        )
        assertEquals(ChargingState.NORMAL, result.state)
        assertEquals(20.0f, result.deviceLearnedBaselinePctPerHr!!, 0.1f)
    }

    // AF. Rapid source switching
    @Test
    fun testAF_RapidSourceSwitching() {
        val sources = listOf("AC", "USB", "AC", "Wireless", "AC")
        var time = 1000L
        for (src in sources) {
            val r = ChargingClassificationEngine.classify(
                ChargingTelemetryInput(
                    isCharging = true,
                    powerSource = src,
                    sessionDurationSeconds = 2L,
                    timestampMs = time
                )
            )
            assertEquals(src, r.powerSource)
            assertEquals(ChargingState.INITIALIZING, r.state)
            time += 500L
        }
    }

    // AG. Thermal recovery
    @Test
    fun testAG_ThermalRecovery() {
        // High temp throttling (43°C)
        val rHot = ChargingClassificationEngine.classify(
            ChargingTelemetryInput(
                isCharging = true,
                powerSource = "AC",
                currentNowMa = 600,
                voltageMv = 4000,
                powerWatt = 2.4f,
                temperatureCelsius = 43.0f,
                sessionDurationSeconds = 40L,
                timestampMs = 1000L
            )
        )
        assertTrue(rHot.isThermalLimited)
        assertEquals(ChargingState.SLOW, rHot.state)

        // Cools down to 32°C and ramps back up to 15W
        ChargingClassificationEngine.classify(
            ChargingTelemetryInput(
                isCharging = true,
                powerSource = "AC",
                currentNowMa = 3000,
                voltageMv = 5000,
                powerWatt = 15.0f,
                temperatureCelsius = 32.0f,
                sessionDurationSeconds = 45L,
                timestampMs = 2000L
            )
        )
        val rCooled = ChargingClassificationEngine.classify(
            ChargingTelemetryInput(
                isCharging = true,
                powerSource = "AC",
                currentNowMa = 3000,
                voltageMv = 5000,
                powerWatt = 15.0f,
                temperatureCelsius = 32.0f,
                sessionDurationSeconds = 50L,
                timestampMs = 3000L
            )
        )
        assertFalse(rCooled.isThermalLimited)
        assertEquals(ChargingState.FAST, rCooled.state)
    }

    // AH. Near-full tapering recovery
    @Test
    fun testAH_NearFullTaperingRecovery() {
        // At 95% -> Maintenance
        val rNearFull = ChargingClassificationEngine.classify(
            ChargingTelemetryInput(
                isCharging = true,
                powerSource = "AC",
                currentNowMa = 200,
                voltageMv = 4350,
                batteryPercentage = 95,
                measuredVelocityPctPerHr = 0.5f,
                sessionDurationSeconds = 60L,
                timestampMs = 1000L
            )
        )
        assertEquals(ChargingState.MAINTENANCE, rNearFull.state)
        assertTrue(rNearFull.isNearFullTapering)

        // New session at 30% battery -> Fast
        ChargingClassificationEngine.onChargingStateChanged(false)
        ChargingClassificationEngine.onChargingStateChanged(true, "AC")

        ChargingClassificationEngine.classify(
            ChargingTelemetryInput(
                isCharging = true,
                powerSource = "AC",
                currentNowMa = 3500,
                voltageMv = 5000,
                powerWatt = 17.5f,
                batteryPercentage = 30,
                measuredVelocityPctPerHr = 30.0f,
                sessionDurationSeconds = 30L,
                timestampMs = 2000L
            )
        )
        val rLowSoC = ChargingClassificationEngine.classify(
            ChargingTelemetryInput(
                isCharging = true,
                powerSource = "AC",
                currentNowMa = 3500,
                voltageMv = 5000,
                powerWatt = 17.5f,
                batteryPercentage = 30,
                measuredVelocityPctPerHr = 30.0f,
                sessionDurationSeconds = 35L,
                timestampMs = 3000L
            )
        )
        assertFalse(rLowSoC.isNearFullTapering)
        assertEquals(ChargingState.FAST, rLowSoC.state)
    }

    // AI. Invalid timestamp sequence
    @Test
    fun testAI_InvalidTimestampSequence() {
        val t0 = 10000L
        ChargingClassificationEngine.classify(
            ChargingTelemetryInput(
                isCharging = true,
                powerSource = "AC",
                currentNowMa = 2000,
                voltageMv = 5000,
                powerWatt = 10.0f,
                sessionDurationSeconds = 30L,
                timestampMs = t0
            )
        )
        // Send erratic timestamp sequence (t0 - 1000, t0, t0 + 500)
        val rBackward = ChargingClassificationEngine.classify(
            ChargingTelemetryInput(
                isCharging = true,
                powerSource = "AC",
                timestampMs = t0 - 1000L
            )
        )
        assertTrue(rBackward.explanation.contains("backward timestamp"))

        val rDuplicate = ChargingClassificationEngine.classify(
            ChargingTelemetryInput(
                isCharging = true,
                powerSource = "AC",
                timestampMs = t0
            )
        )
        assertTrue(rDuplicate.explanation.contains("Duplicate timestamp"))

        val rValid = ChargingClassificationEngine.classify(
            ChargingTelemetryInput(
                isCharging = true,
                powerSource = "AC",
                currentNowMa = 2000,
                voltageMv = 5000,
                powerWatt = 10.0f,
                sessionDurationSeconds = 35L,
                timestampMs = t0 + 500L
            )
        )
        assertEquals(ChargingState.NORMAL, rValid.state)
    }

    // AJ. Baseline persistence / reload
    @Test
    fun testAJ_BaselinePersistenceAndReload() {
        ChargingClassificationEngine.recordSessionCompletion(context, "AC", 25.0f)
        ChargingClassificationEngine.recordSessionCompletion(context, "AC", 35.0f)

        // Clear in-memory and reload
        ChargingClassificationEngine.resetAllForTesting(null) // do not wipe context
        assertNull(ChargingClassificationEngine.getLearnedBaseline("AC"))

        ChargingClassificationEngine.init(context)
        val loaded = ChargingClassificationEngine.getLearnedBaseline("AC")
        assertNotNull(loaded)
        assertEquals(30.0f, loaded!!, 0.1f)
    }

    // AK. Baseline corruption handling
    @Test
    fun testAK_BaselineCorruptionHandling() {
        // Manually write corrupt data into shared prefs
        val prefs = context.getSharedPreferences("netra_charging_learned_baselines", Context.MODE_PRIVATE)
        prefs.edit().putString("baseline_AC", "corrupted,NaN,-50.0,999.0,30.0,40.0").commit()

        ChargingClassificationEngine.resetAllForTesting(null)
        ChargingClassificationEngine.init(context)

        // Only valid rates (3.0% to 120.0%) should be parsed: 30.0 and 40.0 -> average = 35.0%
        val baseline = ChargingClassificationEngine.getLearnedBaseline("AC")
        assertNotNull(baseline)
        assertEquals(35.0f, baseline!!, 0.1f)
    }

    // AL. Extreme but valid telemetry
    @Test
    fun testAL_ExtremeButValidTelemetry() {
        // Extreme fast charger (65W, 6500mA at 10V equivalent, 80%/h)
        val rUltraFast = ChargingClassificationEngine.classify(
            ChargingTelemetryInput(
                isCharging = true,
                powerSource = "AC",
                currentNowMa = 6500,
                voltageMv = 5000,
                powerWatt = 65.0f,
                measuredVelocityPctPerHr = 80.0f,
                sessionDurationSeconds = 40L,
                timestampMs = 1000L
            )
        )
        assertEquals(ChargingState.FAST, rUltraFast.state)
        assertEquals(65.0f, rUltraFast.inputPowerW!!, 0.1f)

        // Extreme slow trickle (0.5W, 100mA at 5V, 0.8%/h)
        val rTrickle = ChargingClassificationEngine.classify(
            ChargingTelemetryInput(
                isCharging = true,
                powerSource = "USB",
                currentNowMa = 100,
                voltageMv = 5000,
                powerWatt = 0.5f,
                measuredVelocityPctPerHr = 0.8f,
                sessionDurationSeconds = 40L,
                timestampMs = 2000L
            )
        )
        assertEquals(ChargingState.SLOW, rTrickle.state)
    }

    // AM. UI / Notification / Engine state consistency
    @Test
    fun testAM_UiNotificationEngineStateConsistency() {
        val input = ChargingTelemetryInput(
            isCharging = true,
            powerSource = "AC",
            currentNowMa = 3000,
            voltageMv = 5000,
            powerWatt = 15.0f,
            measuredVelocityPctPerHr = 28.0f,
            sessionDurationSeconds = 50L,
            timestampMs = 1000L
        )
        val engineResult = ChargingClassificationEngine.classify(input)
        val legacyUiState = ChargingEngine.classifyChargingType(
            isCharging = true,
            powerWatt = 15.0f,
            currentNowMa = 3000,
            voltageMv = 5000,
            sessionDurationSeconds = 50L,
            measuredRatePctPerHr = 28.0f,
            powerSource = "AC"
        )
        val deterministicUiState = DeterministicChargingEngine.evaluate(
            isCharging = true,
            sessionDurationSeconds = 50L,
            measuredRatePctPerHr = 28.0f,
            powerWatt = 15.0f,
            currentMa = 3000,
            voltageMv = 5000,
            temperatureCelsius = 25f,
            temperatureTrend = "STABLE",
            isScreenOn = false,
            powerSource = "AC"
        )
        assertEquals(engineResult.displayName, legacyUiState)
        assertEquals(engineResult.displayName, deterministicUiState.state.displayName)
    }
}
