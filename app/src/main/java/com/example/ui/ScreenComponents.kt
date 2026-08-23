package com.example.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.example.data.ChargingSession
import com.example.data.SettingsEntity
import com.example.service.BatteryState
import com.example.service.WeatherReport
import com.example.viewmodel.BatteryViewModel

@Composable fun DashboardScreen(s: androidx.compose.runtime.State<BatteryState>, vm: BatteryViewModel, set: SettingsEntity, weather: WeatherReport?) { Text("Dashboard") }
@Composable fun ChargingScreen(s: androidx.compose.runtime.State<BatteryState>, vm: BatteryViewModel) { Text("Charging") }
@Composable fun HistoryScreen(sessions: List<ChargingSession>, vm: BatteryViewModel) { Text("History") }
@Composable fun InsightsScreen(s: androidx.compose.runtime.State<BatteryState>, vm: BatteryViewModel) { Text("Insights") }
@Composable fun CustomizeScreen() { Text("Customize") }
