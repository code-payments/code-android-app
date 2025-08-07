package com.flipcash.app.core.encryption

import com.getcode.solana.keys.PrivateKey
import com.getcode.solana.keys.PublicKey
import com.ionspin.kotlin.crypto.box.Box
import com.ionspin.kotlin.crypto.secretbox.SecretBox
import com.ionspin.kotlin.crypto.util.encodeToUByteArray


fun PublicKey.Companion.shared(publicKey: PublicKey, privateKey: PrivateKey): Result<PublicKey> {
    val pubKeyBytes = publicKey.bytes.map { it.toUByte() }.toUByteArray()
    val secretKeyBytes = privateKey.bytes.map { it.toUByte() }.toUByteArray()
    val result = runCatching { Box.beforeNM(pubKeyBytes, secretKeyBytes) }

    if (result.isFailure) {
        return Result.failure(SodiumError.SharedKeyFailed(result.exceptionOrNull()))
    }
    return Result.success(result.getOrNull()!!.toPublicKey())
}

fun PublicKey.Companion.fromUbytes(bytes: List<UByte>): PublicKey {
    return PublicKey(bytes.map { it.toByte() })
}

fun String.boxSeal(privateKey: List<Byte>, publicKey: List<Byte>, nonce: List<Byte>): Result<List<Byte>> {
    val nonceU = nonce.map { it.toUByte() }.toUByteArray()
    val message = encodeToUByteArray()

    val encrypted = runCatching {
        Box.easy(
            message = message,
            nonce = nonceU,
            recipientsPublicKey = publicKey.toByteArray().toUByteArray(),
            sendersSecretKey = privateKey.toByteArray().toUByteArray(),
        )
    }

    if (encrypted.isFailure) {
        return Result.failure(SodiumError.EncryptionFailed(encrypted.exceptionOrNull()))
    }

    return Result.success(encrypted.getOrNull()!!.map { it.toByte() })
}

fun List<Byte>.boxOpen(privateKey: List<Byte>, publicKey: List<Byte>, nonce: List<Byte>): Result<List<Byte>> {
    val sharedKey = PublicKey.shared(PublicKey(publicKey), PrivateKey(privateKey)).getOrThrow()
        .bytes.map { it.toUByte() }.toUByteArray()

    val nonceU = nonce.map { it.toUByte() }.toUByteArray()
    val cipher = map { it.toUByte() }.toUByteArray()

    val decrypted = runCatching { SecretBox.openEasy(cipher, nonceU, sharedKey) }

    if (decrypted.isFailure) {
        return Result.failure(SodiumError.DecryptionFailed(decrypted.exceptionOrNull()))
    }

    return Result.success(decrypted.getOrNull()!!.map { it.toByte() })
}

sealed class SodiumError {
    data class SharedKeyFailed(val root: Throwable? = null): Throwable(cause = root)
    data class EncryptionFailed(val root: Throwable? = null): Throwable(cause = root)
    data class DecryptionFailed(val root: Throwable? = null): Throwable(cause = root)
}

fun UByteArray.toPublicKey(): PublicKey {
    return PublicKey.fromUbytes(this.toList())
}