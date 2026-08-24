package com.prajwal.utilities.tools.wealthtracker.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.prajwal.utilities.tools.wealthtracker.data.db.WealthDatabase
import com.prajwal.utilities.tools.wealthtracker.data.network.MarketDataRepository
import kotlinx.coroutines.flow.first

class PortfolioSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val db = WealthDatabase.getDatabase(applicationContext)
            val holdingsDao = db.holdingsDao()
            // FIX #2: Use the singleton — shares OkHttpClient with the ViewModel,
            // instead of creating a new thread pool + connection pool on every sync job.
            val marketDataRepo = MarketDataRepository.getInstance()

            val holdings = holdingsDao.getAllHoldings().first()
            val startTime = System.currentTimeMillis()
            com.prajwal.utilities.tools.wealthtracker.data.SyncStatusManager.init(applicationContext)
            val startMsg = "=== PortfolioSyncWorker: Starting Daily 6 AM Sync (Total holdings: ${holdings.size}) ==="
            Log.i("WealthTrackerSync", startMsg)
            com.prajwal.utilities.tools.wealthtracker.data.SyncStatusManager.log("I", startMsg)

            var successCount = 0
            var failedCount = 0
            var skippedCount = 0
            val itemReports = mutableListOf<com.prajwal.utilities.tools.wealthtracker.data.SyncItemReport>()

            for ((index, holding) in holdings.withIndex()) {
                if (holding.isManual) {
                    skippedCount++
                    val msg = "Worker: [${index + 1}/${holdings.size}] Skipping manual holding: '${holding.name}'"
                    Log.d("WealthTrackerSync", msg)
                    com.prajwal.utilities.tools.wealthtracker.data.SyncStatusManager.log("D", msg)
                    itemReports.add(
                        com.prajwal.utilities.tools.wealthtracker.data.SyncItemReport(
                            holdingId = holding.id,
                            holdingName = holding.name,
                            identifier = holding.identifier,
                            instrumentType = holding.instrumentType,
                            assetClass = holding.assetClass,
                            status = "SKIPPED",
                            price = holding.latestPrice,
                            previousClose = holding.previousClosePrice,
                            errorMessage = "Manual holding"
                        )
                    )
                    continue
                }

                if (holding.latestPrice > 0.0 && MarketDataRepository.shouldSkipSync(holding.lastUpdatedAt)) {
                    skippedCount++
                    val msg = "Worker: [${index + 1}/${holdings.size}] Skipping recently synced holding: '${holding.name}'"
                    Log.d("WealthTrackerSync", msg)
                    com.prajwal.utilities.tools.wealthtracker.data.SyncStatusManager.log("D", msg)
                    itemReports.add(
                        com.prajwal.utilities.tools.wealthtracker.data.SyncItemReport(
                            holdingId = holding.id,
                            holdingName = holding.name,
                            identifier = holding.identifier,
                            instrumentType = holding.instrumentType,
                            assetClass = holding.assetClass,
                            status = "SKIPPED",
                            price = holding.latestPrice,
                            previousClose = holding.previousClosePrice,
                            errorMessage = "Recently synced"
                        )
                    )
                    continue
                }

                val syncMsg = "Worker: [${index + 1}/${holdings.size}] Syncing ${holding.instrumentType}: '${holding.name}' (${holding.identifier})"
                Log.d("WealthTrackerSync", syncMsg)
                com.prajwal.utilities.tools.wealthtracker.data.SyncStatusManager.log("D", syncMsg)

                val prices = if (holding.instrumentType == "MF") {
                    marketDataRepo.fetchMfNav(holding.identifier, holding.name)
                } else {
                    marketDataRepo.fetchStockPrice(holding.identifier, holding.exchange)
                }

                if (prices != null) {
                    holdingsDao.updatePrice(
                        id = holding.id,
                        price = prices.latestPrice,
                        previousClosePrice = prices.previousClosePrice
                    )
                    successCount++
                    val dateInfo = prices.asOfDate?.let { " [As of $it]" } ?: ""
                    val successMsg = "Worker: ✓ Updated '${holding.name}' -> ₹${prices.latestPrice}$dateInfo"
                    Log.i("WealthTrackerSync", successMsg)
                    com.prajwal.utilities.tools.wealthtracker.data.SyncStatusManager.log("I", successMsg)
                    itemReports.add(
                        com.prajwal.utilities.tools.wealthtracker.data.SyncItemReport(
                            holdingId = holding.id,
                            holdingName = holding.name,
                            identifier = holding.identifier,
                            instrumentType = holding.instrumentType,
                            assetClass = holding.assetClass,
                            status = "SUCCESS",
                            price = prices.latestPrice,
                            previousClose = prices.previousClosePrice,
                            asOfDate = prices.asOfDate
                        )
                    )
                } else {
                    failedCount++
                    val failMsg = "Worker: ✗ Failed to update '${holding.name}' (${holding.identifier})"
                    Log.e("WealthTrackerSync", failMsg)
                    com.prajwal.utilities.tools.wealthtracker.data.SyncStatusManager.log("E", failMsg)
                    itemReports.add(
                        com.prajwal.utilities.tools.wealthtracker.data.SyncItemReport(
                            holdingId = holding.id,
                            holdingName = holding.name,
                            identifier = holding.identifier,
                            instrumentType = holding.instrumentType,
                            assetClass = holding.assetClass,
                            status = "FAILED",
                            price = holding.latestPrice,
                            previousClose = holding.previousClosePrice,
                            errorMessage = "Failed to fetch price from market API"
                        )
                    )
                }
            }
            val durationMs = System.currentTimeMillis() - startTime
            val doneMsg = "=== PortfolioSyncWorker Complete in ${durationMs}ms: $successCount updated, $failedCount failed, $skippedCount skipped ==="
            Log.i("WealthTrackerSync", doneMsg)
            com.prajwal.utilities.tools.wealthtracker.data.SyncStatusManager.log("I", doneMsg)

            // Save the complete sync report
            com.prajwal.utilities.tools.wealthtracker.data.SyncStatusManager.saveReport(
                applicationContext,
                com.prajwal.utilities.tools.wealthtracker.data.SyncReport(
                    timestamp = System.currentTimeMillis(),
                    triggerSource = "Daily 6 AM Auto-Sync",
                    durationMs = durationMs,
                    successCount = successCount,
                    failedCount = failedCount,
                    skippedCount = skippedCount,
                    items = itemReports
                )
            )


            val prefs = com.prajwal.utilities.tools.wealthtracker.data.WealthPreferences(applicationContext)
            val nowMs = System.currentTimeMillis()
            prefs.updateLastAutoSyncTimestamp(nowMs)

            // Save the daily morning category summary baseline (preserves exact previous day performance)
            try {
                val updatedHoldings = holdingsDao.getAllHoldings().first()
                val includeRet = prefs.includeRetirementInTotal.first()
                val dailySummary = com.prajwal.utilities.tools.wealthtracker.data.DailyCategorySummary.fromHoldings(
                    holdings = updatedHoldings,
                    includeRetirement = includeRet,
                    timestamp = nowMs
                )
                val moshi = com.squareup.moshi.Moshi.Builder().add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory()).build()
                val json = moshi.adapter(com.prajwal.utilities.tools.wealthtracker.data.DailyCategorySummary::class.java).toJson(dailySummary)
                prefs.updateDailyCategorySummaryJson(json)
                Log.i("WealthTrackerSync", "Worker: Saved DailyCategorySummary for ${dailySummary.categories.size} categories")
            } catch (e: Exception) {
                Log.e("WealthTrackerSync", "Worker: Error saving DailyCategorySummary", e)
            }

            sendSyncNotification(applicationContext, holdings.count { !it.isManual })

            val autoSnapshotEnabled = prefs.autoSnapshotEnabled.first()
            if (autoSnapshotEnabled) {
                val autoSnapshotDay = prefs.autoSnapshotDayOfMonth.first()
                val now = java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Kolkata"))
                if (now.dayOfMonth == autoSnapshotDay) {
                    val wealthDao = db.wealthDao()
                    val latestSnapshot = wealthDao.getLatestSnapshot()
                    
                    val todayStart = now.withHour(0).withMinute(0).withSecond(0).withNano(0).toInstant().toEpochMilli()
                    if (latestSnapshot == null || latestSnapshot.recordedAt < todayStart) {
                        val updatedHoldings = holdingsDao.getAllHoldings().first()
                        
                        var equityInv = 0.0
                        var goldInv = 0.0
                        var debtInv = 0.0
                        var silverInv = 0.0
                        var reitsInv = 0.0
                        var retirementInv = 0.0
                        
                        var equityCur = 0.0
                        var goldCur = 0.0
                        var debtCur = 0.0
                        var silverCur = 0.0
                        var reitsCur = 0.0
                        var retirementCur = 0.0

                        for (h in updatedHoldings) {
                            val cur = h.unitsHeld * h.latestPrice
                            val inv = h.investedAmount
                            when (h.assetClass) {
                                "Equity" -> { equityInv += inv; equityCur += cur }
                                "Gold" -> { goldInv += inv; goldCur += cur }
                                "Debt" -> { debtInv += inv; debtCur += cur }
                                "Silver" -> { silverInv += inv; silverCur += cur }
                                "REITs" -> { reitsInv += inv; reitsCur += cur }
                                "Retirement" -> { retirementInv += inv; retirementCur += cur }
                            }
                        }
                        
                        val newSnapshot = com.prajwal.utilities.tools.wealthtracker.data.db.AssetSnapshotEntity(
                            recordedAt = System.currentTimeMillis(),
                            equityInvested = equityInv,
                            goldInvested = goldInv,
                            debtInvested = debtInv,
                            silverInvested = silverInv,
                            reitsInvested = reitsInv,
                            retirementInvested = retirementInv,
                            equityCurrent = equityCur,
                            goldCurrent = goldCur,
                            debtCurrent = debtCur,
                            silverCurrent = silverCur,
                            reitsCurrent = reitsCur,
                            retirementCurrent = retirementCur
                        )
                        wealthDao.insertSnapshot(newSnapshot)
                    }
                }
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }

    private fun sendSyncNotification(context: Context, updatedCount: Int) {
        try {
            val channelId = "wealth_tracker_sync_channel"
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
                ?: return

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    channelId,
                    "Wealth Tracker Sync",
                    android.app.NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notifications for daily 6:00 AM portfolio refresh"
                }
                notificationManager.createNotificationChannel(channel)
            }

            val timeStr = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())
            val notification = androidx.core.app.NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle("Wealth Tracker Refreshed")
                .setContentText("Daily portfolio refresh completed at $timeStr.")
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(1001, notification)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
