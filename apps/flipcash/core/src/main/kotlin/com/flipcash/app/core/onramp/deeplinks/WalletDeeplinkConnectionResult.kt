package com.flipcash.app.core.onramp.deeplinks

import android.os.Parcelable
import com.flipcash.app.core.AppRoute
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.utils.base64
import com.getcode.opencode.utils.base64UrlSafe
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.base58
import com.getcode.utils.base58
import com.getcode.utils.decodeBase58
import com.getcode.utils.decodeBase64
import com.getcode.utils.serializer.ByteListAsBase64Serializer
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
data class ExternalWalletConnection(
    @SerialName("public_key")
    val publicKey: PublicKey,
    val session: String,
): Parcelable

@Serializable
@Parcelize
data class ExternallySignedTransaction(
    @SerialName("transaction")
    val serializedTransaction: String,
): Parcelable

@Serializable
@Parcelize
sealed class OnRampDeeplinkOrigin: Parcelable {
    @Serializable @Parcelize
    data object Menu : OnRampDeeplinkOrigin()

    @Serializable @Parcelize
    data class Give(val tokenAddress: Mint?) : OnRampDeeplinkOrigin()

    @Serializable @Parcelize
    data object Wallet: OnRampDeeplinkOrigin()

    @Serializable @Parcelize
    data class TokenInfo(val mint: Mint): OnRampDeeplinkOrigin()

    @Serializable @Parcelize
    data object Reserves: OnRampDeeplinkOrigin()

    @Serializable @Parcelize
    data object CurrencyCreator: OnRampDeeplinkOrigin()


    fun forUri(): String {
        return when(this) {
            Menu -> "menu"
            is Give -> "give-${tokenAddress?.base58()?.base64UrlSafe}"
            Wallet -> "wallet"
            is TokenInfo -> "token-${mint.base58().base64UrlSafe}"
            Reserves -> "reserves"
            CurrencyCreator -> "currency-creator"
        }.lowercase()
    }

    companion object Companion {
        fun fromRoute(route: AppRoute?): OnRampDeeplinkOrigin? {
            return when (route) {
                is AppRoute.Sheets.Menu -> Menu
                is AppRoute.Sheets.Give -> Give(route.mint)
                is AppRoute.Sheets.Wallet -> Wallet
                is AppRoute.Token.Info -> {
                    if (route.mint == Mint.usdf) Reserves else TokenInfo(route.mint)
                }
                is AppRoute.Token.CurrencyCreator -> CurrencyCreator

                else -> null
            }
        }

        fun fromString(value: String?): OnRampDeeplinkOrigin? {
            return when {
                value == "menu" -> Menu
                value?.startsWith("give-") == true -> {
                    val tokenAddress = value.removePrefix("give-").decodeBase64().base58
                    val mint = runCatching {
                        Mint(tokenAddress)
                    }.getOrNull() ?: return null
                    Give(mint)
                }
                value == "wallet" -> Wallet
                value == "reserves" -> Reserves
                value == "currency-creator" -> CurrencyCreator
                value?.startsWith("token-") == true -> {
                    val mintString = value.removePrefix("token-").decodeBase64().base58
                    val mint = runCatching {
                        Mint(mintString)
                    }.onFailure { it.printStackTrace() }.getOrNull() ?: return null

                    TokenInfo(mint)
                }

                else -> return null
            }
        }
    }
}

@Serializable
@Parcelize
data class WalletDeeplinkConnectionResult(
    @Serializable(with = ByteListAsBase64Serializer::class) val encryptionPublicKey: List<Byte>,
    @Serializable(with = ByteListAsBase64Serializer::class) val nonce: List<Byte>,
    @Serializable(with = ByteListAsBase64Serializer::class) val encryptedData: List<Byte>
): Parcelable

@Serializable
@Parcelize
data class WalletDeeplinkSigningResult(
    @Serializable(with = ByteListAsBase64Serializer::class) val nonce: List<Byte>,
    @Serializable(with = ByteListAsBase64Serializer::class) val encryptedData: List<Byte>,
): Parcelable

@Serializable
@Parcelize
data class ExternalWalletDeeplinkError(
    val errorCode: String,
    val errorMessage: String,
): Parcelable