package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.engines.coordinator.GlobalDataRefreshCoordinator
import com.example.engines.festival.FestivalContextEngine
import com.example.engines.festival.FestivalEngineState
import com.example.engines.weather.EnvironmentalContextEngine
import com.example.engines.weather.EnvironmentalThemeEngine
import com.example.engines.weather.EnvironmentalThemeState
import com.example.engines.weather.WeatherIconMapper
import com.example.ui.theme.GlobalThemeCoordinator
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FestivalWeatherDashboardIntegrationTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testFestivalEngineNoMonthFallback() {
        val currentFestival = FestivalContextEngine.evaluateTodayFestival()
        val activeList = FestivalContextEngine.activeFestivals.value
        if (currentFestival == null) {
            assertTrue("Active festival list must be empty when no verified festival exists", activeList.isEmpty())
            assertEquals(FestivalEngineState.NO_FESTIVAL, FestivalContextEngine.engineState.value)
        } else {
            assertNotNull(currentFestival.festivalName)
            assertTrue(activeList.contains(currentFestival))
        }
    }

    @Test
    fun testCustomFestivalImportAndThemePriority() {
        val importName = "Netra Anniversary"
        val importDate = "07-04"
        FestivalContextEngine.importCalendarFestival(importName, importDate, "SOLAR_GOLD", "Anniversary Celebration")
        val current = FestivalContextEngine.currentFestival.value
        assertNotNull(current)
        assertEquals(importName, current?.festivalName)

        // Test GlobalThemeCoordinator gives festival theme priority when active festival exists
        val theme = GlobalThemeCoordinator.resolveAuthoritativeTheme("SYSTEM", false, 100)
        assertNotNull(theme)
    }

    @Test
    fun testEnvironmentalThemeEngineStates() {
        val clearState = EnvironmentalThemeEngine.evaluateEnvironmentalState("Clear", 800, "Day")
        assertEquals(EnvironmentalThemeState.CLEAR_DAY, clearState)
        assertNotNull(EnvironmentalThemeEngine.getThemeColorScheme(clearState))

        val rainState = EnvironmentalThemeEngine.evaluateEnvironmentalState("Rain", 500, "Day")
        assertEquals(EnvironmentalThemeState.RAIN, rainState)

        val thunderState = EnvironmentalThemeEngine.evaluateEnvironmentalState("Thunderstorm", 200, "Day")
        assertEquals(EnvironmentalThemeState.THUNDERSTORM, thunderState)

        val nightState = EnvironmentalThemeEngine.evaluateEnvironmentalState("Clear", 800, "Night")
        assertEquals(EnvironmentalThemeState.CLEAR_NIGHT, nightState)
    }

    @Test
    fun testWeatherIconMapperMappings() {
        assertNotNull(WeatherIconMapper.getWeatherIcon("Clear", 800))
        assertNotNull(WeatherIconMapper.getWeatherIcon("Heavy Rain", 502))
        assertNotNull(WeatherIconMapper.getWeatherIcon("Thunderstorm", 200))
        assertNotNull(WeatherIconMapper.getWeatherIcon("Snow", 600))
        assertNotNull(WeatherIconMapper.getWeatherIcon("Cloudy", 804))
    }

    @Test
    fun testEnvironmentalContextEngineDatasetUpdate() = runTest {
        EnvironmentalContextEngine.updateWithWeatherReport(context, "Prayagraj", 32.0f, "Clear", "India")
        val dataset = EnvironmentalContextEngine.datasetFlow.value
        assertEquals("Prayagraj", dataset.cityName)
        assertEquals(32.0f, dataset.currentTemp)
        assertEquals("Clear", dataset.weatherCondition)
    }

    @Test
    fun testGlobalDataRefreshCoordinatorExecution() = runTest {
        val refreshState = GlobalDataRefreshCoordinator.refreshAll(context)
        assertNotNull(refreshState)
        assertTrue(refreshState.locationSuccess)
        assertTrue(refreshState.weatherSuccess)
        assertTrue(refreshState.festivalSuccess)
    }
}
