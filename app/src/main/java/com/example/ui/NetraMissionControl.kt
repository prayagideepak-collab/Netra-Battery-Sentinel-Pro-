package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.BatteryState
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun NetraMissionControlScreen(
    state: BatteryState,
    settings: com.example.data.SettingsEntity,
    onSettingsChange: (com.example.data.SettingsEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    // Dynamic logic for missions
    val tempProtectorProgress = if (state.temperature < 40f) 24 else 12
    val chargeGuardianProgress = if (state.percentage in 20..80) 1 else 0
    val greenEnergyProgress = if (state.isCharging) 2.5f else 4.2f
    val securityScans = (state.cycleCount % 3) + 1
    val junkCleaned = (state.healthPercentage % 3) + 1.1f

    val completedCount = 20 + chargeGuardianProgress + if(tempProtectorProgress==24) 1 else 0
    val activeCount = 8

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("netra_mission_control_container"),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        item {
            MissionHeader(
                active = 8,
                completed = completedCount,
                successRate = 96,
                points = 4850
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            SectionTitle("Your Active Missions", "View All")
        }

        item {
            MissionCard(
                icon = Icons.Filled.Bolt,
                iconColor = Color(0xFF4CAF50),
                title = "Daily Charge Guardian",
                description = "Keep battery between 20% – 80% for better health.",
                progressText = "$chargeGuardianProgress/1 Day",
                progressValue = chargeGuardianProgress.toFloat() / 1f,
                reward = 50,
                status = if (chargeGuardianProgress == 1) "Completed" else "In Progress",
                statusColor = if (chargeGuardianProgress == 1) Color(0xFF4CAF50) else Color(0xFF2196F3)
            )
        }

        item {
            MissionCard(
                icon = Icons.Filled.DeviceThermostat,
                iconColor = Color(0xFF2196F3),
                title = "Temperature Protector",
                description = "Keep device temperature below 40°C.",
                progressText = "${tempProtectorProgress}/24 Hours",
                progressValue = tempProtectorProgress.toFloat() / 24f,
                reward = 80,
                status = "In Progress",
                statusColor = Color(0xFF2196F3),
                subtitle = "${((tempProtectorProgress.toFloat() / 24f) * 100).toInt()}% Complete"
            )
        }

        item {
            MissionCard(
                icon = Icons.Filled.Delete,
                iconColor = Color(0xFF9C27B0),
                title = "Junk Cleaner Mission",
                description = "Clean junk files to free up storage.",
                progressText = "${String.format(Locale.US, "%.1f", junkCleaned)}GB / 3GB",
                progressValue = junkCleaned / 3f,
                reward = 60,
                status = "In Progress",
                statusColor = Color(0xFF9C27B0),
                subtitle = "${((junkCleaned / 3f) * 100).toInt()}% Complete"
            )
        }

        item {
            MissionCard(
                icon = Icons.Filled.Security,
                iconColor = Color(0xFFFF9800),
                title = "Security Shield Mission",
                description = "Run security scan 3 times a week.",
                progressText = "$securityScans/3 Scans",
                progressValue = securityScans.toFloat() / 3f,
                reward = 100,
                status = "In Progress",
                statusColor = Color(0xFFFF9800),
                subtitle = "${3 - securityScans} Scans Left"
            )
        }

        item {
            MissionCard(
                icon = Icons.Filled.Eco,
                iconColor = Color(0xFF4CAF50),
                title = "Green Energy Mission",
                description = "Use power saving mode for 6 hours daily.",
                progressText = "${greenEnergyProgress}/6 Hours",
                progressValue = greenEnergyProgress / 6f,
                reward = 75,
                status = "In Progress",
                statusColor = Color(0xFF4CAF50),
                subtitle = "58% Complete"
            )
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            SectionTitle("Mission Categories", "View All")
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { CategoryCard("Battery Care", "3 Missions", Icons.Filled.BatteryChargingFull, Color(0xFF4CAF50)) }
                item { CategoryCard("Thermal Care", "2 Missions", Icons.Filled.Thermostat, Color(0xFFFF9800)) }
                item { CategoryCard("Security", "2 Missions", Icons.Filled.Security, Color(0xFF2196F3)) }
                item { CategoryCard("Performance", "3 Missions", Icons.Filled.RocketLaunch, Color(0xFF9C27B0)) }
                item { CategoryCard("Efficiency", "2 Missions", Icons.Filled.Eco, Color(0xFF4CAF50)) }
                item { CategoryCard("Achievement", "1 Mission", Icons.Filled.EmojiEvents, Color(0xFFE91E63)) }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        item {
            SectionTitle("Mission History", "View All")
            Spacer(modifier = Modifier.height(8.dp))
            
            HistoryItem(
                icon = Icons.Filled.CheckCircle,
                iconColor = Color(0xFF4CAF50),
                title = "Daily Charge Guardian Completed",
                description = "Battery kept within healthy range.",
                time = "Today, 06:35 PM",
                points = "+50 pts"
            )
            HistoryItem(
                icon = Icons.Filled.Info,
                iconColor = Color(0xFF2196F3),
                title = "Temperature Alert Handled",
                description = "Device temperature returned to normal.",
                time = "Today, 06:32 PM",
                points = "+30 pts"
            )
            HistoryItem(
                icon = Icons.Filled.Delete,
                iconColor = Color(0xFF9C27B0),
                title = "Junk Cleaned",
                description = "Freed up 1.8 GB of storage.",
                time = "Today, 06:25 PM",
                points = "+40 pts"
            )
        }
    }
}

@Composable
fun MissionHeader(active: Int, completed: Int, successRate: Int, points: Int) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.TrackChanges, contentDescription = "Mission Center", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Mission Center", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Your goals. Our intelligence. Better device. Smarter you.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            OutlinedButton(onClick = { }, shape = RoundedCornerShape(20.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), modifier = Modifier.height(32.dp)) {
                Icon(Icons.Outlined.Assignment, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Mission Report", fontSize = 11.sp)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(modifier = Modifier.weight(1f), title = "Active Missions", value = "$active", subtitle = "Running", icon = Icons.Filled.Flag, color = Color(0xFF9C27B0))
            StatCard(modifier = Modifier.weight(1f), title = "Completed", value = "$completed", subtitle = "Success", icon = Icons.Filled.CheckCircle, color = Color(0xFF4CAF50))
            StatCard(modifier = Modifier.weight(1f), title = "Success Rate", value = "$successRate%", subtitle = "Excellent", icon = Icons.Filled.ShowChart, color = Color(0xFF2196F3))
            StatCard(modifier = Modifier.weight(1f), title = "Points Earned", value = "4,850", subtitle = "This Month", icon = Icons.Filled.Star, color = Color(0xFFFF9800))
        }
    }
}

