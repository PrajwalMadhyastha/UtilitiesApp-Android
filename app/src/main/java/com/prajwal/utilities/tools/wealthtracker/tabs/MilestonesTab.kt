package com.prajwal.utilities.tools.wealthtracker.tabs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.prajwal.utilities.tools.wealthtracker.data.CalculatorSettings
import com.prajwal.utilities.tools.wealthtracker.data.MilestoneCalculator
import com.prajwal.utilities.tools.wealthtracker.data.db.AssetSnapshotEntity

private val milestoneTargets = listOf(
    Triple("1 Cr", 1_00_00_000.0, Color(0xFF6366F1)),
    Triple("5 Cr", 5_00_00_000.0, Color(0xFFF59E0B)),
    Triple("10 Cr", 10_00_00_000.0, Color(0xFFEC4899))
)

@Composable
fun MilestonesTab(
    latestSnapshot: AssetSnapshotEntity?,
    settings: CalculatorSettings,
    includeRetirement: Boolean,
    onMonthlyInvestmentChange: (Double) -> Unit,
    onStepupChange: (Double) -> Unit,
    onStepupMonthChange: (Int) -> Unit,
    onReturnChange: (Double) -> Unit
) {
    // FIX #20: Show a clear empty state instead of a projection based on ₹0.
    // A new user who has added holdings but hasn't saved a snapshot would see confusing
    // ">30 yr" milestone estimates for everything without this guard.
    if (latestSnapshot == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
                Text(
                    "No portfolio snapshot yet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Go to the Portfolio tab, sync your live prices, then tap \"Save Live Snapshot\" to capture your current portfolio value. Your milestone projections will appear here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
        return
    }

    val currentPortfolioValue = latestSnapshot.getTotalCurrent(includeRetirement)
    val totalInvested = latestSnapshot.getTotalInvested(includeRetirement)


    var monthlyText by remember(settings.monthlyInvestment) {
        mutableStateOf(settings.monthlyInvestment.toLong().toString())
    }
    var stepupText by remember(settings.annualStepupPercent) {
        mutableStateOf(settings.annualStepupPercent.toString())
    }
    var returnText by remember(settings.expectedReturnPercent) {
        mutableStateOf(settings.expectedReturnPercent.toString())
    }

    val results = remember(currentPortfolioValue, settings) {
        MilestoneCalculator.calculate(
            currentPortfolioValue = currentPortfolioValue,
            monthlySip = settings.monthlyInvestment,
            annualStepupPct = settings.annualStepupPercent,
            stepupMonth = settings.stepupMonth,
            annualReturnPct = settings.expectedReturnPercent,
            targets = milestoneTargets.map { it.second }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Current portfolio value ────────────────────────────────────
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Current Value", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            MilestoneCalculator.formatInrExact(currentPortfolioValue),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (latestSnapshot != null) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Total Invested", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                MilestoneCalculator.formatInrExact(totalInvested),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    } else {
                        Text(
                            "No snapshot yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // ── Calculator settings ───────────────────────────────────────
        item {
            Text(
                "Calculator Settings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        item {
            Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SettingField(
                        label = "Monthly SIP",
                        value = monthlyText,
                        suffix = "₹ / month",
                        onValueChange = { monthlyText = it },
                        onDone = { monthlyText.toDoubleOrNull()?.let(onMonthlyInvestmentChange) }
                    )
                    SettingField(
                        label = "Annual Step-Up",
                        value = stepupText,
                        suffix = "% per year",
                        onValueChange = { stepupText = it },
                        onDone = { stepupText.toDoubleOrNull()?.let(onStepupChange) }
                    )
                    MonthDropdownField(
                        label = "Step-Up Month",
                        selectedMonth = settings.stepupMonth,
                        onMonthSelected = onStepupMonthChange
                    )
                    SettingField(
                        label = "Expected Annual Return",
                        value = returnText,
                        suffix = "% p.a.",
                        onValueChange = { returnText = it },
                        onDone = { returnText.toDoubleOrNull()?.let(onReturnChange) }
                    )
                }
            }
        }

        // ── Milestone cards ───────────────────────────────────────────
        item {
            Text(
                "Milestones",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                milestoneTargets.forEachIndexed { i, (label, target, color) ->
                    val result = results.getOrNull(i)
                    MilestoneCard(
                        modifier = Modifier.weight(1f),
                        label = label,
                        color = color,
                        currentValue = currentPortfolioValue,
                        targetValue = target,
                        result = result
                    )
                }
            }
        }

        // ── Projection chart ─────────────────────────────────────────
        item {
            Text(
                "Portfolio Projection",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                "Growth curve with milestone crossover markers",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val projectionPoints = results.firstOrNull()?.projectionPoints ?: emptyList()
                    if (projectionPoints.size >= 2) {
                        ProjectionChart(
                            points = projectionPoints.map { it.portfolioValue.toFloat() },
                            milestones = milestoneTargets.map { (label, value, color) ->
                                Triple(label, value.toFloat(), color)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        // Legend
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            milestoneTargets.forEach { (label, _, color) ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        modifier = Modifier.size(10.dp),
                                        shape = androidx.compose.foundation.shape.CircleShape,
                                        color = color
                                    ) {}
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "₹$label",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Configure settings above to see projection.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingField(
    label: String,
    value: String,
    suffix: String,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
            Text(suffix, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                it.toDoubleOrNull()?.let { _ -> onDone() }
            },
            modifier = Modifier.width(130.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonthDropdownField(
    label: String,
    selectedMonth: Int,
    onMonthSelected: (Int) -> Unit
) {
    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
            Text("Month", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.width(130.dp)
        ) {
            OutlinedTextField(
                value = months.getOrElse(selectedMonth - 1) { "Apr" },
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier.menuAnchor(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                months.forEachIndexed { index, month ->
                    DropdownMenuItem(
                        text = { Text(month) },
                        onClick = {
                            onMonthSelected(index + 1)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MilestoneCard(
    modifier: Modifier = Modifier,
    label: String,
    color: Color,
    currentValue: Double,
    targetValue: Double,
    result: MilestoneCalculator.MilestoneResult?
) {
    val alreadyReached = currentValue >= targetValue

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "₹$label",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            if (alreadyReached) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Reached",
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    "Reached",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF10B981),
                    fontWeight = FontWeight.Bold
                )
            } else if (result != null && result.monthsToReach > 0 && result.monthsToReach <= 360) {
                val years = result.monthsToReach / 12
                val months = result.monthsToReach % 12
                
                val calendar = java.util.Calendar.getInstance()
                calendar.add(java.util.Calendar.MONTH, result.monthsToReach)
                val dateFmt = java.text.SimpleDateFormat("MMM yyyy", java.util.Locale.getDefault())
                val targetDateStr = dateFmt.format(calendar.time)

                Text(
                    buildString {
                        if (years > 0) append("${years}y ")
                        if (months > 0) append("${months}m")
                    }.trim(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "by $targetDateStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(">30 yrs", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("to reach", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * Projection chart with:
 *  - Right-side Y labels: milestone dashed lines labeled with ₹ amount
 *  - Bottom X labels: "Now", "5y", "10y" ... every 5 years
 *  - Filled curve showing projected portfolio value
 *  - Colored crossover dots + "Yr N" callouts where curve hits each milestone
 */
@Composable
private fun ProjectionChart(
    points: List<Float>,
    milestones: List<Triple<String, Float, Color>>,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val axisStyle = MaterialTheme.typography.labelSmall
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    val density = androidx.compose.ui.platform.LocalDensity.current
    val paddingTop = with(density) { 16.dp.toPx() }
    val paddingBottom = with(density) { 16.dp.toPx() }
    val paddingLeft = with(density) { 4.dp.toPx() }
    val paddingRight = with(density) { 40.dp.toPx() }

    Canvas(modifier = modifier.clipToBounds()) {
        if (points.size < 2) return@Canvas

        val chartWidth = size.width - paddingLeft - paddingRight
        val chartHeight = size.height - paddingTop - paddingBottom
        val n = points.size

        val maxProjection = points.maxOrNull() ?: 1f
        val highestMilestone = milestones.maxOfOrNull { it.second } ?: 1f
        val visibleMilestones = milestones.filter { it.second <= maxProjection * 1.5f }
        
        // Cap the Y-axis to 1.2x the highest milestone to prevent a giant empty space at the top
        val maxVal = minOf(maxProjection, highestMilestone * 1.2f).coerceAtLeast(1f)

        fun xOf(i: Int) = paddingLeft + i.toFloat() / (n - 1).coerceAtLeast(1) * chartWidth
        fun yOf(v: Float): Float {
            val ratio = (v / maxVal).coerceAtLeast(0f)
            return paddingTop + chartHeight * (1f - ratio)
        }

        // ── Milestone dashed lines + right-side amount labels ─────────
        visibleMilestones.forEach { (label, value, color) ->
            val y = yOf(value)
            drawLine(
                color = color.copy(alpha = 0.35f),
                start = Offset(paddingLeft, y),
                end = Offset(size.width - paddingRight, y),
                strokeWidth = 1.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 5f))
            )
            val labelLayout = textMeasurer.measure(
                "₹$label",
                style = axisStyle.copy(color = color, fontWeight = FontWeight.Bold)
            )
            drawText(
                textLayoutResult = labelLayout,
                topLeft = Offset(
                    x = size.width - paddingRight + 6f,
                    y = y - labelLayout.size.height / 2f
                )
            )
        }

        // ── X-axis ticks + year labels every 5 years ──────────────────
        val yearStep = 5
        for (year in 0 until n step yearStep) {
            val x = xOf(year)
            drawLine(
                color = Color.Gray.copy(alpha = 0.25f),
                start = Offset(x, paddingTop + chartHeight),
                end = Offset(x, paddingTop + chartHeight + 5f),
                strokeWidth = 1.5f
            )
            val lbl = if (year == 0) "Now" else "${year}y"
            val lblLayout = textMeasurer.measure(lbl, style = axisStyle.copy(color = onSurfaceVariant))
            drawText(
                textLayoutResult = lblLayout,
                topLeft = Offset(
                    x = x - lblLayout.size.width / 2f,
                    y = size.height - lblLayout.size.height
                )
            )
        }
        // Always label the last year if it isn't a multiple of yearStep
        val lastYear = n - 1
        if (lastYear % yearStep != 0) {
            val lastLayout = textMeasurer.measure("${lastYear}y", style = axisStyle.copy(color = onSurfaceVariant))
            drawText(
                textLayoutResult = lastLayout,
                topLeft = Offset(
                    x = xOf(lastYear) - lastLayout.size.width / 2f,
                    y = size.height - lastLayout.size.height
                )
            )
        }

        // ── Projection curve + fill ───────────────────────────────────
        val lineColor = Color(0xFF6366F1)
        val curvePath = Path()
        points.forEachIndexed { i, v ->
            if (i == 0) curvePath.moveTo(xOf(i), yOf(v)) else curvePath.lineTo(xOf(i), yOf(v))
        }
        val fillPath = Path().apply {
            addPath(curvePath)
            lineTo(xOf(n - 1), paddingTop + chartHeight)
            lineTo(paddingLeft, paddingTop + chartHeight)
            close()
        }
        drawPath(fillPath, color = lineColor.copy(alpha = 0.1f))
        drawPath(curvePath, color = lineColor, style = Stroke(width = 3f, cap = StrokeCap.Round))

        // ── Crossover markers: dot + "Yr N" callout ───────────────────
        milestones.forEach { (_, value, color) ->
            if (value > maxVal) return@forEach
            val crossIdx = points.indexOfFirst { it >= value }
            if (crossIdx <= 0 || crossIdx >= n) return@forEach

            val x = xOf(crossIdx)
            val y = yOf(value)

            // Vertical dashed drop from dot to X-axis
            drawLine(
                color = color.copy(alpha = 0.3f),
                start = Offset(x, y),
                end = Offset(x, paddingTop + chartHeight),
                strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f))
            )

            // Halo + solid dot + white core
            drawCircle(color.copy(alpha = 0.2f), radius = 14f, center = Offset(x, y))
            drawCircle(color, radius = 8f, center = Offset(x, y))
            drawCircle(Color.White, radius = 4f, center = Offset(x, y))

            // "Yr N" label above the dot
            val calloutLayout = textMeasurer.measure(
                "Yr $crossIdx",
                style = axisStyle.copy(color = color, fontWeight = FontWeight.Bold)
            )
            drawText(
                textLayoutResult = calloutLayout,
                topLeft = Offset(
                    x = x - calloutLayout.size.width / 2f,
                    y = (y - calloutLayout.size.height - 10f).coerceAtLeast(0f)
                )
            )
        }
    }
}
