package com.prajwal.utilities.tools.wealthtracker.data

import com.prajwal.utilities.tools.wealthtracker.data.db.HoldingEntity
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DailyCategorySummary(
    val lastUpdatedTimestamp: Long = 0L,
    val categories: List<DailyCategoryItem> = emptyList()
) {
    companion object {
        fun fromHoldings(
            holdings: List<HoldingEntity>,
            includeRetirement: Boolean = true,
            timestamp: Long = System.currentTimeMillis()
        ): DailyCategorySummary {
            val list = mutableListOf<DailyCategoryItem>()

            // 1. Equity Stocks & ETFs
            val equityStocks = holdings.filter { it.assetClass == "Equity" && it.instrumentType in setOf("Stock", "ETF") }
            if (equityStocks.isNotEmpty()) {
                list.add(computeItem("equity_stocks", "Equity Stocks", equityStocks))
            }

            // 2. Equity Mutual Funds
            val equityMfs = holdings.filter { it.assetClass == "Equity" && it.instrumentType == "MF" }
            if (equityMfs.isNotEmpty()) {
                list.add(computeItem("equity_mf", "Equity Mutual Funds", equityMfs))
            }

            // 3. Arbitrage Funds
            val arbitrageHoldings = holdings.filter { it.assetClass == "Arbitrage" }
            if (arbitrageHoldings.isNotEmpty()) {
                list.add(computeItem("arbitrage", "Arbitrage Funds", arbitrageHoldings))
            }

            // 4. Debt (Funds / Bonds)
            val debtHoldings = holdings.filter { it.assetClass == "Debt" }
            if (debtHoldings.isNotEmpty()) {
                list.add(computeItem("debt", "Debt", debtHoldings))
            }

            // 5. Gold (SGBs / Gold ETFs / Gold Funds)
            val goldHoldings = holdings.filter { it.assetClass == "Gold" }
            if (goldHoldings.isNotEmpty()) {
                list.add(computeItem("gold", "Gold", goldHoldings))
            }

            // 6. Silver (Silver ETFs / Funds)
            val silverHoldings = holdings.filter { it.assetClass == "Silver" }
            if (silverHoldings.isNotEmpty()) {
                list.add(computeItem("silver", "Silver", silverHoldings))
            }

            // 7. REITs
            val reitsHoldings = holdings.filter { it.assetClass == "REITs" }
            if (reitsHoldings.isNotEmpty()) {
                list.add(computeItem("reits", "REITs", reitsHoldings))
            }

            // 8. Retirement (PF / NPS / Other)
            if (includeRetirement) {
                val retHoldings = holdings.filter { it.assetClass == "Retirement" }
                if (retHoldings.isNotEmpty()) {
                    list.add(computeItem("retirement", "Retirement (PF/NPS)", retHoldings))
                }
            }

            // 9. Any other custom category
            val knownClasses = setOf("Equity", "Arbitrage", "Debt", "Gold", "Silver", "REITs", "Retirement")
            val otherHoldings = holdings.filter { it.assetClass !in knownClasses }
            if (otherHoldings.isNotEmpty()) {
                list.add(computeItem("other", "Other", otherHoldings))
            }

            return DailyCategorySummary(
                lastUpdatedTimestamp = timestamp,
                categories = list
            )
        }

        private fun computeItem(
            key: String,
            name: String,
            holdings: List<HoldingEntity>
        ): DailyCategoryItem {
            val count = holdings.size
            val curVal = holdings.sumOf { if (it.latestPrice > 0) it.unitsHeld * it.latestPrice else it.investedAmount }
            val dayGain = holdings.sumOf {
                if (it.previousClosePrice > 0) it.unitsHeld * (it.latestPrice - it.previousClosePrice) else 0.0
            }
            val prevCloseTotal = holdings.sumOf {
                if (it.previousClosePrice > 0) it.unitsHeld * it.previousClosePrice else 0.0
            }
            val dayGainPct = if (prevCloseTotal > 0) (dayGain / prevCloseTotal) * 100 else 0.0
            val hasValidPrice = holdings.any { it.latestPrice > 0 && it.previousClosePrice > 0 }

            return DailyCategoryItem(
                key = key,
                name = name,
                count = count,
                currentValue = curVal,
                dayGain = dayGain,
                dayGainPct = dayGainPct,
                hasValidPrice = hasValidPrice
            )
        }
    }
}

@JsonClass(generateAdapter = true)
data class DailyCategoryItem(
    val key: String,
    val name: String,
    val count: Int,
    val currentValue: Double,
    val dayGain: Double,
    val dayGainPct: Double,
    val hasValidPrice: Boolean
)
