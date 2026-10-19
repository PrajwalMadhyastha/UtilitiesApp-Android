package com.prajwal.utilities.tools.challantracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.prajwal.utilities.tools.challantracker.data.ChallanPreferences
import com.prajwal.utilities.tools.challantracker.data.ChallanRepository
import com.prajwal.utilities.tools.challantracker.model.Challan
import com.prajwal.utilities.tools.challantracker.model.Vehicle
import com.prajwal.utilities.tools.challantracker.model.VehicleType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChallanUiState(
    val vehicles: List<Vehicle> = emptyList(),
    val selectedVehicleRegNo: String? = null,
    val challans: List<Challan> = emptyList(),
    val totalPendingFine: Double = 0.0,
    val totalPendingCount: Int = 0,
    val isRefreshing: Boolean = false,
    val refreshingVehicleNo: String? = null,
    val isMockMode: Boolean = true,
    val rapidApiKey: String = "",
    val rapidApiHost: String = ChallanPreferences.DEFAULT_RAPID_API_HOST,
    val rapidApiPath: String = ChallanPreferences.DEFAULT_RAPID_API_PATH,
    val activePhotoUrl: String? = null,
    val isAddVehicleDialogOpen: Boolean = false,
    val isSettingsDialogOpen: Boolean = false,
    val isKarnatakaOneSyncOpen: Boolean = false,
    val savedPhoneNumber: String = "",
    val detectedTablesCount: Int = 0,
    val karnatakaOneStatusMessage: String? = null,
    val userMessage: String? = null
)

