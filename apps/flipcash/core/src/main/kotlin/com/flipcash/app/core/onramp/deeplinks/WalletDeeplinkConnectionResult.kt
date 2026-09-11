package com.flipcash.app.core.onramp.deeplinks

import android.os.Parcelable
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.PublicKeyParceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
@TypeParceler<PublicKey, PublicKeyParceler>()
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
