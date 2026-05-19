package com.flipcash.app.core.onramp.deeplinks

import android.os.Parcelable
import com.getcode.solana.keys.PublicKey
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
