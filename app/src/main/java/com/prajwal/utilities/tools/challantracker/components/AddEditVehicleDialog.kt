package com.prajwal.utilities.tools.challantracker.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.prajwal.utilities.tools.challantracker.model.Vehicle
import com.prajwal.utilities.tools.challantracker.model.VehicleType

@Composable
fun AddEditVehicleDialog(
    onDismiss: () -> Unit,
    onSave: (regNo: String, nickname: String, type: VehicleType) -> Unit
) {
    var rawRegNo by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var vehicleType by remember { mutableStateOf(VehicleType.TWO_WHEELER) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val formattedPreview = remember(rawRegNo) {
        val clean = rawRegNo.uppercase().replace("[^A-Z0-9]".toRegex(), "")
        Vehicle(clean, "", VehicleType.TWO_WHEELER).formattedPlate
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Add Vehicle", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = rawRegNo,
                    onValueChange = { input ->
                        val clean = input.uppercase().replace("[^A-Z0-9]".toRegex(), "")
                        if (clean.length <= 10) {
                            rawRegNo = clean
                            errorText = null
                        }
                    },
                    label = { Text("Registration Number") },
                    placeholder = { Text("e.g. KA01AB1234") },
                    supportingText = {
                        if (errorText != null) {
                            Text(errorText!!, color = MaterialTheme.colorScheme.error)
                        } else if (rawRegNo.isNotBlank()) {
                            Text("Preview: $formattedPreview")
                        } else {
                            Text("Standard Indian RC format")
                        }
                    },
                    isError = errorText != null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text("Vehicle Nickname") },
                    placeholder = { Text("e.g. Ather 450X, My Scooter, City") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Vehicle Type",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .selectable(
                                selected = vehicleType == VehicleType.TWO_WHEELER,
                                onClick = { vehicleType = VehicleType.TWO_WHEELER }
                            )
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = vehicleType == VehicleType.TWO_WHEELER,
                            onClick = { vehicleType = VehicleType.TWO_WHEELER }
                        )
                        Icon(
                            imageVector = Icons.Default.TwoWheeler,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("2-Wheeler", style = MaterialTheme.typography.bodyMedium)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .selectable(
                                selected = vehicleType == VehicleType.FOUR_WHEELER,
                                onClick = { vehicleType = VehicleType.FOUR_WHEELER }
                            )
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = vehicleType == VehicleType.FOUR_WHEELER,
                            onClick = { vehicleType = VehicleType.FOUR_WHEELER }
                        )
                        Icon(
                            imageVector = Icons.Default.DirectionsCar,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("4-Wheeler", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val clean = rawRegNo.uppercase().replace("[^A-Z0-9]".toRegex(), "")
                    if (clean.length < 5) {
                        errorText = "Enter a valid registration number"
                    } else {
                        onSave(clean, nickname.ifBlank { formattedPreview }, vehicleType)
                    }
                }
            ) {
                Text("Add Vehicle")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
