package com.prajwal.utilities.tools.wealthtracker

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import kotlinx.coroutines.launch
import com.prajwal.utilities.tools.wealthtracker.tabs.HoldingsTab
import com.prajwal.utilities.tools.wealthtracker.tabs.MilestonesTab
import com.prajwal.utilities.tools.wealthtracker.tabs.PortfolioTab
import com.prajwal.utilities.tools.wealthtracker.tabs.ReportsTab
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import android.net.Uri

private enum class WealthTab(val label: String, val icon: ImageVector) {
    PORTFOLIO("Portfolio", Icons.Default.Wallet),
    HOLDINGS("Holdings", Icons.Default.List),
    MILESTONES("Milestones", Icons.Default.EmojiEvents),
    REPORTS("Reports", Icons.Default.BarChart)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WealthTrackerScreen(
    viewModel: WealthTrackerViewModel,
    onNavigateBack: () -> Unit
) {
    val snapshots by viewModel.snapshots.collectAsState()
    val snapshotsChronological by viewModel.snapshotsChronological.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val calculatorSettings by viewModel.calculatorSettings.collectAsState()
    val holdings by viewModel.holdings.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val holdingsSortOption by viewModel.holdingsSortOption.collectAsState()
    val holdingsSortAscending by viewModel.holdingsSortAscending.collectAsState()
    val autoSnapshotEnabled by viewModel.autoSnapshotEnabled.collectAsState()
    val autoSnapshotDayOfMonth by viewModel.autoSnapshotDayOfMonth.collectAsState()
    val includeRetirementInTotal by viewModel.includeRetirementInTotal.collectAsState()
    val nifty50Prices by viewModel.nifty50Prices.collectAsState()
    var selectedTab by remember { mutableStateOf(WealthTab.PORTFOLIO) }

    val isBiometricEnabled by viewModel.isBiometricEnabled.collectAsState()
    val isAuthenticated by viewModel.isAuthenticated.collectAsState()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            viewModel.exportData(uri, context.contentResolver) { success ->
                val msg = if (success) "Export successful" else "Export failed"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // FIX #4: Store the picked URI so we can show a confirmation dialog before importing.
    // Immediately calling importData would silently create duplicates on repeated imports.
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pendingImportUri = uri  // Defer — show confirmation dialog first
        }
    }

