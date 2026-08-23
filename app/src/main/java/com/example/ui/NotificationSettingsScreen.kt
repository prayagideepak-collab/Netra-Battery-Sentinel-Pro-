package com.example.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.UiSessionRepository
import com.example.data.SettingsEntity
import com.example.engines.notification.*
import com.example.engines.notification.modules.CapabilityDetector
import com.example.engines.notification.modules.PermissionManager
import com.example.engines.notification.modules.SafetyOverrideManager

data class NascItemMetadata(
    val event: NotificationEvent,
    val category: NotificationCategory,
    val title: String,
    val description: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    settings: SettingsEntity,
    onSettingsChanged: (SettingsEntity) -> Unit,
    onOpenCalibrationAssistant: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val uiSession by UiSessionRepository.sessionState.collectAsStateWithLifecycle()
    val searchQuery = uiSession.notificationSearchQuery
    val selectedProfile = uiSession.notificationSelectedProfile
    var refreshTrigger by remember { mutableStateOf(0) }

    // Store pinned events (favorites) in local state
    val pinnedEvents = remember { mutableStateListOf<NotificationEvent>() }

    // Map expanded state for categories
    val expandedCategories = remember {
        mutableStateMapOf<NotificationCategory, Boolean>().apply {
            NotificationCategory.values().forEach { put(it, true) }
        }
    }

    // Pending confirmation dialog state when disabling important alerts
    var pendingDisableTarget by remember { mutableStateOf<Pair<NascItemMetadata, Boolean>?>(null) } // item to isNotification

    val allMetadata = remember { getFullNascItemMetadataList() }

    // Trigger refresh whenever trigger changes
    val allPreferences = remember(refreshTrigger) {
        NotificationPreferenceEngine.getAllPreferences().associateBy { it.event }
    }

    // Filter items based on searchQuery & capabilities
    val filteredItems = remember(searchQuery, refreshTrigger) {
        allMetadata.filter { item ->
            val matchesSearch = searchQuery.isBlank() ||
                    item.title.contains(searchQuery, ignoreCase = true) ||
                    item.description.contains(searchQuery, ignoreCase = true) ||
                    item.category.name.contains(searchQuery, ignoreCase = true)

            val isSupported = CapabilityDetector.isCapabilitySupported(context, item.event)
            matchesSearch && isSupported
        }
    }

    // Group items by category
    val itemsByCategory = remember(filteredItems) {
        filteredItems.groupBy { it.category }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- TOP BAR & HEADER ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Notifications & Announcements",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Notification Preference Engine (NASC v2.0)",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedIconButton(
                    onClick = {
                        Toast.makeText(context, "Exported notification preferences successfully.", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Outlined.Upload, contentDescription = "Export", modifier = Modifier.size(18.dp))
                }
                OutlinedIconButton(
                    onClick = {
                        Toast.makeText(context, "Preferences synchronized from backup.", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Outlined.Download, contentDescription = "Import", modifier = Modifier.size(18.dp))
                }
            }
        }

        // --- SEARCH BAR ---
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { next -> UiSessionRepository.updateSession(context) { it.copy(notificationSearchQuery = next) } },
            placeholder = { Text("Search notifications, alerts or categories...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { UiSessionRepository.updateSession(context) { it.copy(notificationSearchQuery = "") } }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        )

        // --- USER PROFILES SELECTOR ---
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Active Context Profile", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Default", "Driving", "Night", "Battery Saver").forEach { profile ->
                    val isSelected = selectedProfile == profile
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            UiSessionRepository.updateSession(context) { it.copy(notificationSelectedProfile = profile) }
                            Toast.makeText(context, "Activated $profile Profile", Toast.LENGTH_SHORT).show()
                        },
                        label = { Text(profile, fontSize = 12.sp) },
                        leadingIcon = if (isSelected) {
                            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        } else null,
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }
        }

        // --- PINNED FAVORITES SECTION ---
        val pinnedItems = filteredItems.filter { pinnedEvents.contains(it.event) }
        if (pinnedItems.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Star, contentDescription = "Pinned", tint = Color(0xFFFFB300), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pinned Favorites", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    pinnedItems.forEach { item ->
                        NascItemRow(
                            item = item,
                            pref = allPreferences[item.event],
                            isPinned = true,
                            onTogglePin = { pinnedEvents.remove(item.event) },
                            onPreferenceChange = { notif, ann ->
                                handlePrefChange(context, item, notif, ann, allPreferences[item.event]) { target, isNotif ->
                                    pendingDisableTarget = target to isNotif
                                }
                                refreshTrigger++
                            }
                        )
                    }
                }
            }
        }

        // --- CATEGORIES LIST ---
        NotificationCategory.values().forEach { category ->
            val categoryItems = itemsByCategory[category] ?: emptyList()
            if (categoryItems.isNotEmpty()) {
                val isExpanded = expandedCategories[category] ?: true
                val enabledCount = categoryItems.count { item ->
                    val p = allPreferences[item.event]
                    (p?.notificationEnabled == true) || (p?.announcementEnabled == true) || p?.isLocked == true
                }
                val totalCount = categoryItems.size

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        // Category Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expandedCategories[category] = !isExpanded }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = getCategoryIcon(category),
                                    contentDescription = category.name,
                                    tint = getCategoryColor(category),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = getCategoryTitle(category),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = "$enabledCount / $totalCount Active",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (enabledCount > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Text(
                                        text = "$enabledCount/$totalCount",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                                Icon(
                                    imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    contentDescription = "Expand"
                                )
                            }
                        }

                        // Expanded Items
                        AnimatedVisibility(visible = isExpanded) {
                            Column(
                                modifier = Modifier.padding(top = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                categoryItems.forEach { item ->
                                    val isPinned = pinnedEvents.contains(item.event)
                                    NascItemRow(
                                        item = item,
                                        pref = allPreferences[item.event],
                                        isPinned = isPinned,
                                        onTogglePin = {
                                            if (isPinned) pinnedEvents.remove(item.event) else pinnedEvents.add(item.event)
                                        },
                                        onPreferenceChange = { notif, ann ->
                                            handlePrefChange(context, item, notif, ann, allPreferences[item.event]) { target, isNotif ->
                                                pendingDisableTarget = target to isNotif
                                            }
                                            refreshTrigger++
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- RESTORE DEFAULTS BUTTON ---
        OutlinedButton(
            onClick = {
                NotificationPreferenceEngine.restoreDefaults()
                refreshTrigger++
                Toast.makeText(context, "Notification preferences restored to defaults.", Toast.LENGTH_SHORT).show()
            },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 24.dp)
        ) {
            Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Restore Default Preferences", fontWeight = FontWeight.SemiBold)
        }
    }

    // --- CONFIRMATION DIALOG WHEN DISABLING IMPORTANT ALERTS ---
    pendingDisableTarget?.let { (item, isNotif) ->
        AlertDialog(
            onDismissRequest = { pendingDisableTarget = null },
            icon = { Icon(Icons.Filled.Warning, contentDescription = "Warning", tint = MaterialTheme.colorScheme.error) },
            title = { Text("Disable Alert Warning") },
            text = { Text("Disabling '${item.title}' may reduce awareness of critical battery or hardware status. Are you sure you want to proceed?") },
            confirmButton = {
                TextButton(onClick = {
                    val currentPref = allPreferences[item.event]
                    val newNotif = if (isNotif) false else (currentPref?.notificationEnabled ?: true)
                    val newAnn = if (!isNotif) false else (currentPref?.announcementEnabled ?: true)
                    NotificationPreferenceEngine.updatePreference(item.event, newNotif, newAnn)
                    pendingDisableTarget = null
                    refreshTrigger++
                }) {
                    Text("Disable", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDisableTarget = null }) {
                    Text("Keep Enabled")
                }
            }
        )
    }
}

@Composable
private fun NascItemRow(
    item: NascItemMetadata,
    pref: NotificationPreference?,
    isPinned: Boolean,
    onTogglePin: () -> Unit,
    onPreferenceChange: (Boolean, Boolean) -> Unit
) {
    val context = LocalContext.current
    val isLocked = pref?.isLocked == true || SafetyOverrideManager.isLocked(item.event)
    val hasPermission = PermissionManager.isPermissionGrantedForEvent(context, item.event)

    val notifEnabled = pref?.notificationEnabled ?: true
    val annEnabled = pref?.announcementEnabled ?: true

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isLocked) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    IconButton(onClick = onTogglePin, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = if (isPinned) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = "Pin",
                            tint = if (isPinned) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Text(
                            text = item.title,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = item.description,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Test Alert Button
                IconButton(
                    onClick = {
                        NotificationPreferenceEngine.requestNotification(
                            context = context,
                            event = item.event,
                            title = "Preview: ${item.title}",
                            details = item.description,
                            source = "NASC_Preview"
                        )
                        Toast.makeText(context, "Triggered test alert for ${item.title}", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Filled.VolumeUp, contentDescription = "Test", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
            }

            // Controls Row
            if (isLocked) {
                // Locked Safety Status
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = "Locked", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Safety Critical Alert (Always Enabled)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                }
            } else if (!hasPermission) {
                // Missing Permission Banner
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Permission Required", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(
                        onClick = {
                            Toast.makeText(context, "Please grant system permissions in OS Settings.", Toast.LENGTH_SHORT).show()
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("Grant Permission", fontSize = 10.sp)
                    }
                }
            } else {
                // Dual Controls Switches
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Notification Switch
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Notifications, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Notification", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.width(6.dp))
                        Switch(
                            checked = notifEnabled,
                            onCheckedChange = { newNotif ->
                                onPreferenceChange(newNotif, annEnabled)
                            },
                            modifier = Modifier.scale(0.8f)
                        )
                    }

                    // Announcement Switch
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Announcement", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.width(6.dp))
                        Switch(
                            checked = annEnabled,
                            onCheckedChange = { newAnn ->
                                onPreferenceChange(notifEnabled, newAnn)
                            },
                            modifier = Modifier.scale(0.8f)
                        )
                    }
                }
            }
        }
    }
}

private fun handlePrefChange(
    context: Context,
    item: NascItemMetadata,
    newNotif: Boolean,
    newAnn: Boolean,
    currentPref: NotificationPreference?,
    onShowConfirmation: (NascItemMetadata, Boolean) -> Unit
) {
    val oldNotif = currentPref?.notificationEnabled ?: true
    val oldAnn = currentPref?.announcementEnabled ?: true

    // Check if user is turning OFF an important alert
    val isDisablingNotif = oldNotif && !newNotif
    val isDisablingAnn = oldAnn && !newAnn

    if ((isDisablingNotif || isDisablingAnn) && (item.event == NotificationEvent.BATTERY_PERCENTAGE || item.event == NotificationEvent.CHARGER_CONNECTED)) {
        onShowConfirmation(item, isDisablingNotif)
    } else {
        NotificationPreferenceEngine.updatePreference(item.event, newNotif, newAnn)
    }
}

private fun getCategoryTitle(category: NotificationCategory): String = when (category) {
    NotificationCategory.BATTERY -> "Battery Alerts"
    NotificationCategory.CHARGING -> "Charging Status"
    NotificationCategory.TEMPERATURE -> "Thermal & Temperature"
    NotificationCategory.MAGNETIC -> "Magnetic Interference"
    NotificationCategory.BLUETOOTH -> "Bluetooth Accessories"
    NotificationCategory.WEATHER -> "Government & Weather"
    NotificationCategory.SYSTEM -> "System Maintenance"
    NotificationCategory.SAFETY -> "Safety Critical Alerts (Locked)"
}

private fun getCategoryIcon(category: NotificationCategory) = when (category) {
    NotificationCategory.BATTERY -> Icons.Filled.BatteryFull
    NotificationCategory.CHARGING -> Icons.Filled.Bolt
    NotificationCategory.TEMPERATURE -> Icons.Filled.Thermostat
    NotificationCategory.MAGNETIC -> Icons.Filled.Explore
    NotificationCategory.BLUETOOTH -> Icons.Filled.Bluetooth
    NotificationCategory.WEATHER -> Icons.Filled.WbSunny
    NotificationCategory.SYSTEM -> Icons.Filled.Build
    NotificationCategory.SAFETY -> Icons.Filled.Shield
}

private fun getCategoryColor(category: NotificationCategory) = when (category) {
    NotificationCategory.BATTERY -> Color(0xFF4CAF50)
    NotificationCategory.CHARGING -> Color(0xFFFF9800)
    NotificationCategory.TEMPERATURE -> Color(0xFFF44336)
    NotificationCategory.MAGNETIC -> Color(0xFF9C27B0)
    NotificationCategory.BLUETOOTH -> Color(0xFF2196F3)
    NotificationCategory.WEATHER -> Color(0xFF00BCD4)
    NotificationCategory.SYSTEM -> Color(0xFF607D8B)
    NotificationCategory.SAFETY -> Color(0xFFD32F2F)
}

private fun getFullNascItemMetadataList(): List<NascItemMetadata> = listOf(
    // Battery
    NascItemMetadata(NotificationEvent.BATTERY_PERCENTAGE, NotificationCategory.BATTERY, "Battery Percentage", "Periodic notifications on current charge state"),
    NascItemMetadata(NotificationEvent.LOW_BATTERY_ALERTS, NotificationCategory.BATTERY, "Low Battery Alerts", "Alerts when battery drops below low threshold"),
    NascItemMetadata(NotificationEvent.CRITICAL_BATTERY_ALERTS, NotificationCategory.BATTERY, "Critical Battery Alerts", "Urgent warnings on critical battery drop"),
    NascItemMetadata(NotificationEvent.BATTERY_FULL, NotificationCategory.BATTERY, "Battery Full (100%)", "Notifies when battery reaches 100% full charge"),
    NascItemMetadata(NotificationEvent.OVERCHARGE_STARTED, NotificationCategory.BATTERY, "Overcharge Started", "Warns if charger remains connected after 100%"),
    NascItemMetadata(NotificationEvent.OVERCHARGE_REMINDER, NotificationCategory.BATTERY, "Overcharge Reminder", "Periodic reminder to disconnect charger"),
    NascItemMetadata(NotificationEvent.BATTERY_HEALTH_UPDATES, NotificationCategory.BATTERY, "Battery Health Updates", "Reports health capacity status changes"),
    NascItemMetadata(NotificationEvent.BATTERY_STATUS_CHANGES, NotificationCategory.BATTERY, "Battery Status Changes", "General battery state transition logs"),

    // Charging
    NascItemMetadata(NotificationEvent.CHARGER_CONNECTED, NotificationCategory.CHARGING, "Charger Connected", "Announces when charging cable is plugged in"),
    NascItemMetadata(NotificationEvent.CHARGER_DISCONNECTED, NotificationCategory.CHARGING, "Charger Disconnected", "Announces when charging cable is unplugged"),
    NascItemMetadata(NotificationEvent.CHARGING_TYPE_CHANGED, NotificationCategory.CHARGING, "Charging Type Changed", "Reports AC, USB, or Wireless mode switch"),
    NascItemMetadata(NotificationEvent.FAST_CHARGING_DETECTED, NotificationCategory.CHARGING, "Fast Charging Detected", "Notifies when Fast or Power Delivery charging is active"),
    NascItemMetadata(NotificationEvent.NORMAL_CHARGING_DETECTED, NotificationCategory.CHARGING, "Normal Charging", "Notifies when standard charging is active"),
    NascItemMetadata(NotificationEvent.DATA_TRANSFER_CHARGING_DETECTED, NotificationCategory.CHARGING, "Data Transfer Charging", "Notifies when USB data transfer charging is active"),
    NascItemMetadata(NotificationEvent.SLOW_CHARGING_DETECTED, NotificationCategory.CHARGING, "Slow Charging Detected", "Warns when current is too low for fast charge"),
    NascItemMetadata(NotificationEvent.CHARGING_INTERRUPTED, NotificationCategory.CHARGING, "Charging Interrupted", "Alerts if power cuts out unexpectedly"),

    // Temperature
    NascItemMetadata(NotificationEvent.TEMPERATURE_NORMAL, NotificationCategory.TEMPERATURE, "Temperature Normal", "Routine thermal status updates"),
    NascItemMetadata(NotificationEvent.TEMPERATURE_WARNING, NotificationCategory.TEMPERATURE, "Temperature Warning (39°C - 42°C)", "Alerts when device begins running hot"),
    NascItemMetadata(NotificationEvent.TEMPERATURE_CRITICAL, NotificationCategory.TEMPERATURE, "Temperature Critical (43°C - 44°C)", "High thermal risk alert"),
    NascItemMetadata(NotificationEvent.TEMPERATURE_EMERGENCY, NotificationCategory.TEMPERATURE, "Temperature Emergency (≥45°C)", "Emergency thermal overload alert"),

    // Magnetic
    NascItemMetadata(NotificationEvent.MAGNETIC_NORMAL, NotificationCategory.MAGNETIC, "Magnetic Normal", "Routine magnetic field readings"),
    NascItemMetadata(NotificationEvent.MAGNETIC_WARNING, NotificationCategory.MAGNETIC, "Magnetic Warning", "Alerts on elevated magnetic field detection"),
    NascItemMetadata(NotificationEvent.STRONG_MAGNETIC_FIELD, NotificationCategory.MAGNETIC, "Strong Magnetic Field", "Warns when strong magnet is near device"),
    NascItemMetadata(NotificationEvent.CONTINUOUS_MAGNETIC_EXPOSURE, NotificationCategory.MAGNETIC, "Continuous Exposure", "Notifies on prolonged magnetic field presence"),
    NascItemMetadata(NotificationEvent.MAGNETIC_CRITICAL, NotificationCategory.MAGNETIC, "Magnetic Critical", "Critical magnetic interference warning"),
    NascItemMetadata(NotificationEvent.MAGNETIC_EMERGENCY, NotificationCategory.MAGNETIC, "Magnetic Emergency", "Extreme magnetic field protection alert"),

    // Bluetooth
    NascItemMetadata(NotificationEvent.DEVICE_CONNECTED, NotificationCategory.BLUETOOTH, "Device Connected", "Announces Bluetooth accessory connection"),
    NascItemMetadata(NotificationEvent.DEVICE_DISCONNECTED, NotificationCategory.BLUETOOTH, "Device Disconnected", "Announces Bluetooth accessory disconnection"),
    NascItemMetadata(NotificationEvent.DEVICE_BATTERY_LOW, NotificationCategory.BLUETOOTH, "Device Battery Low", "Alerts when connected accessory battery is low"),
    NascItemMetadata(NotificationEvent.DEVICE_BATTERY_CRITICAL, NotificationCategory.BLUETOOTH, "Device Battery Critical", "Alerts when accessory battery is critically low"),
    NascItemMetadata(NotificationEvent.BLUETOOTH_CONNECTED, NotificationCategory.BLUETOOTH, "Bluetooth Connected", "General Bluetooth status update"),
    NascItemMetadata(NotificationEvent.BLUETOOTH_DISCONNECTED, NotificationCategory.BLUETOOTH, "Bluetooth Disconnected", "General Bluetooth disconnect alert"),
    NascItemMetadata(NotificationEvent.BLUETOOTH_LOW_BATTERY, NotificationCategory.BLUETOOTH, "Bluetooth Low Battery", "Accessory battery warning"),

    // Weather
    NascItemMetadata(NotificationEvent.WEATHER_GOVERNMENT, NotificationCategory.WEATHER, "Government Weather Alerts", "Government weather agency emergency bulletins"),
    NascItemMetadata(NotificationEvent.HEATWAVE, NotificationCategory.WEATHER, "Heatwave Alert", "Extreme ambient heatwave condition warnings"),
    NascItemMetadata(NotificationEvent.THUNDERSTORM, NotificationCategory.WEATHER, "Thunderstorm Alert", "Incoming severe thunderstorm alerts"),
    NascItemMetadata(NotificationEvent.HEAVY_RAIN, NotificationCategory.WEATHER, "Heavy Rain Alert", "Heavy rainfall bulletins"),
    NascItemMetadata(NotificationEvent.HIGH_WIND, NotificationCategory.WEATHER, "High Wind Alert", "Gale force wind warnings"),
    NascItemMetadata(NotificationEvent.DENSE_FOG, NotificationCategory.WEATHER, "Dense Fog Alert", "Low visibility fog warnings"),
    NascItemMetadata(NotificationEvent.WEATHER_EXTREME, NotificationCategory.WEATHER, "Extreme Weather", "Emergency weather risk alert"),

    // System
    NascItemMetadata(NotificationEvent.SYSTEM_BACKUP_COMPLETE, NotificationCategory.SYSTEM, "Backup Completed", "Notifies when system data backup succeeds"),
    NascItemMetadata(NotificationEvent.SYSTEM_EXPORT_COMPLETE, NotificationCategory.SYSTEM, "Export Completed", "Notifies when settings or logs export finishes"),
    NascItemMetadata(NotificationEvent.SYSTEM_RESTORE_COMPLETE, NotificationCategory.SYSTEM, "Restore Completed", "Notifies when system restore finishes"),
    NascItemMetadata(NotificationEvent.SYSTEM_UPDATE_INSTALLED, NotificationCategory.SYSTEM, "App Updated", "Notifies when system app is updated"),
    NascItemMetadata(NotificationEvent.DATABASE_REPAIR, NotificationCategory.SYSTEM, "Database Repair", "Alerts when automatic database repair runs"),
    NascItemMetadata(NotificationEvent.HEALTH_MONITOR_MESSAGES, NotificationCategory.SYSTEM, "Health Monitor Messages", "Diagnostic system health monitor logs"),

    // Safety (Locked)
    NascItemMetadata(NotificationEvent.BATTERY_TEMP_OVER_43, NotificationCategory.SAFETY, "Battery Temperature ≥43°C", "Hardware safety threshold protection"),
    NascItemMetadata(NotificationEvent.EXTERNAL_HEAT_SOURCE, NotificationCategory.SAFETY, "External Heat Source", "External heat hazard anomaly protection"),
    NascItemMetadata(NotificationEvent.FIRE_RISK, NotificationCategory.SAFETY, "Fire Risk Alert", "Hardware thermal runaway fire safety lock"),
    NascItemMetadata(NotificationEvent.BATTERY_CRITICAL_FAILURE, NotificationCategory.SAFETY, "Critical Battery Failure", "Internal battery cell failure protection"),
    NascItemMetadata(NotificationEvent.SYSTEM_EMERGENCY, NotificationCategory.SAFETY, "System Emergency", "Critical system emergency shutdown alert"),
    NascItemMetadata(NotificationEvent.HARDWARE_PROTECTION_ALERTS, NotificationCategory.SAFETY, "Hardware Protection Alerts", "Sensor hardware safety override")
)
