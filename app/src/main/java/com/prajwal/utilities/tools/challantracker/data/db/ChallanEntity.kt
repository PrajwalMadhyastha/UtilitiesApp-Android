package com.prajwal.utilities.tools.challantracker.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.prajwal.utilities.tools.challantracker.model.Challan
import com.prajwal.utilities.tools.challantracker.model.ChallanStatus

@Entity(
    tableName = "challans",
    indices = [Index(value = ["vehicleNumber"])]
)
data class ChallanEntity(
    @PrimaryKey
    val challanNumber: String,
    val vehicleNumber: String,
    val offenseDescription: String,
    val fineAmount: Double,
    val violationDate: String,
    val violationTimestamp: Long?,
    val location: String,
    val status: String,
    val imageUrl: String?,
    val rawDetails: String?,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): Challan = Challan(
        challanNumber = challanNumber,
        vehicleNumber = vehicleNumber,
        offenseDescription = offenseDescription,
        fineAmount = fineAmount,
        violationDate = violationDate,
        violationTimestamp = violationTimestamp,
        location = location,
        status = runCatching { ChallanStatus.valueOf(status) }.getOrDefault(ChallanStatus.PENDING),
        imageUrl = imageUrl,
        rawDetails = rawDetails,
        updatedAt = updatedAt
    )

    companion object {
        fun fromDomain(challan: Challan): ChallanEntity = ChallanEntity(
            challanNumber = challan.challanNumber,
            vehicleNumber = challan.vehicleNumber,
            offenseDescription = challan.offenseDescription,
            fineAmount = challan.fineAmount,
            violationDate = challan.violationDate,
            violationTimestamp = challan.violationTimestamp,
            location = challan.location,
            status = challan.status.name,
            imageUrl = challan.imageUrl,
            rawDetails = challan.rawDetails,
            updatedAt = challan.updatedAt
        )
    }
}
