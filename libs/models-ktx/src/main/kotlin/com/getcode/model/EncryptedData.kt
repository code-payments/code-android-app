package com.getcode.model

import com.getcode.ed25519.Ed25519.KeyPair

fun EncryptedData.decryptMessageUsingNaClBox(keyPair: KeyPair): String? {
    val encryptionKey = keyPair.encryptionPrivateKey ?: return null

    val data = encryptedData.boxOpen(
        privateKey = encryptionKey,
        publicKey = peerPublicKey,
        nonce = nonce,
    ).getOrNull() ?: return null

    return String(data.toByteArray())
}