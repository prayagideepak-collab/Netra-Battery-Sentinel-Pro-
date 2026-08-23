package com.example.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.viewmodel.BatteryViewModel
import com.example.service.BatteryState

@Composable
fun CustomizeSection(viewModel: BatteryViewModel) {
    var selectedGauge by remember { mutableStateOf("Classic Ring") }
    var cardDensity by remember { mutableStateOf("Standard") }
    var selectedWidget by remember { mutableStateOf("Small") }
    val state by viewModel.sanitizedBatteryState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    val themeOptions = listOf(
        "FESTIVAL_AUTO" to "Auto Calendar Festival",
        "DIWALI" to "Diwali (Golden Diya)",
        "HOLI" to "Holi (Gulal Multicolor)",
        "NAVRATRI" to "Navratri (Ruby Red)",
        "EID" to "Eid Mubarak (Emerald)",
        "CHRISTMAS" to "Christmas (Pine Crimson)",
        "INDEPENDENCE" to "Independence / Tiranga",
        "MAKAR_SANKRANTI" to "Makar Sankranti",
        "GANESH_CHATURTHI" to "Ganesh Chaturthi",
        "DARK" to "Sentinel Cyber Dark",
        "LIGHT" to "Standard Light",
        "AMOLED" to "Pure AMOLED Black",
        "DYNAMIC" to "Battery Level Adaptive",
        "OCEAN_BLUE" to "Oceanic Blue & Cyan",
        "SOLAR_GOLD" to "Solar Gold Amber",
        "AURORA_PURPLE" to "Aurora Purple",
        "FOREST_EMERALD" to "Forest Emerald"
    )

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("CUSTOMIZE & THEMING", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
            
            Text("Active Festival & Theme Engine", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text("Choose an instant festival palette or daily calendar synchronization", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
        }

        items(themeOptions) { (key, label) ->
            val isSelected = settings.theme.equals(key, ignoreCase = true)
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clickable { viewModel.updateSettings(settings.copy(theme = key)) }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { viewModel.updateSettings(settings.copy(theme = key)) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp)
                    }
                    if (isSelected) {
                        Text("Active", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Gauge Style", fontWeight = FontWeight.Bold)
            listOf("Classic Ring", "Speedometer", "Segmented").forEach { style ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selectedGauge == style, onClick = { selectedGauge = style })
                    Text(style)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text("Card Density", fontWeight = FontWeight.Bold)
            listOf("Compact", "Standard", "Detailed").forEach { density ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = cardDensity == density, onClick = { cardDensity = density })
                    Text(density)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text("Widget Preview", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            
            // Widget Selection
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf("Small", "Medium", "Large", "Smart").forEach { type ->
                    FilterChip(
                        selected = selectedWidget == type,
                        onClick = { selectedWidget = type },
                        label = { Text(type) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            
            // Widget Preview Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(Color(0xFF07120B), RoundedCornerShape(16.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                val previewState = if (state.isDataAvailable) state else BatteryState(
                    percentage = 65,
                    isCharging = true,
                    timeTo100Min = 45,
                    remainingTimeMs = 3600000L,
                    temperature = 28.5f,
                    voltage = 4120,
                    currentNow = 250,
                    health = "Good",
                    speed = 4.2f,
                    chargingType = "AC"
                )
                when (selectedWidget) {
                    "Small" -> SmallWidget(previewState, Color(0xFF07120B), Color(0xFF00E676))
                    "Medium" -> MediumWidget(previewState, Color(0xFF07120B), Color(0xFF00E676))
                    "Large" -> LargeWidget(previewState, Color(0xFF07120B), Color(0xFF00E676))
                    "Smart" -> SmartWidget(previewState, Color(0xFF07120B), Color(0xFF00E676))
                }
            }
        }
    }
}
