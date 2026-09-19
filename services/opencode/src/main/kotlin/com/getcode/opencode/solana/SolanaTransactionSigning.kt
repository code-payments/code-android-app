package com.getcode.opencode.solana

import com.getcode.ed25519.Ed25519
import com.getcode.solana.keys.Signature

/**
 * Signing depends on [Ed25519], the Android-only JNI wrapper (its `KeyPair` is `Parcelable`), not
 * the KMP `Ed25519Kmp`. Kept out of [SolanaTransaction] itself so `:libs:solana:encoding` compiles
 * on the Apple targets; encoding a transaction doesn't need to sign one.
 */
fun SolanaTransaction.signatures(vararg keyPair: Ed25519.KeyPair): List<Signature> {
    return keyPair.map { kp ->
        val result = kp.sign(message.encode().toByteArray()).toList()
        Signature(result)
    }
}

fun SolanaTransaction.sign(vararg keyPairs: Ed25519.KeyPair): List<Signature> {
    val requiredSignatureCount = message.header.requiredSignatures
    if (keyPairs.size > requiredSignatureCount) {
        throw Exception(SigningError.tooManySigners.name)
    }

    val messageData = message.encode()
    val newSignatures = mutableListOf<Signature>()

    keyPairs.forEach { keyPair ->
        val signatureIndex =
            message.accountKeys.indexOfFirst { it.bytes == keyPair.publicKeyBytes.toList() }
        if (signatureIndex == -1) {
            throw Exception("accountNotInAccountList. Account: ${keyPair.publicKey}")
        }

        val signature = Ed25519.sign(messageData.toByteArray(), keyPair)
        newSignatures.add(Signature(signature.toList()))
    }

    return newSignatures
}

enum class SigningError {
    tooManySigners,
    accountNotInAccountList,
    invalidKey
}
