package com.prajwal.utilities.tools.challantracker.data

import android.content.Context
import android.util.Log
import com.prajwal.utilities.tools.challantracker.data.db.ChallanDao
import com.prajwal.utilities.tools.challantracker.data.db.ChallanDatabase
import com.prajwal.utilities.tools.challantracker.data.db.ChallanEntity
import com.prajwal.utilities.tools.challantracker.data.db.VehicleEntity
import com.prajwal.utilities.tools.challantracker.data.network.ChallanApiService
import com.prajwal.utilities.tools.challantracker.data.network.ChallanFetchRequest
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
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
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

    suspend fun removeFillerVehicles() = withContext(Dispatchers.IO) {
        val fillerPlates = listOf("KA01MJ2024", "KA04NA8812", "KA05XY4421")
        for (plate in fillerPlates) {
            dao.deleteVehicleAndChallans(plate)
        }
    }

    // ── Fetching & Syncing ────────────────────────────────────────────────────

    suspend fun refreshChallansForVehicle(regNo: String): Result<List<Challan>> = withContext(Dispatchers.IO) {
        try {
            val cleanReg = regNo.uppercase().replace("[^A-Z0-9]".toRegex(), "")
            val isMock = preferences.isMockMode.first()
            val apiKey = preferences.rapidApiKey.first()
            val apiHost = preferences.rapidApiHost.first().ifBlank { ChallanPreferences.DEFAULT_RAPID_API_HOST }
            val apiPath = preferences.rapidApiPath.first().ifBlank { ChallanPreferences.DEFAULT_RAPID_API_PATH }

            if (isMock || apiKey.isBlank()) {
                Log.d(TAG, "Fetching mock challans for vehicle $cleanReg")
                val mockChallans = MockChallanDataProvider.getMockChallansForVehicle(cleanReg)
                val entities = mockChallans.map { ChallanEntity.fromDomain(it) }
                dao.syncChallansForVehicle(cleanReg, entities, System.currentTimeMillis())
                return@withContext Result.success(mockChallans)
            }

            // Live API request via RapidAPI POST
            val cleanPath = apiPath.removePrefix("/")
            val url = "https://$apiHost/$cleanPath"
            Log.d(TAG, "Calling live API for $cleanReg: POST $url with host=$apiHost")

            val response = apiService.fetchChallanInfo(
                url = url,
                apiKey = apiKey,
                apiHost = apiHost,
                request = ChallanFetchRequest(regNo = cleanReg)
            )

            if (!response.isSuccessful) {
                val code = response.code()
                val errBody = response.errorBody()?.string() ?: ""
                val err = "Challan lookup failed (HTTP $code): ${errBody.take(200)}"
                Log.w(TAG, err)
                return@withContext Result.failure(Exception(err))
            }

            val rawJson = response.body()?.string() ?: ""
            Log.d(TAG, "Received response for $cleanReg: ${rawJson.take(300)}")

            val challans = com.prajwal.utilities.tools.challantracker.data.network.ChallanJsonParser.parse(cleanReg, rawJson)
            dao.syncChallansForVehicle(
                regNo = cleanReg,
                newChallans = challans.map { ChallanEntity.fromDomain(it) },
                lastCheckedAt = System.currentTimeMillis()
            )
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

    suspend fun saveKarnatakaOneScrapedChallans(
        vehicleNo: String,
        scrapedJson: String
    ): Result<List<Challan>> = withContext(Dispatchers.IO) {
        try {
            val cleanReg = vehicleNo.uppercase().replace("[^A-Z0-9]".toRegex(), "")
            val parsedChallans = com.prajwal.utilities.tools.challantracker.data.network.ChallanJsonParser.parse(
                vehicleNo = cleanReg,
                rawJson = scrapedJson
            )

            val targetReg = if (cleanReg.isNotBlank()) {
                cleanReg
            } else {
                parsedChallans.firstOrNull()?.vehicleNumber?.uppercase()?.replace("[^A-Z0-9]".toRegex(), "") ?: ""
            }

            if (targetReg.isNotBlank()) {
                // Ensure vehicle exists in DB so it appears in the vehicle bar
                val existing = dao.getVehicle(targetReg)
                if (existing == null) {
                    dao.insertVehicle(
                        VehicleEntity(
                            registrationNumber = targetReg,
                            nickname = targetReg,
                            vehicleType = "FOUR_WHEELER",
                            createdAt = System.currentTimeMillis()
                        )
                    )
                }

                dao.syncChallansForVehicle(
                    regNo = targetReg,
                    newChallans = parsedChallans.map { ChallanEntity.fromDomain(it.copy(vehicleNumber = targetReg)) },
                    lastCheckedAt = System.currentTimeMillis()
                )
            }

            Log.d(TAG, "Saved ${parsedChallans.size} Karnataka One challans for vehicle $targetReg")
            Result.success(parsedChallans)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving Karnataka One challans for vehicle $vehicleNo", e)
            Result.failure(e)
        }
    }

    suspend fun updateChallanPhoto(challanNumber: String, photoUrl: String) = withContext(Dispatchers.IO) {
        try {
            val all = dao.getAllChallans().first()
            val match = all.find { it.challanNumber.equals(challanNumber, ignoreCase = true) }
            if (match != null) {
                dao.updateChallan(match.copy(imageUrl = photoUrl, updatedAt = System.currentTimeMillis()))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update challan photo for $challanNumber", e)
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
