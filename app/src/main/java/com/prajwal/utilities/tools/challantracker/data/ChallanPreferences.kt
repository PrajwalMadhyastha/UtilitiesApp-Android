package com.prajwal.utilities.tools.challantracker.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.prajwal.utilities.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.challanDataStore: DataStore<Preferences> by preferencesDataStore(name = "challan_tracker_settings")

class ChallanPreferences(private val context: Context) {

    companion object {
        val KEY_RAPID_API_KEY = stringPreferencesKey("rapid_api_key")
        val KEY_RAPID_API_HOST = stringPreferencesKey("rapid_api_host")
        val KEY_IS_MOCK_MODE = booleanPreferencesKey("is_mock_mode")
        val KEY_SELECTED_VEHICLE = stringPreferencesKey("selected_vehicle")

        const val DEFAULT_RAPID_API_HOST = "rto-challan-information-india.p.rapidapi.com"
    }

    val rapidApiKey: Flow<String> = context.challanDataStore.data.map { prefs ->
        prefs[KEY_RAPID_API_KEY] ?: BuildConfig.RAPIDAPI_KEY
    }

    val rapidApiHost: Flow<String> = context.challanDataStore.data.map { prefs ->
        prefs[KEY_RAPID_API_HOST] ?: DEFAULT_RAPID_API_HOST
    }

    val isMockMode: Flow<Boolean> = context.challanDataStore.data.map { prefs ->
        val explicitMock = prefs[KEY_IS_MOCK_MODE]
        if (explicitMock != null) {
            explicitMock
        } else {
            // Default to mock mode if no API key is set
            val key = prefs[KEY_RAPID_API_KEY] ?: BuildConfig.RAPIDAPI_KEY
            key.isBlank()
        }
    }

    val selectedVehicle: Flow<String?> = context.challanDataStore.data.map { prefs ->
        prefs[KEY_SELECTED_VEHICLE]
    }

    suspend fun setRapidApiKey(apiKey: String) {
        context.challanDataStore.edit { prefs ->
            prefs[KEY_RAPID_API_KEY] = apiKey.trim()
        }
    }

    suspend fun setRapidApiHost(apiHost: String) {
        context.challanDataStore.edit { prefs ->
            prefs[KEY_RAPID_API_HOST] = apiHost.trim()
        }
    }

    suspend fun setMockMode(enabled: Boolean) {
        context.challanDataStore.edit { prefs ->
            prefs[KEY_IS_MOCK_MODE] = enabled
        }
    }

    suspend fun setSelectedVehicle(regNo: String?) {
        context.challanDataStore.edit { prefs ->
            if (regNo == null) {
                prefs.remove(KEY_SELECTED_VEHICLE)
            } else {
                prefs[KEY_SELECTED_VEHICLE] = regNo
            }
        }
    }
}
