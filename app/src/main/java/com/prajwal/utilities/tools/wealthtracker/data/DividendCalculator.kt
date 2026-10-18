package com.prajwal.utilities.tools.wealthtracker.data

import com.prajwal.utilities.tools.wealthtracker.data.db.DividendEventEntity

object DividendCalculator {

    /**
     * Calculates dividends earned for a holding based on its current unit balance and
     * known dividend events that fall within a given time window.
     *
     * NOTE: This uses the *current* unit balance for all historical events in the window.
     * It is intentionally a "trailing income" estimate, not a precise ledger reconstruction.
     *
     * @param currentUnits  The number of units currently held in this holding.
     * @param events        All dividend events for the associated symbol.
     * @param cutoffMs      Only events with an ex-dividend date on or after this Unix epoch
     *                      millisecond timestamp will be included. Pass 0L for "All Time".
     * @return The estimated dividend amount earned within the specified window.
     */
    fun calculateDividendsEarned(
        currentUnits: Double,
        events: List<DividendEventEntity>,
        cutoffMs: Long = System.currentTimeMillis() - (365L * 24 * 60 * 60 * 1000L)
    ): Double {
        if (currentUnits <= 0) return 0.0

        var total = 0.0
        for (event in events) {
            if ((event.exDate * 1000L) >= cutoffMs) {
                total += currentUnits * event.amount
            }
        }
        return total
    }
}
