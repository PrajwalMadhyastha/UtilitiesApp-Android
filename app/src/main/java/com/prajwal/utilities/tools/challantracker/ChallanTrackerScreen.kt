package com.prajwal.utilities.tools.challantracker

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.prajwal.utilities.tools.challantracker.components.AddEditVehicleDialog
import com.prajwal.utilities.tools.challantracker.components.ChallanSettingsDialog
import com.prajwal.utilities.tools.challantracker.components.ChallanSummaryHeader
import com.prajwal.utilities.tools.challantracker.components.PhotoViewerDialog
import com.prajwal.utilities.tools.challantracker.components.VehicleSelectorBar
import com.prajwal.utilities.tools.challantracker.components.ViolationCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChallanTrackerScreen(
    viewModel: ChallanTrackerViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.userMessage) {
        state.userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearUserMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Traffic Challans", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.setAddVehicleDialogOpen(true) }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Vehicle"
                        )
                    }
                    IconButton(onClick = { viewModel.setSettingsDialogOpen(true) }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        if (state.vehicles.isEmpty()) {
            // Empty state when no vehicles have been added
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.DirectionsCar,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Vehicles Added",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Add your car, scooter, or bike registration number to check for pending Bangalore traffic police fines.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = { viewModel.setAddVehicleDialogOpen(true) },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add Vehicle")
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Summary Header
                item {
                    ChallanSummaryHeader(
                        totalPendingCount = state.totalPendingCount,
                        totalPendingFine = state.totalPendingFine,
                        vehicleCount = state.vehicles.size,
                        isRefreshing = state.isRefreshing,
                        isMockMode = state.isMockMode,
                        onRefreshAll = { viewModel.refreshAllVehicles() },
                        onOpenSettings = { viewModel.setSettingsDialogOpen(true) }
                    )
                }

                // Vehicle Selector Bar
                item {
                    VehicleSelectorBar(
                        vehicles = state.vehicles,
                        selectedVehicleRegNo = state.selectedVehicleRegNo,
                        isRefreshing = state.isRefreshing,
                        refreshingVehicleNo = state.refreshingVehicleNo,
                        onSelectVehicle = { viewModel.selectVehicle(it) },
                        onRefreshVehicle = { viewModel.refreshVehicle(it) },
                        onDeleteVehicle = { viewModel.deleteVehicle(it) },
                        onAddVehicleClick = { viewModel.setAddVehicleDialogOpen(true) }
                    )
                }

                // Violations Section Title
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val filterTitle = state.selectedVehicleRegNo?.let { reg ->
                            val vehicle = state.vehicles.find { it.registrationNumber == reg }
                            "${vehicle?.nickname ?: reg} Violations"
                        } ?: "All Recorded Violations"

                        Text(
                            text = filterTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            text = "${state.challans.size} record${if (state.challans.size != 1) "s" else ""}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                // Challans List
                if (state.challans.isEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircleOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "No Violations Found",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "This vehicle has zero recorded traffic challans. Drive safe!",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(
                        items = state.challans,
                        key = { it.challanNumber }
                    ) { challan ->
                        val vehicle = state.vehicles.find { it.registrationNumber == challan.vehicleNumber }
                        ViolationCard(
                            challan = challan,
                            vehicleNickname = vehicle?.nickname,
                            onPhotoClick = { photoUrl -> viewModel.showPhoto(photoUrl) }
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    // Full-screen Photo Viewer Dialog
    state.activePhotoUrl?.let { photoUrl ->
        PhotoViewerDialog(
            photoUrl = photoUrl,
            onDismiss = { viewModel.dismissPhoto() }
        )
    }

    // Add Vehicle Dialog
    if (state.isAddVehicleDialogOpen) {
        AddEditVehicleDialog(
            onDismiss = { viewModel.setAddVehicleDialogOpen(false) },
            onSave = { regNo, nickname, type ->
                viewModel.addVehicle(regNo, nickname, type)
            }
        )
    }

    // Settings Dialog
    if (state.isSettingsDialogOpen) {
        ChallanSettingsDialog(
            initialApiKey = state.rapidApiKey,
            initialApiHost = state.rapidApiHost,
            initialMockMode = state.isMockMode,
            onDismiss = { viewModel.setSettingsDialogOpen(false) },
            onSave = { key, host, mock ->
                viewModel.saveSettings(key, host, mock)
            }
        )
    }
}
