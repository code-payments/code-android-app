package com.getcode.model

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
) {
    fun decryptMessageUsingNaClBox(keyPair: KeyPair): String? {
        val encryptionKey = keyPair.encryptionPrivateKey ?: return null

        val data = encryptedData.boxOpen(
            privateKey = encryptionKey,
            publicKey = peerPublicKey,
            nonce = nonce,
        ).getOrNull() ?: return null

        return String(data.toByteArray())
    }
}
