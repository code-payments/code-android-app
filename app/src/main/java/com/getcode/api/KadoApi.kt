package com.getcode.api

import com.google.gson.JsonObject
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface KadoApi {
    @GET("v2/public/orders/{orderId}")
    suspend fun getOrderStatus(@Path("orderId") orderId: String): Response<ResponseBody>
}