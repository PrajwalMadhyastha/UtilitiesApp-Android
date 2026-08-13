package com.prajwal.utilities.tools.wealthtracker.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.prajwal.utilities.tools.wealthtracker.components.reports.*
import com.prajwal.utilities.tools.wealthtracker.data.db.AssetSnapshotEntity
import com.prajwal.utilities.tools.wealthtracker.data.toAssetClasses
import kotlin.math.pow

@Composable
fun ReportsTab(
    snapshots: List<AssetSnapshotEntity>,          // newest-first (for latest diversification)
    snapshotsChronological: List<AssetSnapshotEntity>,  // oldest-first (for growth chart)
    transactions: List<com.prajwal.utilities.tools.wealthtracker.data.db.TransactionEntity>,
    dividendEvents: List<com.prajwal.utilities.tools.wealthtracker.data.db.DividendEventEntity>,
    includeRetirement: Boolean,
    holdings: List<com.prajwal.utilities.tools.wealthtracker.data.db.HoldingEntity>,
    expectedReturnPercent: Double,
    expenseRatioSyncFailed: Boolean
) {
    if (snapshots.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ShowChart,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
                Text("No data yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Add your first snapshot in the Portfolio tab to see reports.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    val latest = snapshots.first()
    val oldest = snapshotsChronological.first()
    val allAssetClasses = latest.toAssetClasses(includeRetirement)

    val totalInvested = latest.getTotalInvested(includeRetirement)
    val totalCurrent = latest.getTotalCurrent(includeRetirement)

    // FIX #23: CAGR / annualised return calculation
    val durationMs = latest.recordedAt - oldest.recordedAt
    val durationYears = durationMs / (365.25 * 24.0 * 3600.0 * 1000.0)
    val absoluteReturnPct = if (totalInvested > 0)
        ((totalCurrent - totalInvested) / totalInvested) * 100.0
    else 0.0
    val cagr: Double? = if (durationYears >= 1.0 && totalInvested > 0)
        ((totalCurrent / totalInvested).pow(1.0 / durationYears) - 1.0) * 100.0
    else null

    // Toggle: show Invested or Current value breakdown in the donut
    var showInvested by remember { mutableStateOf(false) }
    val assetClasses = allAssetClasses.filter { if (showInvested) it.invested > 0 else it.current > 0 }
    val totalForPct = if (showInvested) totalInvested else totalCurrent

    // Filter for Wealth Drain Analyzer
    var fundFilter by remember { mutableStateOf(0) } // 0: All, 1: Active, 2: Passive
    val allMfsWithExpense = holdings.filter { it.instrumentType == "MF" && it.expenseRatio > 0.0 }
    val filteredMfsWithExpense = allMfsWithExpense.filter {
        when (fundFilter) {
            1 -> !it.isPassive
            2 -> it.isPassive
            else -> true
        }
    }

    val sections = listOf("Overview", "Growth", "Wealth Drain", "Dividends")
    var selectedSection by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = selectedSection,
            edgePadding = 16.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            sections.forEachIndexed { index, title ->
                Tab(
                    selected = selectedSection == index,
                    onClick = { selectedSection = index },
                    text = { Text(title) }
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        // ── Section 0: Overview (Returns & Diversification) ──────────────────
        if (selectedSection == 0) {
            item {
                Text("Returns", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            item {
                ReturnsSection(absoluteReturnPct = absoluteReturnPct, cagr = cagr)
            }

            item {
                Text(
                    "Diversification",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            item {
                DiversificationSection(
                    showInvested = showInvested,
                    onShowInvestedChange = { showInvested = it },
                    assetClasses = assetClasses,
                    totalForPct = totalForPct
                )
            }
        }

        // ── Section 1: Portfolio Growth Chart ─────────────────────────
        if (selectedSection == 1) {
            item {
                Text(
                    "Portfolio Growth",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Invested (dashed) vs Current Value (solid) — like run-rate vs required rate",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                GrowthChartSection(
                    snapshotsChronological = snapshotsChronological,
                    includeRetirement = includeRetirement
                )
            }
        }

        // ── Section 2: Wealth Drain Analyzer ──────────────────────────────────
        if (selectedSection == 2 && allMfsWithExpense.isNotEmpty()) {
            item {
                Text(
                    "Wealth Drain Analyzer",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "10-year projected fee impact on current Mutual Funds based on ${String.format("%.1f", expectedReturnPercent)}% assumed return",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                WealthDrainSection(
                    expenseRatioSyncFailed = expenseRatioSyncFailed,
                    fundFilter = fundFilter,
                    onFundFilterChange = { fundFilter = it },
                    filteredMfsWithExpense = filteredMfsWithExpense,
                    expectedReturnPercent = expectedReturnPercent
                )
            }
        } else if (selectedSection == 2) {
            item {
                Box(modifier = Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "No Mutual Funds with expense ratios found.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ── Section 3: Dividend Breakdown ──────────────────────────────────
        if (selectedSection == 3) {
            item {
                Text(
                    "Dividend Breakdown",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Dividends earned based on current holdings",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                DividendsSection(holdings = holdings, dividendEvents = dividendEvents)
            }
        }
    }
}
}

