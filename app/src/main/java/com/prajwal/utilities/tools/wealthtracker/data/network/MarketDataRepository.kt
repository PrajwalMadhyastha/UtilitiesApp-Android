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
    val asOfDate: String? = null,
    val source: String? = null,
    val expenseRatio: Double? = null
)

sealed class PriceFetchResult {
    abstract val diagnosticLogs: List<String>

    data class Success(
        val prices: AssetPrices,
        val source: String,
        override val diagnosticLogs: List<String> = emptyList()
    ) : PriceFetchResult()

    data class Failure(
        val errorMessage: String,
        override val diagnosticLogs: List<String> = emptyList()
    ) : PriceFetchResult()
}

data class AmfiSchemeEntry(
    val schemeCode: String,
    val isin: String?,
    val schemeName: String,
    val nav: Double,
    val date: String
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


    private var amfiNavCache: Map<String, AmfiSchemeEntry>? = null
    private var amfiIsinCache: Map<String, AmfiSchemeEntry>? = null
    private var amfiLastFetchTimestamp: Long = 0L
    private val amfiLock = Any()

    suspend fun getAmfiNavMap(): Pair<Map<String, AmfiSchemeEntry>, Map<String, AmfiSchemeEntry>> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        synchronized(amfiLock) {
            val now = System.currentTimeMillis()
            if (amfiNavCache != null && amfiIsinCache != null && (now - amfiLastFetchTimestamp) < 15 * 60 * 1000L) {
                return@withContext Pair(amfiNavCache!!, amfiIsinCache!!)
            }
        }

        try {
            val request = okhttp3.Request.Builder()
                .url("https://portal.amfiindia.com/spages/NAVAll.txt")
                .build()
            val response = sharedOkHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w("MarketDataRepo", "AMFI feed download HTTP error: ${response.code()}")
                return@withContext Pair(emptyMap(), emptyMap())
            }
            val body = response.body()?.string() ?: ""
            val byCode = mutableMapOf<String, AmfiSchemeEntry>()
            val byIsin = mutableMapOf<String, AmfiSchemeEntry>()

            body.lineSequence().forEach { line ->
                if (line.contains(';') && !line.startsWith("Scheme", ignoreCase = true)) {
                    val parts = line.split(';')
                    if (parts.size >= 8) {
                        val code = parts[0].trim()
                        val isin = parts[1].trim().takeIf { it.isNotBlank() && it != "-" }
                        val name = parts[3].trim()
                        val nav = parts[6].trim().toDoubleOrNull() ?: 0.0
                        val date = parts[7].trim()
                        if (code.isNotBlank() && nav > 0.0) {
                            val entry = AmfiSchemeEntry(code, isin, name, nav, date)
                            byCode[code] = entry
                            if (isin != null) {
                                byIsin[isin] = entry
                            }
                        }
                    }
                }
            }

            synchronized(amfiLock) {
                amfiNavCache = byCode
                amfiIsinCache = byIsin
                amfiLastFetchTimestamp = System.currentTimeMillis()
            }
            Log.i("MarketDataRepo", "AMFI feed loaded: ${byCode.size} schemes cached successfully")
            Pair(byCode, byIsin)
        } catch (e: Exception) {
            Log.e("MarketDataRepo", "Failed to download/parse AMFI feed: ${e.message}", e)
            Pair(emptyMap(), emptyMap())
        }
    }

    suspend fun fetchMfFromMoneycontrol(
        fundName: String,
        schemeCode: String? = null
    ): AssetPrices? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val desktopUa = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            val cleanQuery = fundName
                .replace(Regex("(?i)\\b(direct|regular|plan|growth|idcw|dividend|option|fund|reinvestment)\\b"), "")
                .replace(Regex("[^a-zA-Z0-9 ]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
            val searchName = if (cleanQuery.length >= 3) cleanQuery else fundName.trim()
            val encodedQuery = java.net.URLEncoder.encode(searchName, "UTF-8")
            val searchUrl = "https://www.moneycontrol.com/mccode/common/autosuggestion_solr.php?query=$encodedQuery&type=2&format=json"

            val searchRequest = okhttp3.Request.Builder()
                .url(searchUrl)
                .addHeader("User-Agent", desktopUa)
                .addHeader("Accept", "application/json")
                .build()

            val searchResponse = sharedOkHttpClient.newCall(searchRequest).execute()
            if (!searchResponse.isSuccessful) {
                Log.w("MarketDataRepo", "Moneycontrol Solr search HTTP ${searchResponse.code()} for '$fundName'")
                return@withContext null
            }

            val searchBody = searchResponse.body()?.string() ?: ""
            if (searchBody.isBlank()) return@withContext null

            val isDirectFund = fundName.contains("direct", ignoreCase = true)
            val isRegularFund = fundName.contains("regular", ignoreCase = true)
            val isIdcw = fundName.contains("idcw", ignoreCase = true) || fundName.contains("dividend", ignoreCase = true)

            val blocks = searchBody.split("{")
            var bestUrlStr: String? = null
            var firstUrlStr: String? = null

            for (block in blocks) {
                val linkMatch = Regex(""""link_src"\s*:\s*"([^"]+)"""").find(block)
                val nameMatch = Regex(""""name"\s*:\s*"([^"]+)"""").find(block)

                if (linkMatch != null && nameMatch != null) {
                    val url = linkMatch.groupValues[1]
                    val name = nameMatch.groupValues[1]
                    if (firstUrlStr == null && url.isNotBlank()) firstUrlStr = url

                    val targetDirect = (isDirectFund || !isRegularFund)
                    val matchDirect = name.contains("direct", ignoreCase = true) == targetDirect
                    val matchGrowth = if (isIdcw) {
                        name.contains("idcw", ignoreCase = true) || name.contains("dividend", ignoreCase = true)
                    } else {
                        name.contains("growth", ignoreCase = true) || !name.contains("idcw", ignoreCase = true)
                    }

                    if (matchDirect && matchGrowth) {
                        bestUrlStr = url
                        break
                    }
                    if (matchDirect && bestUrlStr == null) {
                        bestUrlStr = url
                    }
                }
            }

            val detailUrlStr = bestUrlStr ?: firstUrlStr ?: return@withContext null
            val detailRequest = okhttp3.Request.Builder()
                .url(detailUrlStr)
                .addHeader("User-Agent", desktopUa)
                .build()

            val detailResponse = sharedOkHttpClient.newCall(detailRequest).execute()
            if (!detailResponse.isSuccessful) {
                Log.w("MarketDataRepo", "Moneycontrol detail HTTP ${detailResponse.code()} for $detailUrlStr")
                return@withContext null
            }

            val detailBody = detailResponse.body()?.string() ?: ""

            val navPatterns = listOf(
                Regex(""""latestNAV"\s*:\s*"([0-9.]+)""""),
                Regex("""class="[^"]*LockIn_web_number[^"]*"[^>]*>\s*([0-9.]+)\s*</span>"""),
                Regex("""id="nav_val"[^>]*>\s*([0-9.]+)"""),
                Regex("""<title>.*?\[([0-9.]+)\].*?- Moneycontrol</title>""")
            )

            val latestNav = navPatterns.firstNotNullOfOrNull { regex ->
                regex.find(detailBody)?.groupValues?.get(1)?.toDoubleOrNull()
            } ?: return@withContext null

            val changePatterns = listOf(
                Regex(""""changeNAV"\s*:\s*"([0-9.-]+)""""),
                Regex("""class="[^"]*LockIn_web_negNumber[^"]*"[^>]*>\s*([0-9.-]+)\s*</span>""")
            )
            val changeNav = changePatterns.firstNotNullOfOrNull { regex ->
                regex.find(detailBody)?.groupValues?.get(1)?.toDoubleOrNull()
            } ?: 0.0

            val datePatterns = listOf(
                Regex(""""navDate"\s*:\s*"([^"]+)""""),
                Regex("""NAV\s+as\s+on[^<]*<!-- -->\s*([0-9A-Za-z ,]+)"""),
                Regex("""NAV\s+as\s+on\s+([0-9A-Za-z ,]+)""")
            )
            val navDate = datePatterns.firstNotNullOfOrNull { regex ->
                regex.find(detailBody)?.groupValues?.get(1)?.trim()
            }

            val expenseRatioPatterns = listOf(
                Regex(""""expenseRatio"\s*:\s*"([0-9.]+)\s*%?""""),
                Regex(""""expense_ratio"\s*:\s*"([0-9.]+)\s*%?""""),
                Regex("""class="[^"]*exp_ratio[^"]*"[^>]*>\s*([0-9.]+)\s*%?""""),
                Regex("""Expense\s+Ratio[^<]*</span>\s*<span[^>]*>\s*([0-9.]+)\s*%?""""),
                Regex("""Expense\s+Ratio[^:]*:\s*([0-9.]+)\s*%"""),
                Regex(""""ter"\s*:\s*"?([0-9.]+)"?""")
            )
            val expenseRatio = expenseRatioPatterns.firstNotNullOfOrNull { regex ->
                regex.find(detailBody)?.groupValues?.get(1)?.toDoubleOrNull()
            }

            val prevClose = latestNav - changeNav
            Log.d("MarketDataRepo", "Moneycontrol: Scraped NAV ₹$latestNav, PrevClose ₹$prevClose, Date: $navDate, TER: $expenseRatio% for '$fundName'")
            AssetPrices(
                latestPrice = latestNav,
                previousClosePrice = if (prevClose > 0.0) prevClose else latestNav,
                asOfDate = navDate,
                source = "Moneycontrol",
                expenseRatio = expenseRatio
            )
        } catch (e: Exception) {
            Log.e("MarketDataRepo", "Moneycontrol fetch exception for '$fundName': ${e.message}", e)
            null
        }
    }

    suspend fun fetchMfNav(
        schemeCode: String,
        fallbackFundName: String? = null,
        existingPrice: Double = 0.0
    ): PriceFetchResult {
        val cleanCode = schemeCode.trim()
        if (cleanCode.startsWith("SGB", ignoreCase = true)) {
            return fetchSgbPrice(cleanCode, null)
        }

        val diagnosticLogs = mutableListOf<String>()
        val fundName = fallbackFundName?.takeIf { it.isNotBlank() } ?: cleanCode

        // 1. Primary Source: Moneycontrol (Solr Search + Detail page)
        // Provides latest NAV, change/previous close, NAV date, and expense ratio.
        diagnosticLogs.add("Attempting Moneycontrol for '$fundName' (code: $cleanCode)...")
        val mcPrices = fetchMfFromMoneycontrol(fundName, cleanCode)
        if (mcPrices != null && mcPrices.latestPrice > 0.0) {
            val dateStr = mcPrices.asOfDate?.let { " [As of $it]" } ?: ""
            val terStr = mcPrices.expenseRatio?.let { ", TER: $it%" } ?: ""
            val successMsg = "Moneycontrol resolved '$fundName' -> NAV: ₹${mcPrices.latestPrice}$dateStr, PrevClose: ₹${mcPrices.previousClosePrice}$terStr"
            Log.i("MarketDataRepo", successMsg)
            diagnosticLogs.add(successMsg)
            return PriceFetchResult.Success(mcPrices, "Moneycontrol", diagnosticLogs)
        } else {
            val failMsg = "Moneycontrol: Could not fetch NAV for '$fundName'"
            Log.w("MarketDataRepo", failMsg)
            diagnosticLogs.add(failMsg)
        }

        // 2. Secondary Source: AMFI Official Daily NAV feed (portal.amfiindia.com)
        diagnosticLogs.add("Attempting AMFI official feed for identifier '$cleanCode'...")
        val (amfiCodeMap, amfiIsinMap) = getAmfiNavMap()
        var amfiEntry = amfiCodeMap[cleanCode]
        if (amfiEntry == null && cleanCode.startsWith("INF", ignoreCase = true)) {
            amfiEntry = amfiIsinMap[cleanCode]
        }
        if (amfiEntry != null && amfiEntry.nav > 0.0) {
            val prevClose = if (existingPrice > 0.0 && existingPrice != amfiEntry.nav) existingPrice else amfiEntry.nav
            val amfiPrices = AssetPrices(
                latestPrice = amfiEntry.nav,
                previousClosePrice = prevClose,
                asOfDate = amfiEntry.date,
                source = "AMFI Official"
            )
            val successMsg = "AMFI Official resolved '$cleanCode' (${amfiEntry.schemeName}) -> NAV: ₹${amfiEntry.nav} [As of ${amfiEntry.date}]"
            Log.i("MarketDataRepo", successMsg)
            diagnosticLogs.add(successMsg)
            return PriceFetchResult.Success(amfiPrices, "AMFI Official", diagnosticLogs)
        } else {
            val failMsg = "AMFI Official: Scheme code/ISIN '$cleanCode' not found in official feed (${amfiCodeMap.size} schemes)"
            Log.w("MarketDataRepo", failMsg)
            diagnosticLogs.add(failMsg)
        }

        // 3. Tertiary Source: mfapi.in (as additional fallback)
        var numericCode = cleanCode.toIntOrNull()
        if (numericCode == null) {
            diagnosticLogs.add("MF identifier '$cleanCode' is non-numeric. Attempting search resolution on mfapi.in...")
            val results = searchAsset(fundName, isMf = true)
            val matched = results.firstOrNull()?.identifier?.toIntOrNull()
            if (matched != null) {
                numericCode = matched
                diagnosticLogs.add("mfapi.in search resolved '$fundName' -> Scheme Code $numericCode")
            }
        }

        if (numericCode != null) {
            try {
                val url = "https://api.mfapi.in/mf/$numericCode"
                diagnosticLogs.add("Fetching from $url...")
                val response = service.getMfNav(url)
                val navs = response.data ?: emptyList()
                if (navs.isNotEmpty()) {
                    val latestNavData = navs[0]
                    val latest = latestNavData.nav?.toDoubleOrNull() ?: 0.0
                    val latestDate = latestNavData.date ?: "N/A"
                    val prevNavData = if (navs.size > 1) navs[1] else latestNavData
                    val previous = prevNavData.nav?.toDoubleOrNull() ?: latest

                    if (latest > 0.0) {
                        val prices = AssetPrices(latest, previous, latestDate, source = "mfapi.in")
                        val successMsg = "mfapi.in resolved [$numericCode] -> NAV: ₹$latest (Date: $latestDate)"
                        Log.i("MarketDataRepo", successMsg)
                        diagnosticLogs.add(successMsg)
                        return PriceFetchResult.Success(prices, "mfapi.in", diagnosticLogs)
                    }
                } else {
                    diagnosticLogs.add("mfapi.in returned empty NAV list for code $numericCode (status=${response.status})")
                }
            } catch (e: Exception) {
                val errMsg = "mfapi.in error for schemeCode $numericCode: ${e.message ?: e.javaClass.simpleName}"
                Log.e("MarketDataRepo", errMsg, e)
                diagnosticLogs.add(errMsg)
            }
        } else {
            diagnosticLogs.add("Could not resolve numeric scheme code for identifier '$cleanCode'")
        }

        return PriceFetchResult.Failure(
            errorMessage = diagnosticLogs.filter { it.contains(":") && !it.startsWith("Attempting") }.joinToString("; "),
            diagnosticLogs = diagnosticLogs
        )
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

    suspend fun fetchStockPrice(ticker: String, exchange: String?): PriceFetchResult {
        if (ticker.startsWith("SGB", ignoreCase = true)) {
            return fetchSgbPrice(ticker, exchange)
        }
        val symbol = resolveYahooSymbol(ticker, exchange)
        val encodedSymbol = symbol.replace("^", "%5E")
        val diagnosticLogs = mutableListOf<String>()

        return try {
            val url = "https://query2.finance.yahoo.com/v8/finance/chart/$encodedSymbol"
            Log.d("MarketDataRepo", "Fetching stock price from $url")
            val response = service.getYahooChart(url)
            val meta = response.chart?.result?.firstOrNull()?.meta
            if (meta?.regularMarketPrice != null && meta.regularMarketPrice > 0.0) {
                val latest = meta.regularMarketPrice
                val prev = meta.chartPreviousClose ?: latest
                val msg = "Yahoo Finance resolved [$symbol] -> Price: ₹$latest, PrevClose: ₹$prev"
                Log.i("MarketDataRepo", msg)
                diagnosticLogs.add(msg)
                PriceFetchResult.Success(
                    prices = AssetPrices(latest, prev, source = "Yahoo Finance"),
                    source = "Yahoo Finance",
                    diagnosticLogs = diagnosticLogs
                )
            } else {
                val err = "Yahoo Finance: Chart response contained null or zero market price for '$symbol'"
                Log.w("MarketDataRepo", err)
                diagnosticLogs.add(err)
                PriceFetchResult.Failure(err, diagnosticLogs)
            }
        } catch (e: retrofit2.HttpException) {
            val err = "Yahoo Finance: HTTP ${e.code()} (${e.message()}) for '$symbol'"
            Log.e("MarketDataRepo", err, e)
            diagnosticLogs.add(err)
            PriceFetchResult.Failure(err, diagnosticLogs)
        } catch (e: Exception) {
            val err = "Yahoo Finance error for '$symbol': ${e.message ?: e.javaClass.simpleName}"
            Log.e("MarketDataRepo", err, e)
            diagnosticLogs.add(err)
            PriceFetchResult.Failure(err, diagnosticLogs)
        }
    }

    /**
     * FIX #5: Multi-source SGB price fetching with Yahoo Finance as primary.
     */
    private suspend fun fetchSgbPrice(ticker: String, exchange: String?): PriceFetchResult {
        val cleanTicker = ticker
            .replace("-GB", "")
            .replace(".NS", "")
            .replace(".BO", "")
            .uppercase()
        val diagnosticLogs = mutableListOf<String>()

        // Step 1: Try Yahoo Finance (NSE listing)
        diagnosticLogs.add("Attempting Yahoo Finance for SGB '$cleanTicker.NS'...")
        val yahooResult = tryYahooSgb(cleanTicker)
        if (yahooResult != null && yahooResult.latestPrice > 0.0) {
            val msg = "Yahoo Finance resolved SGB $cleanTicker -> ₹${yahooResult.latestPrice}"
            Log.d("MarketDataRepo", msg)
            diagnosticLogs.add(msg)
            return PriceFetchResult.Success(
                yahooResult.copy(source = "Yahoo Finance"),
                source = "Yahoo Finance",
                diagnosticLogs = diagnosticLogs
            )
        } else {
            diagnosticLogs.add("Yahoo Finance: Returned no price for SGB $cleanTicker.NS")
        }

        // Step 2: Try Moneycontrol SGB scraping
        diagnosticLogs.add("Falling back to Moneycontrol for SGB '$cleanTicker'...")
        val mcResult = fetchSgbFromMoneycontrol(cleanTicker)
        if (mcResult != null && mcResult.latestPrice > 0.0) {
            val msg = "Moneycontrol resolved SGB $cleanTicker -> ₹${mcResult.latestPrice}"
            Log.d("MarketDataRepo", msg)
            diagnosticLogs.add(msg)
            return PriceFetchResult.Success(
                mcResult.copy(source = "Moneycontrol"),
                source = "Moneycontrol",
                diagnosticLogs = diagnosticLogs
            )
        } else {
            diagnosticLogs.add("Moneycontrol: SGB scraping failed for $cleanTicker")
        }

        return PriceFetchResult.Failure(
            errorMessage = "SGB fetch failed on both Yahoo Finance and Moneycontrol for '$cleanTicker'",
            diagnosticLogs = diagnosticLogs
        )
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
    suspend fun fetchMfExpenseRatio(fundName: String, amfiCode: String? = null): Double? {
        val prices = fetchMfFromMoneycontrol(fundName, amfiCode)
        return prices?.expenseRatio
    }
}
