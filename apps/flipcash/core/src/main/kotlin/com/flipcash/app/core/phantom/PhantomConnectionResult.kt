package com.flipcash.app.core.phantom

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
data class PhantomWalletConnection(
    @SerialName("public_key")
    val publicKey: PublicKey,
    val session: String,
): Parcelable

@Serializable
@Parcelize
data class PhantomSignedTransaction(
    @SerialName("transaction")
    val serializedTransaction: String,
): Parcelable

@Parcelize
sealed class PhantomDeeplinkOrigin: Parcelable {
    @Parcelize
    data object Menu : PhantomDeeplinkOrigin()

    @Parcelize
    data object Cash: PhantomDeeplinkOrigin()

    @Parcelize
    data object Balance: PhantomDeeplinkOrigin()

    @Parcelize
    data class PoolWithId(val id: ID) : PhantomDeeplinkOrigin()

    @Parcelize
    data class PoolWithRendezvous(val keyPair: Ed25519.KeyPair) : PhantomDeeplinkOrigin()

    fun forUri(): String {
        return when(this) {
            is PoolWithId -> "pool-id_${id.base58}"
            is PoolWithRendezvous -> "pool-seed_${keyPair.seed.base64}"
            Menu -> "menu"
            Cash -> "cash"
            Balance -> "balance"
        }.lowercase()
    }

    companion object {
        fun fromRoute(route: AppRoute?): PhantomDeeplinkOrigin? {
            return when (route) {
                is AppRoute.Sheets.Menu -> Menu
                is AppRoute.Sheets.Cash -> Cash
                is AppRoute.Pool.Details -> {
                    route.rendezvous?.let { keyPair -> PoolWithRendezvous(keyPair) }
                    route.poolId?.let { id -> PoolWithId(id) }
                }
                is AppRoute.Sheets.Balance -> Balance

                else -> null
            }
        }

        fun fromString(value: String?): PhantomDeeplinkOrigin? {
            return when {
                value == "menu" -> Menu
                value == "cash" -> Cash
                value == "balance" -> Balance
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
data class PhantomConnectionResult(
    val encryptionPublicKey: List<Byte>,
    val nonce: List<Byte>,
    val encryptedData: List<Byte>
): Parcelable

@Parcelize
data class PhantomSigningResult(
    val nonce: List<Byte>,
    val encryptedData: List<Byte>,
): Parcelable

@Serializable
@Parcelize
data class PhantomDeeplinkError(
    val errorCode: String,
    val errorMessage: String,
): Parcelable