package com.example.ui

import androidx.compose.animation.*
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
import com.example.data.BatteryEvent
import com.example.data.SettingsEntity
import com.example.util.NetraCategory
import com.example.viewmodel.BatteryViewModel
import java.text.SimpleDateFormat
import java.util.*

enum class NotificationPriority(val color: Color, val icon: ImageVector, val label: String) {
    CRITICAL(Color(0xFFE53935), Icons.Outlined.Shield, "CRITICAL"),
    WARNING(Color(0xFFFF9800), Icons.Outlined.WarningAmber, "WARNING"),
    INFO(Color(0xFF2196F3), Icons.Outlined.Info, "INFO"),
    SYSTEM(Color(0xFF4CAF50), Icons.Outlined.CheckCircle, "SYSTEM")
}

data class UnifiedNotificationItem(
    val id: String,
    val notificationCategory: NetraCategory,
    val eventType: String,
    val priority: NotificationPriority,
    val timestamp: Long,
    val title: String,
    val details: String,
    val source: String,
    val rawEvent: BatteryEvent?
)

fun getPriorityForEvent(event: BatteryEvent): NotificationPriority {
    val t = event.title.lowercase()
    val c = event.category.lowercase()
    val d = event.details.lowercase()

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
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val batteryEvents by viewModel.allBatteryEvents.collectAsStateWithLifecycle(initialValue = emptyList())

    // Convert raw events to categorized unified notifications
    val allNotifications = remember(batteryEvents) {
        batteryEvents.map { ev ->
            val cat = NetraCategory.classifyEvent(
                category = ev.category,
                eventType = ev.eventType,
                title = ev.title,
                source = ev.source
            )
            UnifiedNotificationItem(
                id = "notif_${ev.id}",
                notificationCategory = cat,
                eventType = ev.eventType,
                priority = getPriorityForEvent(ev),
                timestamp = ev.timestamp,
                title = ev.title,
                details = ev.details,
                source = ev.source,
                rawEvent = ev
            )
        }.sortedByDescending { it.timestamp }
    }

    // Active Category Selection State for clean 2-level hierarchy: Category -> Items -> Detail
    var selectedCategory by remember { mutableStateOf<NetraCategory?>(null) }
    var selectedNotificationItem by remember { mutableStateOf<UnifiedNotificationItem?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedPriorityFilter by remember { mutableStateOf<NotificationPriority?>(null) }

    // Counts per category
    val countsByCategory = remember(allNotifications) {
        val map = mutableMapOf<NetraCategory, Int>()
        NetraCategory.values().forEach { cat ->
            map[cat] = allNotifications.count { it.notificationCategory == cat }
        }
        map
    }

    val totalCount = allNotifications.size
    val criticalCount = allNotifications.count { it.priority == NotificationPriority.CRITICAL }
    val warningCount = allNotifications.count { it.priority == NotificationPriority.WARNING }

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
                if (selectedCategory != null) {
                    IconButton(
                        onClick = {
                            selectedCategory = null
                            searchQuery = ""
                            selectedPriorityFilter = null
                        },
                        modifier = Modifier.testTag("notification_category_back_button")
                    ) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back to Categories")
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                } else if (onNavigateBack != null) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                } else {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                }

                Column {
                    Text(
                        text = selectedCategory?.let { "${it.title} Notifications" } ?: "Notification Center",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = selectedCategory?.let { "Viewing ${countsByCategory[it] ?: 0} events" }
                            ?: "$totalCount total alerts across ${NetraCategory.values().size} categories",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.testTag("notification_settings_button")
                ) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                }
            }
        }

        // Crossfade between Category Dashboard and Category Detail List
        Crossfade(targetState = selectedCategory, label = "NotificationViewCrossfade") { activeCat ->
            if (activeCat == null) {
                // LEVEL 1: CATEGORY DASHBOARD
                NotificationCategoryDashboard(
                    countsByCategory = countsByCategory,
                    criticalCount = criticalCount,
                    warningCount = warningCount,
                    totalCount = totalCount,
                    onSelectCategory = { cat ->
                        selectedCategory = cat
                        searchQuery = ""
                        selectedPriorityFilter = null
                    }
                )
            } else {
                // LEVEL 2: CATEGORY SPECIFIC NOTIFICATION LIST
                val categoryNotifications = remember(allNotifications, activeCat, searchQuery, selectedPriorityFilter) {
                    allNotifications.filter { notif ->
                        notif.notificationCategory == activeCat &&
                                (selectedPriorityFilter == null || notif.priority == selectedPriorityFilter) &&
                                (searchQuery.isBlank() ||
                                        notif.title.contains(searchQuery, ignoreCase = true) ||
                                        notif.details.contains(searchQuery, ignoreCase = true) ||
                                        notif.eventType.contains(searchQuery, ignoreCase = true))
                    }
                }

                CategoryNotificationListView(
                    category = activeCat,
                    notifications = categoryNotifications,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    selectedPriority = selectedPriorityFilter,
                    onPrioritySelect = { selectedPriorityFilter = it },
                    onItemClick = { item -> selectedNotificationItem = item }
                )
            }
        }
    }

    // LEVEL 3: NOTIFICATION DETAIL DIALOG
    selectedNotificationItem?.let { notif ->
        NotificationDetailDialog(
            item = notif,
            onDismiss = { selectedNotificationItem = null }
        )
    }
}

