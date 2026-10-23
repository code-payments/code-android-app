package com.getcode.model

import com.getcode.solana.keys.PublicKey
import com.getcode.utils.serializer.PublicKeyAsStringSerializer
import kotlinx.serialization.Serializable

@Serializable
data class EncryptedData(
    @Serializable(with = PublicKeyAsStringSerializer::class)
    val peerPublicKey: PublicKey,
    val nonce: List<Byte>,
    val encryptedData: List<Byte>
)
