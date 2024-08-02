package com.getcode.view.main.getKin

import com.getcode.api.KadoApi
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
class KadoWebViewModel @Inject constructor(
    resources: ResourceHelper,
    private val kadoApi: KadoApi,
): BaseViewModel(resources) {

    suspend fun checkOrderStatus(orderId: String): Result<Unit> {
        while (true) {
            println("checking order status for $orderId")
            val result = kadoApi.getOrderStatus(orderId).toResult()

            result.map {
                val ret = parsePaymentStatus(it.string())
                if (ret != null) {
                    return ret
                }
                delay(2.seconds)
            }
        }
    }

    private fun parsePaymentStatus(jsonString: String): Result<Unit>? {
        val json = Json.parseToJsonElement(jsonString).jsonObject["data"]?.jsonObject ?: return null
        val paymentStatus = json["paymentStatus"]?.jsonPrimitive?.content

        return when (paymentStatus) {
            "success" -> Result.success(Unit)
            "failed" -> Result.failure(Throwable("Payment failed"))
            else -> null
        }
    }
}

fun <T> Response<T>.toResult(): Result<T> {
    return try {
        if (isSuccessful) {
            val body = body()
            if (body != null) {
                Result.success(body)
            } else {
                Result.failure(NullPointerException("Response body is null"))
            }
        } else {
            Result.failure(HttpException(this))
        }
    } catch (e: IOException) {
        Result.failure(e)
    }
}