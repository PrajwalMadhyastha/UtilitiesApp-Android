package com.prajwal.utilities.tools.wealthtracker.components.reports

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.prajwal.utilities.tools.wealthtracker.data.MilestoneCalculator
import com.prajwal.utilities.tools.wealthtracker.data.db.HoldingEntity
import kotlin.math.pow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WealthDrainSection(
    expenseRatioSyncFailed: Boolean,
    fundFilter: Int,
    onFundFilterChange: (Int) -> Unit,
    filteredMfsWithExpense: List<HoldingEntity>,
    expectedReturnPercent: Double
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            val filters = listOf("All", "Active", "Passive")
            filters.forEachIndexed { index, label ->
                SegmentedButton(
                    selected = fundFilter == index,
                    onClick = { onFundFilterChange(index) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = filters.size)
                ) { Text(label, style = MaterialTheme.typography.labelSmall) }
            }
        }

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

        // 10-Year Projected Wealth Drain Card
        Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                
                var totalValue = 0.0
                var totalDrain10Y = 0.0
                
                filteredMfsWithExpense.forEach { mf ->
                    val currentVal = mf.unitsHeld * mf.latestPrice
                    totalValue += currentVal
                    val fvWithoutExpense = currentVal * (1.0 + expectedReturnPercent / 100.0).pow(10)
                    val fvWithExpense = currentVal * (1.0 + (expectedReturnPercent - mf.expenseRatio) / 100.0).pow(10)
                    totalDrain10Y += (fvWithoutExpense - fvWithExpense)
                }

                var expandedProjected by remember { mutableStateOf(false) }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("10-Year Projected Wealth Drain", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "-${MilestoneCalculator.formatInrExact(totalDrain10Y)}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (filteredMfsWithExpense.isNotEmpty()) {
                        TextButton(onClick = { expandedProjected = !expandedProjected }) {
                            Text(if (expandedProjected) "Hide" else "Show Details")
                        }
                    }
                }

                AnimatedVisibility(visible = expandedProjected) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        HorizontalDivider()

                        if (filteredMfsWithExpense.isEmpty()) {
                            Text(
                                "No funds match the selected filter.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            filteredMfsWithExpense.sortedByDescending { it.expenseRatio }.forEachIndexed { _, asset ->
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
            }
        }
        
        // Current Fee Run-Rate Card
        Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), elevation = CardDefaults.cardElevation(2.dp)) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Current Fee Run-Rate", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                
                var totalAnnualFee = 0.0
                filteredMfsWithExpense.forEach { mf ->
                    val currentVal = mf.unitsHeld * mf.latestPrice
                    val annualFee = currentVal * (mf.expenseRatio / 100.0)
                    totalAnnualFee += annualFee
                }
                
                val totalMonthlyFee = totalAnnualFee / 12.0
                
                var expandedRunRate by remember { mutableStateOf(false) }
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Column {
                            Text("Monthly Fees", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                MilestoneCalculator.formatInrExact(totalMonthlyFee),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Column {
                            Text("Annual Fees", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                MilestoneCalculator.formatInrExact(totalAnnualFee),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    if (filteredMfsWithExpense.isNotEmpty()) {
                        TextButton(onClick = { expandedRunRate = !expandedRunRate }) {
                            Text(if (expandedRunRate) "Hide" else "Show Details")
                        }
                    }
                }
                
                AnimatedVisibility(visible = expandedRunRate) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        HorizontalDivider()
                        
                        if (filteredMfsWithExpense.isEmpty()) {
                            Text(
                                "No funds match the selected filter.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            filteredMfsWithExpense.sortedByDescending { (it.unitsHeld * it.latestPrice) * (it.expenseRatio / 100.0) }.forEach { asset ->
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
        }
    }
}
