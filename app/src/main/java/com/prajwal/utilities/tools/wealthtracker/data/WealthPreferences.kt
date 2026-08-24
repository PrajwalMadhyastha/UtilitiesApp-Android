package com.prajwal.utilities.tools.wealthtracker.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.wealthDataStore: DataStore<Preferences> by preferencesDataStore(name = "wealth_calculator_settings")

/**
 * DataStore-backed preferences for the milestone calculator settings.
 * All three fields are independently updatable at any time.
 */
class WealthPreferences(val context: Context) {

    companion object {
        val MONTHLY_INVESTMENT = doublePreferencesKey("monthly_investment")
        val ANNUAL_STEPUP_PERCENT = doublePreferencesKey("annual_stepup_percent")
        val EXPECTED_RETURN_PERCENT = doublePreferencesKey("expected_return_percent")
        val IS_BIOMETRIC_ENABLED = booleanPreferencesKey("is_biometric_enabled")
        val HOLDINGS_SORT_OPTION = stringPreferencesKey("holdings_sort_option")
        val HOLDINGS_SORT_ASCENDING = booleanPreferencesKey("holdings_sort_ascending")
        val AUTO_SNAPSHOT_ENABLED = booleanPreferencesKey("auto_snapshot_enabled")
        val AUTO_SNAPSHOT_DAY_OF_MONTH = androidx.datastore.preferences.core.intPreferencesKey("auto_snapshot_day_of_month")
        val INCLUDE_RETIREMENT_IN_TOTAL = booleanPreferencesKey("include_retirement_in_total")
        val LAST_AUTO_SYNC_TIMESTAMP = androidx.datastore.preferences.core.longPreferencesKey("last_auto_sync_timestamp")
        val HIDE_VALUES = booleanPreferencesKey("hide_values")
        val STEPUP_MONTH = androidx.datastore.preferences.core.intPreferencesKey("stepup_month")
        val DAILY_CATEGORY_SUMMARY_JSON = stringPreferencesKey("daily_category_summary_json")

        // Defaults
        const val DEFAULT_MONTHLY_INVESTMENT = 10000.0   // ₹10,000/month
        const val DEFAULT_ANNUAL_STEPUP_PERCENT = 10.0   // 10% step-up per year
        const val DEFAULT_EXPECTED_RETURN_PERCENT = 12.0 // 12% annual return
        const val DEFAULT_STEPUP_MONTH = 4               // 4 = April
    }

    val hideValues: Flow<Boolean> = context.wealthDataStore.data
        .map { it[HIDE_VALUES] ?: false }

    val monthlyInvestment: Flow<Double> = context.wealthDataStore.data
        .map { it[MONTHLY_INVESTMENT] ?: DEFAULT_MONTHLY_INVESTMENT }

    val annualStepupPercent: Flow<Double> = context.wealthDataStore.data
        .map { it[ANNUAL_STEPUP_PERCENT] ?: DEFAULT_ANNUAL_STEPUP_PERCENT }

    val stepupMonth: Flow<Int> = context.wealthDataStore.data
        .map { it[STEPUP_MONTH] ?: DEFAULT_STEPUP_MONTH }

    val expectedReturnPercent: Flow<Double> = context.wealthDataStore.data
        .map { it[EXPECTED_RETURN_PERCENT] ?: DEFAULT_EXPECTED_RETURN_PERCENT }

    val isBiometricEnabled: Flow<Boolean> = context.wealthDataStore.data
        .map { it[IS_BIOMETRIC_ENABLED] ?: false }

    val holdingsSortOption: Flow<SortOption> = context.wealthDataStore.data
        .map { preferences ->
            val optionStr = preferences[HOLDINGS_SORT_OPTION] ?: SortOption.DEFAULT.name
            try {
                SortOption.valueOf(optionStr)
            } catch (e: IllegalArgumentException) {
                SortOption.DEFAULT
            }
        }

    val holdingsSortAscending: Flow<Boolean> = context.wealthDataStore.data
        .map { it[HOLDINGS_SORT_ASCENDING] ?: true }

    val autoSnapshotEnabled: Flow<Boolean> = context.wealthDataStore.data
        .map { it[AUTO_SNAPSHOT_ENABLED] ?: false }

    val autoSnapshotDayOfMonth: Flow<Int> = context.wealthDataStore.data
        .map { it[AUTO_SNAPSHOT_DAY_OF_MONTH] ?: 1 }

    val includeRetirementInTotal: Flow<Boolean> = context.wealthDataStore.data
        .map { it[INCLUDE_RETIREMENT_IN_TOTAL] ?: true }

    val lastAutoSyncTimestamp: Flow<Long> = context.wealthDataStore.data
        .map { it[LAST_AUTO_SYNC_TIMESTAMP] ?: 0L }

    val dailyCategorySummaryJson: Flow<String?> = context.wealthDataStore.data
        .map { it[DAILY_CATEGORY_SUMMARY_JSON] }

    suspend fun updateDailyCategorySummaryJson(json: String?) {
        context.wealthDataStore.edit {
            if (json != null) {
                it[DAILY_CATEGORY_SUMMARY_JSON] = json
            } else {
                it.remove(DAILY_CATEGORY_SUMMARY_JSON)
            }
        }
    }

    suspend fun updateMonthlyInvestment(amount: Double) {
        context.wealthDataStore.edit { it[MONTHLY_INVESTMENT] = amount }
    }

    suspend fun updateAnnualStepup(percent: Double) {
        context.wealthDataStore.edit { it[ANNUAL_STEPUP_PERCENT] = percent }
    }

    suspend fun updateStepupMonth(month: Int) {
        context.wealthDataStore.edit { it[STEPUP_MONTH] = month }
    }

    suspend fun updateExpectedReturn(percent: Double) {
        context.wealthDataStore.edit { it[EXPECTED_RETURN_PERCENT] = percent }
    }

    suspend fun updateBiometricEnabled(enabled: Boolean) {
        context.wealthDataStore.edit { it[IS_BIOMETRIC_ENABLED] = enabled }
    }

    suspend fun updateHoldingsSortOption(option: SortOption) {
        context.wealthDataStore.edit { it[HOLDINGS_SORT_OPTION] = option.name }
    }

    suspend fun updateHoldingsSortAscending(isAscending: Boolean) {
        context.wealthDataStore.edit { it[HOLDINGS_SORT_ASCENDING] = isAscending }
    }

    suspend fun updateAutoSnapshotEnabled(enabled: Boolean) {
        context.wealthDataStore.edit { it[AUTO_SNAPSHOT_ENABLED] = enabled }
    }

    suspend fun updateAutoSnapshotDayOfMonth(day: Int) {
        context.wealthDataStore.edit { it[AUTO_SNAPSHOT_DAY_OF_MONTH] = day }
    }

    suspend fun updateIncludeRetirementInTotal(include: Boolean) {
        context.wealthDataStore.edit { it[INCLUDE_RETIREMENT_IN_TOTAL] = include }
    }

    suspend fun updateLastAutoSyncTimestamp(timestamp: Long) {
        context.wealthDataStore.edit { it[LAST_AUTO_SYNC_TIMESTAMP] = timestamp }
    }

    suspend fun updateHideValues(hide: Boolean) {
        context.wealthDataStore.edit { it[HIDE_VALUES] = hide }
    }
}