class ChallanTrackerViewModel(
    private val repository: ChallanRepository,
    private val preferences: ChallanPreferences
) : ViewModel() {

    private val _internalState = MutableStateFlow(ChallanUiState())

    private data class SettingsBundle(
        val isMock: Boolean,
        val apiKey: String,
        val apiHost: String,
        val apiPath: String,
        val savedPhone: String
    )

    private val settingsFlow = combine(
        preferences.isMockMode,
        preferences.rapidApiKey,
        preferences.rapidApiHost,
        preferences.rapidApiPath,
        preferences.savedPhoneNumber
    ) { isMock, apiKey, apiHost, apiPath, savedPhone ->
        SettingsBundle(isMock, apiKey, apiHost, apiPath, savedPhone)
    }

    val uiState: StateFlow<ChallanUiState> = combine(
        _internalState,
        repository.vehicles,
        repository.allChallans,
        settingsFlow
    ) { internal, vehicles, allChallans, settings ->
        val selected = internal.selectedVehicleRegNo
        val filteredChallans = if (selected != null) {
            allChallans.filter { it.vehicleNumber.equals(selected, ignoreCase = true) }
        } else {
            allChallans
        }

        val pending = allChallans.filter { it.status == com.prajwal.utilities.tools.challantracker.model.ChallanStatus.PENDING }
        val totalPendingCount = pending.size
        val totalPendingFine = pending.sumOf { it.fineAmount }

        internal.copy(
            vehicles = vehicles,
            challans = filteredChallans,
            totalPendingCount = totalPendingCount,
            totalPendingFine = totalPendingFine,
            isMockMode = settings.isMock,
            rapidApiKey = settings.apiKey,
            rapidApiHost = settings.apiHost,
            rapidApiPath = settings.apiPath,
            savedPhoneNumber = settings.savedPhone
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ChallanUiState()
    )

    init {
        viewModelScope.launch {
            repository.removeFillerVehicles()
        }
    }

    fun selectVehicle(regNo: String?) {
        _internalState.update { it.copy(selectedVehicleRegNo = regNo) }
    }

    fun refreshVehicle(regNo: String) {
        viewModelScope.launch {
            _internalState.update {
                it.copy(
                    isRefreshing = true,
                    refreshingVehicleNo = regNo
                )
            }
            val result = repository.refreshChallansForVehicle(regNo)
            _internalState.update { state ->
                state.copy(
                    isRefreshing = false,
                    refreshingVehicleNo = null,
                    userMessage = if (result.isSuccess) {
                        "Refreshed $regNo successfully"
                    } else {
                        result.exceptionOrNull()?.localizedMessage ?: "Failed to refresh $regNo"
                    }
                )
            }
        }
    }

    fun refreshAllVehicles() {
        viewModelScope.launch {
            _internalState.update { it.copy(isRefreshing = true, refreshingVehicleNo = null) }
            val result = repository.refreshAllVehicles()
            _internalState.update { state ->
                state.copy(
                    isRefreshing = false,
                    userMessage = if (result.isSuccess) {
                        "Refreshed all vehicles"
                    } else {
                        result.exceptionOrNull()?.localizedMessage ?: "Failed to refresh vehicles"
                    }
                )
            }
        }
    }

    fun addVehicle(regNo: String, nickname: String, type: VehicleType) {
        viewModelScope.launch {
            val cleanReg = regNo.uppercase().replace("[^A-Z0-9]".toRegex(), "")
            if (cleanReg.isBlank()) {
                _internalState.update { it.copy(userMessage = "Invalid registration number") }
                return@launch
            }
            val newVehicle = Vehicle(
                registrationNumber = cleanReg,
                nickname = nickname.ifBlank { cleanReg },
                vehicleType = type
            )
            repository.addVehicle(newVehicle)
            _internalState.update { it.copy(isAddVehicleDialogOpen = false, selectedVehicleRegNo = cleanReg) }
            // Fetch violations for newly added vehicle
            refreshVehicle(cleanReg)
        }
    }

    fun deleteVehicle(regNo: String) {
        viewModelScope.launch {
            repository.deleteVehicle(regNo)
            _internalState.update { state ->
                val nextSelected = if (state.selectedVehicleRegNo == regNo) null else state.selectedVehicleRegNo
                state.copy(selectedVehicleRegNo = nextSelected, userMessage = "Removed $regNo")
            }
        }
    }

    fun showPhoto(url: String?) {
        _internalState.update { it.copy(activePhotoUrl = url) }
    }

    fun dismissPhoto() {
        _internalState.update { it.copy(activePhotoUrl = null) }
    }

    fun setAddVehicleDialogOpen(open: Boolean) {
        _internalState.update { it.copy(isAddVehicleDialogOpen = open) }
    }

    fun setSettingsDialogOpen(open: Boolean) {
        _internalState.update { it.copy(isSettingsDialogOpen = open) }
    }

    fun openKarnatakaOneSync(initialVehicleNo: String? = null) {
        val target = initialVehicleNo ?: _internalState.value.selectedVehicleRegNo
        _internalState.update {
            it.copy(
                isKarnatakaOneSyncOpen = true,
                selectedVehicleRegNo = target,
                karnatakaOneStatusMessage = "Ready. Follow Quick Pay -> Bengaluru -> Traffic Police Fine"
            )
        }
    }

    fun closeKarnatakaOneSync() {
        _internalState.update { it.copy(isKarnatakaOneSyncOpen = false) }
    }

    fun saveKarnatakaOneChallans(vehicleNo: String, scrapedJson: String) {
        viewModelScope.launch {
            val result = repository.saveKarnatakaOneScrapedChallans(vehicleNo, scrapedJson)
            _internalState.update { state ->
                if (result.isSuccess) {
                    val list = result.getOrDefault(emptyList())
                    val photosCount = list.count { !it.imageUrl.isNullOrBlank() }
                    state.copy(
                        karnatakaOneStatusMessage = "Saved ${list.size} violations ($photosCount with photos)",
                        userMessage = "Successfully imported ${list.size} violations ($photosCount with photos) for $vehicleNo"
                    )
                } else {
                    state.copy(
                        karnatakaOneStatusMessage = "Import failed: ${result.exceptionOrNull()?.localizedMessage}"
                    )
                }
            }
        }
    }

    fun updateChallanPhoto(challanNumber: String, photoUrl: String) {
        viewModelScope.launch {
            repository.updateChallanPhoto(challanNumber, photoUrl)
        }
    }

    fun savePhoneNumber(phone: String) {
        viewModelScope.launch {
            preferences.setSavedPhoneNumber(phone)
        }
    }

    fun updateKarnatakaOneStatus(message: String) {
        _internalState.update { it.copy(karnatakaOneStatusMessage = message) }
    }

    fun updateDetectedTables(count: Int) {
        _internalState.update { it.copy(detectedTablesCount = count) }
    }

    fun saveSettings(apiKey: String, apiHost: String, apiPath: String, isMockMode: Boolean, phoneNumber: String? = null) {
        viewModelScope.launch {
            preferences.setRapidApiKey(apiKey)
            preferences.setRapidApiHost(apiHost)
            preferences.setRapidApiPath(apiPath)
            preferences.setMockMode(isMockMode)
            if (phoneNumber != null) {
                preferences.setSavedPhoneNumber(phoneNumber)
            }
            _internalState.update {
                it.copy(
                    isSettingsDialogOpen = false,
                    userMessage = "Settings saved"
                )
            }
        }
    }

    fun clearUserMessage() {
        _internalState.update { it.copy(userMessage = null) }
    }
}

class ChallanTrackerViewModelFactory(
    private val repository: ChallanRepository,
    private val preferences: ChallanPreferences
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChallanTrackerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChallanTrackerViewModel(repository, preferences) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
