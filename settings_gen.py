content = """package com.example.ui

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SettingsEntity
import com.example.viewmodel.BatteryViewModel
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.style.TextOverflow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetraSettingsCenterScreen(
    settings: SettingsEntity,
    onSettingsChanged: (SettingsEntity) -> Unit,
    viewModel: BatteryViewModel,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("netra_settings_center_container")
            .verticalScroll(scrollState)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Settings Center", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text("Customize app behavior, preferences and advanced options.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            OutlinedButton(
                onClick = { /* Export */ },
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Icon(Icons.Outlined.Upload, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Export Settings", fontSize = 11.sp)
            }
        }
        
        // Navigation Categories
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            item { CategoryNavCard("General", "App preferences", Icons.Filled.Settings, Color(0xFF4CAF50), true) }
            item { CategoryNavCard("Notifications", "Alerts & sounds", Icons.Filled.Notifications, Color(0xFFFF9800), false) }
            item { CategoryNavCard("Battery", "Battery settings", Icons.Filled.BatteryFull, Color(0xFF4CAF50), false) }
            item { CategoryNavCard("Monitoring", "Sensors & data", Icons.Filled.Insights, Color(0xFF673AB7), false) }
            item { CategoryNavCard("Security", "Privacy & safety", Icons.Filled.Security, Color(0xFFF44336), false) }
            item { CategoryNavCard("Advanced", "Developer options", Icons.Filled.Code, Color(0xFF2196F3), false) }
        }

        // Two Column Layout via Column of Rows
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SettingsSection(
                    title = "App Preferences",
                    modifier = Modifier.weight(1f)
                ) {
                    SettingItem(
                        icon = Icons.Filled.DarkMode,
                        iconColor = Color(0xFF673AB7),
                        title = "Theme Mode",
                        subtitle = "Choose light, dark or system theme",
                        value = settings.theme,
                        onClick = {
                            val nextTheme = when (settings.theme) {
                                "SYSTEM" -> "DARK"
                                "DARK" -> "LIGHT"
                                else -> "SYSTEM"
                            }
                            onSettingsChanged(settings.copy(theme = nextTheme))
                        }
                    )
                    SettingItem(
                        icon = Icons.Filled.ColorLens,
                        iconColor = Color(0xFF2196F3),
                        title = "Dynamic Colors",
                        subtitle = "Material You color engine",
                        isToggle = true,
                        toggleState = settings.dynamicBatteryColorEngineEnabled,
                        onToggle = { onSettingsChanged(settings.copy(dynamicBatteryColorEngineEnabled = it)) }
                    )
                    SettingItem(
                        icon = Icons.Filled.RecordVoiceOver,
                        iconColor = Color(0xFFE91E63),
                        title = "Voice Announcements",
                        subtitle = "Enable voice assistant",
                        isToggle = true,
                        toggleState = settings.voiceAssistantEnabled,
                        onToggle = { onSettingsChanged(settings.copy(voiceAssistantEnabled = it)) }
                    )
                }

                SettingsSection(
                    title = "Quick Preferences",
                    modifier = Modifier.weight(1f)
                ) {
                    SettingItem(
                        icon = Icons.Filled.PowerSettingsNew,
                        iconColor = Color(0xFF4CAF50),
                        title = "Run At Startup",
                        subtitle = "Start app on device boot",
                        isToggle = true,
                        toggleState = settings.runAtStartup,
                        onToggle = { onSettingsChanged(settings.copy(runAtStartup = it)) }
                    )
                    SettingItem(
                        icon = Icons.Filled.VolumeUp,
                        iconColor = Color(0xFF4CAF50),
                        title = "Screen On Voice",
                        subtitle = "Play when screen is on",
                        isToggle = true,
                        toggleState = settings.screenOnVoiceEnabled,
                        onToggle = { onSettingsChanged(settings.copy(screenOnVoiceEnabled = it)) }
                    )
                    SettingItem(
                        icon = Icons.Filled.NotificationsActive,
                        iconColor = Color(0xFFFF9800),
                        title = "Smart Alerts",
                        subtitle = "AI battery notifications",
                        isToggle = true,
                        toggleState = settings.smartBatteryAlertsEnabled,
                        onToggle = { onSettingsChanged(settings.copy(smartBatteryAlertsEnabled = it)) }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SettingsSection(
                    title = "Battery Settings",
                    modifier = Modifier.weight(1f)
                ) {
                    SettingItem(
                        icon = Icons.Filled.BatteryAlert,
                        iconColor = Color(0xFFF44336),
                        title = "Low Battery Alert",
                        subtitle = "Set low battery alert level",
                        value = "${settings.lowBatteryThreshold}%",
                        onClick = {
                            val newT = if (settings.lowBatteryThreshold >= 30) 15 else settings.lowBatteryThreshold + 5
                            onSettingsChanged(settings.copy(lowBatteryThreshold = newT))
                        }
                    )
                    SettingItem(
                        icon = Icons.Filled.BatteryChargingFull,
                        iconColor = Color(0xFF4CAF50),
                        title = "Battery Full Alert",
                        subtitle = "Notify when fully charged",
                        value = "${settings.fullBatteryThreshold}%",
                        onClick = {
                            val newT = if (settings.fullBatteryThreshold <= 80) 100 else settings.fullBatteryThreshold - 5
                            onSettingsChanged(settings.copy(fullBatteryThreshold = newT))
                        }
                    )
                    SettingItem(
                        icon = Icons.Filled.Alarm,
                        iconColor = Color(0xFFFF9800),
                        title = "Full Battery Alarm",
                        subtitle = "Ring loud alarm on full",
                        isToggle = true,
                        toggleState = settings.fullBatteryAlarmEnabled,
                        onToggle = { onSettingsChanged(settings.copy(fullBatteryAlarmEnabled = it)) }
                    )
                    SettingItem(
                        icon = Icons.Filled.Security,
                        iconColor = Color(0xFF2196F3),
                        title = "AI Throttling",
                        subtitle = "Smart charging protection",
                        isToggle = true,
                        toggleState = settings.aiThrottlingEnabled,
                        onToggle = { onSettingsChanged(settings.copy(aiThrottlingEnabled = it)) }
                    )
                }

                SettingsSection(
                    title = "Monitoring Settings",
                    modifier = Modifier.weight(1f)
                ) {
                    SettingItem(
                        icon = Icons.Filled.Sensors,
                        iconColor = Color(0xFF673AB7),
                        title = "Magnetic Field",
                        subtitle = "Sensor monitoring",
                        isToggle = true,
                        toggleState = settings.isMagneticFieldDetectionEnabled,
                        onToggle = { onSettingsChanged(settings.copy(isMagneticFieldDetectionEnabled = it)) }
                    )
                    SettingItem(
                        icon = Icons.Filled.LightMode,
                        iconColor = Color(0xFFFF9800),
                        title = "Light Intensity",
                        subtitle = "Environmental tracking",
                        isToggle = true,
                        toggleState = settings.isLightIntensityDetectionEnabled,
                        onToggle = { onSettingsChanged(settings.copy(isLightIntensityDetectionEnabled = it)) }
                    )
                    SettingItem(
                        icon = Icons.Filled.Analytics,
                        iconColor = Color(0xFF2196F3),
                        title = "AI Analytics",
                        subtitle = "Record predictive data",
                        isToggle = true,
                        toggleState = settings.aiAnalyticsEnabled,
                        onToggle = { onSettingsChanged(settings.copy(aiAnalyticsEnabled = it)) }
                    )
                    SettingItem(
                        icon = Icons.Filled.CloudUpload,
                        iconColor = Color(0xFF4CAF50),
                        title = "Cloud Backup",
                        subtitle = "Sync data securely",
                        isToggle = true,
                        toggleState = settings.cloudBackupEnabled,
                        onToggle = { onSettingsChanged(settings.copy(cloudBackupEnabled = it)) }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SettingsSection(
                    title = "Notifications & Quiet Hours",
                    modifier = Modifier.weight(1f)
                ) {
                    SettingItem(
                        icon = Icons.Filled.DoNotDisturb,
                        iconColor = Color(0xFF673AB7),
                        title = "Quiet Hours",
                        subtitle = "Set quiet hours",
                        value = "${settings.activeHoursEnd} - ${settings.activeHoursStart}",
                        onClick = { }
                    )
                    SettingItem(
                        icon = Icons.Filled.PriorityHigh,
                        iconColor = Color(0xFFF44336),
                        title = "High Temp Warnings",
                        subtitle = "Show thermal alerts",
                        isToggle = true,
                        toggleState = settings.tempWarningEnabled,
                        onToggle = { onSettingsChanged(settings.copy(tempWarningEnabled = it)) }
                    )
                    SettingItem(
                        icon = Icons.Filled.HealthAndSafety,
                        iconColor = Color(0xFF4CAF50),
                        title = "Health Declining Alerts",
                        subtitle = "Battery aging notices",
                        isToggle = true,
                        toggleState = settings.healthDecliningAlertEnabled,
                        onToggle = { onSettingsChanged(settings.copy(healthDecliningAlertEnabled = it)) }
                    )
                }

                SettingsSection(
                    title = "Security & Advanced",
                    modifier = Modifier.weight(1f)
                ) {
                    SettingItem(
                        icon = Icons.Filled.AdminPanelSettings,
                        iconColor = Color(0xFF4CAF50),
                        title = "Device Admin",
                        subtitle = "Advanced privileges",
                        isToggle = true,
                        toggleState = settings.deviceAdminEnabled,
                        onToggle = { onSettingsChanged(settings.copy(deviceAdminEnabled = it)) }
                    )
                    SettingItem(
                        icon = Icons.Filled.BugReport,
                        iconColor = Color(0xFF9C27B0),
                        title = "Developer Tools",
                        subtitle = "View detailed logs",
                        value = "Open >",
                        onClick = { /* Open logs */ }
                    )
                    SettingItem(
                        icon = Icons.Filled.Storage,
                        iconColor = Color(0xFF2196F3),
                        title = "Database Optimization",
                        subtitle = "Optimize app database",
                        isButton = true,
                        buttonText = "Optimize Now",
                        onClick = { /* Optimize */ }
                    )
                }
            }
            
            // Footer
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50).copy(alpha = 0.05f)),
                border = BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.2f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Your data is safe and secure", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("We respect your privacy and never share your data.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    OutlinedButton(
                        onClick = { },
                        border = BorderStroke(1.dp, Color(0xFF4CAF50)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("View Privacy Policy", color = Color(0xFF4CAF50), fontSize = 12.sp)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun CategoryNavCard(title: String, subtitle: String, icon: ImageVector, color: Color, isSelected: Boolean) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
        ),
        border = BorderStroke(
            0.5.dp, 
            if (isSelected) color.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        ),
        modifier = Modifier.width(150.dp).clickable { }
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(32.dp).background(color.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun SettingsSection(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = modifier) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                content()
            }
        }
    }
}

@Composable
fun SettingItem(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    value: String? = null,
    isToggle: Boolean = false,
    toggleState: Boolean = false,
    onToggle: ((Boolean) -> Unit)? = null,
    isButton: Boolean = false,
    buttonText: String = "",
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(36.dp).background(iconColor.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (isToggle) {
            Switch(
                checked = toggleState,
                onCheckedChange = onToggle,
                modifier = Modifier.scale(0.8f)
            )
        } else if (isButton) {
            OutlinedButton(
                onClick = { onClick?.invoke() },
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Text(buttonText, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
            }
        } else if (value != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(value, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            }
        }
    }
}
"""

with open("app/src/main/java/com/example/ui/NetraSettingsCenter.kt", "w") as f:
    f.write(content.strip())
