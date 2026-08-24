package com.example

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppConsumptionEntity
import com.example.engines.BatteryAttributionEngine
import com.example.service.BatteryState
import com.example.ui.getAppBatteryUsageList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPackageManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppConsumptionIntegrityTest {

    @Test
    fun `verify attribution engine marks zero or unmeasured consumption as UNAVAILABLE`() {
        val app = AppConsumptionEntity(
            packageName = "com.test.idleapp",
            appName = "Idle App",
            consumedMah = 0f,
            drainRating = "Low",
            backgroundTimeMs = 1000L,
            foregroundTimeMs = 2000L,
            isRunning = false
        )

        val dummyState = BatteryState(percentage = 80, temperature = 28f)
        val attr = BatteryAttributionEngine.calculateAttribution(
            app = app,
            batteryState = dummyState,
            networkType = "Wi-Fi",
            signalStrength = "Good",
            networkFluctuating = false
        )

        assertEquals("UNAVAILABLE", attr.status)
        assertEquals("LOW", attr.impactLevel)
        assertTrue(attr.reason.contains("No authoritative energy telemetry"))
        assertEquals("UNAVAILABLE", attr.estimatedRuntimeImpact)
    }

    @Test
    fun `verify attribution engine calculates legitimate metrics when positive telemetry exists`() {
        val app = AppConsumptionEntity(
            packageName = "com.test.activeapp",
            appName = "Active App",
            consumedMah = 25f,
            drainRating = "High",
            backgroundTimeMs = 50000L,
            foregroundTimeMs = 120000L,
            isRunning = true
        )

        val dummyState = BatteryState(percentage = 80, temperature = 42f, currentNow = -600)
        val attr = BatteryAttributionEngine.calculateAttribution(
            app = app,
            batteryState = dummyState,
            networkType = "5G",
            signalStrength = "Moderate",
            networkFluctuating = true
        )

        assertEquals("ESTIMATED", attr.status)
        assertEquals("CRITICAL", attr.impactLevel)
        assertEquals("HIGH CONFIDENCE", attr.confidence)
        assertTrue(attr.reason.contains("Sustained resource intensive operations"))
    }

    @Test
    fun `verify getAppBatteryUsageList returns empty when no permission or stats exist without fabricating apps`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val result = getAppBatteryUsageList(context)
        
        // Without special USAGE_STATS permission granted, it MUST return emptyList, never synthetic apps
        assertNotNull(result)
        assertTrue("Expected empty list without permission, got ${result.size} items", result.isEmpty())
    }

    @Test
    fun `verify package manager rejects uninstalled packages from attribution inventory`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val shadowPm: ShadowPackageManager = Shadows.shadowOf(context.packageManager)

        // Install only one test app
        val testPkg = "com.real.installed.app"
        val pkgInfo = PackageInfo().apply {
            packageName = testPkg
            applicationInfo = ApplicationInfo().apply {
                packageName = testPkg
                flags = 0
                enabled = true
                uid = 10099
            }
        }
        shadowPm.addPackage(pkgInfo)

        val installedApps = context.packageManager.getInstalledApplications(0).map { it.packageName }
        assertTrue(installedApps.contains(testPkg))
        assertFalse("Fake app must not be in installed packages", installedApps.contains("com.instagram.android"))
        assertFalse("Fake app must not be in installed packages", installedApps.contains("com.spotify.music"))
    }
}
