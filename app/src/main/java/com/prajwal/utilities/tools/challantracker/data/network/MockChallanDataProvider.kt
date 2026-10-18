package com.prajwal.utilities.tools.challantracker.data.network

import com.prajwal.utilities.tools.challantracker.model.Challan
import com.prajwal.utilities.tools.challantracker.model.ChallanStatus
import com.prajwal.utilities.tools.challantracker.model.Vehicle
import com.prajwal.utilities.tools.challantracker.model.VehicleType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MockChallanDataProvider {

    val defaultSampleVehicles = listOf(
        Vehicle(
            registrationNumber = "KA01MJ2024",
            nickname = "Ather 450X",
            vehicleType = VehicleType.TWO_WHEELER,
            createdAt = System.currentTimeMillis() - (86400000L * 30)
        ),
        Vehicle(
            registrationNumber = "KA04NA8812",
            nickname = "Honda City",
            vehicleType = VehicleType.FOUR_WHEELER,
            createdAt = System.currentTimeMillis() - (86400000L * 25)
        ),
        Vehicle(
            registrationNumber = "KA05XY4421",
            nickname = "Activa 6G",
            vehicleType = VehicleType.TWO_WHEELER,
            createdAt = System.currentTimeMillis() - (86400000L * 15)
        )
    )

    fun getMockChallansForVehicle(registrationNumber: String): List<Challan> {
        val clean = registrationNumber.uppercase().replace("[^A-Z0-9]".toRegex(), "")
        val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.ENGLISH)
        val now = System.currentTimeMillis()

        return when {
            // Vehicle 1 has 1 pending violation with photo evidence
            clean.endsWith("2024") || clean.contains("MJ") -> listOf(
                Challan(
                    challanNumber = "BTP24098231",
                    vehicleNumber = clean,
                    offenseDescription = "RIDING TWO WHEELER WITHOUT HELMET - PILLION",
                    fineAmount = 500.0,
                    violationDate = dateFormat.format(Date(now - (86400000L * 3))),
                    violationTimestamp = now - (86400000L * 3),
                    location = "Trinity Circle, MG Road, Bengaluru",
                    status = ChallanStatus.PENDING,
                    imageUrl = "https://images.unsplash.com/photo-1558981403-c5f9899a28bc?auto=format&fit=crop&w=800&q=80",
                    rawDetails = "Captured by AI ANPR Enforcement Camera #BTP-CAM-104",
                    updatedAt = now
                )
            )

            // Vehicle 2 has 2 violations (1 pending with photo, 1 paid)
            clean.endsWith("8812") || clean.contains("NA") -> listOf(
                Challan(
                    challanNumber = "BTP24081190",
                    vehicleNumber = clean,
                    offenseDescription = "FAILING TO OBEY TRAFFIC CONTROL SIGNAL (RED LIGHT JUMP)",
                    fineAmount = 1000.0,
                    violationDate = dateFormat.format(Date(now - (86400000L * 7))),
                    violationTimestamp = now - (86400000L * 7),
                    location = "Silk Board Junction, Outer Ring Road, Bengaluru",
                    status = ChallanStatus.PENDING,
                    imageUrl = "https://images.unsplash.com/photo-1502877338535-766e1452684a?auto=format&fit=crop&w=800&q=80",
                    rawDetails = "Captured by Traffic Interceptor Camera #BTP-SB-02",
                    updatedAt = now
                ),
                Challan(
                    challanNumber = "BTP23072210",
                    vehicleNumber = clean,
                    offenseDescription = "STOPPING ON ZEBRA CROSSING / PEDESTRIAN PATHWAY",
                    fineAmount = 500.0,
                    violationDate = dateFormat.format(Date(now - (86400000L * 45))),
                    violationTimestamp = now - (86400000L * 45),
                    location = "Sony World Signal, 80ft Road, Koramangala",
                    status = ChallanStatus.PAID,
                    imageUrl = null,
                    rawDetails = "Payment settled via Karnataka One",
                    updatedAt = now - (86400000L * 40)
                )
            )

            // Vehicle 3 is clean (no violations)
            clean.endsWith("4421") || clean.contains("XY") -> emptyList()

            // Custom user vehicles generate 1 sample violation so the user can test their entered plate immediately
            else -> listOf(
                Challan(
                    challanNumber = "BTP24011234",
                    vehicleNumber = clean,
                    offenseDescription = "DEFECTIVE REGISTRATION NUMBER PLATE",
                    fineAmount = 500.0,
                    violationDate = dateFormat.format(Date(now - (86400000L * 2))),
                    violationTimestamp = now - (86400000L * 2),
                    location = "Indiranagar 100ft Road, Bengaluru",
                    status = ChallanStatus.PENDING,
                    imageUrl = "https://images.unsplash.com/photo-1549399542-7e3f8b79c341?auto=format&fit=crop&w=800&q=80",
                    rawDetails = "BTP Field Surveillance Capture",
                    updatedAt = now
                )
            )
        }
    }
}
