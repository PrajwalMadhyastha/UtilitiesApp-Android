package com.prajwal.utilities.tools.wealthtracker.tabs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.prajwal.utilities.tools.wealthtracker.components.HoldingLedgerSheet
import com.prajwal.utilities.tools.wealthtracker.data.MilestoneCalculator
import com.prajwal.utilities.tools.wealthtracker.data.SortOption
import com.prajwal.utilities.tools.wealthtracker.data.XirrCalculator
import com.prajwal.utilities.tools.wealthtracker.data.db.HoldingEntity
import com.prajwal.utilities.tools.wealthtracker.data.network.AssetSearchResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import com.prajwal.utilities.tools.wealthtracker.data.network.AssetPrices

@Composable
fun HoldingsTab(
    holdings: List<HoldingEntity>,
    transactions: List<com.prajwal.utilities.tools.wealthtracker.data.db.TransactionEntity>,
    isSyncing: Boolean,
    searchResults: List<AssetSearchResult>,
    sortOption: SortOption,
    sortAscending: Boolean,
    nifty50Prices: AssetPrices? = null,
    onSortOptionChanged: (SortOption) -> Unit,
    onSearchQueryChanged: (String, Boolean) -> Unit,
    onAddHolding: (HoldingEntity) -> Unit,
    onUpdateHolding: (HoldingEntity) -> Unit,
    onTopUpHolding: (HoldingEntity, Double, Double, Long) -> Unit,
    onSellHolding: (HoldingEntity, Double, Double, Long) -> Unit,
    onDeleteHolding: (HoldingEntity) -> Unit,
    onAddTransaction: (Int, com.prajwal.utilities.tools.wealthtracker.data.db.TransactionEntity) -> Unit,
    onUpdateTransaction: (com.prajwal.utilities.tools.wealthtracker.data.db.TransactionEntity) -> Unit,
    onDeleteTransaction: (com.prajwal.utilities.tools.wealthtracker.data.db.TransactionEntity) -> Unit,
    onSyncNow: () -> Unit,
    onRefreshHolding: ((HoldingEntity) -> Unit)? = null
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var showAddManualDialog by remember { mutableStateOf(false) }
    var holdingToEdit by remember { mutableStateOf<HoldingEntity?>(null) }
    var holdingToTopUp by remember { mutableStateOf<HoldingEntity?>(null) }
    var holdingToSell by remember { mutableStateOf<HoldingEntity?>(null) }
    var holdingForLedger by remember { mutableStateOf<HoldingEntity?>(null) }
    // FIX #18: Guard deletion behind a confirmation dialog (mirrors snapshot deletion in PortfolioTab)
    var holdingToDelete by remember { mutableStateOf<HoldingEntity?>(null) }

    holdingToDelete?.let { h ->
        AlertDialog(
            onDismissRequest = { holdingToDelete = null },
            title = { Text("Delete Holding?") },
            text = {
                Text(
                    "\"${h.name.ifBlank { h.identifier }}\" and all its data will be permanently removed. This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteHolding(h)
                    holdingToDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { holdingToDelete = null }) { Text("Cancel") }
            }
        )
    }

    holdingForLedger?.let { selectedHolding ->
        val currentHolding = holdings.firstOrNull { it.id == selectedHolding.id } ?: selectedHolding
        HoldingLedgerSheet(
            holding = currentHolding,
            transactions = transactions.filter { it.holdingId == currentHolding.id },
            onDismiss = { holdingForLedger = null },
            onAddTransaction = { tx -> onAddTransaction(currentHolding.id, tx) },
            onUpdateTransaction = onUpdateTransaction,
            onDeleteTransaction = onDeleteTransaction,
            onRefreshHolding = if (onRefreshHolding != null) { { onRefreshHolding(currentHolding) } } else null
        )
    }

    if (holdingToTopUp != null) {
        TopUpDialog(
            holding = holdingToTopUp!!,
            onDismiss = { holdingToTopUp = null },
            onSave = { addedUnits, addedInvested, timestamp ->
                onTopUpHolding(holdingToTopUp!!, addedUnits, addedInvested, timestamp)
                holdingToTopUp = null
            }
        )
    }

    if (holdingToSell != null) {
        SellDialog(
            holding = holdingToSell!!,
            onDismiss = { holdingToSell = null },
            onSave = { unitsToSell, sellPrice, timestamp ->
                onSellHolding(holdingToSell!!, unitsToSell, sellPrice, timestamp)
                holdingToSell = null
            }
        )
    }

    if (showAddManualDialog || (holdingToEdit != null && holdingToEdit!!.isManual)) {
        AddManualHoldingDialog(
            holdingToEdit = holdingToEdit,
            onDismiss = { showAddManualDialog = false; holdingToEdit = null },
            onSave = {
                if (holdingToEdit != null) onUpdateHolding(it) else onAddHolding(it)
                showAddManualDialog = false
                holdingToEdit = null
            }
        )
    } else if (showAddDialog || holdingToEdit != null) {
        AddHoldingDialog(
            holdingToEdit = holdingToEdit,
            searchResults = searchResults,
            onSearchQueryChanged = onSearchQueryChanged,
            onDismiss = { showAddDialog = false; holdingToEdit = null },
            onSave = {
                if (holdingToEdit != null) onUpdateHolding(it) else onAddHolding(it)
                showAddDialog = false
                holdingToEdit = null
            }
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header with Sync Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Your Holdings",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    var showSortMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            val sortOptionsList = SortOption.entries.filter { it != SortOption.DEFAULT }
                            sortOptionsList.forEach { option ->
                                DropdownMenuItem(
                                    text = { 
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                option.label, 
                                                fontWeight = if (option == sortOption) FontWeight.Bold else FontWeight.Normal,
                                                color = if (option == sortOption) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                            if (option == sortOption) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    imageVector = if (sortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                                    contentDescription = "Sort Direction",
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        onSortOptionChanged(option)
                                    }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onSyncNow,
                        enabled = !isSyncing
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Sync Now")
                            Spacer(Modifier.width(8.dp))
                            Text("Sync Live")
                        }
                    }
                }
            }

            Nifty50Card(prices = nifty50Prices, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

            // Filters
            val assetClassFilters = listOf("All", "Equity", "Arbitrage", "Debt", "Gold", "Silver", "REITs")
            val instrumentFilters = listOf("All", "Stocks", "Mutual Funds", "SGBs")
            
            var selectedAssetClass by remember { mutableStateOf("All") }
            var selectedInstrument by remember { mutableStateOf("All") }
            var showFilterSheet by remember { mutableStateOf(false) }
            
            // Active Filters Row
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    val activeCount = (if (selectedAssetClass != "All") 1 else 0) + (if (selectedInstrument != "All") 1 else 0)
                    FilterChip(
                        selected = activeCount > 0,
                        onClick = { showFilterSheet = true },
                        label = { Text("Filters") },
                        leadingIcon = { Icon(Icons.Default.Tune, contentDescription = "Filters", modifier = Modifier.size(16.dp)) },
                        trailingIcon = if (activeCount > 0) { { Badge { Text(activeCount.toString()) } } } else null
                    )
                }

                if (selectedAssetClass != "All") {
                    item {
                        InputChip(
                            selected = true,
                            onClick = { selectedAssetClass = "All" },
                            label = { Text(selectedAssetClass) },
                            trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp)) }
                        )
                    }
                }
                
                if (selectedInstrument != "All") {
                    item {
                        InputChip(
                            selected = true,
                            onClick = { selectedInstrument = "All" },
                            label = { Text(selectedInstrument) },
                            trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp)) }
                        )
                    }
                }
            }

            if (showFilterSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showFilterSheet = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                            .padding(bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        Text("Filter Portfolio", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Asset Class", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(assetClassFilters) { filter ->
                                    FilterChip(
                                        selected = selectedAssetClass == filter,
                                        onClick = { selectedAssetClass = filter },
                                        label = { Text(filter) }
                                    )
                                }
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Instrument Type", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(instrumentFilters) { filter ->
                                    FilterChip(
                                        selected = selectedInstrument == filter,
                                        onClick = { selectedInstrument = filter },
                                        label = { Text(filter) }
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = { showFilterSheet = false },
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            Text("Apply Filters", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }

            val filteredHoldings = remember(holdings, selectedAssetClass, selectedInstrument) {
                holdings.filter { holding ->
                    val matchAssetClass = when (selectedAssetClass) {
                        "All" -> true
                        else -> holding.assetClass == selectedAssetClass
                    }
                    val matchInstrument = when (selectedInstrument) {
                        "All" -> true
                        "Stocks" -> holding.instrumentType == "Stock"
                        "Mutual Funds" -> holding.instrumentType == "MF"
                        "SGBs" -> holding.instrumentType == "SGB"
                        else -> true
                    }
                    matchAssetClass && matchInstrument
                }
            }

            val groupedAndSortedHoldings = remember(filteredHoldings, sortOption, sortAscending) {
                filteredHoldings.groupBy { it.assetClass }.mapValues { (_, classHoldings) ->
                    when (sortOption) {
                        SortOption.DEFAULT -> classHoldings
                        SortOption.ALPHABETICAL -> if (sortAscending) classHoldings.sortedBy { it.name.lowercase() } else classHoldings.sortedByDescending { it.name.lowercase() }
                        SortOption.DAILY_PCT -> classHoldings.let { list ->
                            val sel: (HoldingEntity) -> Double = { h -> if (h.previousClosePrice > 0) (h.latestPrice - h.previousClosePrice) / h.previousClosePrice else 0.0 }
                            if (sortAscending) list.sortedBy(sel) else list.sortedByDescending(sel)
                        }
                        SortOption.LTP -> if (sortAscending) classHoldings.sortedBy { it.latestPrice } else classHoldings.sortedByDescending { it.latestPrice }
                        SortOption.PL_ABSOLUTE -> classHoldings.let { list ->
                            val sel: (HoldingEntity) -> Double = { h -> (h.unitsHeld * h.latestPrice) - h.investedAmount }
                            if (sortAscending) list.sortedBy(sel) else list.sortedByDescending(sel)
                        }
                        SortOption.PL_PERCENT -> classHoldings.let { list ->
                            val sel: (HoldingEntity) -> Double = { h -> if (h.investedAmount > 0) ((h.unitsHeld * h.latestPrice) - h.investedAmount) / h.investedAmount else 0.0 }
                            if (sortAscending) list.sortedBy(sel) else list.sortedByDescending(sel)
                        }
                        SortOption.INVESTED -> if (sortAscending) classHoldings.sortedBy { it.investedAmount } else classHoldings.sortedByDescending { it.investedAmount }
                    }
                }
            }

            if (filteredHoldings.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (holdings.isEmpty()) "No holdings added yet. Tap + to add." else "No holdings match this filter.", 
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 80.dp), // Space for FAB
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    groupedAndSortedHoldings.forEach { (assetClass, sortedClassHoldings) ->
                        item {
                            val hasDayPL = sortedClassHoldings.any { it.previousClosePrice > 0 }
                            val categoryDayGain = if (hasDayPL) {
                                sortedClassHoldings.sumOf { holding ->
                                    if (holding.previousClosePrice > 0) holding.unitsHeld * (holding.latestPrice - holding.previousClosePrice) else 0.0
                                }
                            } else 0.0
                            val categoryPrevCloseTotal = if (hasDayPL) {
                                sortedClassHoldings.sumOf { holding ->
                                    if (holding.previousClosePrice > 0) holding.unitsHeld * holding.previousClosePrice else 0.0
                                }
                            } else 0.0
                            val categoryDayGainPct = if (categoryPrevCloseTotal > 0) (categoryDayGain / categoryPrevCloseTotal) * 100 else 0.0

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = assetClass,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (hasDayPL) {
                                    val sign = if (categoryDayGain >= 0) "+" else "-"
                                    val color = if (categoryDayGain >= 0) Color(0xFF16A34A) else MaterialTheme.colorScheme.error
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Day's P/L: ",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "$sign${MilestoneCalculator.formatInrExact(kotlin.math.abs(categoryDayGain))} (${"%.2f".format(kotlin.math.abs(categoryDayGainPct))}%)",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = color,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }

                        items(sortedClassHoldings, key = { it.id }) { holding ->
                            HoldingCard(
                                holding = holding,
                                transactions = transactions.filter { it.holdingId == holding.id },
                                onTopUp = { holdingToTopUp = holding },
                                onSell = { holdingToSell = holding },
                                onEdit = { holdingToEdit = holding },
                                onDelete = { holdingToDelete = holding },
                                onOpenLedger = { holdingForLedger = holding },
                                onRefresh = if (onRefreshHolding != null) { { onRefreshHolding(holding) } } else null
                            )
                        }
                    }
                }
            }
        }

        var showAddMenu by remember { mutableStateOf(false) }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
        ) {
            FloatingActionButton(
                onClick = { showAddMenu = true }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Holding")
            }
            DropdownMenu(
                expanded = showAddMenu,
                onDismissRequest = { showAddMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Add Market Holding") },
                    onClick = { showAddMenu = false; showAddDialog = true }
                )
                DropdownMenuItem(
                    text = { Text("Add Manual Holding") },
                    onClick = { showAddMenu = false; showAddManualDialog = true }
                )
            }
        }
    }
}

