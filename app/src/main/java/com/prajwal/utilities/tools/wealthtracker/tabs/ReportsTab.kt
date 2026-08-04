package com.prajwal.utilities.tools.wealthtracker.tabs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.prajwal.utilities.tools.wealthtracker.data.MilestoneCalculator
import com.prajwal.utilities.tools.wealthtracker.data.db.AssetSnapshotEntity
import com.prajwal.utilities.tools.wealthtracker.data.toAssetClasses
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min
import kotlin.math.pow

// Color palette for the 6 asset classes
private val assetColors = listOf(
    Color(0xFF6366F1), // Equity — indigo
    Color(0xFFF59E0B), // Gold — amber
    Color(0xFF10B981), // Debt — emerald
    Color(0xFF94A3B8), // Silver — slate
    Color(0xFFEC4899), // REITs — pink
    Color(0xFF06B6D4)  // Retirement — cyan
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsTab(
    snapshots: List<AssetSnapshotEntity>,          // newest-first (for latest diversification)
    snapshotsChronological: List<AssetSnapshotEntity>,  // oldest-first (for growth chart)
    transactions: List<com.prajwal.utilities.tools.wealthtracker.data.db.TransactionEntity>,
    dividendEvents: List<com.prajwal.utilities.tools.wealthtracker.data.db.DividendEventEntity>,
    includeRetirement: Boolean,
    holdings: List<com.prajwal.utilities.tools.wealthtracker.data.db.HoldingEntity>,
    expectedReturnPercent: Double,
    expenseRatioSyncFailed: Boolean
) {
    if (snapshots.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ShowChart,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
                Text("No data yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Add your first snapshot in the Portfolio tab to see reports.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    val latest = snapshots.first()
    val oldest = snapshotsChronological.first()
    val allAssetClasses = latest.toAssetClasses(includeRetirement)

    val totalInvested = latest.getTotalInvested(includeRetirement)
    val totalCurrent = latest.getTotalCurrent(includeRetirement)

    // FIX #23: CAGR / annualised return calculation
    val durationMs = latest.recordedAt - oldest.recordedAt
    val durationYears = durationMs / (365.25 * 24.0 * 3600.0 * 1000.0)
    val absoluteReturnPct = if (totalInvested > 0)
        ((totalCurrent - totalInvested) / totalInvested) * 100.0
    else 0.0
    val cagr: Double? = if (durationYears >= 1.0 && totalInvested > 0)
        ((totalCurrent / totalInvested).pow(1.0 / durationYears) - 1.0) * 100.0
    else null

    // Toggle: show Invested or Current value breakdown in the donut
    var showInvested by remember { mutableStateOf(false) }
    val assetClasses = allAssetClasses.filter { if (showInvested) it.invested > 0 else it.current > 0 }
    val totalForPct = if (showInvested) totalInvested else totalCurrent

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Section 0: Returns ───────────────────────────────────────────────
        item {
            Text("Returns", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        item {
            val returnPositive = absoluteReturnPct >= 0
            val returnColor = if (returnPositive) Color(0xFF16A34A) else MaterialTheme.colorScheme.error
            Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Absolute Return
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = returnColor.copy(alpha = 0.1f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Abs Ret", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "${if (returnPositive) "+" else ""}${"%.2f".format(absoluteReturnPct)}%",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = returnColor
                            )
                        }
                    }
                    // CAGR
                    val cagrPositive = cagr != null && cagr >= 0
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(
                            containerColor = if (cagrPositive) Color(0xFF6366F1).copy(alpha = 0.1f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("CAGR", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (cagr != null) {
                                Text(
                                    "${if (cagr >= 0) "+" else ""}${"%.2f".format(cagr)}%",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (cagrPositive) Color(0xFF6366F1) else MaterialTheme.colorScheme.error
                                )
                            } else {
                                Text(
                                    "—",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "Need 1+ yr of snapshots",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Section A: Diversification ───────────────────────────────────────
        item {
            Text(
                "Diversification",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }


        item {
            Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

                    // ── Invested / Current toggle ───────────────────────────────
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = !showInvested,
                            onClick = { showInvested = false },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) { Text("Current Value") }
                        SegmentedButton(
                            selected = showInvested,
                            onClick = { showInvested = true },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) { Text("Invested") }
                    }

                    // ── Donut chart ──────────────────────────────────────────
                    if (assetClasses.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DonutChart(
                                values = assetClasses.map {
                                    if (showInvested) it.invested.toFloat() else it.current.toFloat()
                                },
                                colors = assetColors.take(assetClasses.size),
                                modifier = Modifier.size(160.dp)
                            )
                            Spacer(Modifier.width(16.dp))
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                assetClasses.forEachIndexed { i, asset ->
                                    val pct = if (totalForPct > 0)
                                        ((if (showInvested) asset.invested else asset.current) / totalForPct * 100) else 0.0
                                    val value = if (showInvested) asset.invested else asset.current
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(assetColors[i])
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Column {
                                            Text(
                                                "${asset.name} — ${String.format("%.1f", pct)}%",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                MilestoneCalculator.formatInrExact(value),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider()

                    // Per-asset gain/loss table
                    assetClasses.forEachIndexed { i, asset ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(assetColors[i])
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(asset.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "${if (asset.gainLoss >= 0) "+" else ""}${MilestoneCalculator.formatInrExact(asset.gainLoss)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (asset.gainLoss >= 0) Color(0xFF10B981) else MaterialTheme.colorScheme.error
                                )
                                Text(
                                    "${String.format("%.1f", asset.gainLossPct)}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Section B: Portfolio Growth Chart ─────────────────────────
        item {
            Text(
                "Portfolio Growth",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                "Invested (dashed) vs Current Value (solid) — like run-rate vs required rate",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(modifier = Modifier.padding(20.dp)) {
                    if (snapshotsChronological.size < 2) {
                        Box(modifier = Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                            Text(
                                "Add at least 2 snapshots to see the growth chart.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        val investedValues = snapshotsChronological.map { it.getTotalInvested(includeRetirement).toFloat() }
                        val currentValues = snapshotsChronological.map { it.getTotalCurrent(includeRetirement).toFloat() }
                        val dates = snapshotsChronological.map { it.recordedAt }

                        GrowthLineChart(
                            investedSeries = investedValues,
                            currentSeries = currentValues,
                            modifier = Modifier.fillMaxWidth().height(200.dp)
                        )

                        Spacer(Modifier.height(12.dp))

                        // Start / end labels
                        val dateFmt = SimpleDateFormat("MMM yy", Locale.getDefault())
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(dateFmt.format(Date(dates.first())), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(dateFmt.format(Date(dates.last())), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Spacer(Modifier.height(8.dp))

                        // Legend
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            LegendItem(color = Color(0xFF6366F1), label = "Invested", dashed = true)
                            LegendItem(color = Color(0xFF10B981), label = "Current Value", dashed = false)
                        }
                    }
                }
            }
        }

        // ── Section C: Wealth Drain Analyzer ──────────────────────────────────
        val mfsWithExpense = holdings.filter { it.instrumentType == "MF" && it.expenseRatio > 0.0 }
        if (mfsWithExpense.isNotEmpty()) {
            item {
                Text(
                    "Wealth Drain Analyzer",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    "10-year projected fee impact on current Mutual Funds based on ${String.format("%.1f", expectedReturnPercent)}% assumed return",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                if (expenseRatioSyncFailed) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "⚠️ Automatic Expense Ratio syncing is currently unavailable due to source changes. Displayed fees may be outdated.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        var totalValue = 0.0
                        var totalDrain10Y = 0.0
                        
                        mfsWithExpense.forEach { mf ->
                            val currentVal = mf.unitsHeld * mf.latestPrice
                            totalValue += currentVal
                            val fvWithoutExpense = currentVal * (1.0 + expectedReturnPercent / 100.0).pow(10)
                            val fvWithExpense = currentVal * (1.0 + (expectedReturnPercent - mf.expenseRatio) / 100.0).pow(10)
                            totalDrain10Y += (fvWithoutExpense - fvWithExpense)
                        }

                        Column {
                            Text("10-Year Projected Wealth Drain", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "-${MilestoneCalculator.formatInrExact(totalDrain10Y)}",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        HorizontalDivider()

                        mfsWithExpense.sortedByDescending { it.expenseRatio }.forEachIndexed { i, asset ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(asset.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    Text("Current Val: ${MilestoneCalculator.formatInrExact(asset.unitsHeld * asset.latestPrice)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(
                                    "${asset.expenseRatio}%",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (asset.expenseRatio > 1.0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
            
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), elevation = CardDefaults.cardElevation(2.dp)) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Current Fee Run-Rate", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        
                        var totalAnnualFee = 0.0
                        mfsWithExpense.forEach { mf ->
                            val currentVal = mf.unitsHeld * mf.latestPrice
                            val annualFee = currentVal * (mf.expenseRatio / 100.0)
                            totalAnnualFee += annualFee
                        }
                        
                        val totalMonthlyFee = totalAnnualFee / 12.0
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Monthly Fees", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    MilestoneCalculator.formatInrExact(totalMonthlyFee),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Annual Fees", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    MilestoneCalculator.formatInrExact(totalAnnualFee),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        
                        HorizontalDivider()
                        
                        mfsWithExpense.sortedByDescending { (it.unitsHeld * it.latestPrice) * (it.expenseRatio / 100.0) }.forEach { asset ->
                            val currentVal = asset.unitsHeld * asset.latestPrice
                            val annualFee = currentVal * (asset.expenseRatio / 100.0)
                            val monthlyFee = annualFee / 12.0
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(asset.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    Text("${asset.expenseRatio}% TER", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        "${MilestoneCalculator.formatInrExact(monthlyFee)} /mo",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        "${MilestoneCalculator.formatInrExact(annualFee)} /yr",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Section D: Dividend Breakdown ──────────────────────────────────
        item {
            Text(
                "Dividend Breakdown",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                "Dividends earned based on current holdings",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            DividendsCard(holdings = holdings, dividendEvents = dividendEvents)
        }
    }
}

@Composable
private fun DonutChart(
    values: List<Float>,
    colors: List<Color>,
    modifier: Modifier = Modifier
) {
    val total = values.sum().takeIf { it > 0f } ?: 1f
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.18f
        val radius = (size.minDimension / 2f) - strokeWidth / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        var startAngle = -90f

        values.forEachIndexed { i, value ->
            val sweep = (value / total) * 360f
            drawArc(
                color = colors.getOrElse(i) { Color.Gray },
                startAngle = startAngle,
                sweepAngle = sweep - 2f, // small gap between segments
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
            )
            startAngle += sweep
        }
    }
}

@Composable
private fun GrowthLineChart(
    investedSeries: List<Float>,
    currentSeries: List<Float>,
    modifier: Modifier = Modifier
) {
    val primaryColor = Color(0xFF6366F1)
    val currentColor = Color(0xFF10B981)

    Canvas(modifier = modifier) {
        if (investedSeries.size < 2) return@Canvas

        val allValues = investedSeries + currentSeries
        // FIX #19: Y-axis must start at 0. Starting at minVal exaggerates growth —
        // a 5% return would look like a near-vertical line on a chart with no zero baseline.
        val minVal = 0f
        val maxVal = allValues.maxOrNull()?.takeIf { it > minVal } ?: (minVal + 1f)
        val n = investedSeries.size

        fun xOf(i: Int) = i.toFloat() / (n - 1) * size.width
        fun yOf(v: Float) = size.height - ((v - minVal) / (maxVal - minVal)) * size.height * 0.9f - size.height * 0.05f

        // Draw invested as dashed line
        val dashLength = 12f
        val gapLength = 6f
        val invPath = Path()
        investedSeries.forEachIndexed { i, v ->
            if (i == 0) invPath.moveTo(xOf(i), yOf(v)) else invPath.lineTo(xOf(i), yOf(v))
        }
        // Draw dashed by segmenting
        for (i in 0 until n - 1) {
            val x1 = xOf(i); val y1 = yOf(investedSeries[i])
            val x2 = xOf(i + 1); val y2 = yOf(investedSeries[i + 1])
            val dx = x2 - x1; val dy = y2 - y1
            val dist = kotlin.math.sqrt(dx * dx + dy * dy)
            var traveled = 0f
            var drawing = true
            while (traveled < dist) {
                val segEnd = min(traveled + if (drawing) dashLength else gapLength, dist)
                val t1 = traveled / dist; val t2 = segEnd / dist
                if (drawing) {
                    drawLine(
                        color = primaryColor.copy(alpha = 0.7f),
                        start = Offset(x1 + dx * t1, y1 + dy * t1),
                        end = Offset(x1 + dx * t2, y1 + dy * t2),
                        strokeWidth = 3f, cap = StrokeCap.Round
                    )
                }
                traveled = segEnd
                drawing = !drawing
            }
        }

        // Draw current value as solid line
        val curPath = Path()
        currentSeries.forEachIndexed { i, v ->
            if (i == 0) curPath.moveTo(xOf(i), yOf(v)) else curPath.lineTo(xOf(i), yOf(v))
        }
        drawPath(curPath, color = currentColor, style = Stroke(width = 3f, cap = StrokeCap.Round))

        // Data point dots on current line
        currentSeries.forEachIndexed { i, v ->
            drawCircle(color = currentColor, radius = 5f, center = Offset(xOf(i), yOf(v)))
            drawCircle(color = Color.White, radius = 3f, center = Offset(xOf(i), yOf(v)))
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String, dashed: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(24.dp, 3.dp)) {
            if (dashed) {
                drawLine(color, Offset(0f, size.height / 2), Offset(size.width / 2, size.height / 2), strokeWidth = 3f)
            } else {
                drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 3f)
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DividendsCard(
    holdings: List<com.prajwal.utilities.tools.wealthtracker.data.db.HoldingEntity>,
    dividendEvents: List<com.prajwal.utilities.tools.wealthtracker.data.db.DividendEventEntity>
) {
    if (dividendEvents.isEmpty()) {
        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), elevation = CardDefaults.cardElevation(2.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "No dividend data yet",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Tap ⋮ → \"Sync Dividends\" to fetch dividend history for your equity holdings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
        return
    }

    var selectedFilter by remember { mutableStateOf(2) } // 0: 1 Month, 1: 6 Months, 2: 1 Year, 3: All Time

    // Compute breakdown and timeLimitMs in a single remember block to avoid stale time captures
    val breakdown = remember(holdings, dividendEvents, selectedFilter) {
        val now = System.currentTimeMillis()
        val cutoffMs = when (selectedFilter) {
            0 -> now - (30L * 24 * 60 * 60 * 1000L)
            1 -> now - (180L * 24 * 60 * 60 * 1000L)
            2 -> now - (365L * 24 * 60 * 60 * 1000L)
            else -> 0L // All Time
        }
        val map = mutableMapOf<String, Double>()
        for (holding in holdings) {
            if (holding.unitsHeld <= 0) continue
            val eventsForSymbol = dividendEvents.filter { it.symbol == holding.identifier }
            val total = com.prajwal.utilities.tools.wealthtracker.data.DividendCalculator
                .calculateDividendsEarned(holding.unitsHeld, eventsForSymbol, cutoffMs)
            if (total > 0) {
                map[holding.name] = total
            }
        }
        map.toList().sortedByDescending { it.second }
    }

    val totalDividends = breakdown.sumOf { it.second }

    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val filters = listOf("1 Month", "6 Months", "1 Year", "All Time")
                filters.forEachIndexed { index, label ->
                    SegmentedButton(
                        selected = selectedFilter == index,
                        onClick = { selectedFilter = index },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = filters.size)
                    ) { Text(label, style = MaterialTheme.typography.labelSmall) }
                }
            }

            Column {
                Text("Total Earned", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    MilestoneCalculator.formatInrExact(totalDividends),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF10B981)
                )
            }

            HorizontalDivider()

            if (breakdown.isEmpty()) {
                Text(
                    "No dividends found in this period.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                breakdown.forEach { (name, amount) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text(
                            MilestoneCalculator.formatInrExact(amount),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

