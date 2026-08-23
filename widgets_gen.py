import os

content = """package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.BatteryState
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll

@Composable
fun NetraWidgetsSimulation(
    state: BatteryState,
    modifier: Modifier = Modifier
) {
    val sdf = remember { SimpleDateFormat("hh:mm a", Locale.US) }
    var currentTime by remember { mutableStateOf(sdf.format(Date())) }
    
    // Background colors based on the design
    val bgColor = Color(0xFF0A1910)
    val cardBgColor = Color(0xFF10261A)
    val widgetBgColor = Color(0xFF07120B)
    val primaryColor = Color(0xFF00E676)
    val primaryVariantColor = Color(0xFF00C853)
    val surfaceColor = Color(0xFF1A3D2A)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(16.dp)
            .testTag("netra_widgets_simulation_container")
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Netra Battery Sentinel Pro – ", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("Widgets", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = primaryColor)
                }
                Text("Real-time • Live Data • Always Accurate", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
            }
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = surfaceColor.copy(alpha = 0.5f)),
                border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.AccessTime, contentDescription = null, tint = primaryColor, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(currentTime, color = primaryColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Horizontal Scroll for Widgets
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. SMALL WIDGET
            WidgetColumn(title = "SMALL WIDGET", subtitle = "2 x 2", width = 160.dp) {
                SmallWidget(state, widgetBgColor, primaryColor)
                Spacer(modifier = Modifier.weight(1f))
                WidgetFeatureList(
                    title = "Best For Quick Glance",
                    features = listOf(
                        Icons.Filled.BatteryFull to "Battery %",
                        Icons.Filled.AccessTime to "Remaining Time",
                        Icons.Filled.Bolt to "Charging Status"
                    ),
                    primaryColor = primaryColor
                )
            }
            
            // 2. MEDIUM WIDGET
            WidgetColumn(title = "MEDIUM WIDGET", subtitle = "4 x 2", width = 220.dp) {
                MediumWidget(state, widgetBgColor, primaryColor)
                Spacer(modifier = Modifier.weight(1f))
                WidgetFeatureList(
                    title = "More Information",
                    features = listOf(
                        Icons.Filled.BatteryFull to "Battery % & Time",
                        Icons.Filled.Bolt to "Charging / Discharging",
                        Icons.Filled.Thermostat to "Temperature",
                        Icons.Filled.FlashOn to "Voltage",
                        Icons.Filled.Favorite to "Battery Health",
                        Icons.Filled.Bluetooth to "Wi-Fi, Bluetooth Status"
                    ),
                    primaryColor = primaryColor
                )
            }
            
            // 3. LARGE WIDGET
            WidgetColumn(title = "LARGE WIDGET", subtitle = "4 x 3", width = 280.dp) {
                LargeWidget(state, widgetBgColor, primaryColor)
                Spacer(modifier = Modifier.weight(1f))
                WidgetFeatureList(
                    title = "Detailed Overview",
                    features = listOf(
                        Icons.Filled.Power to "All Medium Data",
                        Icons.Filled.BatteryChargingFull to "Charge Rate & Source",
                        Icons.Filled.ShowChart to "Battery Usage Graph (Live)",
                        Icons.Filled.Watch to "Connected Devices",
                        Icons.Filled.Security to "Sensors & Security Status"
                    ),
                    primaryColor = primaryColor
                )
            }
            
            // 4. SMART WIDGET
            WidgetColumn(title = "SMART WIDGET", subtitle = "4 x 4", width = 320.dp) {
                SmartWidget(state, widgetBgColor, primaryColor)
                Spacer(modifier = Modifier.weight(1f))
                WidgetFeatureList(
                    title = "Complete Control & Live Dashboard",
                    features = listOf(
                        Icons.Filled.Dashboard to "All Large Data",
                        Icons.Filled.Headset to "Connected Devices Battery",
                        Icons.Filled.Eco to "Battery Saver Status",
                        Icons.Filled.VerifiedUser to "System Status",
                        Icons.Filled.QueryStats to "Live Graph & Real-time Updates"
                    ),
                    primaryColor = primaryColor
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Footer Note
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Info, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("All widgets show real-time data from your device. Data updates every few seconds.", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
            }
            Text("Note: Widgets follow app theme (Light/Dark) automatically.", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
        }
    }
}

@Composable
fun WidgetColumn(title: String, subtitle: String, width: androidx.compose.ui.unit.Dp, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.width(width).fillMaxHeight(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF10261A)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxHeight()) {
            Text(title, color = Color(0xFF00E676), fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            Text(subtitle, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
fun BatteryCircle(
    percentage: Int, 
    remainingTime: String, 
    isCharging: Boolean,
    primaryColor: Color, 
    size: androidx.compose.ui.unit.Dp = 100.dp,
    strokeWidth: androidx.compose.ui.unit.Dp = 8.dp
) {
    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawArc(
                color = primaryColor.copy(alpha = 0.2f),
                startAngle = 140f,
                sweepAngle = 260f,
                useCenter = false,
                style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color = primaryColor,
                startAngle = 140f,
                sweepAngle = 260f * (percentage / 100f),
                useCenter = false,
                style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Bolt, contentDescription = null, tint = primaryColor, modifier = Modifier.size(20.dp))
            Text("${percentage}%", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
            Text(remainingTime, color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp)
            Text("Remaining", color = Color.White.copy(alpha = 0.6f), fontSize = 9.sp)
        }
    }
}

@Composable
fun SmallWidget(state: BatteryState, bgColor: Color, primaryColor: Color) {
    val remainingText = if (state.isCharging) "${state.timeTo100Min / 60}h ${state.timeTo100Min % 60}m" else "${state.remainingTimeMs / (1000 * 3600)}h ${(state.remainingTimeMs / (1000 * 60)) % 60}m"
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        modifier = Modifier.fillMaxWidth().aspectRatio(1f)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            BatteryCircle(
                percentage = state.percentage,
                remainingTime = remainingText,
                isCharging = state.isCharging,
                primaryColor = primaryColor,
                size = 110.dp
            )
        }
    }
}

@Composable
fun MediumWidget(state: BatteryState, bgColor: Color, primaryColor: Color) {
    val remainingText = if (state.isCharging) "${state.timeTo100Min / 60}h ${state.timeTo100Min % 60}m" else "${state.remainingTimeMs / (1000 * 3600)}h ${(state.remainingTimeMs / (1000 * 60)) % 60}m"
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BatteryCircle(
                    percentage = state.percentage,
                    remainingTime = remainingText,
                    isCharging = state.isCharging,
                    primaryColor = primaryColor,
                    size = 100.dp
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(6.dp).background(primaryColor, CircleShape))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (state.isCharging) "Charging" else "Discharging", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Thermostat, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text("${state.temperature}°C", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("Temp", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Bolt, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text(String.format(Locale.US, "%.2f V", state.voltage / 1000f), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("Voltage", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Favorite, contentDescription = null, tint = primaryColor, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text(state.health, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("Health", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Wifi, contentDescription = null, tint = primaryColor, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("On", color = Color.White, fontSize = 11.sp)
                }
                Text("|", color = Color.White.copy(alpha = 0.3f), fontSize = 12.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Bluetooth, contentDescription = null, tint = Color(0xFF2196F3), modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("2 Devices", color = Color.White, fontSize = 11.sp)
                }
                Text("|", color = Color.White.copy(alpha = 0.3f), fontSize = 12.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Security, contentDescription = null, tint = primaryColor, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Secure", color = Color.White, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun LargeWidget(state: BatteryState, bgColor: Color, primaryColor: Color) {
    val remainingText = if (state.isCharging) "${state.timeTo100Min / 60}h ${state.timeTo100Min % 60}m" else "${state.remainingTimeMs / (1000 * 3600)}h ${(state.remainingTimeMs / (1000 * 60)) % 60}m"
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BatteryCircle(
                    percentage = state.percentage,
                    remainingTime = remainingText,
                    isCharging = state.isCharging,
                    primaryColor = primaryColor,
                    size = 110.dp
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(6.dp).background(primaryColor, CircleShape))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (state.isCharging) "Charging" else "Discharging", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Thermostat, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text("${state.temperature}°C", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("Temperature", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Bolt, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(String.format(Locale.US, "%.2f V", state.voltage / 1000f), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("Voltage", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Favorite, contentDescription = null, tint = primaryColor, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(state.health, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("Health", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Charge Rate", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                    Text(if (state.isCharging) "+${String.format(Locale.US, "%.1f", state.speed)}%/h" else "-${String.format(Locale.US, "%.1f", state.speed)}%/h", color = primaryColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Power, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text("Power Source", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                        Text(state.chargingType.takeIf { it != "None" } ?: "Battery", color = primaryColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            // Fake Graph Area
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Battery Usage (Last 6 Hours)", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(modifier = Modifier.height(8.dp))
                Canvas(modifier = Modifier.fillMaxWidth().height(40.dp)) {
                    val pts = listOf(1f, 0.9f, 0.85f, 0.75f, 0.6f, 0.78f)
                    val w = size.width
                    val h = size.height
                    val step = w / (pts.size - 1)
                    
                    val path = androidx.compose.ui.graphics.Path()
                    path.moveTo(0f, h - (pts[0] * h))
                    for (i in 1 until pts.size) {
                        path.lineTo(i * step, h - (pts[i] * h))
                    }
                    drawPath(
                        path = path,
                        color = primaryColor,
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    )
                    // Draw dots
                    for (i in pts.indices) {
                        drawCircle(
                            color = primaryColor,
                            radius = 4.dp.toPx(),
                            center = androidx.compose.ui.geometry.Offset(i * step, h - (pts[i] * h))
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("10 AM", color = Color.White.copy(alpha = 0.5f), fontSize = 8.sp)
                    Text("12 PM", color = Color.White.copy(alpha = 0.5f), fontSize = 8.sp)
                    Text("02 PM", color = Color.White.copy(alpha = 0.5f), fontSize = 8.sp)
                    Text("04 PM", color = Color.White.copy(alpha = 0.5f), fontSize = 8.sp)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatusItem("Wi-Fi", "On", Icons.Filled.Wifi, Color(0xFF2196F3))
                StatusItem("Bluetooth", "2 Devices", Icons.Filled.Bluetooth, Color(0xFF2196F3))
                StatusItem("Sensors", "Active", Icons.Filled.Sensors, primaryColor)
                StatusItem("Security", "Safe", Icons.Filled.Security, primaryColor)
            }
        }
    }
}

@Composable
fun SmartWidget(state: BatteryState, bgColor: Color, primaryColor: Color) {
    val remainingText = if (state.isCharging) "${state.timeTo100Min / 60}h ${state.timeTo100Min % 60}m" else "${state.remainingTimeMs / (1000 * 3600)}h ${(state.remainingTimeMs / (1000 * 60)) % 60}m"
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BatteryCircle(
                    percentage = state.percentage,
                    remainingTime = remainingText,
                    isCharging = state.isCharging,
                    primaryColor = primaryColor,
                    size = 110.dp
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("SYSTEM STATUS", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, letterSpacing = 1.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(6.dp).background(primaryColor, CircleShape))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Active & Verified", color = primaryColor, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Thermostat, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text("${state.temperature}°C", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("Temp", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Bolt, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(String.format(Locale.US, "%.2f V", state.voltage / 1000f), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("Voltage", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Favorite, contentDescription = null, tint = primaryColor, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(state.health, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("Health", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Charge Rate", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                    Text(if (state.isCharging) "+${String.format(Locale.US, "%.1f", state.speed)}%/h" else "-${String.format(Locale.US, "%.1f", state.speed)}%/h", color = primaryColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Power, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text("Power Source", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                        Text(state.chargingType.takeIf { it != "None" } ?: "Battery", color = primaryColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Eco, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text("Battery Saver", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                        Text("Off", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            // Fake Graph Area
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Battery Usage (Last 6 Hours)", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(modifier = Modifier.height(8.dp))
                Canvas(modifier = Modifier.fillMaxWidth().height(40.dp)) {
                    val pts = listOf(1f, 0.9f, 0.85f, 0.75f, 0.6f, 0.78f)
                    val w = size.width
                    val h = size.height
                    val step = w / (pts.size - 1)
                    
                    val path = androidx.compose.ui.graphics.Path()
                    path.moveTo(0f, h - (pts[0] * h))
                    for (i in 1 until pts.size) {
                        path.lineTo(i * step, h - (pts[i] * h))
                    }
                    drawPath(
                        path = path,
                        color = primaryColor,
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    )
                    for (i in pts.indices) {
                        drawCircle(
                            color = primaryColor,
                            radius = 4.dp.toPx(),
                            center = androidx.compose.ui.geometry.Offset(i * step, h - (pts[i] * h))
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("10 AM", color = Color.White.copy(alpha = 0.5f), fontSize = 8.sp)
                    Text("12 PM", color = Color.White.copy(alpha = 0.5f), fontSize = 8.sp)
                    Text("02 PM", color = Color.White.copy(alpha = 0.5f), fontSize = 8.sp)
                    Text("04 PM", color = Color.White.copy(alpha = 0.5f), fontSize = 8.sp)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Connected Devices", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                Text("2", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Watch, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Smart Watch", color = Color.White, fontSize = 12.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("85%", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Filled.BatteryFull, contentDescription = null, tint = primaryColor, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Headset, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("TWS Earbuds", color = Color.White, fontSize = 12.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("70%", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Filled.BatteryFull, contentDescription = null, tint = primaryColor, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun StatusItem(title: String, subtitle: String, icon: ImageVector, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(title, color = Color.White, fontSize = 10.sp)
        Text(subtitle, color = color, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun WidgetFeatureList(title: String, features: List<Pair<ImageVector, String>>, primaryColor: Color) {
    Column {
        Text(title, color = primaryColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(12.dp))
        features.forEach { (icon, text) ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, tint = primaryColor, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(text, color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
            }
        }
    }
}
"""

with open("app/src/main/java/com/example/ui/NetraWidgetsSimulation.kt", "w") as f:
    f.write(content)
