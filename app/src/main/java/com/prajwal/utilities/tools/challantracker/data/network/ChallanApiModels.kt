package com.prajwal.utilities.tools.challantracker.data.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ChallanFetchRequest(
    @Json(name = "reg_no") val regNo: String
)

@JsonClass(generateAdapter = true)
data class ChallanApiResponse(
    @Json(name = "status") val status: Any? = null,
    @Json(name = "message") val message: String? = null,
    @Json(name = "data") val data: ChallanApiData? = null,
    @Json(name = "challans") val directChallans: List<ChallanApiItem>? = null,
    @Json(name = "result") val result: List<ChallanApiItem>? = null
)

@JsonClass(generateAdapter = true)
data class ChallanApiData(
    @Json(name = "rc_number") val rcNumber: String? = null,
    @Json(name = "vehicle_number") val vehicleNumber: String? = null,
    @Json(name = "challans") val challans: List<ChallanApiItem>? = null,
    @Json(name = "violations") val violations: List<ChallanApiItem>? = null
)

@JsonClass(generateAdapter = true)
data class ChallanApiItem(
    @Json(name = "challan_number") val challanNumber: String? = null,
    @Json(name = "challan_no") val challanNo: String? = null,
    @Json(name = "offense") val offense: String? = null,
    @Json(name = "offence") val offence: String? = null,
    @Json(name = "violation_details") val violationDetails: String? = null,
    @Json(name = "fine_amount") val fineAmount: Double? = null,
    @Json(name = "amount") val amount: Double? = null,
    @Json(name = "challan_date") val challanDate: String? = null,
    @Json(name = "date") val date: String? = null,
    @Json(name = "location") val location: String? = null,
    @Json(name = "place") val place: String? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "payment_status") val paymentStatus: String? = null,
    @Json(name = "image_url") val imageUrl: String? = null,
    @Json(name = "proof_url") val proofUrl: String? = null,
    @Json(name = "photo") val photo: String? = null
) {
    val resolvedNumber: String
        get() = challanNumber ?: challanNo ?: "CH-${System.currentTimeMillis()}"

    val resolvedOffense: String
        get() = violationDetails ?: offense ?: offence ?: "Traffic Violation"

    val resolvedAmount: Double
        get() = fineAmount ?: amount ?: 0.0

    val resolvedDate: String
        get() = challanDate ?: date ?: "Unknown Date"

    val resolvedLocation: String
        get() = location ?: place ?: "Bengaluru"

    val resolvedStatus: String
        get() = status ?: paymentStatus ?: "PENDING"

    val resolvedImageUrl: String?
        get() = imageUrl?.takeIf { it.isNotBlank() }
            ?: proofUrl?.takeIf { it.isNotBlank() }
            ?: photo?.takeIf { it.isNotBlank() }
}
