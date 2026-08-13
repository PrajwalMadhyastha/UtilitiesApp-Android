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
import com.prajwal.utilities.tools.wealthtracker.data.network.AssetSearchResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddHoldingDialog(
    holdingToEdit: HoldingEntity? = null,
    searchResults: List<AssetSearchResult>,
    onSearchQueryChanged: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
    onSave: (HoldingEntity) -> Unit
) {
    var assetClass by remember { mutableStateOf(holdingToEdit?.assetClass ?: "Equity") }
    var instrumentType by remember { mutableStateOf(holdingToEdit?.instrumentType ?: "Stock") }
    var identifier by remember { mutableStateOf(holdingToEdit?.identifier ?: "") }
    var exchange by remember { mutableStateOf(holdingToEdit?.exchange ?: "NSE") }
    var isPassive by remember { mutableStateOf(holdingToEdit?.isPassive ?: false) }

    fun fmt(v: Double) = if (v > 0) { if (v % 1.0 == 0.0) v.toLong().toString() else v.toString() } else ""
    var unitsStr by remember { mutableStateOf(holdingToEdit?.let { fmt(it.unitsHeld) } ?: "") }
    var investedStr by remember { mutableStateOf(holdingToEdit?.let { fmt(it.investedAmount) } ?: "") }

    val assetClasses = listOf("Equity", "Gold", "Debt", "Silver", "REITs")
    val instrumentTypes = listOf("Stock", "MF", "SGB")
    val exchanges = listOf("NSE", "BSE")

    var assetExpanded by remember { mutableStateOf(false) }
    var instrumentExpanded by remember { mutableStateOf(false) }
    var exchangeExpanded by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf(holdingToEdit?.name ?: "") }
    var searchExpanded by remember { mutableStateOf(false) }

    // FIX #16: Track whether user has attempted save for validation feedback
    var saveAttempted by remember { mutableStateOf(false) }
    val isSgb = instrumentType == "SGB"
    val finalIdentifier = if (identifier.isNotBlank()) identifier else searchQuery.trim()
    val identifierError = saveAttempted && finalIdentifier.isBlank()
    val unitsError = saveAttempted && (unitsStr.toDoubleOrNull() ?: 0.0) <= 0.0
    val investedError = saveAttempted && (investedStr.toDoubleOrNull() ?: 0.0) <= 0.0

    // FIX #21: Only trigger autocomplete search for Stock/MF — not SGB
    LaunchedEffect(searchQuery, instrumentType) {
        if (!isSgb) {
            onSearchQueryChanged(searchQuery, instrumentType == "MF")
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (holdingToEdit != null) "Edit Holding" else "Add Holding") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Asset Class Dropdown
                ExposedDropdownMenuBox(
                    expanded = assetExpanded,
                    onExpandedChange = { assetExpanded = !assetExpanded }
                ) {
                    OutlinedTextField(
                        value = assetClass,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Asset Class") },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = assetExpanded,
                        onDismissRequest = { assetExpanded = false }
                    ) {
                        assetClasses.forEach { ac ->
                            DropdownMenuItem(
                                text = { Text(ac) },
                                onClick = { assetClass = ac; assetExpanded = false }
                            )
                        }
                    }
                }

                // Instrument Type Dropdown
                ExposedDropdownMenuBox(
                    expanded = instrumentExpanded,
                    onExpandedChange = { instrumentExpanded = !instrumentExpanded }
                ) {
                    OutlinedTextField(
                        value = instrumentType,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Instrument Type") },
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

                // Exchange Dropdown (Only for Stock/SGB)
                if (instrumentType != "MF") {
                    ExposedDropdownMenuBox(
                        expanded = exchangeExpanded,
                        onExpandedChange = { exchangeExpanded = !exchangeExpanded }
                    ) {
                        OutlinedTextField(
                            value = exchange,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Exchange") },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = exchangeExpanded,
                            onDismissRequest = { exchangeExpanded = false }
                        ) {
                            exchanges.forEach { ex ->
                                DropdownMenuItem(
                                    text = { Text(ex) },
                                    onClick = { exchange = ex; exchangeExpanded = false }
                                )
                            }
                        }
                    }
                }

                // FIX #21: For SGB, skip Yahoo autocomplete — show plain ticker field instead.
                // SGB tickers are standard NSE codes (e.g. SGBAUG28); Yahoo search returns irrelevant results.
                if (isSgb) {
                    OutlinedTextField(
                        value = identifier,
                        onValueChange = { identifier = it.uppercase(); saveAttempted = false },
                        label = { Text("SGB Ticker (e.g. SGBAUG28)") },
                        placeholder = { Text("SGBAUG28", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = identifierError,
                        supportingText = if (identifierError) {{ Text("Enter the NSE SGB ticker symbol") }} else null
                    )
                } else {
                    // Autocomplete Dropdown for Stock / MF
                    ExposedDropdownMenuBox(
                        expanded = searchExpanded,
                        onExpandedChange = { searchExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it; searchExpanded = true; saveAttempted = false },
                            label = { Text(if (instrumentType == "MF") "Search Mutual Fund" else "Search Stock/ETF") },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            singleLine = true,
                            isError = identifierError,
                            supportingText = if (identifierError) {{ Text("Select a fund or stock from the search results") }} else null
                        )
                        if (searchResults.isNotEmpty()) {
                            DropdownMenu(
                                expanded = searchExpanded,
                                onDismissRequest = { searchExpanded = false },
                                modifier = Modifier.exposedDropdownSize(),
                                properties = androidx.compose.ui.window.PopupProperties(focusable = false)
                            ) {
                                searchResults.forEach { result ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    text = result.name,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    maxLines = 1,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                )
                                                val subtext = buildString {
                                                    append(result.identifier)
                                                    if (result.exchange != null) {
                                                        append(" • ${result.exchange}")
                                                    }
                                                }
                                                Text(
                                                    text = subtext,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        onClick = {
                                            searchQuery = result.name
                                            identifier = result.identifier
                                            if (result.exchange != null) exchange = result.exchange
                                            
                                            val lc = result.name.lowercase()
                                            isPassive = lc.contains("index") || lc.contains("etf") || 
                                                       lc.contains("nifty") || lc.contains("sensex") || 
                                                       lc.contains("equal weight") || lc.contains("next 50")
                                            
                                            searchExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

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
                    // Units
                    OutlinedTextField(
                        value = unitsStr,
                        onValueChange = { unitsStr = it; saveAttempted = false },
                        label = { Text("Units") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        isError = unitsError,
                        supportingText = if (unitsError) {{ Text("Required") }} else null
                    )

                    // Invested Amount
                    OutlinedTextField(
                        value = investedStr,
                        onValueChange = { investedStr = it; saveAttempted = false },
                        label = { Text("Invested (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        isError = investedError,
                        supportingText = if (investedError) {{ Text("Required") }} else null
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    saveAttempted = true
                    val u = unitsStr.toDoubleOrNull() ?: 0.0
                    val i = investedStr.toDoubleOrNull() ?: 0.0
                    val resolvedIdentifier = if (isSgb) identifier.trim() else finalIdentifier

                    if (resolvedIdentifier.isNotBlank() && u > 0 && i > 0) {
                        val newHolding = HoldingEntity(
                            id = holdingToEdit?.id ?: 0,
                            assetClass = assetClass,
                            instrumentType = instrumentType,
                            name = if (isSgb) resolvedIdentifier else searchQuery.trim(),
                            identifier = resolvedIdentifier,
                            exchange = if (instrumentType == "MF") null else exchange,
                            unitsHeld = u,
                            investedAmount = i,
                            latestPrice = holdingToEdit?.latestPrice ?: 0.0,
                            lastUpdatedAt = holdingToEdit?.lastUpdatedAt ?: System.currentTimeMillis(),
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
