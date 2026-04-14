package com.flipcash.app.onramp

import android.content.Context
import android.os.Build
import com.google.android.gms.wallet.IsReadyToPayRequest
import com.google.android.gms.wallet.PaymentsClient
import com.google.android.gms.wallet.Wallet
import com.google.android.gms.wallet.WalletConstants
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.featureflags.FeatureFlagController
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

class GooglePayReadiness @Inject constructor(
    @ApplicationContext private val context: Context,
    private val featureFlags: FeatureFlagController,
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
        // Emulators report fake readiness — GMS always returns true
        // even with existingPaymentMethodRequired. Short-circuit unless sandbox mode is on.
        if (isEmulator() && !featureFlags.get(FeatureFlag.CoinbaseOnRampSandbox)) return Status.NotSupported

        val baseRequest = baseIsReadyToPayRequest()

        val supported = try {
            val request = IsReadyToPayRequest.fromJson(baseRequest.toString())
            client.isReadyToPay(request).await()
        } catch (_: Exception) {
            return Status.NotSupported
        }

        if (!supported) return Status.NotSupported

        val withInstrument = try {
            val instrumentRequest = baseIsReadyToPayRequest().apply {
                put("existingPaymentMethodRequired", true)
            }
            val request = IsReadyToPayRequest.fromJson(instrumentRequest.toString())
            client.isReadyToPay(request).await()
        } catch (_: Exception) {
            return Status.NoPaymentMethod
        }

        return if (withInstrument) Status.Ready else Status.NoPaymentMethod
    }

    private fun baseIsReadyToPayRequest() = JSONObject().apply {
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

    private fun isEmulator(): Boolean {
        return Build.FINGERPRINT.startsWith("generic")
            || Build.FINGERPRINT.startsWith("unknown")
            || Build.MODEL.contains("Emulator")
            || Build.MODEL.contains("Android SDK built for")
            || Build.MODEL.contains("sdk_gphone")
            || Build.MANUFACTURER.contains("Genymotion")
            || Build.HARDWARE.contains("goldfish")
            || Build.HARDWARE.contains("ranchu")
            || Build.PRODUCT.contains("sdk")
            || Build.PRODUCT.contains("emulator")
    }

    enum class Status {
        Ready,
        NoPaymentMethod,
        NotSupported,
    }
}
