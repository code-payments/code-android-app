package com.flipcash.app.core.phantom

import android.os.Parcelable
import com.flipcash.app.core.NavScreenProvider
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.model.core.ID
import com.getcode.solana.keys.PublicKey
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
data class PhantomEncryptedData(
    @SerialName("public_key")
    val publicKey: PublicKey,
    val session: String,
): Parcelable

@Parcelize
sealed interface PhantomDeeplinkOrigin: Parcelable {
    @Parcelize
    data object Menu: PhantomDeeplinkOrigin
    @Parcelize
    data class PoolWithId(val id: ID): PhantomDeeplinkOrigin
    @Parcelize
    data class PoolWithRendezvous(val keyPair: Ed25519.KeyPair): PhantomDeeplinkOrigin

    companion object {
        fun fromScreenProvider(provider: NavScreenProvider?): PhantomDeeplinkOrigin? {
            return when (provider) {
                is NavScreenProvider.HomeScreen.Menu.Root -> Menu
                is NavScreenProvider.HomeScreen.Pools.ChoiceSelection -> {
                    provider.rendezvous?.let { keyPair -> PoolWithRendezvous(keyPair) }
                    provider.poolId?.let { id -> PoolWithId(id) }
                }

                else -> null
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