package com.example

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import com.example.ui.MainDashboard
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.BatteryViewModel
import com.example.util.SafeModeInitializer
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(com.example.util.getAttributionContext(newBase, "app_default"))
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i("NetraBoot", "Step 2: MainActivity Started")
        
        // Probe services
        SafeModeInitializer.runSafeTask("ServiceProbing") {
            probeServices()
        }
        
        // Instantiate view model
        val viewModel = SafeModeInitializer.runSafeTask("ViewModelInitialization") {
            ViewModelProvider(this)[BatteryViewModel::class.java]
        }

        // Action ordered: Resume the entire Netra System immediately
        viewModel?.resumeSystem(this)

        // Trigger immediate System Self-Audit on Application Startup
        try {
            lifecycleScope.launch {
                delay(2000)
                com.example.service.SystemSelfAuditEngine.runAudit(this@MainActivity, "App Startup")
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to run initial self-audit", e)
        }

        enableEdgeToEdge()
        if (viewModel != null) {
            setContent {
                // Observe settings to reactively apply user selected themes (LIGHT, DARK, AMOLED)
                val settings by viewModel.settings.collectAsState()
                val batteryState by viewModel.sanitizedBatteryState.collectAsState()

                MyApplicationTheme(
                    themeMode = settings.theme,
                    batteryLevel = batteryState.percentage,
                    lowBatteryRedThemeEnabled = settings.lowBatteryRedThemeEnabled,
                    dynamicColorEngineEnabled = settings.dynamicBatteryColorEngineEnabled || settings.theme.uppercase() == "DYNAMIC",
                    temperature = batteryState.temperature,
                    isCharging = batteryState.isCharging,
                    health = batteryState.healthPercentage
                ) {
                    MainDashboard(viewModel = viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val viewModel = try { ViewModelProvider(this)[BatteryViewModel::class.java] } catch (e: Exception) { null }
        viewModel?.resumeSystem(this)
    }

    private fun probeServices() {
        com.example.util.ServiceInitializer.initialize("BatteryService") {
            // Simple probe
            Class.forName("com.example.service.BatteryService")
        }
        com.example.util.ServiceInitializer.initialize("WeatherService") {
            // Simple probe
            Class.forName("com.example.service.WeatherService")
        }
    }
}
