package com.prajwal.utilities.tools.wealthtracker.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@Entity(tableName = "holdings")
@JsonClass(generateAdapter = true)
data class HoldingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val assetClass: String, // "Equity", "Gold", "Debt", "Silver", "REITs"
    val instrumentType: String, // "Stock", "MF", "SGB"
    val name: String, // Human readable name
    val identifier: String, // Ticker or Scheme Code
    val exchange: String?, // "NSE", "BSE", or null
    val unitsHeld: Double,
    val investedAmount: Double,
    val latestPrice: Double = 0.0,
    val previousClosePrice: Double = 0.0,
    val lastUpdatedAt: Long = System.currentTimeMillis(),
    val isManual: Boolean = false,
    val expenseRatio: Double = 0.0
)
