package com.prajwal.utilities.tools.challantracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChallanDao {

    // ── Vehicles ─────────────────────────────────────────────────────────────

    @Query("SELECT * FROM vehicles ORDER BY createdAt ASC")
    fun getAllVehicles(): Flow<List<VehicleEntity>>

    @Query("SELECT * FROM vehicles WHERE registrationNumber = :regNo LIMIT 1")
    suspend fun getVehicle(regNo: String): VehicleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVehicle(vehicle: VehicleEntity)

    @Update
    suspend fun updateVehicle(vehicle: VehicleEntity)

    @Query("DELETE FROM vehicles WHERE registrationNumber = :regNo")
    suspend fun deleteVehicleOnly(regNo: String)

    @Query("DELETE FROM challans WHERE vehicleNumber = :regNo")
    suspend fun deleteChallansForVehicle(regNo: String)

    @Transaction
    suspend fun deleteVehicleAndChallans(regNo: String) {
        deleteChallansForVehicle(regNo)
        deleteVehicleOnly(regNo)
    }

    // ── Challans ─────────────────────────────────────────────────────────────

    @Query("SELECT * FROM challans ORDER BY updatedAt DESC")
    fun getAllChallans(): Flow<List<ChallanEntity>>

    @Query("SELECT * FROM challans WHERE vehicleNumber = :regNo ORDER BY updatedAt DESC")
    fun getChallansForVehicle(regNo: String): Flow<List<ChallanEntity>>

    @Query("SELECT * FROM challans WHERE status = 'PENDING' ORDER BY updatedAt DESC")
    fun getAllPendingChallans(): Flow<List<ChallanEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChallans(challans: List<ChallanEntity>)

    @Update
    suspend fun updateChallan(challan: ChallanEntity)

    @Query("SELECT COUNT(*) FROM challans WHERE status = 'PENDING'")
    fun getPendingChallansCount(): Flow<Int>

    @Query("SELECT SUM(fineAmount) FROM challans WHERE status = 'PENDING'")
    fun getTotalPendingFineAmount(): Flow<Double?>

    @Transaction
    suspend fun syncChallansForVehicle(
        regNo: String,
        newChallans: List<ChallanEntity>,
        lastCheckedAt: Long
    ) {
        deleteChallansForVehicle(regNo)
        if (newChallans.isNotEmpty()) {
            insertChallans(newChallans)
        }
        val vehicle = getVehicle(regNo)
        if (vehicle != null) {
            val pendingChallans = newChallans.filter { it.status == "PENDING" }
            val updatedVehicle = vehicle.copy(
                lastCheckedAt = lastCheckedAt,
                totalViolations = pendingChallans.size,
                totalFineAmount = pendingChallans.sumOf { it.fineAmount }
            )
            updateVehicle(updatedVehicle)
        }
    }
}
