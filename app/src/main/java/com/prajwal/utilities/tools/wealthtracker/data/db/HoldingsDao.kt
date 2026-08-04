package com.prajwal.utilities.tools.wealthtracker.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HoldingsDao {
    @Query("SELECT * FROM holdings")
    fun getAllHoldings(): Flow<List<HoldingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHolding(holding: HoldingEntity): Long

    @Update
    suspend fun updateHolding(holding: HoldingEntity)

    @Delete
    suspend fun deleteHolding(holding: HoldingEntity)

    // FIX #3: Updated query now also writes previousClosePrice.
    // The original query omitted it, causing Daily P/L to always show ₹0
    // for any holding updated via this targeted query path.
    @Query("UPDATE holdings SET latestPrice = :price, previousClosePrice = :previousClosePrice, lastUpdatedAt = :timestamp WHERE id = :id")
    suspend fun updatePrice(id: Int, price: Double, previousClosePrice: Double, timestamp: Long = System.currentTimeMillis())

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE holdingId = :holdingId ORDER BY timestamp ASC")
    fun getTransactionsForHolding(holdingId: Int): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY timestamp ASC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDividendEvents(events: List<DividendEventEntity>)

    @Query("SELECT * FROM dividend_events ORDER BY exDate DESC")
    fun getAllDividendEvents(): Flow<List<DividendEventEntity>>

    @Query("SELECT * FROM dividend_events WHERE symbol = :symbol ORDER BY exDate DESC")
    suspend fun getDividendEventsForSymbol(symbol: String): List<DividendEventEntity>

    @Query("DELETE FROM dividend_events WHERE symbol = :symbol")
    suspend fun deleteDividendEventsForSymbol(symbol: String)
}
