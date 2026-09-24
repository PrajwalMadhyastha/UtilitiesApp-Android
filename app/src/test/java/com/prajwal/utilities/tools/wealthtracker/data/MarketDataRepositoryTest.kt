package com.prajwal.utilities.tools.wealthtracker.data

import com.prajwal.utilities.tools.wealthtracker.data.network.MarketDataRepository
import com.prajwal.utilities.tools.wealthtracker.data.network.PriceFetchResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketDataRepositoryTest {

    @Test
    fun testNipponSmallcap250NavFetch() = runBlocking {
        val repo = MarketDataRepository.getInstance()
        val result = repo.fetchMfNav(
            schemeCode = "148519",
            fallbackFundName = "Nippon India Nifty Smallcap 250 Index Fund - Direct Plan - Growth Option"
        )

        println("Result: $result")
        assertTrue("Expected PriceFetchResult.Success, got $result", result is PriceFetchResult.Success)

        val success = result as PriceFetchResult.Success
        println("Fetched NAV: ${success.prices.latestPrice}")
        println("Fetched Source: ${success.source}")
        println("Diagnostic Logs: ${success.diagnosticLogs.joinToString("\n")}")

        // NAV must be around 36.5378, absolutely NOT 11.33 (which was DSP)
        assertTrue("NAV must be > 30.0, got ${success.prices.latestPrice}", success.prices.latestPrice > 30.0)
        assertTrue("NAV must not be 11.33 (DSP fund mismatch)", kotlin.math.abs(success.prices.latestPrice - 11.33) > 10.0)
    }
}
