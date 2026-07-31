package com.prajwal.utilities.ui

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.prajwal.utilities.core.navigation.AppNavHost
import com.prajwal.utilities.core.theme.UtilitiesTheme
import com.prajwal.utilities.tools.wealthtracker.worker.PortfolioSyncWorker
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        scheduleDailyPortfolioSync()

        setContent {
            UtilitiesTheme {
                AppNavHost()
            }
        }
    }

    private fun scheduleDailyPortfolioSync() {
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        var nextSixAM = now.withHour(6).withMinute(0).withSecond(0).withNano(0)
        if (now.isAfter(nextSixAM)) {
            nextSixAM = nextSixAM.plusDays(1)
        }
        val initialDelayMillis = Duration.between(now, nextSixAM).toMillis()

        val syncRequest = PeriodicWorkRequestBuilder<PortfolioSyncWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "WealthTrackerSync",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }
}