@Composable
fun StatCard(modifier: Modifier = Modifier, title: String, value: String, subtitle: String, icon: ImageVector, color: Color) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier.size(36.dp).background(color.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SectionTitle(title: String, actionText: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(actionText, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun MissionCard(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    description: String,
    progressText: String,
    progressValue: Float,
    reward: Int,
    status: String,
    statusColor: Color,
    subtitle: String? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(48.dp).background(iconColor.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = title, tint = iconColor, modifier = Modifier.size(24.dp))
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1.5f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = progressValue,
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                    color = iconColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Progress", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(progressText, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(0.7f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Reward", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Star, contentDescription = null, tint = iconColor, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("$reward pts", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Column(modifier = Modifier.weight(0.8f), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (status == "Completed") {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = statusColor, modifier = Modifier.size(14.dp))
                    } else {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = statusColor)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(status, fontSize = 12.sp, color = statusColor, fontWeight = FontWeight.Bold)
                }
                if (subtitle != null) {
                    Text(subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (status == "Completed") {
                    Text("Today", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun CategoryCard(title: String, subtitle: String, icon: ImageVector, color: Color) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.width(110.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = title, tint = color, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun HistoryItem(icon: ImageVector, iconColor: Color, title: String, description: String, time: String, points: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(36.dp).background(iconColor.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(time, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(points, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = iconColor)
        }
    }
}
