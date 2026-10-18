package com.prajwal.utilities.tools.wealthtracker.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.prajwal.utilities.tools.wealthtracker.data.DailyCategoryItem
import com.prajwal.utilities.tools.wealthtracker.data.DailyCategorySummary
import com.prajwal.utilities.tools.wealthtracker.data.MilestoneCalculator
import com.prajwal.utilities.tools.wealthtracker.data.db.HoldingEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
fun DayPLBreakdownCard(
    holdings: List<HoldingEntity>,
    includeRetirement: Boolean = true,
    modifier: Modifier = Modifier,
    initialExpanded: Boolean = true
) {
    var expanded by remember { mutableStateOf(initialExpanded) }
    val rotationState by animateFloatAsState(targetValue = if (expanded) 180f else 0f, label = "arrow_rotation")
    val dateFmt = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }

    val effectiveSummary = remember(holdings, includeRetirement) {
        val lastSyncTime = holdings.filter { !it.isManual && it.latestPrice > 0 }.maxOfOrNull { it.lastUpdatedAt } ?: 0L
        DailyCategorySummary.fromHoldings(holdings, includeRetirement, timestamp = lastSyncTime)
    }

    val categories = effectiveSummary.categories
    val lastUpdatedTimestamp = effectiveSummary.lastUpdatedTimestamp

    // Compute Overall Portfolio Totals
    val totalCur = categories.sumOf { it.currentValue }
    val totalDayGain = categories.sumOf { it.dayGain }
    val totalPrevClose = categories.sumOf { it.prevCloseValue }
    val totalDayGainPct = if (totalPrevClose > 0) (totalDayGain / totalPrevClose) * 100 else 0.0
    val totalPositive = totalDayGain >= 0
    val totalColor = if (totalPositive) Color(0xFF16A34A) else MaterialTheme.colorScheme.error

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Row (Clickable to toggle collapse/expand)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Day's P/L by Category",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    val updateText = if (lastUpdatedTimestamp > 0) {
                        "Last updated: ${dateFmt.format(Date(lastUpdatedTimestamp))}"
                    } else {
                        "Daily overnight summary"
                    }
                    Text(
                        updateText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = totalColor.copy(alpha = 0.12f),
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Text(
                            text = "${if (totalPositive) "+" else "-"}${MilestoneCalculator.formatInrExact(abs(totalDayGain), showDecimals = true)} (${"%.2f".format(abs(totalDayGainPct))}%)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = totalColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    IconButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = if (expanded) "Collapse" else "Expand",
                            modifier = Modifier.rotate(rotationState)
                        )
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Table Column Headers
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "CATEGORY",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1.3f)
                        )
                        Text(
                            "VALUE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                            modifier = Modifier.weight(1.0f)
                        )
                        Text(
                            "DAY'S P/L",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                            modifier = Modifier.weight(1.2f)
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    if (categories.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No holdings to display",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        // Category Rows
                        categories.forEachIndexed { index, item ->
                            val isGainPos = item.dayGain >= 0
                            val rowColor = if (item.dayGain == 0.0) MaterialTheme.colorScheme.onSurfaceVariant
                                else if (isGainPos) Color(0xFF16A34A) else MaterialTheme.colorScheme.error

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Category Col
                                Column(modifier = Modifier.weight(1.3f)) {
                                    Text(
                                        item.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "${item.count} ${if (item.count == 1) "holding" else "holdings"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Value Col
                                Text(
                                    MilestoneCalculator.formatInrExact(item.currentValue),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                    modifier = Modifier.weight(1.0f)
                                )

                                // Day's P/L Col
                                Column(
                                    horizontalAlignment = Alignment.End,
                                    modifier = Modifier.weight(1.2f)
                                ) {
                                    if (item.hasValidPrice) {
                                        Text(
                                            text = "${if (isGainPos) "+" else "-"}${MilestoneCalculator.formatInrExact(abs(item.dayGain), showDecimals = true)}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = rowColor
                                        )
                                        Text(
                                            text = "${if (isGainPos) "+" else ""}${"%.2f".format(item.dayGainPct)}%",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = rowColor
                                        )
                                    } else {
                                        Text(
                                            "—",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            if (index < categories.size - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                )
                            }
                        }

                        // Total Footer Row
                        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1.3f)) {
                                Text(
                                    "Total Portfolio",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "${categories.sumOf { it.count }} total holdings",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Text(
                                MilestoneCalculator.formatInrExact(totalCur),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                modifier = Modifier.weight(1.0f)
                            )

                            Column(
                                horizontalAlignment = Alignment.End,
                                modifier = Modifier.weight(1.2f)
                            ) {
                                Text(
                                    text = "${if (totalPositive) "+" else "-"}${MilestoneCalculator.formatInrExact(abs(totalDayGain), showDecimals = true)}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = totalColor
                                )
                                Text(
                                    text = "${if (totalPositive) "+" else ""}${"%.2f".format(totalDayGainPct)}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = totalColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
