package com.getcode.models

import com.getcode.model.CurrencyCode
import com.getcode.model.Fiat
import com.getcode.network.repository.decodeBase64
import com.getcode.solana.keys.PublicKey
import com.getcode.vendor.Base58
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import timber.log.Timber

data class DeepLinkPaymentRequest(
    val mode: Mode,
    val fiat: Fiat,
    val destination: PublicKey,
    val clientSecret: List<Byte>,
    val successUrl: String?,
    val cancelUrl: String?,
) {
    @Serializable
    enum class Mode {
        @SerialName("payment") Payment,
        @SerialName("donation") Donation
    }

    @Serializable
    private data class RequestKeys(
        val mode: Mode,
        val currency: String,
        val destination: String,
        val amount: Double,
        val clientSecret: String,
        @SerialName("successURL") val successUrl: String?,
        @SerialName("cancelURL") val cancelUrl: String?,
        val confirmParams: ConfirmParams
    )

    @Serializable
    private data class ConfirmParams(
        @SerialName("success") val success: String?,
        @SerialName("cancel") val cancel: String?
    )

    @Serializable
    private enum class UrlKeys {
        @SerialName("url") Url
    }

    companion object {
        fun from(data: ByteArray?): DeepLinkPaymentRequest? {
            data ?: return null
            val container = runCatching {
                val string = data.decodeBase64().decodeToString()
                Json.decodeFromString<JsonObject>(string)
            }.getOrNull() ?: return null

            val mode = container.decode<Mode>("mode") ?: return null
            val currencyCode = container.decode<String, CurrencyCode?>("currency") { CurrencyCode.tryValueOf(it) }
            val amount = container.decode<Double>("amount") ?: return null
            val destinationString = container.decode<String>("destination") ?: return null
            val secret = container.decode<String>("clientSecret") ?: return null

            val (successUrl, cancelUrl) = container.decode<ConfirmParams>("confirmParams")
                ?: ConfirmParams(null, null)

            if (currencyCode == null) {
                Timber.e("Invalid currency code")
                return null
            }

            val destination = runCatching { PublicKey.fromBase58(destinationString) }
                .getOrNull()
            if (destination == null) {
                Timber.e("Invalid destination address")
                return null
            }

            val fiat = Fiat(currency = currencyCode, amount = amount)
            val clientSecret = Base58.decode(secret)
            if (clientSecret.size != 11) {
                Timber.e("Invalid client secret")
                return null
            }

            return DeepLinkPaymentRequest(
                mode = mode,
                fiat = fiat,
                destination = destination,
                clientSecret = clientSecret.toList(),
                successUrl = successUrl,
                cancelUrl = cancelUrl
            )
        }
    }
}

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