@Composable
fun NotificationCategoryDashboard(
    countsByCategory: Map<NetraCategory, Int>,
    criticalCount: Int,
    warningCount: Int,
    totalCount: Int,
    onSelectCategory: (NetraCategory) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Summary Overview Stat Cards
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NotificationOverviewStat(
                    modifier = Modifier.weight(1f),
                    label = "Total Alerts",
                    count = totalCount,
                    color = MaterialTheme.colorScheme.primary,
                    icon = Icons.Filled.Notifications
                )
                NotificationOverviewStat(
                    modifier = Modifier.weight(1f),
                    label = "Critical",
                    count = criticalCount,
                    color = Color(0xFFE53935),
                    icon = Icons.Filled.Warning
                )
                NotificationOverviewStat(
                    modifier = Modifier.weight(1f),
                    label = "Warnings",
                    count = warningCount,
                    color = Color(0xFFFF9800),
                    icon = Icons.Filled.Info
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        // Section Title
        item {
            Text(
                text = "NOTIFICATION CATEGORIES",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }

        // Category Cards
        items(NetraCategory.values(), key = { it.id }) { category ->
            val count = countsByCategory[category] ?: 0
            NotificationCategoryCard(
                category = category,
                count = count,
                onClick = { onSelectCategory(category) }
            )
        }

        item {
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun NotificationOverviewStat(
    modifier: Modifier = Modifier,
    label: String,
    count: Int,
    color: Color,
    icon: ImageVector
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        border = BorderStroke(0.5.dp, color.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(color.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = count.toString(),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Text(
                    text = label,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun NotificationCategoryCard(
    category: NetraCategory,
    count: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("notification_category_card_${category.id.lowercase()}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, category.color.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(category.color.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = category.icon,
                    contentDescription = category.title,
                    tint = category.color,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = category.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Surface(
                        color = if (count > 0) category.color.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (count > 0) "$count alerts" else "0 alerts",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (count > 0) category.color else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = category.description,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 15.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = "Open Category",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun CategoryNotificationListView(
    category: NetraCategory,
    notifications: List<UnifiedNotificationItem>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedPriority: NotificationPriority?,
    onPrioritySelect: (NotificationPriority?) -> Unit,
    onItemClick: (UnifiedNotificationItem) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy • hh:mm:ss a", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // In-Category Search and Filters
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Search ${category.title} alerts...", fontSize = 11.sp) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .testTag("notification_category_search_input"),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = category.color,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                ),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Priority Filter Chips
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FilterChip(
                    selected = selectedPriority == null,
                    onClick = { onPrioritySelect(null) },
                    label = { Text("ALL (${notifications.size})", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                )
                NotificationPriority.values().forEach { prio ->
                    FilterChip(
                        selected = selectedPriority == prio,
                        onClick = { onPrioritySelect(if (selectedPriority == prio) null else prio) },
                        label = {
                            Text(prio.label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = prio.color)
                        }
                    )
                }
            }
        }

        // Notifications List
        if (notifications.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.NotificationsOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No matching ${category.title} alerts found" else "No notifications in this category yet",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(notifications.distinctBy { it.id }, key = { it.id }) { item ->
                NotificationItemCard(
                    item = item,
                    dateFormat = dateFormat,
                    onClick = { onItemClick(item) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun NotificationItemCard(
    item: UnifiedNotificationItem,
    dateFormat: SimpleDateFormat,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("notification_item_${item.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, item.priority.color.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(item.priority.color.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    item.priority.icon,
                    contentDescription = item.priority.label,
                    tint = item.priority.color,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Surface(
                        color = item.priority.color.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = item.priority.label,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = item.priority.color,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = item.details.lines().firstOrNull() ?: item.details,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = dateFormat.format(Date(item.timestamp)),
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.outline
                    )

                    Text(
                        text = "Source: ${item.source}",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun NotificationDetailDialog(
    item: UnifiedNotificationItem,
    onDismiss: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(item.priority.icon, contentDescription = null, tint = item.priority.color)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = item.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Category:", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Surface(color = item.notificationCategory.color.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                        Text(item.notificationCategory.title, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = item.notificationCategory.color, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Severity:", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text(item.priority.label, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = item.priority.color)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Timestamp:", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text(dateFormat.format(Date(item.timestamp)), fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Source / Engine:", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text(item.source, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Text("Details & Telemetry Payload:", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = item.details,
                        fontSize = 10.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
