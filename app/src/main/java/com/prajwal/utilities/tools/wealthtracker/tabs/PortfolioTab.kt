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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.text.input.KeyboardType
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
    totalDividendsEarned: Double = 0.0,
    onToggleIncludeRetirement: () -> Unit,
    onSyncNow: () -> Unit,
    onSave: (AssetSnapshotEntity) -> Unit,
    onUpdate: (AssetSnapshotEntity) -> Unit,
    onDelete: (AssetSnapshotEntity) -> Unit
) {
    var snapshotToDelete by remember { mutableStateOf<AssetSnapshotEntity?>(null) }
    var snapshotToEdit by remember { mutableStateOf<AssetSnapshotEntity?>(null) }
    var showBackfillDialog by remember { mutableStateOf(false) }
    var justSaved by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val dateFmt = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }

    if (snapshotToEdit != null) {
        SnapshotEditDialog(
            initialSnapshot = snapshotToEdit,
            includeRetirement = includeRetirement,
            onDismiss = { snapshotToEdit = null },
            onSave = { updated ->
                onUpdate(updated)
                snapshotToEdit = null
            }
        )
    }

    if (showBackfillDialog) {
        SnapshotEditDialog(
            initialSnapshot = null,
            includeRetirement = includeRetirement,
            onDismiss = { showBackfillDialog = false },
            onSave = { newSnapshot ->
                onSave(newSnapshot)
                showBackfillDialog = false
            }
        )
    }

    // Calculate live totals from holdings
    val eqHoldings = holdings.filter { it.assetClass == "Equity" }
    val abHoldings = holdings.filter { it.assetClass == "Arbitrage" }
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
    val abInv = calcInv(abHoldings); val abCur = calcCur(abHoldings)
    val gdInv = calcInv(gdHoldings); val gdCur = calcCur(gdHoldings)
    val dbInv = calcInv(dbHoldings); val dbCur = calcCur(dbHoldings)
    val slInv = calcInv(slHoldings); val slCur = calcCur(slHoldings)
    val rtInv = calcInv(rtHoldings); val rtCur = calcCur(rtHoldings)
    val retInv = calcInv(retHoldings); val retCur = calcCur(retHoldings)

    val totalInv = eqInv + abInv + gdInv + dbInv + slInv + rtInv + if (includeRetirement) retInv else 0.0
    val totalCur = eqCur + abCur + gdCur + dbCur + slCur + rtCur + if (includeRetirement) retCur else 0.0
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
                    
                    if (totalDividendsEarned > 0) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                "Dividends (Last 12M)",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                MilestoneCalculator.formatInrExact(totalDividendsEarned),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
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

        // ── NIFTY 50 Market Index card ─────────────
        item {
            Nifty50Card(prices = nifty50Prices)
        }

        // ── Month-over-Month (MoM) / Snapshot Growth Delta ─────────────
        if (snapshots.size >= 2) {
            item {
                SnapshotDeltaCard(
                    latestSnapshot = snapshots[0],
                    previousSnapshot = snapshots[1],
                    includeRetirement = includeRetirement
                )
            }
        }

        // ── Day's P/L by Category Table ─────────────
        item {
            com.prajwal.utilities.tools.wealthtracker.components.DayPLBreakdownCard(
                holdings = holdings,
                includeRetirement = includeRetirement,
                initialExpanded = true
            )
        }

        // ── Asset Breakdown ───────────────────────────────────────────────
        item {
            val assets = mutableListOf(
                "Equity" to Pair(eqInv, eqCur),
                "Arbitrage" to Pair(abInv, abCur),
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
                            arbitrageInvested = abInv, arbitrageCurrent = abCur,
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Snapshot History (${snapshots.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    TextButton(onClick = { showBackfillDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Backfill", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            items(snapshots, key = { it.id }) { snapshot ->
                SnapshotHistoryCard(
                    snapshot = snapshot,
                    includeRetirement = includeRetirement,
                    onEdit = { snapshotToEdit = snapshot },
                    onDelete = { snapshotToDelete = snapshot }
                )
            }
        } else {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Snapshot History (0)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    TextButton(onClick = { showBackfillDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Backfill Past Data", style = MaterialTheme.typography.labelMedium)
                    }
                }
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
fun SnapshotDeltaCard(
    latestSnapshot: AssetSnapshotEntity,
    previousSnapshot: AssetSnapshotEntity,
    includeRetirement: Boolean,
    modifier: Modifier = Modifier
) {
    val dateFmt = remember { SimpleDateFormat("dd MMM yy", Locale.getDefault()) }
    val latestCur = latestSnapshot.getTotalCurrent(includeRetirement)
    val prevCur = previousSnapshot.getTotalCurrent(includeRetirement)
    val deltaCur = latestCur - prevCur
    val deltaCurPct = if (prevCur > 0) (deltaCur / prevCur) * 100 else 0.0

    val latestInv = latestSnapshot.getTotalInvested(includeRetirement)
    val prevInv = previousSnapshot.getTotalInvested(includeRetirement)
    val deltaInv = latestInv - prevInv // Capital Inflow

    val latestGain = latestCur - latestInv
    val prevGain = prevCur - prevInv
    val deltaMarketGain = latestGain - prevGain // Market appreciation

    val isPositive = deltaCur >= 0
    val netColor = if (isPositive) Color(0xFF16A34A) else MaterialTheme.colorScheme.error

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Growth vs Previous Snapshot",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Compared to ${dateFmt.format(Date(previousSnapshot.recordedAt))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = netColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        "${if (isPositive) "+" else "-"}${MilestoneCalculator.formatInrExact(abs(deltaCur))} (${"%.2f".format(abs(deltaCurPct))}%)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = netColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Fresh Inflow", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val inflowPositive = deltaInv >= 0
                    Text(
                        "${if (inflowPositive) "+" else "-"}${MilestoneCalculator.formatInrExact(abs(deltaInv))}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text("Market Returns", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val marketPositive = deltaMarketGain >= 0
                    val marketColor = if (marketPositive) Color(0xFF16A34A) else MaterialTheme.colorScheme.error
                    Text(
                        "${if (marketPositive) "+" else "-"}${MilestoneCalculator.formatInrExact(abs(deltaMarketGain))}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = marketColor
                    )
                }
            }
        }
    }
}

@Composable
fun SnapshotEditDialog(
    initialSnapshot: AssetSnapshotEntity? = null,
    includeRetirement: Boolean = true,
    onDismiss: () -> Unit,
    onSave: (AssetSnapshotEntity) -> Unit
) {
    val dateFmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val initialDate = remember(initialSnapshot) {
        dateFmt.format(Date(initialSnapshot?.recordedAt ?: System.currentTimeMillis()))
    }
    var dateStr by remember { mutableStateOf(initialDate) }

    fun fmtVal(v: Double) = if (v > 0) { if (v % 1.0 == 0.0) v.toLong().toString() else v.toString() } else ""

    var eqInv by remember { mutableStateOf(fmtVal(initialSnapshot?.equityInvested ?: 0.0)) }
    var eqCur by remember { mutableStateOf(fmtVal(initialSnapshot?.equityCurrent ?: 0.0)) }

    var abInv by remember { mutableStateOf(fmtVal(initialSnapshot?.arbitrageInvested ?: 0.0)) }
    var abCur by remember { mutableStateOf(fmtVal(initialSnapshot?.arbitrageCurrent ?: 0.0)) }

    var gdInv by remember { mutableStateOf(fmtVal(initialSnapshot?.goldInvested ?: 0.0)) }
    var gdCur by remember { mutableStateOf(fmtVal(initialSnapshot?.goldCurrent ?: 0.0)) }

    var dbInv by remember { mutableStateOf(fmtVal(initialSnapshot?.debtInvested ?: 0.0)) }
    var dbCur by remember { mutableStateOf(fmtVal(initialSnapshot?.debtCurrent ?: 0.0)) }

    var slInv by remember { mutableStateOf(fmtVal(initialSnapshot?.silverInvested ?: 0.0)) }
    var slCur by remember { mutableStateOf(fmtVal(initialSnapshot?.silverCurrent ?: 0.0)) }

    var rtInv by remember { mutableStateOf(fmtVal(initialSnapshot?.reitsInvested ?: 0.0)) }
    var rtCur by remember { mutableStateOf(fmtVal(initialSnapshot?.reitsCurrent ?: 0.0)) }

    var retInv by remember { mutableStateOf(fmtVal(initialSnapshot?.retirementInvested ?: 0.0)) }
    var retCur by remember { mutableStateOf(fmtVal(initialSnapshot?.retirementCurrent ?: 0.0)) }

    var dateError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialSnapshot != null) "Edit Snapshot" else "Backfill Historical Snapshot") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = dateStr,
                    onValueChange = {
                        dateStr = it
                        dateError = try {
                            dateFmt.parse(it) == null
                        } catch (e: Exception) {
                            true
                        }
                    },
                    label = { Text("Snapshot Date (YYYY-MM-DD)") },
                    singleLine = true,
                    isError = dateError,
                    supportingText = if (dateError) { { Text("Enter a valid date in YYYY-MM-DD format") } } else null,
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text("Asset Values (Invested & Current)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)

                AssetValueInputs(name = "Equity", inv = eqInv, cur = eqCur, onInvChange = { eqInv = it }, onCurChange = { eqCur = it })
                AssetValueInputs(name = "Arbitrage", inv = abInv, cur = abCur, onInvChange = { abInv = it }, onCurChange = { abCur = it })
                AssetValueInputs(name = "Gold", inv = gdInv, cur = gdCur, onInvChange = { gdInv = it }, onCurChange = { gdCur = it })
                AssetValueInputs(name = "Debt", inv = dbInv, cur = dbCur, onInvChange = { dbInv = it }, onCurChange = { dbCur = it })
                AssetValueInputs(name = "Silver", inv = slInv, cur = slCur, onInvChange = { slInv = it }, onCurChange = { slCur = it })
                AssetValueInputs(name = "REITs", inv = rtInv, cur = rtCur, onInvChange = { rtInv = it }, onCurChange = { rtCur = it })

                if (includeRetirement) {
                    AssetValueInputs(name = "Retirement (PF/NPS)", inv = retInv, cur = retCur, onInvChange = { retInv = it }, onCurChange = { retCur = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsedDate = try {
                        dateFmt.parse(dateStr)?.time ?: System.currentTimeMillis()
                    } catch (e: Exception) {
                        System.currentTimeMillis()
                    }

                    fun parseD(v: String) = v.toDoubleOrNull() ?: 0.0

                    val snapshot = AssetSnapshotEntity(
                        id = initialSnapshot?.id ?: 0,
                        recordedAt = parsedDate,
                        equityInvested = parseD(eqInv), equityCurrent = parseD(eqCur),
                        arbitrageInvested = parseD(abInv), arbitrageCurrent = parseD(abCur),
                        goldInvested = parseD(gdInv), goldCurrent = parseD(gdCur),
                        debtInvested = parseD(dbInv), debtCurrent = parseD(dbCur),
                        silverInvested = parseD(slInv), silverCurrent = parseD(slCur),
                        reitsInvested = parseD(rtInv), reitsCurrent = parseD(rtCur),
                        retirementInvested = parseD(retInv), retirementCurrent = parseD(retCur)
                    )
                    onSave(snapshot)
                },
                enabled = !dateError
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun AssetValueInputs(
    name: String,
    inv: String,
    cur: String,
    onInvChange: (String) -> Unit,
    onCurChange: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = inv,
                onValueChange = onInvChange,
                label = { Text("Invested (₹)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = cur,
                onValueChange = onCurChange,
                label = { Text("Current (₹)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f)
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
    onEdit: () -> Unit,
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
                    IconButton(onClick = onEdit) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit Snapshot",
                            tint = MaterialTheme.colorScheme.primary
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
                    "Arbitrage" to Pair(snapshot.arbitrageInvested, snapshot.arbitrageCurrent),
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
