package com.prajwal.utilities.tools.wealthtracker.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prajwal.utilities.tools.wealthtracker.WealthTrackerViewModel
import com.prajwal.utilities.tools.wealthtracker.data.MilestoneCalculator
import com.prajwal.utilities.tools.wealthtracker.data.SyncItemReport
import com.prajwal.utilities.tools.wealthtracker.data.SyncLogEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class DiagnosticsTab(val label: String) {
    HOLDINGS("Holdings Status"),
    LOGCAT("Live Sync Logs")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncStatusScreen(
    viewModel: WealthTrackerViewModel,
    onNavigateBack: () -> Unit
) {
    val syncReport by viewModel.syncReport.collectAsState()
    val syncLogs by viewModel.syncLogs.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val syncProgress by viewModel.syncProgress.collectAsState()
    val syncMessage by viewModel.syncMessage.collectAsState()

    var selectedTab by remember { mutableStateOf(DiagnosticsTab.HOLDINGS) }
    var selectedFilter by remember { mutableStateOf("ALL") }

    val context = LocalContext.current
    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy, hh:mm:ss a", Locale.getDefault()) }
    val timeOnlyFmt = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Price Sync Diagnostics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Monitor live NAV & stock updates", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            viewModel.syncPricesNow { msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = !isSyncing,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Syncing...", style = MaterialTheme.typography.labelMedium)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Sync Now", modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Sync Now", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Live Sync Progress Bar (if in progress)
            if (isSyncing) {
                LinearProgressIndicator(
                    progress = { syncProgress },
                    modifier = Modifier.fillMaxWidth()
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = syncMessage,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }

            // Summary Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Last Sync Trigger",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                syncReport?.triggerSource ?: "No sync recorded yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (syncReport != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            ) {
                                val durationSec = (syncReport?.durationMs ?: 0L) / 1000.0
                                Text(
                                    text = String.format(Locale.getDefault(), "%.2f s", durationSec),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    if (syncReport != null) {
                        Text(
                            "Time: ${dateFmt.format(Date(syncReport?.timestamp ?: 0L))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        // Stats counters
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatBadge(
                                label = "Updated",
                                count = syncReport?.successCount ?: 0,
                                color = Color(0xFF16A34A)
                            )
                            StatBadge(
                                label = "Failed",
                                count = syncReport?.failedCount ?: 0,
                                color = if ((syncReport?.failedCount ?: 0) > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            StatBadge(
                                label = "Skipped",
                                count = syncReport?.skippedCount ?: 0,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Tab Selector
            TabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                DiagnosticsTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = {
                            val badgeCount = if (tab == DiagnosticsTab.HOLDINGS) {
                                syncReport?.items?.size ?: 0
                            } else {
                                syncLogs.size
                            }
                            Text("${tab.label} ($badgeCount)")
                        }
                    )
                }
            }

            when (selectedTab) {
                DiagnosticsTab.HOLDINGS -> {
                    HoldingsStatusTab(
                        items = syncReport?.items ?: emptyList(),
                        selectedFilter = selectedFilter,
                        onFilterChanged = { selectedFilter = it },
                        onRefreshHolding = { holdingId ->
                            viewModel.refreshHoldingPriceById(holdingId) { msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
                DiagnosticsTab.LOGCAT -> {
                    LogcatTab(
                        logs = syncLogs,
                        timeFmt = timeOnlyFmt,
                        onCopyLogs = {
                            val text = syncLogs.joinToString("\n") { log ->
                                "[${timeOnlyFmt.format(Date(log.timestamp))}] [${log.level}] ${log.message}"
                            }
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("WealthTrackerSyncLogs", text))
                            Toast.makeText(context, "Copied ${syncLogs.size} log lines to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        onClearLogs = {
                            viewModel.clearSyncLogs()
                            Toast.makeText(context, "Logs cleared", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatBadge(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HoldingsStatusTab(
    items: List<SyncItemReport>,
    selectedFilter: String,
    onFilterChanged: (String) -> Unit,
    onRefreshHolding: ((Int) -> Unit)? = null
) {
    val filteredItems = remember(items, selectedFilter) {
        when (selectedFilter) {
            "SUCCESS" -> items.filter { it.status == "SUCCESS" }
            "FAILED" -> items.filter { it.status == "FAILED" }
            "SKIPPED" -> items.filter { it.status == "SKIPPED" }
            else -> items
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Filter Chips Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedFilter == "ALL",
                onClick = { onFilterChanged("ALL") },
                label = { Text("All (${items.size})") }
            )
            FilterChip(
                selected = selectedFilter == "SUCCESS",
                onClick = { onFilterChanged("SUCCESS") },
                label = { Text("Updated (${items.count { it.status == "SUCCESS" }})") }
            )
            FilterChip(
                selected = selectedFilter == "FAILED",
                onClick = { onFilterChanged("FAILED") },
                label = { Text("Failed (${items.count { it.status == "FAILED" }})") }
            )
            FilterChip(
                selected = selectedFilter == "SKIPPED",
                onClick = { onFilterChanged("SKIPPED") },
                label = { Text("Skipped (${items.count { it.status == "SKIPPED" }})") }
            )
        }

        if (filteredItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (items.isEmpty()) "No holding sync data yet. Tap 'Sync Now' above to refresh."
                    else "No holdings match the selected filter.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredItems, key = { "${it.holdingId}_${it.identifier}" }) { item ->
                    HoldingSyncItemCard(
                        item = item,
                        onRefresh = if (onRefreshHolding != null) { { onRefreshHolding(item.holdingId) } } else null
                    )
                }
            }
        }
    }
}

@Composable
private fun HoldingSyncItemCard(
    item: SyncItemReport,
    onRefresh: (() -> Unit)? = null
) {
    val statusColor = when (item.status) {
        "SUCCESS" -> Color(0xFF16A34A)
        "FAILED" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val statusText = when (item.status) {
        "SUCCESS" -> "✓ Updated"
        "FAILED" -> "✗ Failed"
        else -> "— Skipped"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.holdingName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2
                    )
                    Text(
                        text = "${item.assetClass} • ${item.instrumentType} • ${item.identifier}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = statusColor.copy(alpha = 0.12f),
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    if (item.status != "SKIPPED" && onRefresh != null) {
                        IconButton(
                            onClick = onRefresh,
                            modifier = Modifier.size(32.dp).padding(start = 4.dp)
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Refresh holding",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            if (item.status == "SUCCESS") {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        val dateText = item.asOfDate?.let { "NAV Date: $it" } ?: "Live Price"
                        val sourceText = if (!item.source.isNullOrBlank()) " • via ${item.source}" else ""
                        Text(
                            text = "$dateText$sourceText",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "LTP: ${MilestoneCalculator.formatInrExact(item.price, showDecimals = true)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (item.previousClose > 0.0) {
                            Text(
                                text = "(Prev: ${MilestoneCalculator.formatInrExact(item.previousClose, showDecimals = true)})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else if (item.status == "FAILED" && !item.errorMessage.isNullOrBlank()) {
                Text(
                    text = "Reason: ${item.errorMessage}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else if (item.status == "SKIPPED" && !item.errorMessage.isNullOrBlank()) {
                Text(
                    text = item.errorMessage,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LogcatTab(
    logs: List<SyncLogEntry>,
    timeFmt: SimpleDateFormat,
    onCopyLogs: () -> Unit,
    onClearLogs: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Log Actions Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Console Logcat History",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onCopyLogs,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy Logs", modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Copy", style = MaterialTheme.typography.labelSmall)
                }

                OutlinedButton(
                    onClick = onClearLogs,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Logs", modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Clear", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No sync logs captured yet. Run a sync to see live log output.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            // Dark terminal box
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .background(Color(0xFF18181B), shape = RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(logs) { log ->
                        val logColor = when (log.level) {
                            "E" -> Color(0xFFF87171)
                            "W" -> Color(0xFFFBBF24)
                            "I" -> Color(0xFF4ADE80)
                            else -> Color(0xFF94A3B8)
                        }

                        val timeString = timeFmt.format(Date(log.timestamp))

                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "[$timeString] [${log.level}] ",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color(0xFF64748B)
                            )
                            Text(
                                text = log.message,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                ),
                                color = logColor
                            )
                        }
                    }
                }
            }
        }
    }
}
