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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import timber.log.Timber


data class DeepLinkRequest(
    val mode: Mode,
    val clientSecret: List<Byte>,
    val paymentRequest: PaymentRequest? = null,
    val loginRequest: LoginRequest? = null,
    val successUrl: String?,
    val cancelUrl: String?,

    ) {
    @Serializable
    enum class Mode {
        @SerialName("payment") Payment,
        @SerialName("donation") Donation,
        @SerialName("login") Login,
    }

    companion object {
        fun from(data: ByteArray?): DeepLinkRequest? {
            data ?: return null
            val container = runCatching {
                val string = data.decodeBase64().decodeToString()
                Json.decodeFromString<JsonObject>(string)
            }.getOrNull() ?: return null

            val mode = container.decode<Mode>("mode") ?: return null
            Timber.d("mode=$mode")
            val secret = container.decode<String>("clientSecret") ?: return null
            val clientSecret = Base58.decode(secret)
            if (clientSecret.size != 11) {
                Timber.e("Invalid client secret")
                return null
            }

            val (successUrl, cancelUrl) = container.decode<ConfirmParams>("confirmParams")
                ?: ConfirmParams(null, null)


            val baseRequest = DeepLinkRequest(
                mode = mode,
                clientSecret = clientSecret.toList(),
                successUrl = successUrl?.url,
                cancelUrl = cancelUrl?.url,
            )

            when (mode) {
                Mode.Payment,
                Mode.Donation -> {
                    val currencyCode = container.decode<String, CurrencyCode?>("currency") { CurrencyCode.tryValueOf(it) }
                    val amount = container.decode<Double>("amount") ?: return null
                    val destinationString = container.decode<String>("destination") ?: return null

                    if (currencyCode == null) {
                        Timber.e("Invalid currency code")
                        return null
                    }

                    val destination = runCatching { PublicKey.fromBase58(destinationString) }
                        .getOrNull()
                    if (destination == null) {
                        ErrorUtils.handleError(Throwable())
                        Timber.e("Invalid destination address")
                        return null
                    }

                    val fiat = Fiat(currency = currencyCode, amount = amount)

                    return baseRequest.copy(
                        paymentRequest = PaymentRequest(
                            fiat = fiat,
                            destination = destination,
                        )
                    )
                }
                Mode.Login -> {
                    val loginContainer = container.decode<LoginKeys>("login")
                        ?: return null

                    val verifier = runCatching { PublicKey.fromBase58(loginContainer.verifier.orEmpty()) }
                        .getOrNull()

                    if (verifier == null) {
                        ErrorUtils.handleError(Throwable())
                        Timber.e("Invalid verifier address")
                        return null
                    }

                    val domain = Domain.from(loginContainer.domain) ?: return null

                    return baseRequest.copy(
                        loginRequest = LoginRequest(
                            verifier = verifier,
                            domain = domain
                        )
                    )
                }
            }
        }
    }
}
data class PaymentRequest(
    val fiat: Fiat,
    val destination: PublicKey,
)

data class LoginRequest(
    val verifier: PublicKey,
    val domain: Domain,
)

private inline fun <reified T> JsonObject.decode(key: String): T? {
    return runCatching { Json.decodeFromJsonElement<T>(getValue(key)) }
        .getOrElse {
            runCatching { Json.decodeFromString<T>(getValue(key).toString()) }
                .getOrNull()
        }
}

private inline fun <reified T, R> JsonObject.decode(key: String, map: (T) -> R): R? {
    return decode<T>(key)?.let { map(it) }
}

@Serializable
private data class RequestKeys(
    val mode: DeepLinkRequest.Mode,
    val currency: String,
    val destination: String,
    val amount: Double,
    val clientSecret: String,
    @SerialName("successURL") val successUrl: String?,
    @SerialName("cancelURL") val cancelUrl: String?,
    val confirmParams: ConfirmParams
)

@Serializable
private data class LoginKeys(
    val verifier: String? = null,
    val domain: String? = null,
    val clientSecret: String? = null,
)

@Serializable
private data class ConfirmParams(
    @SerialName("success") val success: UrlHolder?,
    @SerialName("cancel") val cancel: UrlHolder?
)

@Serializable
private data class UrlHolder(
    @SerialName("url") val url: String?,
)
