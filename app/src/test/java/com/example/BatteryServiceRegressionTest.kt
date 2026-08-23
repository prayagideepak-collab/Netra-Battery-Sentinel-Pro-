package com.example

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.battery.engine.BatteryPredictionEngine
import com.example.battery.engine.BatteryVelocityEngine
import com.example.battery.model.BatterySample
import com.example.battery.model.DataTrustLevel
import com.example.battery.widget.WidgetUpdatePolicy
import com.example.battery.widget.WidgetUpdateReason
import com.example.service.BatteryService
import com.example.widget.NetraSmartWidget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowApplication

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BatteryServiceRegressionTest {

    @Test
    fun `verify service emits widget update intent on battery change`() {
        val application = ApplicationProvider.getApplicationContext<android.app.Application>()
        val controller = Robolectric.buildService(BatteryService::class.java)
        val service = controller.create().get()

        // Simulate a battery change event
        val intent = Intent(Intent.ACTION_BATTERY_CHANGED).apply {
            putExtra("level", 50)
            putExtra("plugged", 1) // AC
            putExtra("temperature", 300)
            putExtra("voltage", 3700)
            putExtra("current", 500)
        }
        service.batteryReceiver.onReceive(service, intent)

        // Verify broadcast
        val shadowApplication = Shadows.shadowOf(application) as ShadowApplication
        val broadcastIntents = shadowApplication.broadcastIntents
        println("All broadcast intents actions: ${broadcastIntents.map { it.action }}")
        
        assertNotNull("Broadcast intents should not be null", broadcastIntents)
        
        val updateWidgetIntent = broadcastIntents.find { it.action == NetraSmartWidget.ACTION_UPDATE_WIDGETS }
        assertNotNull("Should find update widget intent. All actions found: ${broadcastIntents.map { it.action }}", updateWidgetIntent)
    }

    @Test
    fun `verify velocity engine calculates correct rate of change`() {
        val engine = BatteryVelocityEngine(maxSamples = 5, minSampleIntervalMs = 1000L)
        val now = System.currentTimeMillis()

        // Sample 1: 50% at t=0
        val v1 = engine.addSample(BatterySample(50, now))
        assertNull("First sample should have no velocity", v1)

        // Sample 2: 60% after 1 hour (3600000 ms)
        val v2 = engine.addSample(BatterySample(60, now + 3600000L))
        assertNotNull("Second sample should yield velocity", v2)
        assertEquals("Velocity should be +10%/hr", 10.0f, v2!!, 0.1f)
    }

    @Test
    fun `verify velocity engine rejects short intervals`() {
        val engine = BatteryVelocityEngine(maxSamples = 5, minSampleIntervalMs = 10000L)
        val now = System.currentTimeMillis()

        engine.addSample(BatterySample(50, now))
        // Too fast (100 ms after first)
        val vTooFast = engine.addSample(BatterySample(51, now + 100L))
        assertNull("Should reject samples under min interval threshold", vTooFast)
    }

    @Test
    fun `verify prediction engine estimates correct time to full and empty`() {
        // Charging at +20%/hr from 60% -> remaining 40% -> 2 hours (120 minutes)
        val timeToFull = BatteryPredictionEngine.estimateTimeToFullMinutes(60, 20.0f)
        assertEquals(120L, timeToFull)

        // Discharging at -10%/hr from 50% -> remaining 50% -> 5 hours (300 minutes)
        val timeToEmpty = BatteryPredictionEngine.estimateTimeToEmptyMinutes(50, -10.0f)
        assertEquals(300L, timeToEmpty)

        // Invalid velocity -> null
        assertNull(BatteryPredictionEngine.estimateTimeToFullMinutes(50, null))
        assertNull(BatteryPredictionEngine.estimateTimeToEmptyMinutes(50, 0f))
    }

    @Test
    fun `verify widget update policy respects rate limiting and power events`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Force update should always pass
        assertTrue(WidgetUpdatePolicy.shouldUpdate(context, WidgetUpdateReason.PERIODIC, force = true))

        // Power connected should immediately pass
        assertTrue(WidgetUpdatePolicy.shouldUpdate(context, WidgetUpdateReason.POWER_CONNECTED))

        // Power disconnected should immediately pass
        assertTrue(WidgetUpdatePolicy.shouldUpdate(context, WidgetUpdateReason.POWER_DISCONNECTED))

        // Record update and test periodic rate limiting
        WidgetUpdatePolicy.recordUpdate(context)
        assertFalse(WidgetUpdatePolicy.shouldUpdate(context, WidgetUpdateReason.PERIODIC, force = false))
    }

    @Test
    fun `verify data trust level provenance enum`() {
        assertEquals("SYSTEM_REPORTED", DataTrustLevel.SYSTEM_REPORTED.name)
        assertEquals("CALCULATED", DataTrustLevel.CALCULATED.name)
        assertEquals("ESTIMATED", DataTrustLevel.ESTIMATED.name)
    }
}
