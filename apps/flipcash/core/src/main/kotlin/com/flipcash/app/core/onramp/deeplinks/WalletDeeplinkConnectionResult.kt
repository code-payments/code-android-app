package com.flipcash.app.core.onramp.deeplinks

import android.os.Parcelable
import com.flipcash.app.core.AppRoute
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.utils.base64
import com.getcode.solana.keys.PublicKey
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
    data object Give: OnRampDeeplinkOrigin()

    @Parcelize
    data object Wallet: OnRampDeeplinkOrigin()

    @Parcelize
    data class PoolWithId(val id: ID) : OnRampDeeplinkOrigin()

    @Parcelize
    data class PoolWithRendezvous(val keyPair: Ed25519.KeyPair) : OnRampDeeplinkOrigin()

    fun forUri(): String {
        return when(this) {
            is PoolWithId -> "pool-id_${id.base58}"
            is PoolWithRendezvous -> "pool-seed_${keyPair.seed.base64}"
            Menu -> "menu"
            Give -> "give"
            Wallet -> "wallet"
        }.lowercase()
    }

    companion object Companion {
        fun fromRoute(route: AppRoute?): OnRampDeeplinkOrigin? {
            return when (route) {
                is AppRoute.Sheets.Menu -> Menu
                is AppRoute.Sheets.Give -> Give
                is AppRoute.Pool.Details -> {
                    route.rendezvous?.let { keyPair -> PoolWithRendezvous(keyPair) }
                    route.poolId?.let { id -> PoolWithId(id) }
                }
                is AppRoute.Sheets.Wallet -> Wallet

                else -> null
            }
        }

        fun fromString(value: String?): OnRampDeeplinkOrigin? {
            return when {
                value == "menu" -> Menu
                value == "give" -> Give
                value == "wallet" -> Wallet
                value?.startsWith("pool-") == true -> {
                    val idStringWithPrefix = value.removePrefix("pool-")
                    val splits = idStringWithPrefix.split("_")
                    val prefix = splits.getOrNull(0) ?: return null
                    when (prefix) {
                        "seed" -> PoolWithRendezvous(Ed25519.createKeyPair(splits[1].decodeBase64()))
                        "id" -> PoolWithId(splits[1].decodeBase58().toList())
                        else -> return null
                    }
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