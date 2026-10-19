package com.prajwal.utilities.tools.challantracker.karnatakaone

import android.webkit.JavascriptInterface

/**
 * JavaScript interface bridge exposed to Karnataka One portal inside Android WebView.
 * Receives scraped violation data, detected image popup URLs, and DOM state updates.
 */
class KarnatakaOneBridge(
    private val onViolationsScrapedCallback: (vehicleNo: String, rawJson: String) -> Unit,
    private val onImageCapturedCallback: (challanNo: String, imageUrl: String) -> Unit,
    private val onStatusUpdateCallback: (message: String) -> Unit,
    private val onTableDetectedCallback: (tableCount: Int) -> Unit
) {
    @JavascriptInterface
    fun onViolationsScraped(vehicleNo: String, json: String) {
        onViolationsScrapedCallback(vehicleNo, json)
    }

    @JavascriptInterface
    fun onImageCaptured(challanNo: String, imageUrl: String) {
        onImageCapturedCallback(challanNo, imageUrl)
    }

    @JavascriptInterface
    fun onStatusUpdate(message: String) {
        onStatusUpdateCallback(message)
    }

    @JavascriptInterface
    fun onTableDetected(tableCount: Int) {
        onTableDetectedCallback(tableCount)
    }
}
