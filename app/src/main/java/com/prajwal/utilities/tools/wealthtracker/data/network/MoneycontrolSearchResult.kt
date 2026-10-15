package com.prajwal.utilities.tools.wealthtracker.data.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Represents a single result from the Moneycontrol stock search autocomplete:
 *   https://www.moneycontrol.com/mccode/common/autosuggestion_solr.php?query={ticker}&type=1&format=json
 *
 * The key field is [scId] (Moneycontrol's "sc_id"), which is used as the path
 * parameter in the pricefeed JSON API:
 *   https://priceapi.moneycontrol.com/pricefeed/nse/equitycash/{scId}
 */
@JsonClass(generateAdapter = true)
data class MoneycontrolSearchResult(
    /** Moneycontrol internal scrip ID, e.g. "RI" for Reliance, "TCS" for TCS */
    @Json(name = "sc_id") val scId: String?,

    /** Human-readable company name, e.g. "Reliance Industries" */
    @Json(name = "name") val name: String?,

    /**
     * Display name with inline ticker/ISIN details:
     * e.g. "Reliance Industries&nbsp;<span>INE002A01018, RELIANCE, 500325</span>"
     * Used for ticker matching to pick the right result.
     */
    @Json(name = "pdt_dis_nm") val pdtDisNm: String?,

    /** Direct link to the MC stock page, e.g. ".../relianceindustries/RI" */
    @Json(name = "link_src") val linkSrc: String?,

    /** Moneycontrol price identifier, e.g. "RI-N" for NSE, "RI-B" for BSE */
    @Json(name = "price_identifier") val priceIdentifier: String?
)

/**
 * Root response structure from the Moneycontrol pricefeed endpoint:
 *   https://priceapi.moneycontrol.com/pricefeed/{exchange}/equitycash/{scId}
 */
@JsonClass(generateAdapter = true)
data class MoneycontrolPriceResponse(
    @Json(name = "code") val code: String?,
    @Json(name = "message") val message: String?,
    @Json(name = "data") val data: MoneycontrolPriceData?
)

/**
 * Key price fields from Moneycontrol pricefeed JSON
 */
@JsonClass(generateAdapter = true)
data class MoneycontrolPriceData(
    /** Last traded / current price as string (e.g. "1208.20") */
    @Json(name = "pricecurrent") val priceCurrent: String?,

    /** Previous trading day's close price as string (e.g. "1174.60") */
    @Json(name = "priceprevclose") val pricePrevClose: String?,

    /** Day's high price as fallback if pricecurrent is missing (e.g. "1209.60") */
    @Json(name = "HP") val highPrice: String?
)

