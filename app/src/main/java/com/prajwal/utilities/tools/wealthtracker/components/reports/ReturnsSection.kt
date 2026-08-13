package com.prajwal.utilities.tools.wealthtracker.components.reports

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ReturnsSection(absoluteReturnPct: Double, cagr: Double?) {
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
