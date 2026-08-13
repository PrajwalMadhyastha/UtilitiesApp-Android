package com.prajwal.utilities.tools.wealthtracker.components.reports

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.prajwal.utilities.tools.wealthtracker.data.db.AssetSnapshotEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

@Composable
fun GrowthChartSection(
    snapshotsChronological: List<AssetSnapshotEntity>,
    includeRetirement: Boolean
) {
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

@Composable
fun GrowthLineChart(
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
