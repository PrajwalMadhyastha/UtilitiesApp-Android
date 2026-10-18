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

                // Deep image detection
                val image = extractImageUrl(item)

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

    private fun extractImageUrl(item: Map<*, *>): String? {
        val primaryKeys = listOf(
            "image_url", "proof_url", "photo", "image", "img", "img_url",
            "photo_url", "photos", "images", "evidence", "evidence_url",
            "proof", "proofs", "camera_image", "camera_url", "camera_photo",
            "violation_image", "violation_photo", "challan_image", "challan_photo",
            "anpr_image", "snapshot", "attachment", "attachments", "media", "pic", "picture", "url"
        )

        for (k in primaryKeys) {
            val v = item[k] ?: continue
            val resolved = resolveImageValue(v)
            if (resolved != null) return resolved
        }

        val keywords = listOf("image", "photo", "img", "proof", "evidence", "cam", "snap", "pic")
        for ((key, value) in item) {
            val keyStr = key?.toString()?.lowercase() ?: continue
            if (keywords.any { keyStr.contains(it) }) {
                val resolved = resolveImageValue(value)
                if (resolved != null) return resolved
            }
        }

        for ((_, value) in item) {
            if (value is String) {
                val clean = value.trim()
                if ((clean.startsWith("http://") || clean.startsWith("https://")) &&
                    (clean.endsWith(".jpg", ignoreCase = true) ||
                     clean.endsWith(".jpeg", ignoreCase = true) ||
                     clean.endsWith(".png", ignoreCase = true) ||
                     clean.endsWith(".webp", ignoreCase = true) ||
                     clean.contains("image", ignoreCase = true) ||
                     clean.contains("photo", ignoreCase = true) ||
                     clean.contains("evidence", ignoreCase = true))) {
                    return clean
                }
            }
        }

        return null
    }

    private fun resolveImageValue(value: Any?): String? {
        when (value) {
            is String -> {
                val trimmed = value.trim()
                if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("data:image/")) {
                    return trimmed
                }
                if (trimmed.startsWith("//")) {
                    return "https:$trimmed"
                }
                if (trimmed.length > 200 && !trimmed.contains(" ") && (trimmed.startsWith("/9j/") || trimmed.startsWith("iVBOR"))) {
                    return "data:image/jpeg;base64,$trimmed"
                }
            }
            is List<*> -> {
                for (elem in value) {
                    val resolved = resolveImageValue(elem)
                    if (resolved != null) return resolved
                }
            }
            is Map<*, *> -> {
                val nestedKeys = listOf("url", "link", "src", "image", "path", "href")
                for (nk in nestedKeys) {
                    val resolved = resolveImageValue(value[nk])
                    if (resolved != null) return resolved
                }
            }
        }
        return null
    }
}
