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

    @Test
    fun testRelianceStockPriceFetch() = runBlocking {
        val repo = MarketDataRepository.getInstance()
        val result = repo.fetchStockPrice(ticker = "RELIANCE", exchange = "NSE")

        println("Stock Result: $result")
        assertTrue("Expected PriceFetchResult.Success, got $result", result is PriceFetchResult.Success)

        val success = result as PriceFetchResult.Success
        println("Fetched Price: ${success.prices.latestPrice}")
        println("Fetched Prev Close: ${success.prices.previousClosePrice}")
        println("Fetched Source: ${success.source}")
        println("Diagnostic Logs: ${success.diagnosticLogs.joinToString("\n")}")

        assertEquals("Moneycontrol", success.source)
        assertTrue("Reliance stock price should be > 1000", success.prices.latestPrice > 1000.0)
        assertTrue("Prev close should be > 1000", success.prices.previousClosePrice > 1000.0)
    }

    @Test
    fun testTcsStockPriceFetch() = runBlocking {
        val repo = MarketDataRepository.getInstance()
        val result = repo.fetchStockPrice(ticker = "TCS", exchange = "NSE")

        println("Stock Result: $result")
        assertTrue("Expected PriceFetchResult.Success, got $result", result is PriceFetchResult.Success)

        val success = result as PriceFetchResult.Success
        println("Fetched Price: ${success.prices.latestPrice}")
        println("Fetched Prev Close: ${success.prices.previousClosePrice}")
        println("Fetched Source: ${success.source}")

        assertEquals("Moneycontrol", success.source)
        assertTrue("TCS stock price should be > 2000", success.prices.latestPrice > 2000.0)
    }

    @Test
    fun testNifty50IndexFetch() = runBlocking {
        val repo = MarketDataRepository.getInstance()
        val result = repo.fetchStockPrice(ticker = "^NSEI", exchange = null)

        println("NIFTY 50 Result: $result")
        assertTrue("Expected PriceFetchResult.Success, got $result", result is PriceFetchResult.Success)

        val success = result as PriceFetchResult.Success
        println("Fetched NIFTY 50 Price: ${success.prices.latestPrice}")
        println("Fetched Prev Close: ${success.prices.previousClosePrice}")
        println("Fetched Source: ${success.source}")

        assertEquals("Moneycontrol", success.source)
        assertTrue("Nifty 50 value should be > 20,000", success.prices.latestPrice > 20000.0)
        assertTrue("Nifty 50 prev close should be > 20,000", success.prices.previousClosePrice > 20000.0)
    }
}
