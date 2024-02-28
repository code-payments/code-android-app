package com.getcode.models

import com.getcode.model.CurrencyCode
import com.getcode.model.Domain
import com.getcode.model.Fiat
import com.getcode.network.repository.decodeBase64
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.ErrorUtils
import com.getcode.vendor.Base58
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber

@Serializable
data class DeepLinkRequest(
    val mode: Mode,
    val clientSecret: List<Byte>,
    val paymentRequest: PaymentRequest? = null,
    val loginRequest: LoginRequest? = null,
    val successUrl: String?,
    val cancelUrl: String?
) {
    enum class Mode {
        @SerialName("payment") Payment,
        @SerialName("donation") Donation,
        @SerialName("login") Login,
    }

    companion object {
        fun from(data: ByteArray?): DeepLinkRequest? {
            data ?: return null
            val json = Json { ignoreUnknownKeys = true }

            val stringData = data.decodeBase64().decodeToString()
            val container = json.decodeFromString<JsonObject>(stringData)

            val mode = json.decodeFromString<Mode?>(container["mode"]?.jsonPrimitive?.content ?: return null)
            Timber.d("mode=$mode")

            val secret = container["clientSecret"]?.jsonPrimitive?.content ?: return null
            val clientSecret = Base58.decode(secret)
            if (clientSecret.size != 11) {
                Timber.e("Invalid client secret")
                return null
            }

            val successUrl = container["successURL"]?.jsonPrimitive?.content
            val cancelUrl = container["cancelURL"]?.jsonPrimitive?.content

            val baseRequest = DeepLinkRequest(mode, clientSecret.toList(), null, null, successUrl, cancelUrl)

            return when (mode) {
                Mode.Payment, Mode.Donation -> handlePaymentOrDonationRequest(container, baseRequest)
                Mode.Login -> handleLoginRequest(container, baseRequest)
                else -> {
                    Timber.e("Unsupported mode")
                    null
                }
            }
        }

        private fun handlePaymentOrDonationRequest(container: JsonObject, baseRequest: DeepLinkRequest): DeepLinkRequest? {
            val currencyCode = CurrencyCode.tryValueOf(container["currency"]?.jsonPrimitive?.content ?: return null)
            val amount = container["amount"]?.jsonPrimitive?.doubleOrNull ?: return null
            val destinationString = container["destination"]?.jsonPrimitive?.content ?: return null

            val destination = PublicKey.fromBase58(destinationString)
            if (destination == null) {
                Timber.e("Invalid destination address")
                return null
            }

            val paymentRequest = PaymentRequest(Fiat(currencyCode, amount), destination)
            return baseRequest.copy(paymentRequest = paymentRequest)
        }

        private fun handleLoginRequest(container: JsonObject, baseRequest: DeepLinkRequest): DeepLinkRequest? {
            val verifierString = container["verifier"]?.jsonPrimitive?.content ?: return null
            val domainString = container["domain"]?.jsonPrimitive?.content ?: return null

            val verifier = PublicKey.fromBase58(verifierString)
            val domain = Domain.from(domainString)

            if (verifier == null || domain == null) {
                Timber.e("Invalid login request parameters")
                return null
            }

            val loginRequest = LoginRequest(verifier, domain)
            return baseRequest.copy(loginRequest = loginRequest)
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
