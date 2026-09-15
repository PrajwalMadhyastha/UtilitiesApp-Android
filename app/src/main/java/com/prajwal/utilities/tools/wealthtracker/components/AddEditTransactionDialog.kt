package com.prajwal.utilities.tools.wealthtracker.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.prajwal.utilities.tools.wealthtracker.data.MilestoneCalculator
import com.prajwal.utilities.tools.wealthtracker.data.db.TransactionEntity
import com.prajwal.utilities.tools.wealthtracker.data.db.TransactionType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditTransactionDialog(
    holdingName: String,
    initialTransaction: TransactionEntity? = null,
    onDismiss: () -> Unit,
    onSave: (TransactionEntity) -> Unit
) {
    var type by remember { mutableStateOf(initialTransaction?.type ?: TransactionType.BUY) }
    var unitsStr by remember { mutableStateOf(initialTransaction?.units?.toString() ?: "") }
    var priceStr by remember { mutableStateOf(initialTransaction?.pricePerUnit?.toString() ?: "") }
    var selectedDateMillis by remember {
        mutableLongStateOf(initialTransaction?.timestamp ?: System.currentTimeMillis())
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var saveAttempted by remember { mutableStateOf(false) }

    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    val units = unitsStr.toDoubleOrNull() ?: 0.0
    val price = priceStr.toDoubleOrNull() ?: 0.0
    val totalAmount = units * price

    val unitsError = saveAttempted && units <= 0.0
    val priceError = saveAttempted && price <= 0.0

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        // Preserve current time-of-day so order within the same day is maintained
                        val nowCalendar = java.util.Calendar.getInstance()
                        val selectedCalendar = java.util.Calendar.getInstance().apply { timeInMillis = it }
                        selectedCalendar.set(java.util.Calendar.HOUR_OF_DAY, nowCalendar.get(java.util.Calendar.HOUR_OF_DAY))
                        selectedCalendar.set(java.util.Calendar.MINUTE, nowCalendar.get(java.util.Calendar.MINUTE))
                        selectedCalendar.set(java.util.Calendar.SECOND, nowCalendar.get(java.util.Calendar.SECOND))
                        selectedDateMillis = selectedCalendar.timeInMillis
                    }
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (initialTransaction == null) "Add Transaction" else "Edit Transaction")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = holdingName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Transaction Type Selector (BUY / SELL)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = type == TransactionType.BUY,
                        onClick = { type = TransactionType.BUY },
                        label = { Text("BUY (Invest)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF16A34A).copy(alpha = 0.15f),
                            selectedLabelColor = Color(0xFF16A34A)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = type == TransactionType.SELL,
                        onClick = { type = TransactionType.SELL },
                        label = { Text("SELL (Exit)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                // Date Picker field
                OutlinedCard(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Transaction Date", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(dateFmt.format(Date(selectedDateMillis)), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = "Select Date",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Units Field
                OutlinedTextField(
                    value = unitsStr,
                    onValueChange = { unitsStr = it; saveAttempted = false },
                    label = { Text("Number of Units") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = unitsError,
                    supportingText = if (unitsError) { { Text("Enter a valid number of units > 0") } } else null,
                    modifier = Modifier.fillMaxWidth()
                )

                // Price per Unit Field
                OutlinedTextField(
                    value = priceStr,
                    onValueChange = { priceStr = it; saveAttempted = false },
                    label = { Text("Price per Unit (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = priceError,
                    supportingText = if (priceError) { { Text("Enter a valid price > 0") } } else null,
                    modifier = Modifier.fillMaxWidth()
                )

                // Total Amount preview card
                if (units > 0 && price > 0) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Total Amount:", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                MilestoneCalculator.formatInrExact(totalAmount, showDecimals = true),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (type == TransactionType.BUY) Color(0xFF16A34A) else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    saveAttempted = true
                    if (units > 0 && price > 0) {
                        onSave(
                            TransactionEntity(
                                id = initialTransaction?.id ?: 0,
                                holdingId = initialTransaction?.holdingId ?: 0,
                                timestamp = selectedDateMillis,
                                units = units,
                                pricePerUnit = price,
                                type = type
                            )
                        )
                    }
                }
            ) {
                Text(if (initialTransaction == null) "Add" else "Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
