package com.prajwal.utilities.tools.challantracker.karnatakaone

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.prajwal.utilities.tools.challantracker.model.Vehicle

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun KarnatakaOneSyncScreen(
    vehicles: List<Vehicle>,
    selectedVehicleRegNo: String?,
    savedPhoneNumber: String,
    statusMessage: String?,
    detectedTablesCount: Int,
    onSelectVehicle: (String) -> Unit,
    onViolationsScraped: (vehicleNo: String, json: String) -> Unit,
    onImageCaptured: (challanNo: String, imageUrl: String) -> Unit,
    onStatusUpdate: (String) -> Unit,
    onTableDetected: (Int) -> Unit,
    onSavePhone: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var pageProgress by remember { mutableIntStateOf(0) }
    var showHelp by remember { mutableStateOf(false) }
    var activeVehicle by remember { mutableStateOf(selectedVehicleRegNo ?: vehicles.firstOrNull()?.registrationNumber ?: "") }

    val context = LocalContext.current

    val bridge = remember {
        KarnatakaOneBridge(
            onViolationsScrapedCallback = { vehicleNo, json ->
                val target = if (vehicleNo.isNotBlank()) vehicleNo else activeVehicle
                onViolationsScraped(target, json)
            },
            onImageCapturedCallback = { challanNo, imageUrl ->
                onImageCaptured(challanNo, imageUrl)
            },
            onStatusUpdateCallback = { msg ->
                onStatusUpdate(msg)
            },
            onTableDetectedCallback = { count ->
                onTableDetected(count)
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Karnataka One Sync",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "BTP Camera Photos & Challans",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close")
                    }
                },
                actions = {
                    IconButton(onClick = { showHelp = !showHelp }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Help",
                            tint = if (showHelp) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { webViewRef?.reload() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Loading Progress Bar
            if (pageProgress in 1..99) {
                LinearProgressIndicator(
                    progress = { pageProgress / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Quick Guidance Accordion
            AnimatedVisibility(visible = showHelp) {
                Card(
                    shape = RoundedCornerShape(0.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Karnataka One Assisted Flow",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "1. Click Quick Pay -> Bengaluru -> Collection of Traffic Police Violation Fine.\n" +
                                    "2. Enter Mobile number & OTP received on your phone.\n" +
                                    "3. Tap a vehicle chip below to autofill your plate number -> Submit.\n" +
                                    "4. When the violation table appears, tap 'Extract & Save' to capture violations + real camera photos!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Top Control Action Bar
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    // Vehicle Selector Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Vehicle:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        vehicles.forEach { vehicle ->
                            val isSelected = vehicle.registrationNumber == activeVehicle
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    activeVehicle = vehicle.registrationNumber
                                    onSelectVehicle(vehicle.registrationNumber)
                                    // Inject JavaScript to autofill this vehicle into the registration field!
                                    webViewRef?.evaluateJavascript(
                                        KarnatakaOneScripts.getFillVehicleScript(vehicle.registrationNumber),
                                        null
                                    )
                                },
                                label = { Text(vehicle.nickname.ifBlank { vehicle.formattedPlate }) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.DirectionsCar,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }

                        // Autofill Phone Chip if phone saved
                        if (savedPhoneNumber.isNotBlank()) {
                            OutlinedButton(
                                onClick = {
                                    webViewRef?.evaluateJavascript(
                                        KarnatakaOneScripts.getFillMobileScript(savedPhoneNumber),
                                        null
                                    )
                                },
                                contentPadding = ButtonDefaults.TextButtonContentPadding,
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Fill Phone ($savedPhoneNumber)", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Scrape Action Button + Status Pill
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                webViewRef?.evaluateJavascript(
                                    KarnatakaOneScripts.getScrapeViolationsScript(activeVehicle),
                                    null
                                )
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                            modifier = Modifier.height(38.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Extract & Save Violations", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }

                        // Status message badge
                        statusMessage?.let { msg ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .background(
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = msg.take(30),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
            }

            // Embedded WebView
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            webViewRef = this

                            val cookieManager = CookieManager.getInstance()
                            cookieManager.setAcceptCookie(true)
                            cookieManager.setAcceptThirdPartyCookies(this, true)

                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                cacheMode = WebSettings.LOAD_DEFAULT
                                setSupportMultipleWindows(true)
                                javaScriptCanOpenWindowsAutomatically = true
                                loadWithOverviewMode = true
                                useWideViewPort = true
                                builtInZoomControls = true
                                displayZoomControls = false

                                // Modern mobile browser User-Agent
                                userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                            }

                            addJavascriptInterface(bridge, "KarnatakaOneBridge")

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    pageProgress = 15
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    pageProgress = 100
                                    // Inject assistant routines into the loaded page
                                    view?.evaluateJavascript(KarnatakaOneScripts.INJECTION_SCRIPT, null)

                                    // Pre-fill phone if available and on mobile entry step
                                    if (savedPhoneNumber.isNotBlank()) {
                                        view?.evaluateJavascript(KarnatakaOneScripts.getFillMobileScript(savedPhoneNumber), null)
                                    }
                                }

                                override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                                    val url = request?.url?.toString() ?: ""
                                    // Intercept direct photo downloads/views to register photo URL
                                    if (url.endsWith(".jpg", ignoreCase = true) ||
                                        url.endsWith(".png", ignoreCase = true) ||
                                        url.endsWith(".jpeg", ignoreCase = true) ||
                                        url.contains("GetImage", ignoreCase = true) ||
                                        url.contains("ShowImage", ignoreCase = true)) {
                                        onImageCaptured("", url)
                                    }
                                    return false
                                }
                            }

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    pageProgress = newProgress
                                }
                            }

                            loadUrl(KarnatakaOneScripts.KARNATAKA_ONE_HOME_URL)
                        }
                    },
                    update = { view ->
                        webViewRef = view
                    }
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.stopLoading()
            webViewRef?.destroy()
            webViewRef = null
        }
    }
}
