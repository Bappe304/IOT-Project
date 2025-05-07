package com.example.coin_classifier.api

import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface AzureMLService {
    @POST("score")  // The endpoint path
    suspend fun classifyBankNote(
        @Header("Authorization") authHeader: String,
        @Body requestBody: RequestBody
    ): Response<CoinResponse>
}