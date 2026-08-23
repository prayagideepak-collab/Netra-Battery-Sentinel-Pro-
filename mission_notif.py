import re

content = """package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.viewmodel.BatteryViewModel
import com.example.data.SettingsEntity
import com.example.data.BatteryEvent
import java.text.SimpleDateFormat
import java.util.*

enum class NotificationPriority(val color: Color, val icon: ImageVector, val label: String) {
    CRITICAL(Color(0xFFE53935), Icons.Outlined.Shield, "CRITICAL"),
    WARNING(Color(0xFFFF9800), Icons.Outlined.WarningAmber, "WARNING"),
    INFO(Color(0xFF2196F3), Icons.Outlined.Info, "INFO"),
    SYSTEM(Color(0xFF4CAF50), Icons.Outlined.CheckCircle, "SYSTEM")
}

data class NotificationGroup(
    val title: String,
    val details: String,
    val timestamp: Long,
    val priority: NotificationPriority,
    val count: Int,
    val events: List<BatteryEvent>
)

fun getPriorityForEvent(event: BatteryEvent): NotificationPriority {
    val t = event.title.lowercase()
    val c = event.category.lowercase()
    
    if (t.contains("high temperature") || t.contains("critical") || t.contains("overheat") || t.contains("fail") || t.contains("emergency") || c.contains("critical")) return NotificationPriority.CRITICAL
    if (t.contains("warning") || t.contains("fast charging") || t.contains("magnetic") || t.contains("anomaly") || t.contains("threshold") || t.contains("low") || t.contains("disconnect")) return NotificationPriority.WARNING
    if (t.contains("fully charged") || t.contains("recovery") || t.contains("background") || c.contains("system") || event.eventType.lowercase() == "system") return NotificationPriority.SYSTEM
    return NotificationPriority.INFO
}

@Composable
fun NetraNotificationCenterScreen(
    viewModel: BatteryViewModel,
    settings: SettingsEntity,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val batteryEvents by viewModel.allBatteryEvents.collectAsStateWithLifecycle(initialValue = emptyList())
    
    // Process and group notifications
    val groupedNotifications = remember(batteryEvents) {
        val groups = mutableListOf<NotificationGroup>()
        val groupedByTitle = batteryEvents.sortedByDescending { it.timestamp }.groupBy { it.title }
        
        for ((title, events) in groupedByTitle) {
            val firstEvent = events.first()
            val priority = getPriorityForEvent(firstEvent)
            
            val details = if (events.size > 1) {
                "${firstEvent.details} (and ${events.size - 1} more similar events)"
            } else {
                firstEvent.details
            }
            
            groups.add(
                NotificationGroup(
                    title = title,
                    details = details,
                    timestamp = firstEvent.timestamp,
                    priority = priority,
                    count = events.size,
                    events = events
                )
            )
        }
        groups.sortedByDescending { it.timestamp }
    }
    
    val criticalCount = groupedNotifications.count { it.priority == NotificationPriority.CRITICAL }
    val warningCount = groupedNotifications.count { it.priority == NotificationPriority.WARNING }
    val infoCount = groupedNotifications.count { it.priority == NotificationPriority.INFO }
    val systemCount = groupedNotifications.count { it.priority == NotificationPriority.SYSTEM }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("netra_notification_center_container")
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Text("Notification Center", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { /* Filter */ }) {
                    Icon(Icons.Outlined.FilterAlt, contentDescription = "Filter")
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                }
            }
        }
        
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            // Overview Stats
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NotificationStatCard(modifier = Modifier.weight(1f), count = criticalCount, label = "Critical", priority = NotificationPriority.CRITICAL)
                    NotificationStatCard(modifier = Modifier.weight(1f), count = warningCount, label = "Warnings", priority = NotificationPriority.WARNING)
                    NotificationStatCard(modifier = Modifier.weight(1f), count = infoCount, label = "Info", priority = NotificationPriority.INFO)
                    NotificationStatCard(modifier = Modifier.weight(1f), count = systemCount, label = "System", priority = NotificationPriority.SYSTEM)
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Section Title
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("LIVE PRIORITY FEED", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp)
                    Text("Mark All Read", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            // Notification List
            if (groupedNotifications.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No notifications available.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(groupedNotifications) { group ->
                    NotificationItemCard(group = group)
                }
            }
        }
    }
}

@Composable
fun NotificationStatCard(modifier: Modifier = Modifier, count: Int, label: String, priority: NotificationPriority) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(priority.icon, contentDescription = null, tint = priority.color, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("$count", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun NotificationItemCard(group: NotificationGroup) {
    val sdf = remember { SimpleDateFormat("hh:mm a", Locale.US) }
    val timeStr = sdf.format(Date(group.timestamp))
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { /* Expand logic */ },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(group.priority.color.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(group.priority.icon, contentDescription = null, tint = group.priority.color, modifier = Modifier.size(20.dp))
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.title + if (group.count > 1) " (${group.count})" else "",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = group.priority.color
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = group.details,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Time & Badge
            Column(horizontalAlignment = Alignment.End) {
                Text(text = timeStr, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .background(group.priority.color.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                        .border(0.5.dp, group.priority.color.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = group.priority.label,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = group.priority.color
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Icon(Icons.Filled.ChevronRight, contentDescription = "View Details", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            }
        }
    }
}
"""

with open("app/src/main/java/com/example/ui/NetraNotificationCenter.kt", "w") as f:
    f.write(content.strip())
