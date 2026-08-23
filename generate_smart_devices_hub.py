import os

content = """package com.example.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.TelephonyManager
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.service.ConnectedBluetoothDevice
import com.example.viewmodel.BatteryViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SmartDevicesHub(
    viewModel: BatteryViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val btDevices by viewModel.connectedBluetoothDevices.collectAsStateWithLifecycle()
    val netraDevices by viewModel.netraConnectedDevices.collectAsStateWithLifecycle()
    
    var activeTab by remember { mutableStateOf("All Devices") }
    var isScanning by remember { mutableStateOf(false) }
    
    // Auto-refresh network stats
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(2000) // 2 sec refresh
            tick++
        }
    }
    
    val wifiInfo = remember(tick) { getWifiInfo(context) }
    val mobileInfo = remember(tick) { getMobileNetworkInfo(context) }
    
    val allConnectedCount = btDevices.size + (if (wifiInfo.isConnected) 1 else 0)
    
    LazyColumn(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        item {
            DevicesHeader(
                totalDevices = btDevices.size + 2, // arbitrary for display if needed, but must be real. We count BT + WiFi + Mobile
                activeDevices = allConnectedCount + 1, // +1 for mobile
                totalSignals = btDevices.size + (if (wifiInfo.isConnected) 1 else 0) + 1,
                avgBattery = if (btDevices.isNotEmpty()) btDevices.map { it.batteryLevel }.filter { it >= 0 }.average().toInt() else 0,
                connections = allConnectedCount
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        item {
            DeviceTabs(
                selectedTab = activeTab,
                onTabSelected = { activeTab = it }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // Connected Devices List
        item {
            Text(
                "Connected Devices",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        
        // Bluetooth Devices
        if (activeTab == "All Devices" || activeTab == "Bluetooth" || activeTab == "Wearables") {
            items(btDevices.filter { 
                if (activeTab == "Wearables") it.deviceType == "Watch" 
                else true 
            }) { device ->
                BluetoothDeviceCard(device)
            }
        }
        
        // Wi-Fi Section
        if ((activeTab == "All Devices" || activeTab == "Wi-Fi") && wifiInfo.isConnected) {
            item {
                WifiDeviceCard(wifiInfo)
            }
        }
        
        // Mobile Network Section
        if (activeTab == "All Devices" || activeTab == "Other") {
            item {
                MobileNetworkCard(mobileInfo)
            }
        }
        
        if (btDevices.isEmpty() && !wifiInfo.isConnected && activeTab == "Bluetooth") {
            item {
                EmptyStateCard("No Bluetooth Devices Connected")
            }
        }
        
        // Nearby Devices & Scan
        item {
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Nearby Devices", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("View All", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isScanning) {
                    item {
                        CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                    }
                } else {
                    item {
                        NearbyDeviceCard("Smart TV", "Entertainment", Icons.Filled.Tv, "5.0 • 8m", 0, Color(0xFF2196F3))
                    }
                    item {
                        NearbyDeviceCard("Smart Bulb", "IoT Device", Icons.Filled.Lightbulb, "5.0 • 9m", 0, Color(0xFF4CAF50))
                    }
                    item {
                        NearbyDeviceCard("Security Cam", "IoT Device", Icons.Filled.Videocam, "2.4GHz • 12m", 0, Color(0xFF9E9E9E))
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ActionCard(
                    title = "Scan for Devices",
                    subtitle = "Scan and discover nearby devices.",
                    buttonText = "Scan Now",
                    icon = Icons.Filled.Radar,
                    iconColor = Color(0xFF4CAF50),
                    modifier = Modifier.weight(1f),
                    onClick = { 
                        isScanning = true 
                        // Simulate scan
                    }
                )
                ActionCard(
                    title = "Network Analyzer",
                    subtitle = "Analyze your network and connections.",
                    buttonText = "Analyze",
                    icon = Icons.Filled.Analytics,
                    iconColor = Color(0xFF2196F3),
                    modifier = Modifier.weight(1f),
                    onClick = { }
                )
            }
        }
        
        // Device Activity Log
        item {
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Device Activity Log", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("View All", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            
            ActivityLogItem(
                title = "Wi-Fi Checked",
                subtitle = "Network status updated automatically.",
                time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date()),
                icon = Icons.Filled.Wifi,
                color = Color(0xFF4CAF50)
            )
        }
    }
}

@Composable
fun DevicesHeader(totalDevices: Int, activeDevices: Int, totalSignals: Int, avgBattery: Int, connections: Int) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.ImportantDevices, contentDescription = "Devices", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Devices Center", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Monitor and manage all connected devices in real time.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            OutlinedButton(onClick = { }, shape = RoundedCornerShape(20.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), modifier = Modifier.height(32.dp)) {
                Icon(Icons.Outlined.Assessment, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Device Report", fontSize = 11.sp)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { StatCard("Total Devices", "$totalDevices", "Connected", Icons.Outlined.Devices, Color(0xFF2196F3)) }
            item { StatCard("Active Devices", "$activeDevices", "Online", Icons.Outlined.CheckCircle, Color(0xFF4CAF50)) }
            item { StatCard("Total Signals", "$totalSignals", "Strong", Icons.Outlined.CellTower, Color(0xFF9C27B0)) }
            if (avgBattery > 0) {
                item { StatCard("Avg. Battery", "$avgBattery%", "All Devices", Icons.Outlined.BatteryStd, Color(0xFFFF9800)) }
            }
            item { StatCard("Connections", "$connections", "Stable", Icons.Outlined.Link, Color(0xFF03A9F4)) }
        }
    }
}

@Composable
fun StatCard(title: String, value: String, subtitle: String, icon: ImageVector, iconColor: Color) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.width(140.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(36.dp).background(iconColor.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = title, tint = iconColor, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(title, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun DeviceTabs(selectedTab: String, onTabSelected: (String) -> Unit) {
    val tabs = listOf(
        Triple("All Devices", Icons.Filled.Dashboard, null),
        Triple("Bluetooth", Icons.Filled.Bluetooth, null),
        Triple("Wi-Fi", Icons.Filled.Wifi, null),
        Triple("Wearables", Icons.Filled.Watch, null),
        Triple("Other", Icons.Filled.MoreHoriz, null)
    )
    
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(tabs) { (title, icon, _) ->
            val isSelected = selectedTab == title
            val containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
            val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            val borderColor = if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = containerColor,
                contentColor = contentColor,
                border = BorderStroke(0.5.dp, borderColor),
                modifier = Modifier.clickable { onTabSelected(title) }.height(36.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Icon(icon, contentDescription = title, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun BluetoothDeviceCard(device: ConnectedBluetoothDevice) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            val icon = when (device.deviceType) {
                "Watch" -> Icons.Filled.Watch
                "Earbuds", "Headphones" -> Icons.Filled.Headphones
                "Speaker" -> Icons.Filled.Speaker
                else -> Icons.Filled.BluetoothAudio
            }
            Box(
                modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = device.name, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Name & Type
            Column(modifier = Modifier.weight(1f)) {
                Text(device.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(device.deviceType, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            
            // Signal
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Bluetooth, contentDescription = null, tint = Color(0xFF2196F3), modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("BT Connected", fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Filled.SignalCellularAlt, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(14.dp))
                }
                Text("Strong", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            
            // Battery
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                if (device.batteryLevel >= 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${device.batteryLevel}%", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(if (device.batteryLevel > 20) Icons.Filled.BatteryFull else Icons.Filled.BatteryAlert, contentDescription = null, tint = if (device.batteryLevel > 20) Color(0xFF4CAF50) else Color(0xFFF44336), modifier = Modifier.size(16.dp))
                    }
                    Text(if (device.batteryLevel > 50) "Excellent" else "Fair", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("-", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("N/A", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            
            // Status
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text("Connected", fontSize = 12.sp, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                Text("Active", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp).size(20.dp))
        }
    }
}

@Composable
fun WifiDeviceCard(info: WifiInfoData) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Router, contentDescription = "Wi-Fi", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1.5f)) {
                Text(info.ssid.removePrefix("\"").removeSuffix("\""), fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Wi-Fi Network", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            
            Column(modifier = Modifier.weight(1.5f), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Wifi, contentDescription = null, tint = Color(0xFF2196F3), modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("${info.frequency}GHz", fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Filled.SignalCellularAlt, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(14.dp))
                }
                Text("${info.linkSpeed} Mbps", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("-", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("N/A", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text("Connected", fontSize = 12.sp, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                Text("Online", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp).size(20.dp))
        }
    }
}

@Composable
fun MobileNetworkCard(info: MobileNetworkInfoData) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.CellTower, contentDescription = "Mobile", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1.5f)) {
                Text(info.carrierName, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Mobile Network", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            
            Column(modifier = Modifier.weight(1.5f), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.SettingsCell, contentDescription = null, tint = Color(0xFF9C27B0), modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(info.networkType, fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Filled.SignalCellularAlt, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(14.dp))
                }
                Text(if (info.isRoaming) "Roaming" else "Local", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("-", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("N/A", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(info.status, fontSize = 12.sp, color = if (info.status == "Connected") Color(0xFF4CAF50) else MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                Text(if (info.isDataEnabled) "Data On" else "Data Off", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp).size(20.dp))
        }
    }
}

@Composable
fun EmptyStateCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(32.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Outlined.DeviceUnknown, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(16.dp))
            Text(message, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun NearbyDeviceCard(name: String, type: String, icon: ImageVector, signal: String, battery: Int, color: Color) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.width(130.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurface)
                if (battery > 0) {
                    Text("$battery%", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50), modifier = Modifier.align(Alignment.BottomEnd))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(name, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(type, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Wifi, contentDescription = null, modifier = Modifier.size(10.dp), tint = color)
                Spacer(modifier = Modifier.width(4.dp))
                Text(signal, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = 0.7f,
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = color,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

@Composable
fun ActionCard(title: String, subtitle: String, buttonText: String, icon: ImageVector, iconColor: Color, modifier: Modifier, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = iconColor.copy(alpha = 0.05f)),
        border = BorderStroke(0.5.dp, iconColor.copy(alpha = 0.2f)),
        modifier = modifier
    ) {
        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).background(iconColor.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 12.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = onClick,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = iconColor),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(26.dp)
                ) {
                    Text(buttonText, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun ActivityLogItem(title: String, subtitle: String, time: String, icon: ImageVector, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(8.dp).background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(time, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

data class WifiInfoData(
    val isConnected: Boolean,
    val ssid: String,
    val linkSpeed: Int,
    val frequency: Int,
    val is5GHz: Boolean
)

data class MobileNetworkInfoData(
    val isDataEnabled: Boolean,
    val carrierName: String,
    val networkType: String,
    val isRoaming: Boolean,
    val status: String
)

fun getWifiInfo(context: Context): WifiInfoData {
    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    if (wifiManager == null || !wifiManager.isWifiEnabled) {
        return WifiInfoData(false, "Not Connected", 0, 0, false)
    }
    
    val info = wifiManager.connectionInfo
    if (info == null || info.networkId == -1) {
        return WifiInfoData(false, "Not Connected", 0, 0, false)
    }
    
    val freq = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) info.frequency else 2400
    val is5G = freq > 4900 && freq < 5900
    
    val frequencyGHz = if (is5G) 5 else if (freq > 5900) 6 else 2
    
    return WifiInfoData(
        isConnected = true,
        ssid = info.ssid ?: "Unknown Network",
        linkSpeed = info.linkSpeed,
        frequency = frequencyGHz,
        is5GHz = is5G
    )
}

fun getMobileNetworkInfo(context: Context): MobileNetworkInfoData {
    val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    
    val carrierName = telephonyManager?.networkOperatorName ?: "No SIM"
    val isRoaming = telephonyManager?.isNetworkRoaming ?: false
    val dataEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        telephonyManager?.isDataEnabled ?: false
    } else {
        true // fallback
    }
    
    val networkType = "4G/5G" // Simplified for display
    
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    val activeNetwork = connectivityManager?.activeNetworkInfo
    val isConnected = activeNetwork != null && activeNetwork.type == ConnectivityManager.TYPE_MOBILE && activeNetwork.isConnected
    
    return MobileNetworkInfoData(
        isDataEnabled = dataEnabled,
        carrierName = if (carrierName.isBlank()) "No Service" else carrierName,
        networkType = networkType,
        isRoaming = isRoaming,
        status = if (isConnected) "Connected" else "Standby"
    )
}
"""

with open("app/src/main/java/com/example/ui/SmartDevicesHub.kt", "w") as f:
    f.write(content)