@Composable
fun HoldingCard(
    holding: HoldingEntity, 
    transactions: List<com.prajwal.utilities.tools.wealthtracker.data.db.TransactionEntity>,
    onTopUp: () -> Unit, 
    onSell: () -> Unit,
    onEdit: () -> Unit, 
    onDelete: () -> Unit,
    onOpenLedger: () -> Unit,
    onRefresh: (() -> Unit)? = null
) {
    val currentVal = holding.unitsHeld * holding.latestPrice
    val gain = currentVal - holding.investedAmount
    val gainPct = if (holding.investedAmount > 0) (gain / holding.investedAmount) * 100 else 0.0
    
    val sdf = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenLedger),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        holding.name.ifBlank { "${holding.identifier}${holding.exchange?.let { ".$it" } ?: ""}" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    val feeText = if (holding.instrumentType == "MF" && holding.expenseRatio > 0.0) " • ${holding.expenseRatio}% TER" else ""
                    Text(
                        "${holding.instrumentType} • ${holding.unitsHeld} units$feeText",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row {
                    IconButton(onClick = onSell) {
                        Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Sell", tint = MaterialTheme.colorScheme.error)
                    }
                    IconButton(onClick = onTopUp) {
                        Icon(Icons.Default.AddCircleOutline, contentDescription = "Top Up", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Invested", style = MaterialTheme.typography.labelSmall)
                    Text(MilestoneCalculator.formatInrExact(holding.investedAmount), fontWeight = FontWeight.Medium)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("LTP (Live)", style = MaterialTheme.typography.labelSmall)
                    Text(
                        if (holding.latestPrice > 0) MilestoneCalculator.formatInrExact(holding.latestPrice, showDecimals = true) else "-",
                        fontWeight = FontWeight.Medium
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Current Value", style = MaterialTheme.typography.labelSmall)
                    Text(
                        if (holding.latestPrice > 0) MilestoneCalculator.formatInrExact(currentVal) else "-",
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            if (holding.latestPrice > 0) {
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(8.dp))
                // FIX #22: Show Avg Buy Price — a fundamental metric investors always need
                val avgBuyPrice = if (holding.unitsHeld > 0) holding.investedAmount / holding.unitsHeld else 0.0
                val dayGain = if (holding.previousClosePrice > 0)
                    holding.unitsHeld * (holding.latestPrice - holding.previousClosePrice) else 0.0
                val dayGainPct = if (holding.previousClosePrice > 0)
                    ((holding.latestPrice - holding.previousClosePrice) / holding.previousClosePrice) * 100 else 0.0
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Avg Buy Price", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            MilestoneCalculator.formatInrExact(avgBuyPrice, showDecimals = true),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (holding.previousClosePrice > 0) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Day's P&L", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "${if (dayGain >= 0) "+" else "-"}${MilestoneCalculator.formatInrExact(kotlin.math.abs(dayGain))} (${"%.2f".format(kotlin.math.abs(dayGainPct))}%)",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = if (dayGain >= 0) Color(0xFF16A34A) else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Last sync: ${sdf.format(Date(holding.lastUpdatedAt))}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!holding.isManual && onRefresh != null) {
                            Spacer(Modifier.width(4.dp))
                            IconButton(
                                onClick = onRefresh,
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Refresh holding price",
                                    modifier = Modifier.size(13.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "${if (gain >= 0) "+" else "-"}${MilestoneCalculator.formatInrExact(kotlin.math.abs(gain))} (${"%.2f".format(gainPct)}%)",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (gain >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Prices not yet synced — tap refresh to fetch live prices",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!holding.isManual && onRefresh != null) {
                        IconButton(
                            onClick = onRefresh,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Refresh holding price",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onOpenLedger,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ReceiptLong,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${transactions.size} trades • View Ledger",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                val holdingXirr = remember(transactions, currentVal) {
                    if (transactions.isNotEmpty() && currentVal > 0) {
                        XirrCalculator.calculateXirr(transactions, currentVal)
                    } else 0.0
                }
                if (holdingXirr != 0.0 && !holdingXirr.isNaN()) {
                    Text(
                        text = "XIRR: ${if (holdingXirr >= 0) "+" else ""}${"%.2f".format(holdingXirr * 100)}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (holdingXirr >= 0) Color(0xFF16A34A) else MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopUpDialog(
    holding: HoldingEntity,
    onDismiss: () -> Unit,
    onSave: (Double, Double, Long) -> Unit
) {
    val isMf = holding.instrumentType == "MF"
    var newUnitsStr by remember { mutableStateOf("") }
    var secondFieldStr by remember {
        mutableStateOf(
            if (!isMf && holding.latestPrice > 0) holding.latestPrice.toString() else ""
        )
    }
    var selectedDateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var saveAttempted by remember { mutableStateOf(false) }

    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val unitsVal = newUnitsStr.toDoubleOrNull() ?: 0.0
    val secondVal = secondFieldStr.toDoubleOrNull() ?: 0.0

    val unitsError = saveAttempted && unitsVal <= 0.0
    val secondFieldError = saveAttempted && secondVal <= 0.0

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        val nowCalendar = java.util.Calendar.getInstance()
                        val selectedCalendar = java.util.Calendar.getInstance().apply { timeInMillis = it }
                        selectedCalendar.set(java.util.Calendar.HOUR_OF_DAY, nowCalendar.get(java.util.Calendar.HOUR_OF_DAY))
                        selectedCalendar.set(java.util.Calendar.MINUTE, nowCalendar.get(java.util.Calendar.MINUTE))
                        selectedCalendar.set(java.util.Calendar.SECOND, nowCalendar.get(java.util.Calendar.SECOND))
                        selectedDateMillis = selectedCalendar.timeInMillis
                    }
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Top-Up ${holding.name.ifBlank { holding.identifier }}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    buildString {
                        append("Current Units: ${holding.unitsHeld}\nCurrent Invested: ${MilestoneCalculator.formatInrExact(holding.investedAmount)}")
                        if (holding.latestPrice > 0) {
                            append("\n${if (isMf) "Latest NAV" else "Market Price"}: ${MilestoneCalculator.formatInrExact(holding.latestPrice, showDecimals = true)}")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedCard(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Purchase Date", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(dateFmt.format(Date(selectedDateMillis)), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = "Select Date",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = newUnitsStr,
                    onValueChange = { newUnitsStr = it; saveAttempted = false },
                    label = { Text(if (isMf) "New Units Bought" else "Number of Units") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = unitsError,
                    supportingText = if (unitsError) {{ Text("Enter a valid number of units greater than 0") }} else null
                )
                OutlinedTextField(
                    value = secondFieldStr,
                    onValueChange = { secondFieldStr = it; saveAttempted = false },
                    label = { Text(if (isMf) "New Amount Invested (₹)" else "Price per Unit (₹)") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = secondFieldError,
                    supportingText = if (secondFieldError) {
                        { Text(if (isMf) "Enter the amount invested in ₹" else "Enter a valid price greater than 0") }
                    } else if (isMf && holding.latestPrice > 0) {
                        { Text("Latest NAV: ${MilestoneCalculator.formatInrExact(holding.latestPrice, showDecimals = true)}") }
                    } else if (!isMf && holding.latestPrice > 0 && secondFieldStr != holding.latestPrice.toString()) {
                        { Text("Market Price: ${MilestoneCalculator.formatInrExact(holding.latestPrice, showDecimals = true)}") }
                    } else null
                )

                if (unitsVal > 0 && secondVal > 0) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isMf) "Allotment NAV:" else "Total Investment:",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = if (isMf) {
                                    val allotmentNav = secondVal / unitsVal
                                    "₹${"%.4f".format(Locale.US, allotmentNav)}"
                                } else {
                                    val totalAmount = unitsVal * secondVal
                                    MilestoneCalculator.formatInrExact(totalAmount, showDecimals = true)
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF16A34A)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    saveAttempted = true
                    val units = newUnitsStr.toDoubleOrNull() ?: 0.0
                    val second = secondFieldStr.toDoubleOrNull() ?: 0.0
                    if (units > 0 && second > 0) {
                        val totalInvested = if (isMf) second else (units * second)
                        onSave(units, totalInvested, selectedDateMillis)
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SellDialog(
    holding: HoldingEntity,
    onDismiss: () -> Unit,
    onSave: (Double, Double, Long) -> Unit
) {
    var unitsStr by remember { mutableStateOf(holding.unitsHeld.toString()) }
    var priceStr by remember { mutableStateOf(if (holding.latestPrice > 0) holding.latestPrice.toString() else "") }
    var selectedDateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var saveAttempted by remember { mutableStateOf(false) }

    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val unitsError = saveAttempted && ((unitsStr.toDoubleOrNull() ?: 0.0) <= 0.0 || (unitsStr.toDoubleOrNull() ?: 0.0) > holding.unitsHeld)
    val priceError = saveAttempted && (priceStr.toDoubleOrNull() ?: 0.0) <= 0.0

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        val nowCalendar = java.util.Calendar.getInstance()
                        val selectedCalendar = java.util.Calendar.getInstance().apply { timeInMillis = it }
                        selectedCalendar.set(java.util.Calendar.HOUR_OF_DAY, nowCalendar.get(java.util.Calendar.HOUR_OF_DAY))
                        selectedCalendar.set(java.util.Calendar.MINUTE, nowCalendar.get(java.util.Calendar.MINUTE))
                        selectedCalendar.set(java.util.Calendar.SECOND, nowCalendar.get(java.util.Calendar.SECOND))
                        selectedDateMillis = selectedCalendar.timeInMillis
                    }
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sell ${holding.name.ifBlank { holding.identifier }}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "You own ${holding.unitsHeld} units.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedCard(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Sale Date", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(dateFmt.format(Date(selectedDateMillis)), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = "Select Date",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = unitsStr,
                    onValueChange = { unitsStr = it; saveAttempted = false },
                    label = { Text("Units to Sell") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    singleLine = true,
                    isError = unitsError,
                    supportingText = if (unitsError) {
                        { Text(if ((unitsStr.toDoubleOrNull() ?: 0.0) > holding.unitsHeld) "Cannot sell more than you own" else "Enter a valid number") }
                    } else null
                )
                OutlinedTextField(
                    value = priceStr,
                    onValueChange = { priceStr = it; saveAttempted = false },
                    label = { Text("Selling Price (per unit)") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    singleLine = true,
                    isError = priceError,
                    supportingText = if (priceError) {{ Text("Enter a valid price greater than 0") }} else null
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    saveAttempted = true
                    val units = unitsStr.toDoubleOrNull() ?: 0.0
                    val price = priceStr.toDoubleOrNull() ?: 0.0
                    if (units > 0 && units <= holding.unitsHeld && price > 0) {
                        onSave(units, price, selectedDateMillis)
                    }
                }
            ) {
                Text("Sell", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
