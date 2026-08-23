package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AppActivity
import com.example.data.BatteryEvent
import com.example.data.SystemAuditRecord
import com.example.util.NetraCategory
import com.example.viewmodel.BatteryViewModel
import java.text.SimpleDateFormat
import java.util.*

data class CategorizedLogItem(
    val id: String,
    val category: NetraCategory,
    val eventType: String,
    val timestamp: Long,
    val title: String,
    val details: String,
    val source: String,
    val isVerified: Boolean = true
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemLogScreen(
    viewModel: BatteryViewModel,
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val batteryEvents by viewModel.allBatteryEvents.collectAsStateWithLifecycle(initialValue = emptyList())
    val auditRecords by viewModel.allSystemAuditRecords.collectAsStateWithLifecycle(initialValue = emptyList())
    val appActivities by viewModel.allAppActivity.collectAsStateWithLifecycle(initialValue = emptyList())

    // Map all data sources into standard authoritative Unified Log items
    val allLogs = remember(batteryEvents, auditRecords, appActivities) {
        val list = mutableListOf<CategorizedLogItem>()

        batteryEvents.forEach { ev ->
            val cat = NetraCategory.classifyEvent(
                category = ev.category,
                eventType = ev.eventType,
                title = ev.title,
                source = ev.source
            )
            list.add(
                CategorizedLogItem(
                    id = "event_${ev.id}",
                    category = cat,
                    eventType = ev.eventType,
                    timestamp = ev.timestamp,
                    title = ev.title,
                    details = ev.details,
                    source = ev.source,
                    isVerified = true
                )
            )
        }

        auditRecords.forEach { audit ->
            list.add(
                CategorizedLogItem(
                    id = "audit_${audit.id}",
                    category = NetraCategory.WATCHDOG,
                    eventType = "SELF_AUDIT_REPORT",
                    timestamp = audit.timestamp,
                    title = "System Self-Audit (Score: ${audit.healthScore}%)",
                    details = "Duration: ${audit.durationMs}ms | Healthy: ${audit.healthyServices}/${audit.totalServicesChecked} | Restarted: ${audit.restartedServices} | Failed: ${audit.failedServices}\nActions: ${audit.recoveryActions}",
                    source = "SelfAuditEngine",
                    isVerified = true
                )
            )
        }

        appActivities.forEach { app ->
            list.add(
                CategorizedLogItem(
                    id = "app_${app.id}",
                    category = NetraCategory.APPLICATION_ACTIVITY,
                    eventType = "APP_ACTIVITY_TRACK",
                    timestamp = app.timestamp,
                    title = "${app.appName} (${app.activityType})",
                    details = "Package: ${app.packageName}\nActivity: ${app.activityType}\nDetails: ${app.details}",
                    source = "AppConsumptionTracker",
                    isVerified = true
                )
            )
        }

        list.sortedByDescending { it.timestamp }
    }

    // Active Category Selection State
    var selectedCategory by remember { mutableStateOf<NetraCategory?>(null) }
    var selectedLogItem by remember { mutableStateOf<CategorizedLogItem?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    // Counts per category
    val countsByCategory = remember(allLogs) {
        val map = mutableMapOf<NetraCategory, Int>()
        NetraCategory.values().forEach { cat ->
            map[cat] = allLogs.count { it.category == cat }
        }
        map
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("netra_log_center_container")
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
                        },
                        modifier = Modifier.testTag("log_category_back_button")
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
                            Icons.Filled.Terminal,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                }

                Column {
                    Text(
                        text = selectedCategory?.let { "${it.title} Logs" } ?: "System Log Center",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = selectedCategory?.let { "${countsByCategory[it] ?: 0} events recorded" }
                            ?: "${allLogs.size} total verified events across ${NetraCategory.values().size} categories",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(
                onClick = {
                    val exportText = allLogs.joinToString("\n\n") { log ->
                        "[${Date(log.timestamp)}] [${log.category.title}] [${log.eventType}] ${log.title}\nDetails: ${log.details}\nSource: ${log.source}"
                    }
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Netra System Logs", exportText))
                    Toast.makeText(context, "All logs copied to clipboard (${allLogs.size} events)", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.testTag("export_all_logs_button")
            ) {
                Icon(Icons.Filled.Share, contentDescription = "Export All")
            }
        }

        // View Transition
        Crossfade(targetState = selectedCategory, label = "LogViewCrossfade") { activeCat ->
            if (activeCat == null) {
                // LEVEL 1: CATEGORY DASHBOARD
                LogCategoryDashboard(
                    countsByCategory = countsByCategory,
                    totalCount = allLogs.size,
                    onSelectCategory = { cat ->
                        selectedCategory = cat
                        searchQuery = ""
                    }
                )
            } else {
                // LEVEL 2: CATEGORY SPECIFIC LOG LIST
                val categoryLogs = remember(allLogs, activeCat, searchQuery) {
                    allLogs.filter { log ->
                        log.category == activeCat &&
                                (searchQuery.isBlank() ||
                                        log.title.contains(searchQuery, ignoreCase = true) ||
                                        log.details.contains(searchQuery, ignoreCase = true) ||
                                        log.eventType.contains(searchQuery, ignoreCase = true) ||
                                        log.source.contains(searchQuery, ignoreCase = true))
                    }
                }

                CategoryLogListView(
                    category = activeCat,
                    logs = categoryLogs,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    onItemClick = { item -> selectedLogItem = item },
                    onExportCategory = {
                        val exportText = categoryLogs.joinToString("\n\n") { log ->
                            "[${Date(log.timestamp)}] [${log.eventType}] ${log.title}\nDetails: ${log.details}\nSource: ${log.source}"
                        }
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Netra ${activeCat.title} Logs", exportText))
                        Toast.makeText(context, "${activeCat.title} logs copied to clipboard (${categoryLogs.size} items)", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    // LEVEL 3: LOG DETAIL DIALOG
    selectedLogItem?.let { log ->
        LogDetailDialog(
            item = log,
            onDismiss = { selectedLogItem = null },
            onCopy = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val text = "Timestamp: ${Date(log.timestamp)}\nCategory: ${log.category.title}\nEvent: ${log.eventType}\nTitle: ${log.title}\nDetails:\n${log.details}\nSource: ${log.source}\nStatus: VERIFIED"
                clipboard.setPrimaryClip(ClipData.newPlainText("Log Entry", text))
                Toast.makeText(context, "Log entry copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
fun LogCategoryDashboard(
    countsByCategory: Map<NetraCategory, Int>,
    totalCount: Int,
    onSelectCategory: (NetraCategory) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Top Overview Banner
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Authoritative Event Engine",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Discrete categorical event persistence & audit trail",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "$totalCount total",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        // Section Title
        item {
            Text(
                text = "LOG CATEGORIES",
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
            LogCategoryCard(
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
fun LogCategoryCard(
    category: NetraCategory,
    count: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("log_category_card_${category.id.lowercase()}"),
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
                        text = "${category.title} Logs",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Surface(
                        color = if (count > 0) category.color.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (count > 0) "$count logs" else "0 logs",
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
                contentDescription = "Open Category Logs",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun CategoryLogListView(
    category: NetraCategory,
    logs: List<CategorizedLogItem>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onItemClick: (CategorizedLogItem) -> Unit,
    onExportCategory: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy • HH:mm:ss.SSS", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // In-Category Search and Action Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Search inside ${category.title}...", fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .testTag("log_category_search_input"),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = category.color,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp),
                    singleLine = true
                )

                Button(
                    onClick = onExportCategory,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = category.color),
                    modifier = Modifier.height(46.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Icon(Icons.Filled.Share, contentDescription = "Export", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Export", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        // List
        if (logs.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.FolderOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No matching ${category.title} logs found" else "No logs recorded in ${category.title} yet",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(logs.distinctBy { it.id }, key = { it.id }) { log ->
                LogItemCard(
                    log = log,
                    dateFormat = dateFormat,
                    onClick = { onItemClick(log) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun LogItemCard(
    log: CategorizedLogItem,
    dateFormat: SimpleDateFormat,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("log_item_${log.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, log.category.color.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = log.category.color.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = log.eventType,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = log.category.color,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = Color(0xFF4CAF50).copy(alpha = 0.12f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "VERIFIED",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4CAF50),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = dateFormat.format(Date(log.timestamp)),
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = log.title,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = log.details.lines().firstOrNull() ?: log.details,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Source: ${log.source}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "Tap to view details →",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
fun LogDetailDialog(
    item: CategorizedLogItem,
    onDismiss: () -> Unit,
    onCopy: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Terminal, contentDescription = null, tint = item.category.color)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = item.eventType,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Category:", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Surface(color = item.category.color.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                        Text(item.category.title, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = item.category.color, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Event Title:", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text(item.title, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Status:", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text("VERIFIED (Authoritative)", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF4CAF50))
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Timestamp:", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text(dateFormat.format(Date(item.timestamp)), fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Originating Source:", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text(item.source, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Text("Raw Payload / Structured Details:", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = item.details,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onCopy) {
                    Text("Copy Details")
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}
