package com.prajwal.utilities.tools.challantracker.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.prajwal.utilities.tools.challantracker.model.Vehicle
import com.prajwal.utilities.tools.challantracker.model.VehicleType

@Entity(tableName = "vehicles")
data class VehicleEntity(
    @PrimaryKey
    val registrationNumber: String,
    val nickname: String,
    val vehicleType: String,
    val lastCheckedAt: Long?,
    val totalViolations: Int = 0,
    val totalFineAmount: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): Vehicle = Vehicle(
        registrationNumber = registrationNumber,
        nickname = nickname,
        vehicleType = runCatching { VehicleType.valueOf(vehicleType) }.getOrDefault(VehicleType.TWO_WHEELER),
        lastCheckedAt = lastCheckedAt,
        totalViolations = totalViolations,
        totalFineAmount = totalFineAmount,
        createdAt = createdAt
    )

    companion object {
        fun fromDomain(vehicle: Vehicle): VehicleEntity = VehicleEntity(
            registrationNumber = vehicle.registrationNumber,
            nickname = vehicle.nickname,
            vehicleType = vehicle.vehicleType.name,
            lastCheckedAt = vehicle.lastCheckedAt,
            totalViolations = vehicle.totalViolations,
            totalFineAmount = vehicle.totalFineAmount,
            createdAt = vehicle.createdAt
        )
    }
}
