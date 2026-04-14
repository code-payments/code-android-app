package com.flipcash.app.onramp

import android.content.Context
import com.google.android.gms.wallet.IsReadyToPayRequest
import com.google.android.gms.wallet.PaymentsClient
import com.google.android.gms.wallet.Wallet
import com.google.android.gms.wallet.WalletConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

class GooglePayReadiness @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val client: PaymentsClient by lazy {
        Wallet.getPaymentsClient(
            context,
            Wallet.WalletOptions.Builder()
                .setEnvironment(WalletConstants.ENVIRONMENT_PRODUCTION)
                .build()
        )
    }

    suspend fun check(): Status {
        val baseRequest = JSONObject().apply {
            put("apiVersion", 2)
            put("apiVersionMinor", 0)
            put("allowedPaymentMethods", JSONArray().put(
                JSONObject().apply {
                    put("type", "CARD")
                    put("parameters", JSONObject().apply {
                        put("allowedAuthMethods", JSONArray().apply {
                            put("PAN_ONLY")
                            put("CRYPTOGRAM_3DS")
                        })
                        put("allowedCardNetworks", JSONArray().apply {
                            put("VISA")
                            put("MASTERCARD")
                        })
                    })
                }
            ))
        }

        val supported = try {
            val request = IsReadyToPayRequest.fromJson(baseRequest.toString())
            client.isReadyToPay(request).await()
        } catch (_: Exception) {
            return Status.NotSupported
        }

        if (!supported) return Status.NotSupported

        val withInstrument = try {
            val request = IsReadyToPayRequest.fromJson(
                baseRequest.apply {
                    put("existingPaymentMethodRequired", true)
                }.toString()
            )
            client.isReadyToPay(request).await()
        } catch (_: Exception) {
            return Status.NoPaymentMethod
        }

        return if (withInstrument) Status.Ready else Status.NoPaymentMethod
    }

    enum class Status {
        Ready,
        NoPaymentMethod,
        NotSupported,
    }
}
