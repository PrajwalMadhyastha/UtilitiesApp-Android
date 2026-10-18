package com.prajwal.utilities.tools.wealthtracker.data

import com.prajwal.utilities.tools.wealthtracker.data.db.HoldingEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyCategorySummaryTest {

    @Test
    fun testDayGainComputation() {
        val holdings = listOf(
            HoldingEntity(
                id = 1,
                assetClass = "Equity",
                instrumentType = "MF",
                name = "Nippon Smallcap",
                identifier = "148519",
                exchange = null,
                unitsHeld = 100.0,
                investedAmount = 3000.0,
                latestPrice = 35.0,
                previousClosePrice = 34.0
            ),
            HoldingEntity(
                id = 2,
                assetClass = "Equity",
                instrumentType = "MF",
                name = "HDFC Smallcap",
                identifier = "118989",
                exchange = null,
                unitsHeld = 50.0,
                investedAmount = 5000.0,
                latestPrice = 100.0,
                previousClosePrice = 100.0
            )
        )

        val summary = DailyCategorySummary.fromHoldings(holdings, includeRetirement = true)
        val mfCategory = summary.categories.firstOrNull { it.key == "equity_mf" }

        assertTrue(mfCategory != null)
        assertEquals(8500.0, mfCategory!!.currentValue, 0.001)
        assertEquals(100.0, mfCategory.dayGain, 0.001) // 100 * (35 - 34) + 50 * (100 - 100) = 100.0
        assertEquals(8400.0, mfCategory.prevCloseValue, 0.001) // 100 * 34 + 50 * 100 = 8400.0
        assertEquals((100.0 / 8400.0) * 100, mfCategory.dayGainPct, 0.01)
    }
}
