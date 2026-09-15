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
    val previousClosePrice: Double,
    val asOfDate: String? = null
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
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val original = chain.request()
                val request = original.newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36")
                    .header("Accept", "*/*")
                    .build()
                chain.proceed(request)
            }
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


    suspend fun fetchMfNav(schemeCode: String, fallbackFundName: String? = null): AssetPrices? {
        val cleanCode = schemeCode.trim()
        if (cleanCode.startsWith("SGB", ignoreCase = true)) {
            return fetchSgbPrice(cleanCode, null)
        }

        var numericCode = cleanCode.toIntOrNull()
        if (numericCode == null) {
            Log.w("MarketDataRepo", "MF identifier '$cleanCode' is non-numeric. Attempting search resolution...")
            val searchQuery = fallbackFundName?.takeIf { it.isNotBlank() } ?: cleanCode
            val results = searchAsset(searchQuery, isMf = true)
            val matched = results.firstOrNull()
            if (matched != null && matched.identifier.toIntOrNull() != null) {
                numericCode = matched.identifier.toInt()
                Log.i("MarketDataRepo", "Resolved MF '$searchQuery' -> AMFI Scheme Code $numericCode (${matched.name})")
            } else {
                Log.e("MarketDataRepo", "Could not resolve AMFI schemeCode for identifier '$cleanCode'")
                return null
            }
        }

        return try {
            val url = "https://api.mfapi.in/mf/$numericCode"
            Log.d("MarketDataRepo", "Fetching MF NAV from $url")
            val response = service.getMfNav(url)
            val navs = response.data ?: emptyList()
            if (navs.isNotEmpty()) {
                val latestNavData = navs[0]
                val latest = latestNavData.nav?.toDoubleOrNull() ?: 0.0
                val latestDate = latestNavData.date ?: "N/A"

                val prevNavData = if (navs.size > 1) navs[1] else latestNavData
                val previous = prevNavData.nav?.toDoubleOrNull() ?: latest
                val prevDate = prevNavData.date ?: latestDate

                Log.i("MarketDataRepo", "MF [$numericCode]: latest NAV = ₹$latest (Date: $latestDate), prev NAV = ₹$previous (Date: $prevDate)")
                AssetPrices(latest, previous, latestDate)
            } else {
                Log.w("MarketDataRepo", "MF [$numericCode]: Response returned empty NAV data list (status=${response.status})")
                null
            }
        } catch (e: Exception) {
            Log.e("MarketDataRepo", "Error fetching MF NAV for schemeCode $numericCode: ${e.message}", e)
            null
        }
    }

    private fun resolveYahooSymbol(ticker: String, exchange: String?): String {
        val cleanTicker = ticker.trim()
        if (cleanTicker.startsWith("^") || cleanTicker.contains(".")) {
            return cleanTicker
        }
        val suffix = when (exchange?.trim()?.uppercase()) {
            "BSE" -> ".BO"
            "NSE" -> ".NS"
            else -> ".NS" // Default Indian equities to NSE
        }
        return "$cleanTicker$suffix"
    }

    suspend fun fetchDividends(ticker: String, exchange: String?): List<com.prajwal.utilities.tools.wealthtracker.data.db.DividendEventEntity> {
        val symbol = resolveYahooSymbol(ticker, exchange)
        val encodedSymbol = symbol.replace("^", "%5E")
        
        return try {
            val url = "https://query2.finance.yahoo.com/v8/finance/chart/$encodedSymbol?events=div&range=10y&interval=1d"
            val response = service.getYahooChart(url)
            val eventsMap = response.chart?.result?.firstOrNull()?.events?.dividends
            
            eventsMap?.values?.map { div ->
                com.prajwal.utilities.tools.wealthtracker.data.db.DividendEventEntity(
                    symbol = ticker,
                    exDate = div.date,
                    amount = div.amount
                )
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e("MarketDataRepo", "Error fetching dividends for $symbol", e)
            emptyList()
        }
    }

    suspend fun fetchStockPrice(ticker: String, exchange: String?): AssetPrices? {
        if (ticker.startsWith("SGB", ignoreCase = true)) {
            return fetchSgbPrice(ticker, exchange)
        }
        val symbol = resolveYahooSymbol(ticker, exchange)
        val encodedSymbol = symbol.replace("^", "%5E")

        return try {
            val url = "https://query2.finance.yahoo.com/v8/finance/chart/$encodedSymbol"
            Log.d("MarketDataRepo", "Fetching stock price from $url")
            val response = service.getYahooChart(url)
            val meta = response.chart?.result?.firstOrNull()?.meta
            if (meta?.regularMarketPrice != null) {
                val latest = meta.regularMarketPrice
                val prev = meta.chartPreviousClose ?: latest
                Log.i("MarketDataRepo", "Stock [$symbol]: latest price = ₹$latest, previous close = ₹$prev")
                AssetPrices(latest, prev)
            } else {
                Log.w("MarketDataRepo", "Stock [$symbol]: response contained null price")
                null
            }
        } catch (e: Exception) {
            Log.e("MarketDataRepo", "Error fetching stock price for $symbol: ${e.message}", e)
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
     * Scrape Expense Ratio from Moneycontrol for a given mutual fund name with multi-pattern resilience.
     */
    suspend fun fetchMfExpenseRatio(fundName: String, amfiCode: String? = null): Double? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            // Clean query to avoid Solr zero-result matches on verbose fund names
            val cleanQuery = fundName
                .replace(Regex("(?i)\\b(direct|regular|plan|growth|idcw|dividend|option|fund|reinvestment)\\b"), "")
                .replace(Regex("[^a-zA-Z0-9 ]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
            val searchName = if (cleanQuery.length >= 3) cleanQuery else fundName.trim()
            val desktopUa = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            
            val encodedQuery = java.net.URLEncoder.encode(searchName, "UTF-8")
            val searchRequest = okhttp3.Request.Builder()
                .url("https://www.moneycontrol.com/mccode/common/autosuggestion_solr.php?query=$encodedQuery&type=2&format=json")
                .addHeader("User-Agent", desktopUa)
                .addHeader("Accept", "application/json")
                .build()

            val searchResponse = sharedOkHttpClient.newCall(searchRequest).execute()
            if (!searchResponse.isSuccessful) {
                Log.w("MarketDataRepo", "MF Expense Ratio Solr search HTTP error: ${searchResponse.code()} for '$fundName'")
                return@withContext null
            }

            val searchBody = searchResponse.body()?.string() ?: ""
            if (searchBody.isBlank()) return@withContext null
            
            val isDirectFund = fundName.contains("direct", ignoreCase = true)
            val isRegularFund = fundName.contains("regular", ignoreCase = true)
            
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
                .addHeader("User-Agent", desktopUa)
                .build()

            val detailResponse = sharedOkHttpClient.newCall(detailRequest).execute()
            if (!detailResponse.isSuccessful) return@withContext null

            val detailBody = detailResponse.body()?.string() ?: ""

            // Multiple fallback patterns for extracting expense ratio across Next.js JSON and HTML
            val expenseRatioPatterns = listOf(
                Regex(""""expenseRatio"\s*:\s*"([0-9.]+)\s*%?""""),
                Regex(""""expense_ratio"\s*:\s*"([0-9.]+)\s*%?""""),
                Regex("""class="[^"]*exp_ratio[^"]*"[^>]*>\s*([0-9.]+)\s*%?"""),
                Regex("""Expense\s+Ratio[^<]*</span>\s*<span[^>]*>\s*([0-9.]+)\s*%?"""),
                Regex("""Expense\s+Ratio[^:]*:\s*([0-9.]+)\s*%"""),
                Regex(""""ter"\s*:\s*"?([0-9.]+)"?""")
            )

            val result = expenseRatioPatterns.firstNotNullOfOrNull { regex ->
                regex.find(detailBody)?.groupValues?.get(1)?.toDoubleOrNull()
            }
            
            if (result != null && result > 0.0) {
                Log.d("MarketDataRepo", "Scraped expense ratio for '$fundName': $result%")
            } else {
                Log.w("MarketDataRepo", "Expense ratio could not be extracted from detail page: $detailUrlStr")
            }
            return@withContext result
        } catch (e: Exception) {
            Log.e("MarketDataRepo", "MF Expense Ratio fetch exception for $fundName: ${e.message}", e)
            null
        }
    }
}
