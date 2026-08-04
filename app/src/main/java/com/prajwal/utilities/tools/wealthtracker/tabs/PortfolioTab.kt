package com.prajwal.utilities.tools.wealthtracker.tabs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.prajwal.utilities.tools.wealthtracker.data.MilestoneCalculator
import com.prajwal.utilities.tools.wealthtracker.data.db.AssetSnapshotEntity
import com.prajwal.utilities.tools.wealthtracker.data.db.HoldingEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.prajwal.utilities.tools.wealthtracker.data.network.AssetPrices

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioTab(
    snapshots: List<AssetSnapshotEntity>,
    holdings: List<HoldingEntity>,
    transactions: List<com.prajwal.utilities.tools.wealthtracker.data.db.TransactionEntity>,
    isSyncing: Boolean,
    includeRetirement: Boolean,
    lastAutoSyncTimestamp: Long = 0L,
    nifty50Prices: AssetPrices? = null,
    onToggleIncludeRetirement: () -> Unit,
    onSyncNow: () -> Unit,
    onSave: (AssetSnapshotEntity) -> Unit,
    onDelete: (AssetSnapshotEntity) -> Unit
) {
    var snapshotToDelete by remember { mutableStateOf<AssetSnapshotEntity?>(null) }
    var justSaved by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val dateFmt = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }

    // Calculate live totals from holdings
    val eqHoldings = holdings.filter { it.assetClass == "Equity" }
    val gdHoldings = holdings.filter { it.assetClass == "Gold" }
    val dbHoldings = holdings.filter { it.assetClass == "Debt" }
    val slHoldings = holdings.filter { it.assetClass == "Silver" }
    val rtHoldings = holdings.filter { it.assetClass == "REITs" }
    val retHoldings = holdings.filter { it.assetClass == "Retirement" }

    fun calcInv(list: List<HoldingEntity>) = list.sumOf { it.investedAmount }
    fun calcCur(list: List<HoldingEntity>) = list.sumOf { 
        if (it.latestPrice > 0) it.unitsHeld * it.latestPrice else it.investedAmount 
    }

    val eqInv = calcInv(eqHoldings); val eqCur = calcCur(eqHoldings)
    val gdInv = calcInv(gdHoldings); val gdCur = calcCur(gdHoldings)
    val dbInv = calcInv(dbHoldings); val dbCur = calcCur(dbHoldings)
    val slInv = calcInv(slHoldings); val slCur = calcCur(slHoldings)
    val rtInv = calcInv(rtHoldings); val rtCur = calcCur(rtHoldings)
    val retInv = calcInv(retHoldings); val retCur = calcCur(retHoldings)

    val totalInv = eqInv + gdInv + dbInv + slInv + rtInv + if (includeRetirement) retInv else 0.0
    val totalCur = eqCur + gdCur + dbCur + slCur + rtCur + if (includeRetirement) retCur else 0.0
    val gain = totalCur - totalInv
    val gainPositive = gain >= 0

    val dailyGain = holdings.sumOf { if (it.previousClosePrice > 0) it.unitsHeld * (it.latestPrice - it.previousClosePrice) else 0.0 }
    val dailyPrevCloseTotal = holdings.sumOf { if (it.previousClosePrice > 0) it.unitsHeld * it.previousClosePrice else 0.0 }
    val dailyGainPct = if (dailyPrevCloseTotal > 0) (dailyGain / dailyPrevCloseTotal) * 100 else 0.0
    val dailyGainPositive = dailyGain >= 0

    // Only enable save if we have some investment and haven't just saved
    val canSave = totalInv > 0 && !justSaved

    snapshotToDelete?.let { snapshot ->
        AlertDialog(
            onDismissRequest = { snapshotToDelete = null },
            title = { Text("Delete Snapshot?") },
            text = {
                val fmt = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                Text("The snapshot from ${fmt.format(Date(snapshot.recordedAt))} will be permanently removed.")
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(snapshot)
                    snapshotToDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { snapshotToDelete = null }) { Text("Cancel") }
            }
        )
    }

    val pullToRefreshState = rememberPullToRefreshState()

    if (pullToRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            onSyncNow()
        }
    }

    LaunchedEffect(isSyncing) {
        if (isSyncing) {
            pullToRefreshState.startRefresh()
        } else {
            pullToRefreshState.endRefresh()
        }
    }

    Box(modifier = Modifier.fillMaxSize().nestedScroll(pullToRefreshState.nestedScrollConnection)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (lastAutoSyncTimestamp > 0) {
                    Text(
                        "Auto-refreshed: ${dateFmt.format(Date(lastAutoSyncTimestamp))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Spacer(Modifier.width(1.dp))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Include PF/NPS", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = includeRetirement,
                        onCheckedChange = { onToggleIncludeRetirement() },
                        modifier = Modifier.height(24.dp)
                    )
                }
            }
        }

        // ── Live summary card ─────────────
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (gainPositive)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.errorContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            "Total Invested",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            MilestoneCalculator.formatInrExact(totalInv),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            "Live Value",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            MilestoneCalculator.formatInrExact(totalCur),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                    Row(
                        modifier = Modifier.fillMaxWidth(), 
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Total P/L",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "${if (gainPositive) "+" else "-"}${MilestoneCalculator.formatInrExact(abs(gain))}",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (gainPositive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                            if (totalInv > 0) {
                                val pct = (gain / totalInv * 100)
                                Text(
                                    "${if (gainPositive) "+" else ""}${String.format("%.2f", pct)}%",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (gainPositive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Daily P/L card ─────────────
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (dailyGainPositive)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    else
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Today's P/L",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "${if (dailyGainPositive) "+" else "-"}${MilestoneCalculator.formatInrExact(abs(dailyGain), showDecimals = true)} (${String.format("%.2f", abs(dailyGainPct))}%)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (dailyGainPositive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // ── NIFTY 50 Market Index card ─────────────
        item {
            Nifty50Card(prices = nifty50Prices)
        }

        // ── Asset Breakdown ───────────────────────────────────────────────
        item {
            val assets = mutableListOf(
                "Equity" to Pair(eqInv, eqCur),
                "Gold" to Pair(gdInv, gdCur),
                "Debt" to Pair(dbInv, dbCur),
                "Silver" to Pair(slInv, slCur),
                "REITs" to Pair(rtInv, rtCur)
            )
            
            if (includeRetirement) {
                assets.add("Retirement" to Pair(retInv, retCur))
            }
            
            val filteredAssets = assets.filter { it.second.first > 0 || it.second.second > 0 }
            
            if (filteredAssets.isNotEmpty()) {
                AssetBreakdownCard(filteredAssets, totalCur)
            }
        }

        // ── Stale price warning (Fix #24) ─────────────────────────────────────
        val sixHoursMs = 6 * 60 * 60 * 1000L
        val now = System.currentTimeMillis()
        val hasStalePrices = !isSyncing && holdings.any { h ->
            !h.isManual && h.latestPrice > 0 && (now - h.lastUpdatedAt) > sixHoursMs
        }
        if (hasStalePrices) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFEF3C7)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⚠️", style = MaterialTheme.typography.titleMedium)
                        Column {
                            Text(
                                "Prices may be stale",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF92400E)
                            )
                            Text(
                                "Some holdings haven't synced in 6+ hours. Tap \"Sync Live\" above to update prices before saving.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF92400E)
                            )
                        }
                    }
                }
            }
        }

        // ── Save button ───────────────────────────────────────────────
        item {
            Button(
                onClick = {
                    onSave(
                        AssetSnapshotEntity(
                            equityInvested = eqInv, equityCurrent = eqCur,
                            goldInvested = gdInv, goldCurrent = gdCur,
                            debtInvested = dbInv, debtCurrent = dbCur,
                            silverInvested = slInv, silverCurrent = slCur,
                            reitsInvested = rtInv, reitsCurrent = rtCur,
                            retirementInvested = retInv, retirementCurrent = retCur
                        )
                    )
                    justSaved = true
                    coroutineScope.launch {
                        delay(2500)
                        justSaved = false
                    }
                },
                enabled = canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                AnimatedContent(
                    targetState = justSaved,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "save_button_content"
                ) { saved ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (saved) Icons.Default.Check else Icons.Default.Save,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (saved) "Snapshot Saved!" else "Save Live Snapshot",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }
            }
        }

        // ── Snapshot history ──────────────────────────────────────────
        if (snapshots.isNotEmpty()) {
            item {
                Text(
                    "Snapshot History (${snapshots.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(snapshots, key = { it.id }) { snapshot ->
                SnapshotHistoryCard(
                    snapshot = snapshot,
                    includeRetirement = includeRetirement,
                    onDelete = { snapshotToDelete = snapshot }
                )
            }
        }
    }

    if (pullToRefreshState.progress > 0f || pullToRefreshState.isRefreshing) {
        PullToRefreshContainer(
            state = pullToRefreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}
}

