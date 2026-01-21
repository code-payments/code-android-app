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

@Parcelize
sealed class OnRampDeeplinkOrigin: Parcelable {
    @Parcelize
    data object Menu : OnRampDeeplinkOrigin()

    @Parcelize
    data class Give(val tokenAddress: Mint?) : OnRampDeeplinkOrigin()

    @Parcelize
    data object Wallet: OnRampDeeplinkOrigin()

    @Parcelize
    data class TokenInfo(val mint: Mint): OnRampDeeplinkOrigin()

    @Parcelize
    data object Reserves: OnRampDeeplinkOrigin()


    fun forUri(): String {
        return when(this) {
            Menu -> "menu"
            is Give -> "give-${tokenAddress?.base58()?.base64UrlSafe}"
            Wallet -> "wallet"
            is TokenInfo -> "token-${mint.base58().base64UrlSafe}"
            Reserves -> "reserves"
        }.lowercase()
    }

    companion object Companion {
        fun fromRoute(route: AppRoute?): OnRampDeeplinkOrigin? {
            return when (route) {
                is AppRoute.Sheets.Menu -> Menu
                is AppRoute.Main.Give -> Give(route.mint)
                is AppRoute.Sheets.Wallet -> Wallet
                is AppRoute.Token.Info -> {
                    if (route.mint == Mint.usdf) Reserves else TokenInfo(route.mint)
                }

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

@Parcelize
data class WalletDeeplinkConnectionResult(
    val encryptionPublicKey: List<Byte>,
    val nonce: List<Byte>,
    val encryptedData: List<Byte>
): Parcelable

@Parcelize
data class WalletDeeplinkSigningResult(
    val nonce: List<Byte>,
    val encryptedData: List<Byte>,
): Parcelable

@Serializable
@Parcelize
data class ExternalWalletDeeplinkError(
    val errorCode: String,
    val errorMessage: String,
): Parcelable