package com.prajwal.utilities.tools.wealthtracker.data

import android.content.Context
import android.util.Log
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentLinkedQueue

@JsonClass(generateAdapter = true)
data class SyncReport(
    val timestamp: Long = System.currentTimeMillis(),
    val triggerSource: String = "Manual", // "Manual" or "Daily 6 AM Auto-Sync"
    val durationMs: Long = 0L,
    val successCount: Int = 0,
    val failedCount: Int = 0,
    val skippedCount: Int = 0,
    val items: List<SyncItemReport> = emptyList(),
    val rawLogs: List<SyncLogEntry> = emptyList()
)

@JsonClass(generateAdapter = true)
data class SyncItemReport(
    val holdingId: Int = 0,
    val holdingName: String,
    val identifier: String,
    val instrumentType: String,
    val assetClass: String,
    val status: String, // "SUCCESS", "FAILED", "SKIPPED"
    val price: Double = 0.0,
    val previousClose: Double = 0.0,
    val asOfDate: String? = null,
    val errorMessage: String? = null,
    val source: String? = null
)

@JsonClass(generateAdapter = true)
data class SyncLogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val level: String, // "I", "D", "W", "E"
    val message: String
)

object SyncStatusManager {
    private const val PREFS_NAME = "wealth_sync_status_prefs"
    private const val KEY_REPORT = "latest_sync_report"
    private const val MAX_LOGS = 800

    private val _latestReport = MutableStateFlow<SyncReport?>(null)
    val latestReport: StateFlow<SyncReport?> = _latestReport.asStateFlow()

    private val _inMemoryLogs = MutableStateFlow<List<SyncLogEntry>>(emptyList())
    val inMemoryLogs: StateFlow<List<SyncLogEntry>> = _inMemoryLogs.asStateFlow()

    private val logQueue = ConcurrentLinkedQueue<SyncLogEntry>()
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(KEY_REPORT, null)
            if (!json.isNullOrBlank()) {
                val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
                val report = moshi.adapter(SyncReport::class.java).fromJson(json)
                _latestReport.value = report
                if (report != null && report.rawLogs.isNotEmpty()) {
                    logQueue.clear()
                    logQueue.addAll(report.rawLogs)
                    _inMemoryLogs.value = logQueue.toList()
                }
            }
        } catch (e: Exception) {
            Log.e("WealthTrackerSync", "Failed to load cached sync report", e)
        }
    }

    fun log(level: String, message: String) {
        val entry = SyncLogEntry(timestamp = System.currentTimeMillis(), level = level, message = message)
        logQueue.add(entry)
        while (logQueue.size > MAX_LOGS) {
            logQueue.poll()
        }
        _inMemoryLogs.value = logQueue.toList()
    }

    fun saveReport(context: Context, report: SyncReport) {
        val fullReport = report.copy(rawLogs = _inMemoryLogs.value)
        _latestReport.value = fullReport
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            val json = moshi.adapter(SyncReport::class.java).toJson(fullReport)
            prefs.edit().putString(KEY_REPORT, json).apply()
        } catch (e: Exception) {
            Log.e("WealthTrackerSync", "Failed to persist sync report", e)
        }
    }

    fun clearLogs() {
        logQueue.clear()
        _inMemoryLogs.value = emptyList()
    }
}
