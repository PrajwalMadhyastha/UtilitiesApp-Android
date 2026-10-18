package com.prajwal.utilities.tools.wealthtracker.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

/**
 * A single timestamped snapshot of the user's entire portfolio.
 * Each row represents the state of all 5 asset classes at a point in time.
 * This enables the portfolio growth-over-time chart.
 */
@Entity(tableName = "asset_snapshots")
@JsonClass(generateAdapter = true)
data class AssetSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val recordedAt: Long = System.currentTimeMillis(), // epoch ms

    // Invested amounts (cumulative cost basis)
    val equityInvested: Double = 0.0,   // Mutual funds + stocks
    val arbitrageInvested: Double = 0.0, // Arbitrage mutual funds
    val goldInvested: Double = 0.0,
    val debtInvested: Double = 0.0,
    val silverInvested: Double = 0.0,
    val reitsInvested: Double = 0.0,    // Real Estate Investment Trusts
    val retirementInvested: Double = 0.0,

    // Current market value
    val equityCurrent: Double = 0.0,
    val arbitrageCurrent: Double = 0.0,
    val goldCurrent: Double = 0.0,
    val debtCurrent: Double = 0.0,
    val silverCurrent: Double = 0.0,
    val reitsCurrent: Double = 0.0,
    val retirementCurrent: Double = 0.0
) {
    fun getTotalInvested(includeRetirement: Boolean = true): Double {
        val base = equityInvested + arbitrageInvested + goldInvested + debtInvested + silverInvested + reitsInvested
        return if (includeRetirement) base + retirementInvested else base
    }

    fun getTotalCurrent(includeRetirement: Boolean = true): Double {
        val base = equityCurrent + arbitrageCurrent + goldCurrent + debtCurrent + silverCurrent + reitsCurrent
        return if (includeRetirement) base + retirementCurrent else base
    }

    val totalInvested: Double get() = getTotalInvested(true)
    val totalCurrent: Double get() = getTotalCurrent(true)
    val totalGainLoss: Double get() = totalCurrent - totalInvested
    val totalGainLossPct: Double get() = if (totalInvested > 0) (totalGainLoss / totalInvested) * 100 else 0.0
}
