package com.prajwal.utilities.tools.wealthtracker.data

import com.prajwal.utilities.tools.wealthtracker.data.db.TransactionEntity
import com.prajwal.utilities.tools.wealthtracker.data.db.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class TransactionRecalculationTest {

    private fun recalculate(transactions: List<TransactionEntity>): Pair<Double, Double> {
        var units = 0.0
        var invested = 0.0
        val sorted = transactions.sortedBy { it.timestamp }
        for (tx in sorted) {
            if (tx.type == TransactionType.BUY) {
                units += tx.units
                invested += (tx.units * tx.pricePerUnit)
            } else if (tx.type == TransactionType.SELL) {
                if (units > 0) {
                    val avgCostPerUnit = invested / units
                    val unitsSold = kotlin.math.min(tx.units, units)
                    invested = kotlin.math.max(0.0, invested - (unitsSold * avgCostPerUnit))
                    units = kotlin.math.max(0.0, units - unitsSold)
                }
            }
        }
        return Pair(units, invested)
    }

    @Test
    fun testSingleBuyTransaction() {
        val txs = listOf(
            TransactionEntity(id = 1, holdingId = 1, timestamp = 1000L, units = 10.0, pricePerUnit = 100.0, type = TransactionType.BUY)
        )
        val (units, invested) = recalculate(txs)
        assertEquals(10.0, units, 0.0001)
        assertEquals(1000.0, invested, 0.0001)
    }

    @Test
    fun testMultipleBuyTransactions() {
        val txs = listOf(
            TransactionEntity(id = 1, holdingId = 1, timestamp = 1000L, units = 10.0, pricePerUnit = 100.0, type = TransactionType.BUY),
            TransactionEntity(id = 2, holdingId = 1, timestamp = 2000L, units = 10.0, pricePerUnit = 200.0, type = TransactionType.BUY)
        )
        val (units, invested) = recalculate(txs)
        assertEquals(20.0, units, 0.0001)
        assertEquals(3000.0, invested, 0.0001)
    }

    @Test
    fun testPartialSellMaintainsAverageCost() {
        // Buy 20 units for ₹3,000 (average buy price = ₹150)
        // Sell 5 units @ ₹250
        // Remaining units = 15
        // Cost basis reduced by 5 * 150 = 750 -> remaining invested = 2,250 (avg buy price remains ₹150)
        val txs = listOf(
            TransactionEntity(id = 1, holdingId = 1, timestamp = 1000L, units = 10.0, pricePerUnit = 100.0, type = TransactionType.BUY),
            TransactionEntity(id = 2, holdingId = 1, timestamp = 2000L, units = 10.0, pricePerUnit = 200.0, type = TransactionType.BUY),
            TransactionEntity(id = 3, holdingId = 1, timestamp = 3000L, units = 5.0, pricePerUnit = 250.0, type = TransactionType.SELL)
        )
        val (units, invested) = recalculate(txs)
        assertEquals(15.0, units, 0.0001)
        assertEquals(2250.0, invested, 0.0001)
        assertEquals(150.0, invested / units, 0.0001)
    }

    @Test
    fun testFullSellClearsUnitsAndInvested() {
        val txs = listOf(
            TransactionEntity(id = 1, holdingId = 1, timestamp = 1000L, units = 10.0, pricePerUnit = 100.0, type = TransactionType.BUY),
            TransactionEntity(id = 2, holdingId = 1, timestamp = 2000L, units = 10.0, pricePerUnit = 150.0, type = TransactionType.SELL)
        )
        val (units, invested) = recalculate(txs)
        assertEquals(0.0, units, 0.0001)
        assertEquals(0.0, invested, 0.0001)
    }

    @Test
    fun testXirrCalculation() {
        val now = System.currentTimeMillis()
        val oneYearAgo = now - (365L * 24 * 60 * 60 * 1000L)
        // Invested ₹10,000 one year ago, now worth ₹12,000 -> return should be approx 20%
        val txs = listOf(
            TransactionEntity(id = 1, holdingId = 1, timestamp = oneYearAgo, units = 100.0, pricePerUnit = 100.0, type = TransactionType.BUY)
        )
        val xirr = XirrCalculator.calculateXirr(txs, 12000.0)
        assertTrue("XIRR should be around 20%", abs(xirr - 0.20) < 0.02)
    }
}
