package com.prajwal.utilities.tools.wealthtracker.data.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MfApiResponse(
    @Json(name = "meta") val meta: MfMeta? = null,
    @Json(name = "data") val data: List<MfNavData>? = null,
    @Json(name = "status") val status: String? = null
)

@JsonClass(generateAdapter = true)
data class MfMeta(
    @Json(name = "scheme_code") val schemeCode: Any? = null,
    @Json(name = "scheme_name") val schemeName: String? = null
)

@JsonClass(generateAdapter = true)
data class MfNavData(
    @Json(name = "date") val date: String? = null,
    @Json(name = "nav") val nav: String? = null
)

