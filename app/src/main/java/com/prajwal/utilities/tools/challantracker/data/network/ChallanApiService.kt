package com.prajwal.utilities.tools.challantracker.data.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query
import retrofit2.http.Url

interface ChallanApiService {

    @GET
    suspend fun fetchChallans(
        @Url url: String,
        @Header("X-RapidAPI-Key") apiKey: String,
        @Header("X-RapidAPI-Host") apiHost: String,
        @Query("vehicle_id") vehicleId: String
    ): Response<ChallanApiResponse>

    @GET
    suspend fun fetchChallansByRc(
        @Url url: String,
        @Header("X-RapidAPI-Key") apiKey: String,
        @Header("X-RapidAPI-Host") apiHost: String,
        @Query("rc_number") rcNumber: String
    ): Response<ChallanApiResponse>
}
