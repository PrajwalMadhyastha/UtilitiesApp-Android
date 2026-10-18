package com.prajwal.utilities.tools.challantracker.data

import android.content.Context
import android.util.Log
import com.prajwal.utilities.tools.challantracker.data.db.ChallanDao
import com.prajwal.utilities.tools.challantracker.data.db.ChallanDatabase
import com.prajwal.utilities.tools.challantracker.data.db.ChallanEntity
import com.prajwal.utilities.tools.challantracker.data.db.VehicleEntity
import com.prajwal.utilities.tools.challantracker.data.network.ChallanApiService
import com.prajwal.utilities.tools.challantracker.data.network.MockChallanDataProvider
import com.prajwal.utilities.tools.challantracker.model.Challan
import com.prajwal.utilities.tools.challantracker.model.ChallanStatus
import com.prajwal.utilities.tools.challantracker.model.Vehicle
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class ChallanRepository(
    private val dao: ChallanDao,
    private val preferences: ChallanPreferences
) {
    private val TAG = "ChallanRepository"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val apiService: ChallanApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://rto-challan-information-india.p.rapidapi.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(ChallanApiService::class.java)
    }

    // ── Reactive Streams ──────────────────────────────────────────────────────

    val vehicles: Flow<List<Vehicle>> = dao.getAllVehicles()
        .map { list -> list.map { it.toDomain() } }
        .flowOn(Dispatchers.IO)

    val allChallans: Flow<List<Challan>> = dao.getAllChallans()
        .map { list -> list.map { it.toDomain() } }
        .flowOn(Dispatchers.IO)

    val pendingChallans: Flow<List<Challan>> = dao.getAllPendingChallans()
        .map { list -> list.map { it.toDomain() } }
        .flowOn(Dispatchers.IO)

    fun getChallansForVehicle(regNo: String): Flow<List<Challan>> =
        dao.getChallansForVehicle(regNo)
            .map { list -> list.map { it.toDomain() } }
            .flowOn(Dispatchers.IO)

    val pendingCount: Flow<Int> = dao.getPendingChallansCount()
        .flowOn(Dispatchers.IO)

    val totalPendingFine: Flow<Double> = dao.getTotalPendingFineAmount()
        .map { it ?: 0.0 }
        .flowOn(Dispatchers.IO)

    // ── Vehicle Management ────────────────────────────────────────────────────

    suspend fun addVehicle(vehicle: Vehicle) = withContext(Dispatchers.IO) {
        val cleanReg = vehicle.registrationNumber.uppercase().replace("[^A-Z0-9]".toRegex(), "")
        val normalized = vehicle.copy(registrationNumber = cleanReg)
        dao.insertVehicle(VehicleEntity.fromDomain(normalized))
    }

    suspend fun updateVehicle(vehicle: Vehicle) = withContext(Dispatchers.IO) {
        dao.updateVehicle(VehicleEntity.fromDomain(vehicle))
    }

    suspend fun deleteVehicle(regNo: String) = withContext(Dispatchers.IO) {
        dao.deleteVehicleAndChallans(regNo)
    }

    suspend fun seedSampleVehiclesIfEmpty() = withContext(Dispatchers.IO) {
        val current = dao.getAllVehicles().first()
        if (current.isEmpty()) {
            MockChallanDataProvider.defaultSampleVehicles.forEach { v ->
                dao.insertVehicle(VehicleEntity.fromDomain(v))
                val mockChallans = MockChallanDataProvider.getMockChallansForVehicle(v.registrationNumber)
                dao.syncChallansForVehicle(
                    regNo = v.registrationNumber,
                    newChallans = mockChallans.map { ChallanEntity.fromDomain(it) },
                    lastCheckedAt = System.currentTimeMillis() - 86400000L
                )
            }
        }
    }

    // ── Fetching & Syncing ────────────────────────────────────────────────────

    suspend fun refreshChallansForVehicle(regNo: String): Result<List<Challan>> = withContext(Dispatchers.IO) {
        try {
            val cleanReg = regNo.uppercase().replace("[^A-Z0-9]".toRegex(), "")
            val isMock = preferences.isMockMode.first()
            val apiKey = preferences.rapidApiKey.first()
            val apiHost = preferences.rapidApiHost.first()

            if (isMock || apiKey.isBlank()) {
                Log.d(TAG, "Fetching mock challans for vehicle $cleanReg")
                val mockChallans = MockChallanDataProvider.getMockChallansForVehicle(cleanReg)
                val entities = mockChallans.map { ChallanEntity.fromDomain(it) }
                dao.syncChallansForVehicle(cleanReg, entities, System.currentTimeMillis())
                return@withContext Result.success(mockChallans)
            }

            // Live API request via RapidAPI
            val url = "https://$apiHost/v1/challan"
            Log.d(TAG, "Calling live API for $cleanReg at $url")

            val response = apiService.fetchChallans(
                url = url,
                apiKey = apiKey,
                apiHost = apiHost,
                vehicleId = cleanReg
            )

            if (!response.isSuccessful) {
                // Try fallback query param
                val fallbackResp = apiService.fetchChallansByRc(
                    url = url,
                    apiKey = apiKey,
                    apiHost = apiHost,
                    rcNumber = cleanReg
                )
                if (!fallbackResp.isSuccessful) {
                    val code = fallbackResp.code()
                    val err = "Challan lookup failed (HTTP $code). Verify API key or RapidAPI host."
                    Log.w(TAG, err)
                    return@withContext Result.failure(Exception(err))
                }
                val items = extractChallans(fallbackResp.body())
                val challans = mapToDomain(cleanReg, items)
                dao.syncChallansForVehicle(cleanReg, challans.map { ChallanEntity.fromDomain(it) }, System.currentTimeMillis())
                return@withContext Result.success(challans)
            }

            val items = extractChallans(response.body())
            val challans = mapToDomain(cleanReg, items)
            dao.syncChallansForVehicle(cleanReg, challans.map { ChallanEntity.fromDomain(it) }, System.currentTimeMillis())
            Result.success(challans)
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing challans for $regNo", e)
            Result.failure(e)
        }
    }

    suspend fun refreshAllVehicles(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val list = dao.getAllVehicles().first()
            for (vehicle in list) {
                refreshChallansForVehicle(vehicle.registrationNumber)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing all vehicles", e)
            Result.failure(e)
        }
    }

    // ── Helper Mappers ────────────────────────────────────────────────────────

    private fun extractChallans(body: com.prajwal.utilities.tools.challantracker.data.network.ChallanApiResponse?): List<com.prajwal.utilities.tools.challantracker.data.network.ChallanApiItem> {
        if (body == null) return emptyList()
        return body.data?.challans
            ?: body.data?.violations
            ?: body.directChallans
            ?: body.result
            ?: emptyList()
    }

    private fun mapToDomain(
        vehicleNo: String,
        items: List<com.prajwal.utilities.tools.challantracker.data.network.ChallanApiItem>
    ): List<Challan> {
        val now = System.currentTimeMillis()
        return items.map { item ->
            val status = when (item.resolvedStatus.uppercase()) {
                "PAID", "CLEARED", "SUCCESS" -> ChallanStatus.PAID
                "DISPOSED" -> ChallanStatus.DISPOSED
                else -> ChallanStatus.PENDING
            }

            Challan(
                challanNumber = item.resolvedNumber,
                vehicleNumber = vehicleNo,
                offenseDescription = item.resolvedOffense,
                fineAmount = item.resolvedAmount,
                violationDate = item.resolvedDate,
                location = item.resolvedLocation,
                status = status,
                imageUrl = item.resolvedImageUrl,
                rawDetails = null,
                updatedAt = now
            )
        }
    }

    companion object {
        fun create(context: Context): ChallanRepository {
            val db = ChallanDatabase.getDatabase(context)
            val preferences = ChallanPreferences(context)
            return ChallanRepository(db.challanDao(), preferences)
        }
    }
}
