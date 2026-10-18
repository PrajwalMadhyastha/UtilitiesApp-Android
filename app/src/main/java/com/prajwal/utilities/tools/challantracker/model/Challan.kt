package com.prajwal.utilities.tools.challantracker.model

data class Challan(
    val challanNumber: String,
    val vehicleNumber: String,
    val offenseDescription: String,
    val fineAmount: Double,
    val violationDate: String,
    val violationTimestamp: Long? = null,
    val location: String,
    val status: ChallanStatus = ChallanStatus.PENDING,
    val imageUrl: String? = null,
    val rawDetails: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
