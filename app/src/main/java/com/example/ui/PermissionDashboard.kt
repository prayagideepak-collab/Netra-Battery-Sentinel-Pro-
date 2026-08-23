package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.PermissionItem
import com.example.data.PermissionRepository
import com.example.data.PermissionState
import com.example.viewmodel.BatteryViewModel

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PermissionControlCenter(
    viewModel: BatteryViewModel? = null,
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Trigger initial check on load
    LaunchedEffect(Unit) {
        PermissionRepository.recheckAllPermissions(context)
    }

    // Settings Return Synchronization
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                PermissionRepository.recheckAllPermissions(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val permissionsList by PermissionRepository.permissionsFlow.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") } // ALL, GRANTED, DENIED, SKIPPED
    var selectedPermissionItem by remember { mutableStateOf<PermissionItem?>(null) }

    // Setup permission launch mechanisms
    var activeRequestingId by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val currentId = activeRequestingId
        if (currentId != null) {
            if (isGranted) {
                PermissionRepository.setSkipped(context, currentId, false)
            }
            PermissionRepository.recheckAllPermissions(context)
            activeRequestingId = null
        }
    }

    val requestPermissionAction = { item: PermissionItem ->
        if (item.id == "USAGE_STATS") {
            PermissionRepository.openUsageAccessSettings(context)
        } else if (item.id == "WRITE_SETTINGS") {
            PermissionRepository.openModifySystemSettings(context)
        } else if (item.id == "DEVICE_ADMIN") {
            PermissionRepository.openDeviceAdminSettings(context)
        } else if (item.runtimePermission != null) {
            activeRequestingId = item.id
            permissionLauncher.launch(item.runtimePermission)
        } else if (item.androidIntent != null) {
            try {
                val intent = if (item.id == "BATTERY_OPTIMIZATION") {
                    Intent(item.androidIntent).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                } else {
                    Intent(item.androidIntent)
                }
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            } catch (e: Exception) {
                openAppSettingsFallback(context)
            }
        }
    }

    // Filter list
    val filteredList = remember(permissionsList, searchQuery, selectedFilter) {
        permissionsList.filter { item ->
            val matchesSearch = item.name.contains(searchQuery, ignoreCase = true) ||
                    item.category.contains(searchQuery, ignoreCase = true) ||
                    item.requiredBy.contains(searchQuery, ignoreCase = true)
            val matchesFilter = when (selectedFilter) {
                "GRANTED", "ALLOWED" -> item.state == PermissionState.GRANTED || item.state == PermissionState.LIMITED
                "DENIED", "ATTENTION" -> item.state == PermissionState.DENIED
                "SKIPPED" -> item.state == PermissionState.SKIPPED
                else -> true
            }
            matchesSearch && matchesFilter
        }
    }

    // Counters
    val allowedCount = permissionsList.count { item -> item.state == PermissionState.GRANTED || item.state == PermissionState.LIMITED }
    val attentionCount = permissionsList.count { item -> item.state == PermissionState.DENIED }
    val skippedCount = permissionsList.count { item -> item.state == PermissionState.SKIPPED }
    val requiredMissing = permissionsList.any { item -> item.isRequired && item.state == PermissionState.DENIED }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("permission_control_center_container")
    ) {
        // Top Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onNavigateBack != null) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("permission_back_button")
                    ) {
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
                            imageVector = Icons.Filled.AdminPanelSettings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Column {
                    Text(
                        text = "Permission Control Center",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Category-based access & privilege management",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (requiredMissing) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "ATTENTION REQUIRED",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Summary Overview Cards (Clickable to switch filter)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ClickableSummaryStatCard(
                        modifier = Modifier.weight(1f),
                        label = "Allowed",
                        count = allowedCount,
                        badge = "🟢",
                        color = Color(0xFF43A047),
                        isSelected = selectedFilter == "ALLOWED" || selectedFilter == "GRANTED",
                        onClick = {
                            selectedFilter = if (selectedFilter == "ALLOWED") "ALL" else "ALLOWED"
                        }
                    )
                    ClickableSummaryStatCard(
                        modifier = Modifier.weight(1f),
                        label = "Attention",
                        count = attentionCount,
                        badge = "🔴",
                        color = Color(0xFFD32F2F),
                        isSelected = selectedFilter == "ATTENTION" || selectedFilter == "DENIED",
                        onClick = {
                            selectedFilter = if (selectedFilter == "ATTENTION") "ALL" else "ATTENTION"
                        }
                    )
                    ClickableSummaryStatCard(
                        modifier = Modifier.weight(1f),
                        label = "Skipped",
                        count = skippedCount,
                        badge = "🔵",
                        color = Color(0xFF1976D2),
                        isSelected = selectedFilter == "SKIPPED",
                        onClick = {
                            selectedFilter = if (selectedFilter == "SKIPPED") "ALL" else "SKIPPED"
                        }
                    )
                }
            }

            // Search and Filter Badges
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search permissions...", fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1.4f)
                            .height(46.dp)
                            .testTag("permission_search_input"),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        ),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp),
                        singleLine = true
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("ALL", "ALLOWED", "ATTENTION", "SKIPPED").forEach { filter ->
                            val isSel = when (filter) {
                                "ALL" -> selectedFilter == "ALL"
                                "ALLOWED" -> selectedFilter == "ALLOWED" || selectedFilter == "GRANTED"
                                "ATTENTION" -> selectedFilter == "ATTENTION" || selectedFilter == "DENIED"
                                "SKIPPED" -> selectedFilter == "SKIPPED"
                                else -> false
                            }
                            FilterBadge(
                                label = filter,
                                isSelected = isSel,
                                onClick = { selectedFilter = filter }
                            )
                        }
                    }
                }
            }

            if (filteredList.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (searchQuery.isNotEmpty()) "No matching permissions found" else "No permissions in this category",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(filteredList.distinctBy { it.id }, key = { it.id }) { item ->
                    PermissionCard(
                        item = item,
                        onClick = { selectedPermissionItem = item },
                        onDirectAllow = {
                            requestPermissionAction(item)
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }

    // Detail Dialog
    selectedPermissionItem?.let { item ->
        PermissionDetailDialog(
            item = item,
            onDismiss = { selectedPermissionItem = null },
            onAllow = {
                selectedPermissionItem = null
                requestPermissionAction(item)
            },
            onSkip = {
                selectedPermissionItem = null
                PermissionRepository.setSkipped(context, item.id, true)
            },
            onAllowNow = {
                selectedPermissionItem = null
                PermissionRepository.setSkipped(context, item.id, false)
                requestPermissionAction(item)
            },
            onKeepSkipped = {
                selectedPermissionItem = null
            },
            onOpenSettings = {
                selectedPermissionItem = null
                if (item.id == "DEVICE_ADMIN") {
                    PermissionRepository.openDeviceAdminSettings(context)
                } else if (item.id == "USAGE_STATS") {
                    PermissionRepository.openUsageAccessSettings(context)
                } else if (item.id == "WRITE_SETTINGS") {
                    PermissionRepository.openModifySystemSettings(context)
                } else {
                    openAppSettingsFallback(context)
                }
            }
        )
    }
}

@Composable
fun ClickableSummaryStatCard(
    modifier: Modifier = Modifier,
    label: String,
    count: Int,
    badge: String,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clickable { onClick() }
            .testTag("stat_card_${label.lowercase()}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) color.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 0.5.dp,
            color = if (isSelected) color else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$badge $count",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun FilterBadge(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(0.5.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        modifier = Modifier
            .clickable { onClick() }
            .testTag("filter_badge_${label.lowercase()}")
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun PermissionCard(
    item: PermissionItem,
    onClick: () -> Unit,
    onDirectAllow: (() -> Unit)? = null
) {
    val (statusLabel, statusColor, statusIcon) = when (item.state) {
        PermissionState.GRANTED -> Triple("✓ Allowed", Color(0xFF43A047), Icons.Filled.CheckCircle)
        PermissionState.DENIED -> Triple("✕ Attention", Color(0xFFD32F2F), Icons.Filled.Cancel)
        PermissionState.SKIPPED -> Triple("• Skipped", Color(0xFF1976D2), Icons.Filled.Info)
        PermissionState.LIMITED -> Triple("! Limited", Color(0xFFF57C00), Icons.Filled.Warning)
        PermissionState.UNAVAILABLE -> Triple("— N/A", Color(0xFF78909C), Icons.Filled.RemoveCircle)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.5.dp, statusColor.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("permission_card_${item.id.lowercase()}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(statusColor.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = statusLabel,
                    tint = statusColor,
                    modifier = Modifier.size(20.dp)
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
                        text = item.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Surface(
                        color = statusColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = statusLabel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = "Why Netra needs it: " + item.reason,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 15.sp,
                    maxLines = 2
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Used for: ${item.requiredBy}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (item.state == PermissionState.DENIED && onDirectAllow != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.clickable { onDirectAllow() }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "Allow",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Icon(
                                    Icons.Filled.ChevronRight,
                                    contentDescription = "Allow",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    } else {
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = "Details",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionDetailDialog(
    item: PermissionItem,
    onDismiss: () -> Unit,
    onAllow: () -> Unit,
    onSkip: () -> Unit,
    onAllowNow: () -> Unit,
    onKeepSkipped: () -> Unit,
    onOpenSettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = item.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Current Status: ", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    val statusText = when (item.state) {
                        PermissionState.GRANTED -> "✓ Allowed (Active)"
                        PermissionState.DENIED -> "✕ Not Allowed (Attention)"
                        PermissionState.SKIPPED -> "• Skipped"
                        PermissionState.LIMITED -> "! Limited Access"
                        PermissionState.UNAVAILABLE -> "— Not Available"
                    }
                    val statusColor = when (item.state) {
                        PermissionState.GRANTED -> Color(0xFF43A047)
                        PermissionState.DENIED -> Color(0xFFD32F2F)
                        PermissionState.SKIPPED -> Color(0xFF1976D2)
                        PermissionState.LIMITED -> Color(0xFFF57C00)
                        PermissionState.UNAVAILABLE -> Color(0xFF78909C)
                    }
                    Text(statusText, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = statusColor)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                Text(
                    text = "Why Netra needs it:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = item.reason,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )

                Text(
                    text = "Used by:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = item.requiredBy,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "What happens if unavailable:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = item.ifDenied,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (item.state) {
                    PermissionState.GRANTED -> {
                        Button(
                            onClick = onOpenSettings,
                            modifier = Modifier.testTag("dialog_open_settings_button")
                        ) {
                            Text("Manage in Settings")
                        }
                    }
                    PermissionState.DENIED -> {
                        Button(
                            onClick = onAllow,
                            modifier = Modifier.testTag("dialog_allow_button")
                        ) {
                            Text("Allow")
                        }
                        OutlinedButton(
                            onClick = onSkip,
                            modifier = Modifier.testTag("dialog_skip_button")
                        ) {
                            Text("Skip")
                        }
                    }
                    PermissionState.SKIPPED -> {
                        Button(
                            onClick = onAllowNow,
                            modifier = Modifier.testTag("dialog_allow_now_button")
                        ) {
                            Text("Allow Now")
                        }
                        OutlinedButton(
                            onClick = onKeepSkipped,
                            modifier = Modifier.testTag("dialog_keep_skipped_button")
                        ) {
                            Text("Keep Skipped")
                        }
                    }
                    else -> {
                        Button(
                            onClick = onOpenSettings,
                            modifier = Modifier.testTag("dialog_settings_button")
                        ) {
                            Text("Open Settings")
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("dialog_close_button")
            ) {
                Text("Close")
            }
        }
    )
}

private fun openAppSettingsFallback(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (ex: Exception) {}
    }
}
