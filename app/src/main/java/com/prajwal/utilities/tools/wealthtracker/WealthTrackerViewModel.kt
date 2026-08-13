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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
            val prices = marketDataRepository.fetchStockPrice("^NSEI", null)
            if (prices != null) {
                _nifty50Prices.value = prices
            }
        }
    }

    val holdings: StateFlow<List<HoldingEntity>> = repository.getAllHoldings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val transactions: StateFlow<List<com.prajwal.utilities.tools.wealthtracker.data.db.TransactionEntity>> = repository.getAllTransactions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dividendEvents: StateFlow<List<com.prajwal.utilities.tools.wealthtracker.data.db.DividendEventEntity>> = repository.getAllDividendEvents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        fetchNifty50()
        viewModelScope.launch {
            combine(holdings, transactions) { h, t -> Pair(h, t) }.collect {
                calculateDividendsLocal()
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

    fun topUpHolding(holding: HoldingEntity, addedUnits: Double, addedInvested: Double) {
        viewModelScope.launch {
            val updatedHolding = holding.copy(
                unitsHeld = holding.unitsHeld + addedUnits,
                investedAmount = holding.investedAmount + addedInvested
            )
            repository.updateHolding(updatedHolding)
            
            if (addedUnits > 0) {
                val pricePerUnit = addedInvested / addedUnits
                repository.insertTransaction(
                    com.prajwal.utilities.tools.wealthtracker.data.db.TransactionEntity(
                        holdingId = holding.id,
                        timestamp = System.currentTimeMillis(),
                        units = addedUnits,
                        pricePerUnit = pricePerUnit,
                        type = com.prajwal.utilities.tools.wealthtracker.data.db.TransactionType.BUY
                    )
                )
            }
            
            syncPricesNow()
        }
    }

    fun sellHolding(holding: HoldingEntity, unitsToSell: Double, sellPrice: Double) {
        viewModelScope.launch {
            if (unitsToSell <= 0 || holding.unitsHeld <= 0) return@launch
            
            val actualUnitsToSell = kotlin.math.min(unitsToSell, holding.unitsHeld)
            
            // Proportionally reduce invested amount to keep average buy price the same
            val proportionSold = actualUnitsToSell / holding.unitsHeld
            val amountToReduce = holding.investedAmount * proportionSold
            
            val updatedHolding = holding.copy(
                unitsHeld = holding.unitsHeld - actualUnitsToSell,
                investedAmount = kotlin.math.max(0.0, holding.investedAmount - amountToReduce)
            )
            
            repository.updateHolding(updatedHolding)
            
            repository.insertTransaction(
                com.prajwal.utilities.tools.wealthtracker.data.db.TransactionEntity(
                    holdingId = holding.id,
                    timestamp = System.currentTimeMillis(),
                    units = actualUnitsToSell,
                    pricePerUnit = sellPrice,
                    type = com.prajwal.utilities.tools.wealthtracker.data.db.TransactionType.SELL
                )
            )
            
            syncPricesNow()
        }
    }

    fun updateHolding(holding: HoldingEntity) {
        viewModelScope.launch {
            repository.updateHolding(holding)
            syncPricesNow()
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

    private fun calculateDividendsLocal() {
        viewModelScope.launch(Dispatchers.IO) {
            val currentHoldings = holdings.value
            val cutoffMs = System.currentTimeMillis() - (365L * 24 * 60 * 60 * 1000L)
            var total = 0.0
            
            for (holding in currentHoldings) {
                val dbEvents = repository.getDividendEventsForSymbol(holding.identifier)
                val calculated = com.prajwal.utilities.tools.wealthtracker.data.DividendCalculator
                    .calculateDividendsEarned(holding.unitsHeld, dbEvents, cutoffMs)
                if (calculated > 0) {
                    Log.d("WealthTracker", "Dividends for ${holding.identifier}: events=${dbEvents.size}, units=${holding.unitsHeld}, calculated=$calculated")
                }
                total += calculated
            }
            Log.d("WealthTracker", "Total dividends earned (Last 12M): $total")
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
            try {
                fetchNifty50()
                val currentHoldings = holdings.value
                val allHoldingsSynced = currentHoldings.isNotEmpty() && currentHoldings.all { it.latestPrice > 0.0 && (it.instrumentType != "MF" || it.expenseRatio > 0.0) }
                var skippedCount = 0
                var anyExpenseRatioFetchFailed = false

                val totalHoldings = currentHoldings.size
                currentHoldings.forEachIndexed { index, holding ->
                    _syncProgress.value = if (totalHoldings > 0) index.toFloat() / totalHoldings else 0f
                    _syncMessage.value = "Updating ${holding.name}..."

                    if (holding.isManual) {
                        skippedCount++
                        return@forEachIndexed
                    }
                    
                    // For manual syncs, we bypass the MarketDataRepository.shouldSkipSync check
                    // The PortfolioSyncWorker will still respect it for background auto-syncs.

                    val prices = if (holding.instrumentType == "MF") {
                        val fetchedPrices = marketDataRepository.fetchMfNav(holding.identifier)
                        val expenseRatio = marketDataRepository.fetchMfExpenseRatio(holding.name, holding.identifier)
                        if (expenseRatio == null) {
                            anyExpenseRatioFetchFailed = true
                        } else if (expenseRatio > 0.0 && expenseRatio != holding.expenseRatio) {
                            repository.updateHolding(holding.copy(expenseRatio = expenseRatio))
                        }
                        fetchedPrices
                    } else {
                        marketDataRepository.fetchStockPrice(holding.identifier, holding.exchange)
                    }

                    if (prices != null) {
                        repository.updateHoldingPrice(
                            id = holding.id,
                            price = prices.latestPrice,
                            previousClosePrice = prices.previousClosePrice
                        )
                    }
                }
                
                _syncProgress.value = 1f
                _syncMessage.value = "Sync complete"
                _expenseRatioSyncFailed.value = anyExpenseRatioFetchFailed

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
                        val oldToNewIdMap = mutableMapOf<Int, Int>()
                        backup.holdings.forEach { oldHolding ->
                            val newId = repository.insertHolding(oldHolding.copy(id = 0)).toInt()
                            oldToNewIdMap[oldHolding.id] = newId
                        }
                        
                        backup.transactions.forEach { oldTx ->
                            val newHoldingId = oldToNewIdMap[oldTx.holdingId]
                            if (newHoldingId != null) {
                                repository.insertTransaction(oldTx.copy(id = 0, holdingId = newHoldingId))
                            }
                        }
                        
                        backup.snapshots.forEach { repository.insertSnapshot(it.copy(id = 0)) }
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
