package com.prajwal.utilities.tools.challantracker.data.network

import com.prajwal.utilities.tools.challantracker.model.Challan
import com.prajwal.utilities.tools.challantracker.model.ChallanStatus
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

object ChallanJsonParser {

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    fun parse(vehicleNo: String, rawJson: String): List<Challan> {
        val list = mutableListOf<Challan>()
        if (rawJson.isBlank()) return list

        try {
            val parsedObj = runCatching {
                moshi.adapter(Any::class.java).fromJson(rawJson)
            }.getOrNull() ?: return list

            val itemsList: List<Map<*, *>> = when (parsedObj) {
                is List<*> -> parsedObj.filterIsInstance<Map<*, *>>()
                is Map<*, *> -> {
                    val candidateKeys = listOf("data", "challans", "result", "records", "details", "violations", "response")
                    var found: List<Map<*, *>>? = null
                    for (k in candidateKeys) {
                        val v = parsedObj[k]
                        if (v is List<*>) {
                            found = v.filterIsInstance<Map<*, *>>()
                            break
                        } else if (v is Map<*, *>) {
                            for (innerK in candidateKeys) {
                                val innerV = v[innerK]
                                if (innerV is List<*>) {
                                    found = innerV.filterIsInstance<Map<*, *>>()
                                    break
                                }
                            }
                        }
                    }
                    found ?: emptyList()
                }
                else -> emptyList()
            }

            for ((index, item) in itemsList.withIndex()) {
                val challanNo = listOf("challan_number", "challan_no", "challanno", "id", "number", "notice_no")
                    .firstNotNullOfOrNull { key -> item[key]?.toString()?.takeIf { it.isNotBlank() } }
                    ?: "CH-$index-${System.currentTimeMillis()}"

                val offense = listOf("offense", "offence", "violation", "violation_details", "reason", "crime", "offense_name")
                    .firstNotNullOfOrNull { key -> item[key]?.toString()?.takeIf { it.isNotBlank() } }
                    ?: "Traffic Violation"

                val amountStr = listOf("fine_amount", "amount", "fine", "penalty", "total_amount")
                    .firstNotNullOfOrNull { key -> item[key]?.toString()?.takeIf { it.isNotBlank() } }
                    ?: "0"
                val amount = amountStr.replace("[^0-9.]".toRegex(), "").toDoubleOrNull() ?: 0.0

                val date = listOf("challan_date", "violation_date", "date", "date_time", "time")
                    .firstNotNullOfOrNull { key -> item[key]?.toString()?.takeIf { it.isNotBlank() } }
                    ?: "Unknown Date"

                val location = listOf("location", "place", "area", "address", "circle", "traffic_police_station")
                    .firstNotNullOfOrNull { key -> item[key]?.toString()?.takeIf { it.isNotBlank() } }
                    ?: "Bengaluru"

                val statusStr = listOf("status", "payment_status", "challan_status")
                    .firstNotNullOfOrNull { key -> item[key]?.toString()?.takeIf { it.isNotBlank() } }
                    ?: "PENDING"

                val status = when (statusStr.uppercase()) {
                    "PAID", "CLEARED", "SUCCESS" -> ChallanStatus.PAID
                    "DISPOSED" -> ChallanStatus.DISPOSED
                    else -> ChallanStatus.PENDING
                }

                val image = listOf("image_url", "proof_url", "photo", "evidence", "camera_image", "proof", "image")
                    .firstNotNullOfOrNull { key ->
                        item[key]?.toString()?.takeIf { it.isNotBlank() && it.startsWith("http") }
                    }

                list.add(
                    Challan(
                        challanNumber = challanNo,
                        vehicleNumber = vehicleNo,
                        offenseDescription = offense,
                        fineAmount = amount,
                        violationDate = date,
                        location = location,
                        status = status,
                        imageUrl = image,
                        rawDetails = item.toString(),
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        } catch (_: Exception) {
            // Graceful fallback to empty list
        }

        return list
    }
}
