package com.coinbase.onramp.api

import com.coinbase.onramp.data.OnRampPurchaseResponse
import com.coinbase.onramp.data.SessionTokenRequest
import com.coinbase.onramp.data.SessionTokenResponse
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

interface CoinbaseApi {
    @POST
    suspend fun placeOrder(
        // URL is provided by [OnRampApiConfig.path] to keep it centralized for the JWT request as well
        @Url url: String,
        @Header("Authorization") jwt: String,
        @Body request: Map<String, String>
    ): OnRampPurchaseResponse

    @POST("/onramp/v1/token")
    suspend fun generateSessionToken(
        @Header("Authorization") jwt: String,
        @Body request: SessionTokenRequest
    ): SessionTokenResponse
}