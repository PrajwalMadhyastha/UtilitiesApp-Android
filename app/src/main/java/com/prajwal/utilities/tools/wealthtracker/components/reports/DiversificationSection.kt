package com.prajwal.utilities.tools.wealthtracker.components.reports

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.prajwal.utilities.tools.wealthtracker.data.AssetClass
import com.prajwal.utilities.tools.wealthtracker.data.MilestoneCalculator

val assetColors = listOf(
    Color(0xFF6366F1), // Equity — indigo
    Color(0xFFF59E0B), // Gold — amber
    Color(0xFF10B981), // Debt — emerald
    Color(0xFF94A3B8), // Silver — slate
    Color(0xFFEC4899), // REITs — pink
    Color(0xFF06B6D4)  // Retirement — cyan
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiversificationSection(
    showInvested: Boolean,
    onShowInvestedChange: (Boolean) -> Unit,
    assetClasses: List<AssetClass>,
    totalForPct: Double
) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

            // ── Invested / Current toggle ───────────────────────────────
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !showInvested,
                    onClick = { onShowInvestedChange(false) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) { Text("Current Value") }
                SegmentedButton(
                    selected = showInvested,
                    onClick = { onShowInvestedChange(true) },
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

@Composable
fun DonutChart(
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
