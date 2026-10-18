package com.prajwal.utilities.tools.wealthtracker.data

import com.prajwal.utilities.tools.wealthtracker.data.db.AssetSnapshotEntity
import com.prajwal.utilities.tools.wealthtracker.data.db.WealthDao
import com.prajwal.utilities.tools.wealthtracker.data.db.HoldingEntity
import com.prajwal.utilities.tools.wealthtracker.data.db.HoldingsDao
import kotlinx.coroutines.flow.Flow

class WealthRepository(
    private val dao: WealthDao,
    private val holdingsDao: HoldingsDao
) {

    /** All snapshots newest-first — used for the history list and diversification (latest). */
    fun getAllSnapshots(): Flow<List<AssetSnapshotEntity>> = dao.getAllSnapshots()

    /** All snapshots oldest-first — used for the growth-over-time line chart. */
    fun getAllSnapshotsChronological(): Flow<List<AssetSnapshotEntity>> = dao.getAllSnapshotsAscending()

    /** Latest single snapshot — used to pre-fill the portfolio input form. */
    suspend fun getLatestSnapshot(): AssetSnapshotEntity? = dao.getLatestSnapshot()

    /** Insert a new timestamped snapshot. */
    suspend fun insertSnapshot(snapshot: AssetSnapshotEntity) = dao.insertSnapshot(snapshot)

    /** Update an existing snapshot. */
    suspend fun updateSnapshot(snapshot: AssetSnapshotEntity) = dao.updateSnapshot(snapshot)

    /** Delete a snapshot (accidental entry removal). */
    suspend fun deleteSnapshot(snapshot: AssetSnapshotEntity) = dao.deleteSnapshot(snapshot)

    // --- Holdings ---

    fun getAllHoldings(): Flow<List<HoldingEntity>> = holdingsDao.getAllHoldings()

    suspend fun insertHolding(holding: HoldingEntity): Long = holdingsDao.insertHolding(holding)

    suspend fun updateHolding(holding: HoldingEntity) = holdingsDao.updateHolding(holding)

    suspend fun deleteHolding(holding: HoldingEntity) = holdingsDao.deleteHolding(holding)

    suspend fun getHoldingById(id: Int): HoldingEntity? = holdingsDao.getHoldingById(id)

    suspend fun updateHoldingPrice(id: Int, price: Double, previousClosePrice: Double) =
        holdingsDao.updatePrice(id, price, previousClosePrice)

    suspend fun updateHoldingExpenseRatio(id: Int, expenseRatio: Double) =
        holdingsDao.updateExpenseRatio(id, expenseRatio)

    suspend fun insertTransaction(transaction: com.prajwal.utilities.tools.wealthtracker.data.db.TransactionEntity) =
        holdingsDao.insertTransaction(transaction)

    suspend fun updateTransaction(transaction: com.prajwal.utilities.tools.wealthtracker.data.db.TransactionEntity) =
        holdingsDao.updateTransaction(transaction)

    suspend fun deleteTransaction(transaction: com.prajwal.utilities.tools.wealthtracker.data.db.TransactionEntity) =
        holdingsDao.deleteTransaction(transaction)

    fun getTransactionsForHolding(holdingId: Int): Flow<List<com.prajwal.utilities.tools.wealthtracker.data.db.TransactionEntity>> =
        holdingsDao.getTransactionsForHolding(holdingId)

    suspend fun getTransactionsForHoldingSync(holdingId: Int): List<com.prajwal.utilities.tools.wealthtracker.data.db.TransactionEntity> =
        holdingsDao.getTransactionsForHoldingSync(holdingId)

    fun getAllTransactions(): Flow<List<com.prajwal.utilities.tools.wealthtracker.data.db.TransactionEntity>> =
        holdingsDao.getAllTransactions()

    suspend fun insertDividendEvents(events: List<com.prajwal.utilities.tools.wealthtracker.data.db.DividendEventEntity>) =
        holdingsDao.insertDividendEvents(events)

    fun getAllDividendEvents(): Flow<List<com.prajwal.utilities.tools.wealthtracker.data.db.DividendEventEntity>> =
        holdingsDao.getAllDividendEvents()

    suspend fun getDividendEventsForSymbol(symbol: String): List<com.prajwal.utilities.tools.wealthtracker.data.db.DividendEventEntity> =
        holdingsDao.getDividendEventsForSymbol(symbol)

    suspend fun deleteDividendEventsForSymbol(symbol: String) =
        holdingsDao.deleteDividendEventsForSymbol(symbol)
}