    // Import confirmation dialog
    pendingImportUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text("Import Data") },
            text = {
                Text(
                    "This will add all holdings and snapshots from the backup file to your existing data.\n\n" +
                    "⚠\uFE0F Importing the same backup more than once will create duplicate entries."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingImportUri = null
                    viewModel.importData(uri, context.contentResolver) { success ->
                        val msg = if (success) "Import successful" else "Import failed"
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Import") }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportUri = null }) { Text("Cancel") }
            }
        )
    }

    var showAutoSnapshotDialog by remember { mutableStateOf(false) }

    if (showAutoSnapshotDialog) {
        var localEnabled by remember { mutableStateOf(autoSnapshotEnabled) }
        var localDay by remember { mutableStateOf(autoSnapshotDayOfMonth.toString()) }
        var inputError by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAutoSnapshotDialog = false },
            title = { Text("Auto Snapshot Settings") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        "Automatically take a portfolio snapshot once a month on your chosen day after a background sync completes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Enable Auto Snapshots")
                        Switch(checked = localEnabled, onCheckedChange = { localEnabled = it })
                    }
                    if (localEnabled) {
                        OutlinedTextField(
                            value = localDay,
                            onValueChange = { 
                                localDay = it
                                val day = it.toIntOrNull()
                                inputError = day == null || day !in 1..31
                            },
                            label = { Text("Day of Month (1-31)") },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                            isError = inputError,
                            supportingText = if (inputError) { { Text("Enter a valid day between 1 and 31") } } else null
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (!inputError) {
                            viewModel.updateAutoSnapshotEnabled(localEnabled)
                            localDay.toIntOrNull()?.let { viewModel.updateAutoSnapshotDayOfMonth(it) }
                            showAutoSnapshotDialog = false
                        }
                    },
                    enabled = !inputError
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showAutoSnapshotDialog = false }) { Text("Cancel") }
            }
        )
    }

    val currentIsAuthenticated by rememberUpdatedState(isAuthenticated)
    val currentIsBiometricEnabled by rememberUpdatedState(isBiometricEnabled)

    // Observe lifecycle to reset authentication on background and prompt on foreground
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.setAuthenticated(false)
            } else if (event == Lifecycle.Event.ON_RESUME) {
                if (currentIsBiometricEnabled && !currentIsAuthenticated) {
                    val activity = context as? FragmentActivity
                    if (activity != null && BiometricHelper.isBiometricAvailable(activity)) {
                        coroutineScope.launch {
                            val success = BiometricHelper.authenticate(
                                activity = activity,
                                title = "Unlock Wealth Tracker",
                                subtitle = "Verify your identity to access your portfolio"
                            )
                            if (success) {
                                viewModel.setAuthenticated(true)
                            }
                        }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (isBiometricEnabled && !isAuthenticated) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Wealth Tracker", fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Lock, contentDescription = "Locked", modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Wealth Tracker is locked", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = {
                        val activity = context as? FragmentActivity
                        if (activity != null) {
                            coroutineScope.launch {
                                val success = BiometricHelper.authenticate(
                                    activity = activity,
                                    title = "Unlock Wealth Tracker",
                                    subtitle = "Verify your identity to access your portfolio"
                                )
                                if (success) {
                                    viewModel.setAuthenticated(true)
                                }
                            }
                        }
                    }) {
                        Text("Unlock")
                    }
                }
            }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Wealth Tracker", fontWeight = FontWeight.Bold)
                        Text(
                            "Your personal portfolio hub",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (BiometricHelper.isBiometricAvailable(context)) {
                        IconButton(onClick = { viewModel.toggleBiometric() }) {
                            Icon(
                                imageVector = if (isBiometricEnabled) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = "Toggle Biometric Lock"
                            )
                        }
                    }

                    var showMenu by remember { mutableStateOf(false) }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More Options")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Export Data") },
                            onClick = {
                                showMenu = false
                                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", java.util.Locale.getDefault())
                                val fileName = "wealth_tracker_backup_${sdf.format(java.util.Date())}.json"
                                exportLauncher.launch(fileName)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Import Data") },
                            onClick = {
                                showMenu = false
                                importLauncher.launch(arrayOf("application/json", "*/*"))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Auto Snapshots") },
                            onClick = {
                                showMenu = false
                                showAutoSnapshotDialog = true
                            }
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        val syncProgress by viewModel.syncProgress.collectAsState()
        val syncMessage by viewModel.syncMessage.collectAsState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isSyncing) {
                LinearProgressIndicator(
                    progress = { syncProgress },
                    modifier = Modifier.fillMaxWidth(),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Text(
                    text = syncMessage,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // Tab row
            ScrollableTabRow(
                selectedTabIndex = selectedTab.ordinal,
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                WealthTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.label, fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal, style = MaterialTheme.typography.labelMedium) },
                        icon = { Icon(tab.icon, contentDescription = tab.label, modifier = Modifier) }
                    )
                }
            }

            // Tab content
            when (selectedTab) {
                WealthTab.HOLDINGS -> HoldingsTab(
                    holdings = holdings,
                    transactions = transactions,
                    isSyncing = isSyncing,
                    searchResults = searchResults,
                    sortOption = holdingsSortOption,
                    sortAscending = holdingsSortAscending,
                    nifty50Prices = nifty50Prices,
                    onSortOptionChanged = viewModel::onSortChipClicked,
                    onSearchQueryChanged = viewModel::updateSearchQuery,
                    onAddHolding = viewModel::addHolding,
                    onUpdateHolding = viewModel::updateHolding,
                    onTopUpHolding = viewModel::topUpHolding,
                    onDeleteHolding = viewModel::deleteHolding,
                    onSyncNow = {
                        viewModel.syncPricesNow { msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                WealthTab.PORTFOLIO -> PortfolioTab(
                    snapshots = snapshots,
                    holdings = holdings,
                    transactions = transactions,
                    isSyncing = isSyncing,
                    includeRetirement = includeRetirementInTotal,
                    lastAutoSyncTimestamp = viewModel.lastAutoSyncTimestamp.collectAsState().value,
                    nifty50Prices = nifty50Prices,
                    onToggleIncludeRetirement = viewModel::toggleIncludeRetirementInTotal,
                    onSyncNow = {
                        viewModel.syncPricesNow { msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onSave = { snapshot ->
                        viewModel.saveSnapshot(snapshot)
                    },
                    onDelete = viewModel::deleteSnapshot
                )

                WealthTab.REPORTS -> ReportsTab(
                    snapshots = snapshots,
                    snapshotsChronological = snapshotsChronological,
                    transactions = transactions,
                    includeRetirement = includeRetirementInTotal,
                    holdings = holdings,
                    expectedReturnPercent = calculatorSettings.expectedReturnPercent,
                    expenseRatioSyncFailed = viewModel.expenseRatioSyncFailed.collectAsState().value
                )

                WealthTab.MILESTONES -> MilestonesTab(
                    latestSnapshot = snapshots.firstOrNull(),
                    settings = calculatorSettings,
                    onMonthlyInvestmentChange = viewModel::updateMonthlyInvestment,
                    onStepupChange = viewModel::updateAnnualStepup,
                    onReturnChange = viewModel::updateExpectedReturn
                )
            }
        }
    }
}
