package com.prajwal.utilities.tools.wealthtracker.data

import com.prajwal.utilities.tools.wealthtracker.data.db.AssetSnapshotEntity

data class CalculatorSettings(
    val monthlyInvestment: Double = WealthPreferences.DEFAULT_MONTHLY_INVESTMENT,
    val annualStepupPercent: Double = WealthPreferences.DEFAULT_ANNUAL_STEPUP_PERCENT,
    val stepupMonth: Int = WealthPreferences.DEFAULT_STEPUP_MONTH,
    val expectedReturnPercent: Double = WealthPreferences.DEFAULT_EXPECTED_RETURN_PERCENT
)

data class AssetClass(
    val name: String,
    val invested: Double,
    val current: Double
) {
    val gainLoss: Double get() = current - invested
    val gainLossPct: Double get() = if (invested > 0) (gainLoss / invested) * 100 else 0.0
}

/** Extracts the 5 asset classes (or 6 if retirement is included) from a snapshot for display in the Reports tab. */
fun AssetSnapshotEntity.toAssetClasses(includeRetirement: Boolean = true): List<AssetClass> {
    val classes = mutableListOf(
        AssetClass("Equity", equityInvested, equityCurrent),
        AssetClass("Arbitrage", arbitrageInvested, arbitrageCurrent),
        AssetClass("Gold", goldInvested, goldCurrent),
        AssetClass("Debt", debtInvested, debtCurrent),
        AssetClass("Silver", silverInvested, silverCurrent),
        AssetClass("REITs", reitsInvested, reitsCurrent)
    )
    if (includeRetirement) {
        classes.add(AssetClass("Retirement", retirementInvested, retirementCurrent))
    }
    return classes
}
