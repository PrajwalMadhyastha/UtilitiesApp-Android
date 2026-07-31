package com.prajwal.utilities

import android.app.Application
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.prajwal.utilities.tools.wifiusage.workers.UsageWorker
import java.util.concurrent.TimeUnit

class UsageTrackerApp : Application() {
    
    private val TAG = "UsageTrackerApp"
    
    override fun onCreate() {
        super.onCreate()
        
        Log.d(TAG, "Application started, scheduling background worker")
        scheduleUsageWorker()
        scheduleDailyPortfolioSync()
    }
    
    private fun scheduleUsageWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        val workRequest = PeriodicWorkRequestBuilder<UsageWorker>(
            15, TimeUnit.MINUTES
        ).setConstraints(constraints)
            .build()
        
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "usage_tracker_worker",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
        
        Log.d(TAG, "Background worker scheduled (15-minute intervals)")
    }

    private fun scheduleDailyPortfolioSync() {
        val now = java.time.ZonedDateTime.now(java.time.ZoneId.systemDefault())
        var nextSixAM = now.withHour(6).withMinute(0).withSecond(0).withNano(0)
        if (now.isAfter(nextSixAM)) {
            nextSixAM = nextSixAM.plusDays(1)
        }
        val initialDelayMillis = java.time.Duration.between(now, nextSixAM).toMillis()

        val syncRequest = PeriodicWorkRequestBuilder<com.prajwal.utilities.tools.wealthtracker.worker.PortfolioSyncWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "WealthTrackerSync",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
        Log.d(TAG, "Portfolio sync worker scheduled (Daily 6 AM, initial delay $initialDelayMillis ms)")
    }
}
