package com.prajwal.utilities.tools.wealthtracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prajwal.utilities.tools.wealthtracker.data.CalculatorSettings
import com.prajwal.utilities.tools.wealthtracker.data.SortOption
import com.prajwal.utilities.tools.wealthtracker.data.WealthPreferences
import com.prajwal.utilities.tools.wealthtracker.data.WealthRepository
import com.prajwal.utilities.tools.wealthtracker.data.db.AssetSnapshotEntity
import com.prajwal.utilities.tools.wealthtracker.data.db.HoldingEntity
import com.prajwal.utilities.tools.wealthtracker.data.network.AssetSearchResult
import com.prajwal.utilities.tools.wealthtracker.data.network.MarketDataRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.withPermit
import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import com.prajwal.utilities.tools.wealthtracker.data.WealthBackup
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import com.prajwal.utilities.tools.wealthtracker.data.network.AssetPrices

class WealthTrackerViewModel(
    private val repository: WealthRepository,
    private val marketDataRepository: MarketDataRepository,
    private val prefs: WealthPreferences
) : ViewModel() {

    private val _nifty50Prices = MutableStateFlow<AssetPrices?>(null)
    val nifty50Prices: StateFlow<AssetPrices?> = _nifty50Prices.asStateFlow()

    private val _totalDividendsEarned = MutableStateFlow(0.0)
    val totalDividendsEarned: StateFlow<Double> = _totalDividendsEarned.asStateFlow()

    private fun fetchNifty50() {
        viewModelScope.launch(Dispatchers.IO) {
            val result = marketDataRepository.fetchStockPrice("^NSEI", null)
            if (result is com.prajwal.utilities.tools.wealthtracker.data.network.PriceFetchResult.Success) {
                _nifty50Prices.value = result.prices
            }
        }
    }

    val holdings: StateFlow<List<HoldingEntity>> = repository.getAllHoldings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val transactions: StateFlow<List<com.prajwal.utilities.tools.wealthtracker.data.db.TransactionEntity>> = repository.getAllTransactions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dividendEvents: StateFlow<List<com.prajwal.utilities.tools.wealthtracker.data.db.DividendEventEntity>> = repository.getAllDividendEvents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var dividendCalcJob: kotlinx.coroutines.Job? = null

    val syncReport: StateFlow<com.prajwal.utilities.tools.wealthtracker.data.SyncReport?> = com.prajwal.utilities.tools.wealthtracker.data.SyncStatusManager.latestReport
    val syncLogs: StateFlow<List<com.prajwal.utilities.tools.wealthtracker.data.SyncLogEntry>> = com.prajwal.utilities.tools.wealthtracker.data.SyncStatusManager.inMemoryLogs

    fun clearSyncLogs() {
        com.prajwal.utilities.tools.wealthtracker.data.SyncStatusManager.clearLogs()
    }

    init {
        com.prajwal.utilities.tools.wealthtracker.data.SyncStatusManager.init(prefs.context)
        fetchNifty50()
        seedMissingTransactionsForLegacyHoldings()
        viewModelScope.launch {
            @OptIn(FlowPreview::class)
            combine(holdings, transactions) { h, t -> Pair(h, t) }
                .debounce(1000)
                .collect { (currentHoldings, _) ->
                    calculateDividendsLocal(currentHoldings)
                }
        }
    }

    private fun seedMissingTransactionsForLegacyHoldings() {
        viewModelScope.launch(Dispatchers.IO) {
            val allHoldings = repository.getAllHoldings().first()
            for (holding in allHoldings) {
                if (holding.unitsHeld > 0) {
                    val txs = repository.getTransactionsForHoldingSync(holding.id)
                    if (txs.isEmpty()) {
                        val pricePerUnit = holding.investedAmount / holding.unitsHeld
                        repository.insertTransaction(
                            com.prajwal.utilities.tools.wealthtracker.data.db.TransactionEntity(
                                holdingId = holding.id,
                                timestamp = holding.lastUpdatedAt,
                                units = holding.unitsHeld,
                                pricePerUnit = pricePerUnit,
                                type = com.prajwal.utilities.tools.wealthtracker.data.db.TransactionType.BUY
                            )
                        )
                        Log.i("WealthTracker", "Auto-seeded initial BUY transaction for legacy holding '${holding.name}'")
                    }
                }
            }
        }
    }

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _expenseRatioSyncFailed = MutableStateFlow(false)
    val expenseRatioSyncFailed: StateFlow<Boolean> = _expenseRatioSyncFailed.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    private val _searchIsMf = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val searchResults: StateFlow<List<AssetSearchResult>> = _searchQuery
        .debounce(500)
        .flatMapLatest { query ->
            if (query.length < 2) {
                flowOf(emptyList())
            } else {
                flow {
                    emit(marketDataRepository.searchAsset(query, _searchIsMf.value))
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateSearchQuery(query: String, isMf: Boolean) {
        _searchIsMf.value = isMf
        _searchQuery.value = query
    }

    /** All snapshots newest-first (for history list + latest diversification). */
    val snapshots: StateFlow<List<AssetSnapshotEntity>> = repository.getAllSnapshots()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** All snapshots oldest-first (for growth chart). */
    val snapshotsChronological: StateFlow<List<AssetSnapshotEntity>> =
        repository.getAllSnapshotsChronological()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Combined calculator settings as a single object. */
    val calculatorSettings: StateFlow<CalculatorSettings> = combine(
        prefs.monthlyInvestment,
        prefs.annualStepupPercent,
        prefs.stepupMonth,
        prefs.expectedReturnPercent
    ) { monthly, stepup, stepupMonth, returns ->
        CalculatorSettings(monthly, stepup, stepupMonth, returns)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        CalculatorSettings()
    )

    val isBiometricEnabled: StateFlow<Boolean> = prefs.isBiometricEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    fun setAuthenticated(auth: Boolean) {
        _isAuthenticated.value = auth
    }

    val holdingsSortOption: StateFlow<SortOption> = prefs.holdingsSortOption
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SortOption.DEFAULT)

    val holdingsSortAscending: StateFlow<Boolean> = prefs.holdingsSortAscending
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun onSortChipClicked(option: SortOption) {
        viewModelScope.launch {
            val currentOption = prefs.holdingsSortOption.first()
            val defaultAsc = option == SortOption.ALPHABETICAL || option == SortOption.DEFAULT
            
            if (currentOption == option) {
                val currentAsc = prefs.holdingsSortAscending.first()
                if (currentAsc == defaultAsc) {
                    prefs.updateHoldingsSortAscending(!defaultAsc)
                } else {
                    prefs.updateHoldingsSortOption(SortOption.DEFAULT)
                    prefs.updateHoldingsSortAscending(true)
                }
            } else {
                prefs.updateHoldingsSortOption(option)
                prefs.updateHoldingsSortAscending(defaultAsc)
            }
        }
    }



    fun toggleBiometric() {
        viewModelScope.launch {
            prefs.updateBiometricEnabled(!isBiometricEnabled.value)
        }
    }

    val autoSnapshotEnabled: StateFlow<Boolean> = prefs.autoSnapshotEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val autoSnapshotDayOfMonth: StateFlow<Int> = prefs.autoSnapshotDayOfMonth
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)

    val includeRetirementInTotal: StateFlow<Boolean> = prefs.includeRetirementInTotal
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val lastAutoSyncTimestamp: StateFlow<Long> = prefs.lastAutoSyncTimestamp
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)
        
    val hideValues: StateFlow<Boolean> = prefs.hideValues
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun toggleHideValues() {
        viewModelScope.launch { prefs.updateHideValues(!hideValues.value) }
    }

    fun updateAutoSnapshotEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.updateAutoSnapshotEnabled(enabled) }
    }

    fun updateAutoSnapshotDayOfMonth(day: Int) {
        viewModelScope.launch { prefs.updateAutoSnapshotDayOfMonth(day) }
    }

    fun toggleIncludeRetirementInTotal() {
        viewModelScope.launch { prefs.updateIncludeRetirementInTotal(!includeRetirementInTotal.value) }
    }

    /** Tracks if the "pre-fill from latest snapshot" operation is loading. */
    private val _prefillSnapshot = MutableStateFlow<AssetSnapshotEntity?>(null)
    val prefillSnapshot: StateFlow<AssetSnapshotEntity?> = _prefillSnapshot.asStateFlow()

    /** Save a new portfolio snapshot (called when user taps "Save Snapshot"). */
    fun saveSnapshot(snapshot: AssetSnapshotEntity) {
        viewModelScope.launch {
            repository.insertSnapshot(snapshot)
        }
    }

    /** Update an existing portfolio snapshot in history. */
    fun updateSnapshot(snapshot: AssetSnapshotEntity) {
        viewModelScope.launch {
            repository.updateSnapshot(snapshot)
        }
    }

    /** Delete a snapshot from history. */
    fun deleteSnapshot(snapshot: AssetSnapshotEntity) {
        viewModelScope.launch {
            repository.deleteSnapshot(snapshot)
        }
    }

    /** Holdings management */
    fun addHolding(holding: HoldingEntity) {
        viewModelScope.launch {
            val id = repository.insertHolding(holding)
            
            // Log initial BUY transaction for Day Zero tracking
            if (holding.unitsHeld > 0) {
                val pricePerUnit = holding.investedAmount / holding.unitsHeld
                repository.insertTransaction(
                    com.prajwal.utilities.tools.wealthtracker.data.db.TransactionEntity(
                        holdingId = id.toInt(),
                        timestamp = System.currentTimeMillis(),
                        units = holding.unitsHeld,
                        pricePerUnit = pricePerUnit,
                        type = com.prajwal.utilities.tools.wealthtracker.data.db.TransactionType.BUY
                    )
                )
            }
            
            syncPricesNow()
        }
    }

    private suspend fun recalculateHoldingFromTransactions(holdingId: Int) {
        val holding = repository.getHoldingById(holdingId) ?: return
        val txs = repository.getTransactionsForHoldingSync(holdingId).sortedBy { it.timestamp }

        if (txs.isEmpty()) {
            repository.updateHolding(holding.copy(unitsHeld = 0.0, investedAmount = 0.0))
            return
        }

        var units = 0.0
        var invested = 0.0
        for (tx in txs) {
            if (tx.type == com.prajwal.utilities.tools.wealthtracker.data.db.TransactionType.BUY) {
                units += tx.units
                invested += (tx.units * tx.pricePerUnit)
            } else if (tx.type == com.prajwal.utilities.tools.wealthtracker.data.db.TransactionType.SELL) {
                if (units > 0) {
                    val avgCostPerUnit = invested / units
                    val unitsSold = kotlin.math.min(tx.units, units)
                    invested = kotlin.math.max(0.0, invested - (unitsSold * avgCostPerUnit))
                    units = kotlin.math.max(0.0, units - unitsSold)
                }
            }
        }
        repository.updateHolding(holding.copy(unitsHeld = units, investedAmount = invested))
    }

    fun addTransaction(holdingId: Int, transaction: com.prajwal.utilities.tools.wealthtracker.data.db.TransactionEntity) {
        viewModelScope.launch {
            val existingTxs = repository.getTransactionsForHoldingSync(holdingId)
            val holding = repository.getHoldingById(holdingId)
            if (existingTxs.isEmpty() && holding != null && holding.unitsHeld > 0) {
                // Seed initial holding state as a base BUY transaction
                val basePrice = holding.investedAmount / holding.unitsHeld
                repository.insertTransaction(
                    com.prajwal.utilities.tools.wealthtracker.data.db.TransactionEntity(
                        holdingId = holdingId,
                        timestamp = kotlin.math.min(holding.lastUpdatedAt, transaction.timestamp - 1000),
                        units = holding.unitsHeld,
                        pricePerUnit = basePrice,
                        type = com.prajwal.utilities.tools.wealthtracker.data.db.TransactionType.BUY
                    )
                )
            }
            repository.insertTransaction(transaction.copy(id = 0, holdingId = holdingId))
            recalculateHoldingFromTransactions(holdingId)
            syncPricesNow()
        }
    }

    fun updateTransaction(transaction: com.prajwal.utilities.tools.wealthtracker.data.db.TransactionEntity) {
        viewModelScope.launch {
            repository.updateTransaction(transaction)
            recalculateHoldingFromTransactions(transaction.holdingId)
            syncPricesNow()
        }
    }

    fun deleteTransaction(transaction: com.prajwal.utilities.tools.wealthtracker.data.db.TransactionEntity) {
        viewModelScope.launch {
            repository.deleteTransaction(transaction)
            recalculateHoldingFromTransactions(transaction.holdingId)
            syncPricesNow()
        }
    }

    fun topUpHolding(holding: HoldingEntity, addedUnits: Double, addedInvested: Double, timestamp: Long = System.currentTimeMillis()) {
        viewModelScope.launch {
            if (addedUnits > 0) {
                val existingTxs = repository.getTransactionsForHoldingSync(holding.id)
                if (existingTxs.isEmpty() && holding.unitsHeld > 0) {
                    val basePrice = holding.investedAmount / holding.unitsHeld
                    repository.insertTransaction(
                        com.prajwal.utilities.tools.wealthtracker.data.db.TransactionEntity(
                            holdingId = holding.id,
                            timestamp = kotlin.math.min(holding.lastUpdatedAt, timestamp - 1000),
                            units = holding.unitsHeld,
                            pricePerUnit = basePrice,
                            type = com.prajwal.utilities.tools.wealthtracker.data.db.TransactionType.BUY
                        )
                    )
                }

                val pricePerUnit = addedInvested / addedUnits
                repository.insertTransaction(
                    com.prajwal.utilities.tools.wealthtracker.data.db.TransactionEntity(
                        holdingId = holding.id,
                        timestamp = timestamp,
                        units = addedUnits,
                        pricePerUnit = pricePerUnit,
                        type = com.prajwal.utilities.tools.wealthtracker.data.db.TransactionType.BUY
                    )
                )
                recalculateHoldingFromTransactions(holding.id)
            }
            syncPricesNow()
        }
    }

    fun sellHolding(holding: HoldingEntity, unitsToSell: Double, sellPrice: Double, timestamp: Long = System.currentTimeMillis()) {
        viewModelScope.launch {
            if (unitsToSell <= 0 || holding.unitsHeld <= 0) return@launch
            val actualUnitsToSell = kotlin.math.min(unitsToSell, holding.unitsHeld)

            val existingTxs = repository.getTransactionsForHoldingSync(holding.id)
            if (existingTxs.isEmpty() && holding.unitsHeld > 0) {
                val basePrice = holding.investedAmount / holding.unitsHeld
                repository.insertTransaction(
                    com.prajwal.utilities.tools.wealthtracker.data.db.TransactionEntity(
                        holdingId = holding.id,
                        timestamp = kotlin.math.min(holding.lastUpdatedAt, timestamp - 1000),
                        units = holding.unitsHeld,
                        pricePerUnit = basePrice,
                        type = com.prajwal.utilities.tools.wealthtracker.data.db.TransactionType.BUY
                    )
                )
            }

            repository.insertTransaction(
                com.prajwal.utilities.tools.wealthtracker.data.db.TransactionEntity(
                    holdingId = holding.id,
                    timestamp = timestamp,
                    units = actualUnitsToSell,
                    pricePerUnit = sellPrice,
                    type = com.prajwal.utilities.tools.wealthtracker.data.db.TransactionType.SELL
                )
            )
            recalculateHoldingFromTransactions(holding.id)
            syncPricesNow()
        }
    }

    fun updateHolding(holding: HoldingEntity) {
        viewModelScope.launch {
            repository.updateHolding(holding)
            refreshHoldingPrice(holding)
        }
    }

    fun refreshHoldingPrice(holding: HoldingEntity, onComplete: ((String) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            if (holding.isManual) {
                withContext(Dispatchers.Main) {
                    onComplete?.invoke("Manual holdings cannot be refreshed from market")
                }
                return@launch
            }

            val syncMsg = "Refreshing price for '${holding.name}' (${holding.identifier})..."
            Log.d("WealthTrackerSync", syncMsg)
            com.prajwal.utilities.tools.wealthtracker.data.SyncStatusManager.log("D", syncMsg)

            val result = if (holding.instrumentType == "MF") {
                marketDataRepository.fetchMfNav(
                    schemeCode = holding.identifier,
                    fallbackFundName = holding.name,
                    existingPrice = holding.latestPrice
                )
            } else {
                marketDataRepository.fetchStockPrice(holding.identifier, holding.exchange)
            }

            when (result) {
                is com.prajwal.utilities.tools.wealthtracker.data.network.PriceFetchResult.Success -> {
                    val prices = result.prices
                    repository.updateHoldingPrice(
                        id = holding.id,
                        price = prices.latestPrice,
                        previousClosePrice = prices.previousClosePrice
                    )
                    if (prices.expenseRatio != null && prices.expenseRatio > 0.0) {
                        repository.updateHoldingExpenseRatio(holding.id, prices.expenseRatio)
                    }
                    val dateInfo = prices.asOfDate?.let { " [As of $it]" } ?: ""
                    val sourceInfo = prices.source?.let { " via $it" } ?: ""
                    val msg = "✓ Updated '${holding.name}' -> ₹${prices.latestPrice}$dateInfo$sourceInfo"
                    Log.i("WealthTrackerSync", msg)
                    com.prajwal.utilities.tools.wealthtracker.data.SyncStatusManager.log("I", msg)

                    com.prajwal.utilities.tools.wealthtracker.data.SyncStatusManager.updateSingleItemReport(
                        prefs.context,
                        com.prajwal.utilities.tools.wealthtracker.data.SyncItemReport(
                            holdingId = holding.id,
                            holdingName = holding.name,
                            identifier = holding.identifier,
                            instrumentType = holding.instrumentType,
                            assetClass = holding.assetClass,
                            status = "SUCCESS",
                            price = prices.latestPrice,
                            previousClose = prices.previousClosePrice,
                            asOfDate = prices.asOfDate,
                            source = prices.source
                        )
                    )

                    withContext(Dispatchers.Main) {
                        onComplete?.invoke("Updated '${holding.name}' -> ₹${prices.latestPrice}")
                    }
                }
                is com.prajwal.utilities.tools.wealthtracker.data.network.PriceFetchResult.Failure -> {
                    val failMsg = "✗ Failed to update '${holding.name}': ${result.errorMessage}"
                    Log.e("WealthTrackerSync", failMsg)
                    com.prajwal.utilities.tools.wealthtracker.data.SyncStatusManager.log("E", failMsg)
                    result.diagnosticLogs.forEach { d ->
                        com.prajwal.utilities.tools.wealthtracker.data.SyncStatusManager.log("D", "  └─ $d")
                    }

                    com.prajwal.utilities.tools.wealthtracker.data.SyncStatusManager.updateSingleItemReport(
                        prefs.context,
                        com.prajwal.utilities.tools.wealthtracker.data.SyncItemReport(
                            holdingId = holding.id,
                            holdingName = holding.name,
                            identifier = holding.identifier,
                            instrumentType = holding.instrumentType,
                            assetClass = holding.assetClass,
                            status = "FAILED",
                            price = holding.latestPrice,
                            previousClose = holding.previousClosePrice,
                            errorMessage = result.errorMessage
                        )
                    )

                    withContext(Dispatchers.Main) {
                        onComplete?.invoke("Failed: ${result.errorMessage}")
                    }
                }
            }
        }
    }

    fun refreshHoldingPriceById(holdingId: Int, onComplete: ((String) -> Unit)? = null) {
        viewModelScope.launch {
            val holding = repository.getHoldingById(holdingId)
            if (holding != null) {
                refreshHoldingPrice(holding, onComplete)
            } else {
                withContext(Dispatchers.Main) {
                    onComplete?.invoke("Holding not found")
                }
            }
        }
    }

    fun deleteHolding(holding: HoldingEntity) {
        viewModelScope.launch {
            repository.deleteHolding(holding)
        }
    }

    private val _syncProgress = MutableStateFlow(0f)
    val syncProgress: StateFlow<Float> = _syncProgress.asStateFlow()

    private val _syncMessage = MutableStateFlow("")
    val syncMessage: StateFlow<String> = _syncMessage.asStateFlow()

    fun syncDividends(onMessage: ((String) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            _isSyncing.value = true
            // Only equity-type instruments (Stock, ETF) can pay dividends.
            // Mutual Funds (Growth/Direct), Gold, Debt, and Retirement assets are excluded.
            val currentHoldings = holdings.value.filter {
                !it.isManual && it.instrumentType in setOf("Stock", "ETF")
            }
            var successCount = 0
            
            for (holding in currentHoldings) {
                val symbol = holding.identifier
                val exchange = holding.exchange
                
                try {
                    val newEvents = marketDataRepository.fetchDividends(symbol, exchange)
                    if (newEvents.isNotEmpty()) {
                        // Use upsert semantics (REPLACE on conflict) — no delete needed
                        repository.insertDividendEvents(newEvents)
                        successCount++
                    }
                } catch (e: Exception) {
                    Log.e("WealthTracker", "Failed to sync dividends for $symbol", e)
                }
            }
            
            calculateDividendsLocal()
            _isSyncing.value = false
            withContext(Dispatchers.Main) {
                onMessage?.invoke("Synced dividends for $successCount stocks")
            }
        }
    }

    private fun calculateDividendsLocal(currentHoldings: List<HoldingEntity> = holdings.value) {
        dividendCalcJob?.cancel()
        dividendCalcJob = viewModelScope.launch(Dispatchers.IO) {
            val cutoffMs = System.currentTimeMillis() - (365L * 24 * 60 * 60 * 1000L)
            var total = 0.0
            val eligibleHoldings = currentHoldings.filter { !it.isManual && it.instrumentType in setOf("Stock", "ETF") }
            
            for (holding in eligibleHoldings) {
                val dbEvents = repository.getDividendEventsForSymbol(holding.identifier)
                val calculated = com.prajwal.utilities.tools.wealthtracker.data.DividendCalculator
                    .calculateDividendsEarned(holding.unitsHeld, dbEvents, cutoffMs)
                total += calculated
            }
            Log.d("WealthTracker", "Recalculated 12M dividends: ₹$total across ${eligibleHoldings.size} dividend-eligible holdings")
            _totalDividendsEarned.value = total
        }
    }

    fun syncPricesNow(onMessage: ((String) -> Unit)? = null) {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            _syncProgress.value = 0f
            _syncMessage.value = "Starting sync..."
            val startTime = System.currentTimeMillis()
            val startMsg = "=== Starting Portfolio Price Sync (Manual/UI Trigger) ==="
            Log.i("WealthTrackerSync", startMsg)
            com.prajwal.utilities.tools.wealthtracker.data.SyncStatusManager.log("I", startMsg)
            try {
                fetchNifty50()
                val currentHoldings = repository.getAllHoldings().first()
                val totalHoldings = currentHoldings.size
                var anyExpenseRatioFetchFailed = false

                val semaphore = kotlinx.coroutines.sync.Semaphore(6)
                val completedCounter = java.util.concurrent.atomic.AtomicInteger(0)
                val successCounter = java.util.concurrent.atomic.AtomicInteger(0)
                val failedCounter = java.util.concurrent.atomic.AtomicInteger(0)
                val skippedCounter = java.util.concurrent.atomic.AtomicInteger(0)

                val deferredReports = currentHoldings.mapIndexed { index, holding ->
                    async(Dispatchers.IO) {
                        if (holding.isManual) {
                            skippedCounter.incrementAndGet()
                            val msg = "[${index + 1}/$totalHoldings] Skipping manual holding: '${holding.name}'"
                            Log.d("WealthTrackerSync", msg)
                            com.prajwal.utilities.tools.wealthtracker.data.SyncStatusManager.log("D", msg)
                            val done = completedCounter.incrementAndGet()
                            _syncProgress.value = if (totalHoldings > 0) done.toFloat() / totalHoldings else 0f
                            return@async com.prajwal.utilities.tools.wealthtracker.data.SyncItemReport(
                                holdingId = holding.id,
                                holdingName = holding.name,
                                identifier = holding.identifier,
                                instrumentType = holding.instrumentType,
                                assetClass = holding.assetClass,
                                status = "SKIPPED",
                                price = holding.latestPrice,
                                previousClose = holding.previousClosePrice,
                                errorMessage = "Manual holding"
                            )
                        }

                        semaphore.withPermit {
                            _syncMessage.value = "Updating ${holding.name}..."
                            val syncMsg = "[${index + 1}/$totalHoldings] Syncing ${holding.instrumentType}: '${holding.name}' (ID: ${holding.identifier})"
                            Log.d("WealthTrackerSync", syncMsg)
                            com.prajwal.utilities.tools.wealthtracker.data.SyncStatusManager.log("D", syncMsg)

                            val result = if (holding.instrumentType == "MF") {
                                marketDataRepository.fetchMfNav(
                                    schemeCode = holding.identifier,
                                    fallbackFundName = holding.name,
                                    existingPrice = holding.latestPrice
                                )
                            } else {
                                marketDataRepository.fetchStockPrice(holding.identifier, holding.exchange)
                            }

                            val done = completedCounter.incrementAndGet()
                            _syncProgress.value = if (totalHoldings > 0) done.toFloat() / totalHoldings else 0f

                            if (result is com.prajwal.utilities.tools.wealthtracker.data.network.PriceFetchResult.Success) {
                                val prices = result.prices
                                repository.updateHoldingPrice(
                                    id = holding.id,
                                    price = prices.latestPrice,
                                    previousClosePrice = prices.previousClosePrice
                                )
                                // If expense ratio was scraped, update holding's expense ratio if currently missing
                                if (prices.expenseRatio != null && prices.expenseRatio > 0.0 && holding.expenseRatio <= 0.0) {
                                    repository.updateHoldingExpenseRatio(holding.id, prices.expenseRatio)
                                    val expMsg = "Updated expense ratio for '${holding.name}': ${prices.expenseRatio}%"
                                    Log.d("WealthTrackerSync", expMsg)
                                    com.prajwal.utilities.tools.wealthtracker.data.SyncStatusManager.log("D", expMsg)
                                } else if (holding.instrumentType == "MF" && holding.expenseRatio <= 0.0) {
                                    // Fallback background fetch if expense ratio wasn't retrieved
                                    try {
                                        val expenseRatio = marketDataRepository.fetchMfExpenseRatio(holding.name, holding.identifier)
                                        if (expenseRatio == null) {
                                            anyExpenseRatioFetchFailed = true
                                        } else if (expenseRatio > 0.0 && expenseRatio != holding.expenseRatio) {
                                            repository.updateHoldingExpenseRatio(holding.id, expenseRatio)
                                            val expMsg = "Updated expense ratio for '${holding.name}': $expenseRatio%"
                                            Log.d("WealthTrackerSync", expMsg)
                                            com.prajwal.utilities.tools.wealthtracker.data.SyncStatusManager.log("D", expMsg)
                                        }
                                    } catch (e: Exception) {
                                        Log.w("WealthTrackerSync", "Failed to fetch expense ratio for '${holding.name}'", e)
                                        com.prajwal.utilities.tools.wealthtracker.data.SyncStatusManager.log("W", "Failed expense ratio: '${holding.name}': ${e.message}")
                                        anyExpenseRatioFetchFailed = true
                                    }
                                }
                                successCounter.incrementAndGet()
                                val dateInfo = prices.asOfDate?.let { " [As of $it]" } ?: ""
                                val sourceInfo = prices.source?.let { " via $it" } ?: ""
                                val successMsg = "✓ Updated '${holding.name}' -> Price: ₹${prices.latestPrice}$dateInfo$sourceInfo, Prev Close: ₹${prices.previousClosePrice}"
                                Log.i("WealthTrackerSync", successMsg)
                                com.prajwal.utilities.tools.wealthtracker.data.SyncStatusManager.log("I", successMsg)
                                val itemReport = com.prajwal.utilities.tools.wealthtracker.data.SyncItemReport(
                                    holdingId = holding.id,
                                    holdingName = holding.name,
                                    identifier = holding.identifier,
                                    instrumentType = holding.instrumentType,
                                    assetClass = holding.assetClass,
                                    status = "SUCCESS",
                                    price = prices.latestPrice,
                                    previousClose = prices.previousClosePrice,
                                    asOfDate = prices.asOfDate,
                                    source = prices.source
                                )
                                com.prajwal.utilities.tools.wealthtracker.data.SyncStatusManager.updateSingleItemReport(prefs.context, itemReport)
                                itemReport
                            } else if (result is com.prajwal.utilities.tools.wealthtracker.data.network.PriceFetchResult.Failure) {
                                failedCounter.incrementAndGet()
                                val failMsg = "✗ Failed to update prices for '${holding.name}' (Type: ${holding.instrumentType}, Identifier: ${holding.identifier}) - Reason: ${result.errorMessage}"
                                Log.e("WealthTrackerSync", failMsg)
                                com.prajwal.utilities.tools.wealthtracker.data.SyncStatusManager.log("E", failMsg)
                                result.diagnosticLogs.forEach { diag ->
                                    com.prajwal.utilities.tools.wealthtracker.data.SyncStatusManager.log("D", "  └─ $diag")
                                }
                                val itemReport = com.prajwal.utilities.tools.wealthtracker.data.SyncItemReport(
                                    holdingId = holding.id,
                                    holdingName = holding.name,
                                    identifier = holding.identifier,
                                    instrumentType = holding.instrumentType,
                                    assetClass = holding.assetClass,
                                    status = "FAILED",
                                    price = holding.latestPrice,
                                    previousClose = holding.previousClosePrice,
                                    errorMessage = result.errorMessage
                                )
                                com.prajwal.utilities.tools.wealthtracker.data.SyncStatusManager.updateSingleItemReport(prefs.context, itemReport)
                                itemReport
                            } else {
                                failedCounter.incrementAndGet()
                                val itemReport = com.prajwal.utilities.tools.wealthtracker.data.SyncItemReport(
                                    holdingId = holding.id,
                                    holdingName = holding.name,
                                    identifier = holding.identifier,
                                    instrumentType = holding.instrumentType,
                                    assetClass = holding.assetClass,
                                    status = "FAILED",
                                    price = holding.latestPrice,
                                    previousClose = holding.previousClosePrice,
                                    errorMessage = "Unknown fetch failure"
                                )
                                com.prajwal.utilities.tools.wealthtracker.data.SyncStatusManager.updateSingleItemReport(prefs.context, itemReport)
                                itemReport
                            }
                        }
                    }
                }

                val itemReports = deferredReports.awaitAll()
                
                _syncProgress.value = 1f
                _syncMessage.value = "Sync complete"
                _expenseRatioSyncFailed.value = anyExpenseRatioFetchFailed

                val elapsedTime = System.currentTimeMillis() - startTime
                val successCount = successCounter.get()
                val failedCount = failedCounter.get()
                val skippedCount = skippedCounter.get()

                val finishMsg = "=== Sync Complete in ${elapsedTime}ms: $successCount updated, $failedCount failed, $skippedCount skipped ==="
                Log.i("WealthTrackerSync", finishMsg)
                com.prajwal.utilities.tools.wealthtracker.data.SyncStatusManager.log("I", finishMsg)

                // Save full report
                com.prajwal.utilities.tools.wealthtracker.data.SyncStatusManager.saveReport(
                    prefs.context,
                    com.prajwal.utilities.tools.wealthtracker.data.SyncReport(
                        timestamp = System.currentTimeMillis(),
                        triggerSource = "Manual Sync",
                        durationMs = elapsedTime,
                        successCount = successCount,
                        failedCount = failedCount,
                        skippedCount = skippedCount,
                        items = itemReports
                    )
                )

                if (currentHoldings.isNotEmpty() && skippedCount == currentHoldings.size) {
                    withContext(Dispatchers.Main) {
                        onMessage?.invoke("Market is closed. Prices are up to date.")
                    }
                }
            } finally {
                val elapsedTime = System.currentTimeMillis() - startTime
                if (elapsedTime < 500) {
                    kotlinx.coroutines.delay(500 - elapsedTime)
                }
                _isSyncing.value = false
            }
        }
    }

    /** Load the latest snapshot to pre-fill the portfolio form. */
    fun loadPrefill() {
        viewModelScope.launch {
            _prefillSnapshot.value = repository.getLatestSnapshot()
        }
    }

    /** Update monthly SIP setting. */
    fun updateMonthlyInvestment(amount: Double) {
        viewModelScope.launch { prefs.updateMonthlyInvestment(amount) }
    }

    /** Update annual step-up % setting. */
    fun updateAnnualStepup(percent: Double) {
        viewModelScope.launch { prefs.updateAnnualStepup(percent) }
    }

    /** Update step-up month setting. */
    fun updateStepupMonth(month: Int) {
        viewModelScope.launch { prefs.updateStepupMonth(month) }
    }

    /** Update expected annual return % setting. */
    fun updateExpectedReturn(percent: Double) {
        viewModelScope.launch { prefs.updateExpectedReturn(percent) }
    }

    fun exportData(uri: Uri, contentResolver: ContentResolver, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentHoldings = holdings.value
                val currentSnapshots = snapshots.value
                val currentTransactions = transactions.value
                val backup = WealthBackup(currentHoldings, currentSnapshots, currentTransactions)
                val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
                val json = moshi.adapter(WealthBackup::class.java).toJson(backup)
                
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(json.toByteArray())
                }
                withContext(Dispatchers.Main) { onComplete(true) }
            } catch (e: Exception) {
                Log.e("WealthTracker", "Error exporting data", e)
                withContext(Dispatchers.Main) { onComplete(false) }
            }
        }
    }

    fun importData(uri: Uri, contentResolver: ContentResolver, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader().use { it.readText() }
                }
                if (json != null) {
                    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
                    val backup = moshi.adapter(WealthBackup::class.java).fromJson(json)
                    if (backup != null) {
                        val currentHoldings = repository.getAllHoldings().first()
                        val currentTransactions = repository.getAllTransactions().first()
                        val currentSnapshots = repository.getAllSnapshots().first()

                        val oldToNewIdMap = mutableMapOf<Int, Int>()

                        // 1. Merge / deduplicate Holdings
                        backup.holdings.forEach { oldHolding ->
                            val existing = currentHoldings.firstOrNull { h ->
                                if (oldHolding.isManual) {
                                    h.isManual && h.name.equals(oldHolding.name, ignoreCase = true) && h.assetClass == oldHolding.assetClass
                                } else {
                                    h.identifier.equals(oldHolding.identifier, ignoreCase = true) &&
                                        (h.exchange.equals(oldHolding.exchange, ignoreCase = true) || (h.exchange == null && oldHolding.exchange == null))
                                }
                            }

                            if (existing != null) {
                                oldToNewIdMap[oldHolding.id] = existing.id
                                // If existing had 0 units or backup has updated values, update
                                if (existing.unitsHeld <= 0 && oldHolding.unitsHeld > 0) {
                                    repository.updateHolding(existing.copy(
                                        unitsHeld = oldHolding.unitsHeld,
                                        investedAmount = oldHolding.investedAmount,
                                        expenseRatio = if (oldHolding.expenseRatio > 0) oldHolding.expenseRatio else existing.expenseRatio
                                    ))
                                }
                            } else {
                                val newId = repository.insertHolding(oldHolding.copy(id = 0)).toInt()
                                oldToNewIdMap[oldHolding.id] = newId
                            }
                        }

                        // 2. Merge / deduplicate Transactions
                        backup.transactions.forEach { oldTx ->
                            val targetHoldingId = oldToNewIdMap[oldTx.holdingId]
                            if (targetHoldingId != null) {
                                val txExists = currentTransactions.any { tx ->
                                    tx.holdingId == targetHoldingId &&
                                    tx.timestamp == oldTx.timestamp &&
                                    kotlin.math.abs(tx.units - oldTx.units) < 0.0001 &&
                                    tx.type == oldTx.type
                                }
                                if (!txExists) {
                                    repository.insertTransaction(oldTx.copy(id = 0, holdingId = targetHoldingId))
                                }
                            }
                        }

                        // 3. Merge / deduplicate Snapshots (check for same calendar day within 12h window)
                        backup.snapshots.forEach { oldSnapshot ->
                            val snapshotExists = currentSnapshots.any { s ->
                                kotlin.math.abs(s.recordedAt - oldSnapshot.recordedAt) < 12 * 60 * 60 * 1000L
                            }
                            if (!snapshotExists) {
                                repository.insertSnapshot(oldSnapshot.copy(id = 0))
                            }
                        }

                        syncPricesNow()
                        withContext(Dispatchers.Main) { onComplete(true) }
                        return@launch
                    }
                }
                withContext(Dispatchers.Main) { onComplete(false) }
            } catch (e: Exception) {
                Log.e("WealthTracker", "Error importing data", e)
                withContext(Dispatchers.Main) { onComplete(false) }
            }
        }
    }
}
