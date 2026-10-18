package com.prajwal.utilities.tools.wealthtracker.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.prajwal.utilities.tools.wealthtracker.data.db.WealthDatabase
import com.prajwal.utilities.tools.wealthtracker.data.network.MarketDataRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withPermit

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

            val totalHoldings = holdings.size
            val semaphore = kotlinx.coroutines.sync.Semaphore(6)
            val successCounter = java.util.concurrent.atomic.AtomicInteger(0)
            val failedCounter = java.util.concurrent.atomic.AtomicInteger(0)
            val skippedCounter = java.util.concurrent.atomic.AtomicInteger(0)

            val deferredReports = kotlinx.coroutines.coroutineScope {
                holdings.mapIndexed { index, holding ->
                    async(kotlinx.coroutines.Dispatchers.IO) {
                        if (holding.isManual) {
                            skippedCounter.incrementAndGet()
                            val msg = "Worker: [${index + 1}/$totalHoldings] Skipping manual holding: '${holding.name}'"
                            Log.d("WealthTrackerSync", msg)
                            com.prajwal.utilities.tools.wealthtracker.data.SyncStatusManager.log("D", msg)
                            return@async com.prajwal.utilities.tools.wealthtracker.data.SyncItemReport(
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
                        }

                        semaphore.withPermit {
                            val syncMsg = "Worker: [${index + 1}/$totalHoldings] Syncing ${holding.instrumentType}: '${holding.name}' (${holding.identifier})"
                            Log.d("WealthTrackerSync", syncMsg)
                            com.prajwal.utilities.tools.wealthtracker.data.SyncStatusManager.log("D", syncMsg)

                            val fetchResult = if (holding.instrumentType == "MF") {
                                marketDataRepo.fetchMfNav(holding.identifier, holding.name)
                            } else {
                                marketDataRepo.fetchStockPrice(holding.identifier, holding.exchange)
                            }

                            // Log diagnostic details if any
                            fetchResult.diagnosticLogs.forEach { dLog ->
                                com.prajwal.utilities.tools.wealthtracker.data.SyncStatusManager.log("D", "Worker:   $dLog")
                            }

                            when (fetchResult) {
                                is com.prajwal.utilities.tools.wealthtracker.data.network.PriceFetchResult.Success -> {
                                    val prices = fetchResult.prices
                                    holdingsDao.updatePrice(
                                        id = holding.id,
                                        price = prices.latestPrice,
                                        previousClosePrice = prices.previousClosePrice
                                    )

                                    // Update expense ratio if returned by source (e.g. Moneycontrol) and missing locally
                                    if (holding.instrumentType == "MF") {
                                        val expRatio = prices.expenseRatio
                                        if (expRatio != null && expRatio > 0.0 && expRatio != holding.expenseRatio) {
                                            holdingsDao.updateExpenseRatio(holding.id, expRatio)
                                            val expMsg = "Worker: Updated expense ratio for '${holding.name}': $expRatio% via ${prices.source ?: "source"}"
                                            Log.d("WealthTrackerSync", expMsg)
                                            com.prajwal.utilities.tools.wealthtracker.data.SyncStatusManager.log("D", expMsg)
                                        } else if (holding.expenseRatio <= 0.0) {
                                            try {
                                                val fetchedExp = marketDataRepo.fetchMfExpenseRatio(holding.name, holding.identifier)
                                                if (fetchedExp != null && fetchedExp > 0.0 && fetchedExp != holding.expenseRatio) {
                                                    holdingsDao.updateExpenseRatio(holding.id, fetchedExp)
                                                }
                                            } catch (e: Exception) {
                                                Log.w("WealthTrackerSync", "Worker: Failed expense ratio for '${holding.name}'", e)
                                            }
                                        }
                                    }

                                    successCounter.incrementAndGet()
                                    val dateInfo = prices.asOfDate?.let { " [As of $it]" } ?: ""
                                    val srcInfo = prices.source?.let { " (via $it)" } ?: ""
                                    val successMsg = "Worker: ✓ Updated '${holding.name}' -> ₹${prices.latestPrice}$dateInfo$srcInfo"
                                    Log.i("WealthTrackerSync", successMsg)
                                    com.prajwal.utilities.tools.wealthtracker.data.SyncStatusManager.log("I", successMsg)
                                    com.prajwal.utilities.tools.wealthtracker.data.SyncItemReport(
                                        holdingId = holding.id,
                                        holdingName = holding.name,
                                        identifier = holding.identifier,
                                        instrumentType = holding.instrumentType,
                                        assetClass = holding.assetClass,
                                        status = "SUCCESS",
                                        price = prices.latestPrice,
                                        previousClose = prices.previousClosePrice,
                                        asOfDate = prices.asOfDate,
                                        source = prices.source
                                    )
                                }
                                is com.prajwal.utilities.tools.wealthtracker.data.network.PriceFetchResult.Failure -> {
                                    failedCounter.incrementAndGet()
                                    val failMsg = "Worker: ✗ Failed to update '${holding.name}' (${holding.identifier}): ${fetchResult.errorMessage}"
                                    Log.e("WealthTrackerSync", failMsg)
                                    com.prajwal.utilities.tools.wealthtracker.data.SyncStatusManager.log("E", failMsg)
                                    com.prajwal.utilities.tools.wealthtracker.data.SyncItemReport(
                                        holdingId = holding.id,
                                        holdingName = holding.name,
                                        identifier = holding.identifier,
                                        instrumentType = holding.instrumentType,
                                        assetClass = holding.assetClass,
                                        status = "FAILED",
                                        price = holding.latestPrice,
                                        previousClose = holding.previousClosePrice,
                                        errorMessage = fetchResult.errorMessage
                                    )
                                }
                            }
                        }
                    }
                }
            }

            val itemReports = deferredReports.awaitAll()
            val durationMs = System.currentTimeMillis() - startTime
            val successCount = successCounter.get()
            val failedCount = failedCounter.get()
            val skippedCount = skippedCounter.get()

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
                        var arbitrageInv = 0.0
                        var goldInv = 0.0
                        var debtInv = 0.0
                        var silverInv = 0.0
                        var reitsInv = 0.0
                        var retirementInv = 0.0
                        
                        var equityCur = 0.0
                        var arbitrageCur = 0.0
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
                                "Arbitrage" -> { arbitrageInv += inv; arbitrageCur += cur }
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
                            arbitrageInvested = arbitrageInv,
                            goldInvested = goldInv,
                            debtInvested = debtInv,
                            silverInvested = silverInv,
                            reitsInvested = reitsInv,
                            retirementInvested = retirementInv,
                            equityCurrent = equityCur,
                            arbitrageCurrent = arbitrageCur,
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
