package com.prajwal.utilities.tools.wealthtracker.data.network

import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

data class AssetSearchResult(
    val identifier: String,
    val name: String,
    val exchange: String?
)

data class AssetPrices(
    val latestPrice: Double,
    val previousClosePrice: Double
)

// FIX #2: MarketDataRepository is now a singleton.
// Previously, every ViewModel + every Worker invocation constructed a new OkHttpClient,
// which creates a new thread pool and connection pool each time — a resource leak.
// Now one shared instance is used across the entire app lifetime.
class MarketDataRepository private constructor() {

    companion object {
        @Volatile
        private var INSTANCE: MarketDataRepository? = null

        fun getInstance(): MarketDataRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MarketDataRepository().also { INSTANCE = it }
            }
        }

        // Shared HTTP infrastructure — built once, reused everywhere.
        val sharedOkHttpClient: okhttp3.OkHttpClient = okhttp3.OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
            
        fun shouldSkipSync(lastUpdatedAt: Long): Boolean {
            val currentTime = System.currentTimeMillis()
            val oneHourMillis = 60 * 60 * 1000L
            val timeSinceLastSync = currentTime - lastUpdatedAt

            if (timeSinceLastSync < oneHourMillis) {
                val zoneId = java.time.ZoneId.of("Asia/Kolkata")
                val now = java.time.ZonedDateTime.now(zoneId)
                val dayOfWeek = now.dayOfWeek
                val isWeekend = dayOfWeek == java.time.DayOfWeek.SATURDAY || dayOfWeek == java.time.DayOfWeek.SUNDAY

                val timeInMinutes = now.hour * 60 + now.minute
                val marketOpenMinutes = 9 * 60 + 15
                val marketCloseMinutes = 15 * 60 + 30

                val isMarketClosed = isWeekend || timeInMinutes < marketOpenMinutes || timeInMinutes >= marketCloseMinutes

                if (isMarketClosed) {
                    return true
                }
            }
            return false
        }
    }

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://localhost/") // Dummy URL as we pass absolute URLs
        .client(sharedOkHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val service = retrofit.create(MarketDataService::class.java)


    suspend fun fetchMfNav(schemeCode: String): AssetPrices? {
        if (schemeCode.startsWith("SGB", ignoreCase = true)) {
            return fetchSgbPrice(schemeCode, null)
        }
        return try {
            val response = service.getMfNav("https://api.mfapi.in/mf/$schemeCode")
            val navs = response.data ?: emptyList()
            if (navs.isNotEmpty()) {
                val latest = navs[0].nav?.toDoubleOrNull() ?: 0.0
                val previous = if (navs.size > 1) navs[1].nav?.toDoubleOrNull() ?: latest else latest
                AssetPrices(latest, previous)
            } else null
        } catch (e: Exception) {
            Log.e("MarketDataRepo", "Error fetching MF NAV for $schemeCode", e)
            null
        }
    }

    suspend fun fetchStockPrice(ticker: String, exchange: String?): AssetPrices? {
        if (ticker.startsWith("SGB", ignoreCase = true)) {
            return fetchSgbPrice(ticker, exchange)
        }
        val suffix = when (exchange) {
            "NSE" -> ".NS"
            "BSE" -> ".BO"
            else -> ""
        }
        val symbol = "$ticker$suffix"
        val encodedSymbol = symbol.replace("^", "%5E")

        return try {
            val response = service.getYahooChart("https://query2.finance.yahoo.com/v8/finance/chart/$encodedSymbol")
            val meta = response.chart?.result?.firstOrNull()?.meta
            if (meta?.regularMarketPrice != null) {
                AssetPrices(meta.regularMarketPrice, meta.chartPreviousClose ?: meta.regularMarketPrice)
            } else null
        } catch (e: Exception) {
            Log.e("MarketDataRepo", "Error fetching stock price for $symbol", e)
            null
        }
    }

    /**
     * FIX #5: Multi-source SGB price fetching with Yahoo Finance as primary.
     *
     * Strategy:
     *  1. Try Yahoo Finance with standard NSE ticker (e.g. SGBAUG28.NS).
     *     SGBs are listed on NSE and Yahoo Finance carries them reliably.
     *     This reuses the same robust Retrofit path as all other stocks.
     *  2. If Yahoo returns no price, fall back to Moneycontrol HTML scraping.
     *     Multiple regex patterns are tried to survive minor DOM changes.
     *     On failure, full diagnostic context is logged to Logcat so DOM
     *     changes are immediately visible in development.
     */
    private suspend fun fetchSgbPrice(ticker: String, exchange: String?): AssetPrices? {
        // Step 1: Try Yahoo Finance (NSE listing). SGBs trade as SGBXXX28.NS on Yahoo.
        val cleanTicker = ticker
            .replace("-GB", "")
            .replace(".NS", "")
            .replace(".BO", "")
            .uppercase()

        val yahooResult = tryYahooSgb(cleanTicker)
        if (yahooResult != null) {
            Log.d("MarketDataRepo", "SGB $cleanTicker: price fetched from Yahoo Finance")
            return yahooResult
        }

        Log.w("MarketDataRepo", "SGB $cleanTicker: Yahoo Finance returned no price, falling back to Moneycontrol")

        // Step 2: Moneycontrol fallback
        return fetchSgbFromMoneycontrol(cleanTicker)
    }

    private suspend fun tryYahooSgb(cleanTicker: String): AssetPrices? {
        return try {
            val symbol = "$cleanTicker.NS"
            val response = service.getYahooChart(
                "https://query2.finance.yahoo.com/v8/finance/chart/$symbol"
            )
            val meta = response.chart?.result?.firstOrNull()?.meta
            if (meta?.regularMarketPrice != null && meta.regularMarketPrice > 0) {
                AssetPrices(
                    latestPrice = meta.regularMarketPrice,
                    previousClosePrice = meta.chartPreviousClose ?: meta.regularMarketPrice
                )
            } else null
        } catch (e: Exception) {
            Log.d("MarketDataRepo", "SGB Yahoo Finance fetch failed for $cleanTicker.NS: ${e.message}")
            null
        }
    }

    private suspend fun fetchSgbFromMoneycontrol(
        cleanTicker: String
    ): AssetPrices? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val searchRequest = okhttp3.Request.Builder()
                .url("https://www.moneycontrol.com/mccode/common/autosuggestion_solr.php?query=$cleanTicker&type=1&format=json")
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10)")
                .addHeader("Accept", "application/json")
                .build()

            val searchResponse = sharedOkHttpClient.newCall(searchRequest).execute()
            if (!searchResponse.isSuccessful) {
                Log.e("MarketDataRepo", "SGB Moneycontrol search failed: HTTP ${searchResponse.code()} for $cleanTicker")
                return@withContext null
            }

            val searchBody = searchResponse.body()?.string() ?: ""
            val linkMatch = Regex(""""link_src"\s*:\s*"([^"]+)"""").find(searchBody)

            if (linkMatch == null) {
                Log.e("MarketDataRepo",
                    "SGB Moneycontrol: 'link_src' not found in search response for $cleanTicker. " +
                    "Response preview: ${searchBody.take(500)}"
                )
                return@withContext null
            }

            val detailUrlStr = linkMatch.groupValues[1]
            Log.d("MarketDataRepo", "SGB Moneycontrol: fetching detail page $detailUrlStr")

            val detailRequest = okhttp3.Request.Builder()
                .url(detailUrlStr)
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10)")
                .build()

            val detailResponse = sharedOkHttpClient.newCall(detailRequest).execute()
            if (!detailResponse.isSuccessful) {
                Log.e("MarketDataRepo", "SGB Moneycontrol detail page failed: HTTP ${detailResponse.code()} ($detailUrlStr)")
                return@withContext null
            }

            val detailBody = detailResponse.body()?.string() ?: ""

            // Multiple price patterns to survive minor DOM changes:
            val price = listOf(
                Regex("""id="nsespotval"[^>]*value="([0-9,.]+)""""),   // Primary NSE field
                Regex("""id="bsespotval"[^>]*value="([0-9,.]+)""""),   // BSE fallback field
                Regex(""""nsespot"\s*:\s*"?([0-9,.]+)"?"""),           // JSON-in-HTML pattern
                Regex("""class="[^"]*nseSpotPrice[^"]*"[^>]*>\s*([0-9,.]+)""")  // Class-based
            ).firstNotNullOfOrNull { regex ->
                regex.find(detailBody)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
            }

            // Multiple previous-close patterns:
            val prevClose = listOf(
                Regex("""class="nseprvclose[^>]*>\s*([0-9,.]+)"""),
                Regex("""id="nsepclose"[^>]*value="([0-9,.]+)""""),
                Regex(""""prevClose"\s*:\s*"?([0-9,.]+)"?""")
            ).firstNotNullOfOrNull { regex ->
                regex.find(detailBody)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
            }

            if (price != null && price > 0) {
                Log.d("MarketDataRepo", "SGB Moneycontrol: price=₹$price, prevClose=₹$prevClose for $cleanTicker")
                AssetPrices(price, prevClose ?: price)
            } else {
                // Log first 2000 chars of the detail body to help diagnose DOM changes
                Log.e("MarketDataRepo",
                    "SGB Moneycontrol: price not found in detail page ($detailUrlStr). " +
                    "Detail body preview:\n${detailBody.take(2000)}"
                )
                null
            }
        } catch (e: Exception) {
            Log.e("MarketDataRepo", "SGB Moneycontrol fetch exception for $cleanTicker", e)
            null
        }
    }

    suspend fun searchAsset(query: String, isMf: Boolean): List<AssetSearchResult> {
        if (query.length < 2) return emptyList()
        return try {
            if (isMf) {
                val results = service.searchMfScheme(query = query)
                results.map {
                    AssetSearchResult(
                        identifier = it.schemeCode.toString(),
                        name = it.schemeName,
                        exchange = null
                    )
                }
            } else {
                val results = service.searchYahooTicker(query = query)
                results.quotes
                    .filter { it.quoteType == "EQUITY" || it.quoteType == "ETF" || it.quoteType == "MUTUALFUND" || it.quoteType == null }
                    .map { quote ->
                        val parts = quote.symbol.split(".")
                        val identifier = parts[0]
                        val exchange = if (parts.size > 1) {
                            when (parts[1]) {
                                "NS" -> "NSE"
                                "BO" -> "BSE"
                                else -> quote.exchDisp
                            }
                        } else {
                            quote.exchDisp
                        }
                        AssetSearchResult(
                            identifier = identifier,
                            name = quote.longname ?: quote.shortname ?: quote.symbol,
                            exchange = exchange
                        )
                    }
            }
        } catch (e: Exception) {
            Log.e("MarketDataRepo", "Asset search failed for '$query'", e)
            emptyList()
        }
    }

    /**
     * Scrape Expense Ratio from Moneycontrol for a given mutual fund name.
     */
    suspend fun fetchMfExpenseRatio(fundName: String, amfiCode: String? = null): Double? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            var searchName = fundName
            
            if (amfiCode != null) {
                try {
                    val mfapiReq = okhttp3.Request.Builder().url("https://api.mfapi.in/mf/$amfiCode").build()
                    val mfapiResp = sharedOkHttpClient.newCall(mfapiReq).execute()
                    if (mfapiResp.isSuccessful) {
                        val body = mfapiResp.body()?.string() ?: ""
                        val nameMatch = Regex(""""scheme_name"\s*:\s*"([^"]+)"""").find(body)
                        if (nameMatch != null) {
                            searchName = nameMatch.groupValues[1]
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MarketDataRepo", "Failed to fetch official AMFI name for $amfiCode", e)
                }
            }

            val encodedQuery = java.net.URLEncoder.encode(searchName, "UTF-8")
            val searchRequest = okhttp3.Request.Builder()
                .url("https://www.moneycontrol.com/mccode/common/autosuggestion_solr.php?query=$encodedQuery&type=2&format=json")
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10)")
                .addHeader("Accept", "application/json")
                .build()

            val searchResponse = sharedOkHttpClient.newCall(searchRequest).execute()
            if (!searchResponse.isSuccessful) return@withContext null

            val searchBody = searchResponse.body()?.string() ?: ""
            
            val isDirectFund = searchName.contains("direct", ignoreCase = true)
            val isRegularFund = searchName.contains("regular", ignoreCase = true)
            
            val blocks = searchBody.split("{")
            var bestUrlStr: String? = null
            var firstUrlStr: String? = null

            for (block in blocks) {
                val linkMatch = Regex(""""link_src"\s*:\s*"([^"]+)"""").find(block)
                val nameMatch = Regex(""""name"\s*:\s*"([^"]+)"""").find(block)
                
                if (linkMatch != null && nameMatch != null) {
                    val url = linkMatch.groupValues[1]
                    val name = nameMatch.groupValues[1]
                    if (firstUrlStr == null) firstUrlStr = url
                    
                    if (isDirectFund && name.contains("direct", ignoreCase = true)) {
                        bestUrlStr = url
                        break
                    }
                    if (isRegularFund && name.contains("regular", ignoreCase = true)) {
                        bestUrlStr = url
                        break
                    }
                }
            }
            
            val detailUrlStr = bestUrlStr ?: firstUrlStr ?: return@withContext null
            val detailRequest = okhttp3.Request.Builder()
                .url(detailUrlStr)
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10)")
                .build()

            val detailResponse = sharedOkHttpClient.newCall(detailRequest).execute()
            if (!detailResponse.isSuccessful) return@withContext null

            val detailBody = detailResponse.body()?.string() ?: ""

            // Extract from Next.js JSON blob or HTML: "expenseRatio":"0.53 %"
            val expenseRatioRegex = Regex(""""expenseRatio"\s*:\s*"([0-9.]+)\s*%?"""")
            val match = expenseRatioRegex.find(detailBody)
            
            return@withContext match?.groupValues?.get(1)?.toDoubleOrNull()
        } catch (e: Exception) {
            Log.e("MarketDataRepo", "MF Expense Ratio fetch exception for $fundName", e)
            null
        }
    }
}
