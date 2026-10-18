package com.prajwal.utilities.tools.wealthtracker.components.reports

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.prajwal.utilities.tools.wealthtracker.data.MilestoneCalculator
import com.prajwal.utilities.tools.wealthtracker.data.db.DividendEventEntity
import com.prajwal.utilities.tools.wealthtracker.data.db.HoldingEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DividendsSection(
    holdings: List<HoldingEntity>,
    dividendEvents: List<DividendEventEntity>
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
