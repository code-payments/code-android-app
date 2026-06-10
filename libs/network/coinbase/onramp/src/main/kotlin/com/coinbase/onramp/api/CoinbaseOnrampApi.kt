package com.coinbase.onramp.api

import com.coinbase.onramp.data.OnRampOrderResponse
import com.coinbase.onramp.data.OnRampPurchaseResponse
import com.coinbase.onramp.data.SessionTokenRequest
import com.coinbase.onramp.data.SessionTokenResponse
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url

interface CoinbaseApi {
    @POST
    suspend fun placeOrder(
        // URL is provided by [OnRampApiConfig.path] to keep it centralized for the JWT request as well
        @Url url: String,
        @Header("Authorization") jwt: String,
        @Body request: Map<String, String>
    ): OnRampPurchaseResponse

    @GET
    suspend fun getOrderById(
        // URL is provided by [OnRampApiConfig] to keep it centralized for the JWT request as well
        @Url url: String,
        @Header("Authorization") jwt: String,
    ): OnRampOrderResponse

    @GET
    suspend fun getBuyOptions(
        @Url url: String,
        @Header("Authorization") jwt: String,
        @Query("country") country: String? = null,
        @Query("subdivision") subdivision: String? = null,
    ): JsonObject

    @POST("/onramp/v1/token")
    suspend fun generateSessionToken(
        @Header("Authorization") jwt: String,
        @Body request: SessionTokenRequest
    ): SessionTokenResponse
}