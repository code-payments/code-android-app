package com.flipcash.app.core.bill

import android.net.Uri
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.ErrorUtils
import com.getcode.utils.decodeBase64
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
    val imageRequest: ImageRequest? = null,
    val successUrl: String?,
    val cancelUrl: String?,

    ) {
    @Serializable
    enum class Mode {
        @SerialName("payment") Payment,
        @SerialName("donation") Donation,
//        @SerialName("login") Login,
//        @SerialName("tip") Tip,
        @SerialName("internal:image") Image,
    }

    companion object {
        fun fromImage(uri: Uri): DeepLinkRequest {
            return DeepLinkRequest(
                mode = Mode.Image,
                clientSecret = emptyList(),
                imageRequest = ImageRequest(uri),
                cancelUrl = null,
                successUrl = null,
            )
        }

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

                    val destination = runCatching { com.getcode.solana.keys.PublicKey.fromBase58(destinationString) }
                        .getOrNull()
                    if (destination == null) {
                        ErrorUtils.handleError(Throwable())
                        Timber.e("Invalid destination address")
                        return null
                    }

                    // optional fees
                    val fees = container.decode<List<ProvidedFee>>("fees").orEmpty()

                    val fiat = Fiat(currencyCode = currencyCode, quarks = amount.toULong())

                    Timber.d("fiat=${fiat.quarks}, fees=$fees")
                    return baseRequest.copy(
                        paymentRequest = PaymentRequest(
                            fiat = fiat,
                            destination = destination,
                            fees = fees,
                        )
                    )
                }

                Mode.Image -> {
                    return baseRequest
                }
            }
        }
    }
}
data class PaymentRequest(
    val fiat: Fiat,
    val destination: PublicKey,
    val fees: List<ProvidedFee>,
)

data class ImageRequest(
    val uri: Uri
)

private inline fun <reified T> JsonObject.decode(key: String): T? {
    return runCatching { Json.decodeFromJsonElement<T>(getValue(key)) }
        .onFailure { Timber.e("failed to decode $key from result") }
        .getOrElse {
            runCatching { Json.decodeFromString<T>(getValue(key).toString()) }
                .getOrNull()
        }
}

private inline fun <reified T, R> JsonObject.decode(key: String, map: (T) -> R): R? {
    return decode<T>(key)?.let { map(it) }
}

@Serializable
private data class LoginKeys(
    val verifier: String? = null,
    val domain: String? = null,
    val clientSecret: String? = null,
)

@Serializable
private data class PlatformKeys(
    val name: String,
    val username: String,
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

@Serializable
data class ProvidedFee(
    val destination: String,
    val basisPoints: Int
)
