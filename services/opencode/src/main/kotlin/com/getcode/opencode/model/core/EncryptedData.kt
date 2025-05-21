package com.getcode.opencode.model.core

import com.getcode.ed25519.Ed25519.KeyPair
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
