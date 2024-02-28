package com.getcode.models

import com.getcode.model.CurrencyCode
import com.getcode.model.Domain
import com.getcode.model.Fiat
import com.getcode.network.repository.decodeBase64
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.ErrorUtils
import com.getcode.vendor.Base58
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber

sealed class Mode {
    object Payment : Mode()
    object Donation : Mode()
    object Login : Mode()
}

data class DeepLinkRequest(
    val mode: Mode,
    val clientSecret: List<Byte>,
    val paymentRequest: PaymentRequest? = null,
    val loginRequest: LoginRequest? = null,
    val successUrl: String?,
    val cancelUrl: String?
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun from(data: ByteArray?, base58Decoder: (String) -> ByteArray = Base58::decode): DeepLinkRequest? {
            data ?: return null

            val stringData = data.decodeBase64().decodeToString()
            val container = json.decodeFromString<JsonObject>(stringData)

            val mode = when (container["mode"]?.jsonPrimitive?.content) {
                "payment" -> Mode.Payment
                "donation" -> Mode.Donation
                "login" -> Mode.Login
                else -> {
                    Timber.e("Unsupported mode")
                    return null
                }
            }

            val clientSecret = base58Decoder(container["clientSecret"]?.jsonPrimitive?.content ?: return null)
            validateClientSecret(clientSecret)

            val successUrl = container["successURL"]?.jsonPrimitive?.content
            val cancelUrl = container["cancelURL"]?.jsonPrimitive?.content

            val baseRequest = DeepLinkRequest(mode, clientSecret.toList(), null, null, successUrl, cancelUrl)

            return when (mode) {
                is Mode.Payment, is Mode.Donation -> handlePaymentOrDonationRequest(container, baseRequest)
                is Mode.Login -> handleLoginRequest(container, baseRequest)
            }
        }

        private fun validateClientSecret(clientSecret: ByteArray) {
            if (clientSecret.size != 11) {
                throw IllegalArgumentException("Invalid client secret size")
            }
        }

        private fun handlePaymentOrDonationRequest(container: JsonObject, baseRequest: DeepLinkRequest): DeepLinkRequest? {
            // Similar to previous implementation, with adjusted error handling and logging
        }

        private fun handleLoginRequest(container: JsonObject, baseRequest: DeepLinkRequest): DeepLinkRequest? {
            // Similar to previous implementation, with adjusted error handling and logging
        }
    }
}

data class PaymentRequest(
    val fiat: Fiat,
    val destination: PublicKey
)

data class LoginRequest(
    val verifier: PublicKey,
    val domain: Domain
)

// Implement error handling, logging, and other suggested improvements within these functions.
