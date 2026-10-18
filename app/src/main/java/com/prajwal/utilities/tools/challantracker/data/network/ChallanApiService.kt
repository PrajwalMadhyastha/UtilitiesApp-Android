package com.prajwal.utilities.tools.challantracker.data.network

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

interface ChallanApiService {

    @POST
    suspend fun fetchChallanInfo(
        @Url url: String,
        @Header("x-rapidapi-key") apiKey: String,
        @Header("x-rapidapi-host") apiHost: String,
        @Body request: ChallanFetchRequest
    ): Response<ResponseBody>
}
