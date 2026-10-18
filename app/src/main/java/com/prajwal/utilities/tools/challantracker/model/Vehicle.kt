package com.prajwal.utilities.tools.challantracker.model

data class Vehicle(
    val registrationNumber: String, // Normalized, e.g. "KA01AB1234"
    val nickname: String,
    val vehicleType: VehicleType,
    val lastCheckedAt: Long? = null,
    val totalViolations: Int = 0,
    val totalFineAmount: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis()
) {
    /**
     * Formats registration number like KA-01-AB-1234 for readability.
     */
    val formattedPlate: String
        get() {
            val clean = registrationNumber.uppercase().replace("[^A-Z0-9]".toRegex(), "")
            val regex = Regex("""^([A-Z]{2})([0-9]{1,2})([A-Z]{0,3})([0-9]{1,4})$""")
            val match = regex.find(clean)
            return if (match != null) {
                val parts = match.destructured.toList().filter { it.isNotEmpty() }
                parts.joinToString("-")
            } else {
                registrationNumber
            }
        }
}