@Composable
private fun AssetBreakdownCard(assets: List<Pair<String, Pair<Double, Double>>>, totalCurrent: Double) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Asset Breakdown", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            
            assets.forEachIndexed { index, (name, values) ->
                val (inv, cur) = values
                val gain = cur - inv
                val gainPositive = gain >= 0
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        if (totalCurrent > 0) {
                            val allocationPct = (cur / totalCurrent) * 100
                            Text(
                                "${String.format("%.1f", allocationPct)}% of total",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            MilestoneCalculator.formatInrExact(cur),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Inv: ${MilestoneCalculator.formatInrExact(inv)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (inv > 0) {
                                val pct = (gain / inv) * 100
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "${if (gainPositive) "+" else ""}${String.format("%.1f", pct)}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (gainPositive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                if (index < assets.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                }
            }
        }
    }
}

@Composable
private fun SnapshotHistoryCard(
    snapshot: AssetSnapshotEntity,
    includeRetirement: Boolean,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val fmt = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    
    val totalInv = snapshot.getTotalInvested(includeRetirement)
    val totalCur = snapshot.getTotalCurrent(includeRetirement)
    val gain = totalCur - totalInv
    val gainPct = if (totalInv > 0) (gain / totalInv) * 100 else 0.0
    val gainPositive = gain >= 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        fmt.format(Date(snapshot.recordedAt)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        MilestoneCalculator.formatInrExact(totalCur),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${if (gainPositive) "+" else "-"}${MilestoneCalculator.formatInrExact(abs(gain))} (${String.format("%.1f", gainPct)}%)",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (gainPositive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                    )
                }
                Row {
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Expand"
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            if (expanded) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                val assets = listOf(
                    "Equity" to Pair(snapshot.equityInvested, snapshot.equityCurrent),
                    "Gold" to Pair(snapshot.goldInvested, snapshot.goldCurrent),
                    "Debt" to Pair(snapshot.debtInvested, snapshot.debtCurrent),
                    "Silver" to Pair(snapshot.silverInvested, snapshot.silverCurrent),
                    "REITs" to Pair(snapshot.reitsInvested, snapshot.reitsCurrent),
                    "Retirement" to Pair(snapshot.retirementInvested, snapshot.retirementCurrent)
                )
                assets.forEach { (name, values) ->
                    val (inv, cur) = values
                    if (inv > 0 || cur > 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            Text(
                                "${MilestoneCalculator.formatInrExact(inv)} → ${MilestoneCalculator.formatInrExact(cur)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Nifty50Card(
    prices: AssetPrices?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "📈",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "NIFTY 50",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Market Index",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (prices != null && prices.latestPrice > 0) {
                val dayChange = prices.latestPrice - prices.previousClosePrice
                val dayChangePct = if (prices.previousClosePrice > 0) (dayChange / prices.previousClosePrice) * 100 else 0.0
                val isPositive = dayChange >= 0
                val color = if (isPositive) Color(0xFF16A34A) else MaterialTheme.colorScheme.error
                val sign = if (isPositive) "+" else "-"

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "%.2f".format(prices.latestPrice),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "$sign${"%.2f".format(kotlin.math.abs(dayChange))} (${"%.2f".format(kotlin.math.abs(dayChangePct))}%)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = color
                    )
                }
            } else {
                Text(
                    "Tap Sync Live to update",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
