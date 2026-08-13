package com.prajwal.utilities.tools.wealthtracker.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import com.prajwal.utilities.tools.wealthtracker.data.db.HoldingEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddManualHoldingDialog(
    holdingToEdit: HoldingEntity? = null,
    onDismiss: () -> Unit,
    onSave: (HoldingEntity) -> Unit
) {
    var instrumentType by remember { mutableStateOf(holdingToEdit?.instrumentType ?: "PF") }
    var name by remember { mutableStateOf(holdingToEdit?.name ?: "") }
    var isPassive by remember { mutableStateOf(holdingToEdit?.isPassive ?: false) }
    
    fun fmt(v: Double) = if (v > 0) { if (v % 1.0 == 0.0) v.toLong().toString() else v.toString() } else ""
    var investedStr by remember { mutableStateOf(holdingToEdit?.let { fmt(it.investedAmount) } ?: "") }
    var currentStr by remember { mutableStateOf(holdingToEdit?.let { fmt(it.latestPrice) } ?: "") }

    val instrumentTypes = listOf("PF", "NPS", "Other")
    var instrumentExpanded by remember { mutableStateOf(false) }
    var saveAttempted by remember { mutableStateOf(false) }

    val nameError = saveAttempted && name.isBlank()
    val investedError = saveAttempted && (investedStr.toDoubleOrNull() ?: -1.0) < 0.0
    val currentError = saveAttempted && (currentStr.toDoubleOrNull() ?: -1.0) < 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (holdingToEdit != null) "Edit Manual Holding" else "Add Manual Holding") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExposedDropdownMenuBox(
                    expanded = instrumentExpanded,
                    onExpandedChange = { instrumentExpanded = !instrumentExpanded }
                ) {
                    OutlinedTextField(
                        value = instrumentType,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Type") },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = instrumentExpanded,
                        onDismissRequest = { instrumentExpanded = false }
                    ) {
                        instrumentTypes.forEach { it ->
                            DropdownMenuItem(
                                text = { Text(it) },
                                onClick = { instrumentType = it; instrumentExpanded = false }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; saveAttempted = false },
                    label = { Text("Name (e.g. EPF Account)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = nameError,
                    supportingText = if (nameError) { { Text("Required") } } else null
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isPassive,
                        onCheckedChange = { isPassive = it }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Passive Fund (Index/ETF)", style = MaterialTheme.typography.bodyMedium)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = investedStr,
                        onValueChange = { investedStr = it; saveAttempted = false },
                        label = { Text("Invested (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        isError = investedError,
                        supportingText = if (investedError) { { Text("Required") } } else null
                    )

                    OutlinedTextField(
                        value = currentStr,
                        onValueChange = { currentStr = it; saveAttempted = false },
                        label = { Text("Current Value (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        isError = currentError,
                        supportingText = if (currentError) { { Text("Required") } } else null
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    saveAttempted = true
                    val i = investedStr.toDoubleOrNull() ?: -1.0
                    val c = currentStr.toDoubleOrNull() ?: -1.0

                    if (name.isNotBlank() && i >= 0 && c >= 0) {
                        val newHolding = HoldingEntity(
                            id = holdingToEdit?.id ?: 0,
                            assetClass = "Retirement",
                            instrumentType = instrumentType,
                            name = name.trim(),
                            identifier = name.trim(),
                            exchange = null,
                            unitsHeld = 1.0,
                            investedAmount = i,
                            latestPrice = c,
                            lastUpdatedAt = System.currentTimeMillis(),
                            isManual = true,
                            isPassive = isPassive
                        )
                        onSave(newHolding)
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
