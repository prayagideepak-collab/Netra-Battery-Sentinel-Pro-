package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.example.ui.MainDashboard
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.BatteryViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Instantiate view model
        val viewModel = ViewModelProvider(this)[BatteryViewModel::class.java]

        enableEdgeToEdge()
        setContent {
            // Observe settings to reactively apply user selected themes (LIGHT, DARK, AMOLED)
            val settings by viewModel.settings.collectAsState()

            MyApplicationTheme(themeMode = settings.theme) {
                MainDashboard(viewModel = viewModel)
            }
        }
    }
}
