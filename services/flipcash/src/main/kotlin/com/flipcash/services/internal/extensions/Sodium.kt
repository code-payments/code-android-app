package com.flipcash.services.internal.extensions

import android.util.Base64
import com.getcode.ed25519.Ed25519
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.model.core.errors.SodiumError
import com.getcode.solana.keys.PrivateKey
import com.getcode.solana.keys.PublicKey
import com.ionspin.kotlin.crypto.box.Box
import com.ionspin.kotlin.crypto.secretbox.SecretBox
import com.ionspin.kotlin.crypto.signature.Signature

/** Some cryptographic function require the private
 * key to be formatted this way to work correctly.
 * A good example of this would Sodium and the box
 * `seal` and `open` functions.
 */
internal val KeyPair.encryptionPrivateKey: PrivateKey?
    get() {
        if (seed.isNullOrEmpty()) return null

        val bytes = Base64.decode(seed, Base64.DEFAULT) + publicKeyBytes
        return PrivateKey(bytes.toList())
    }

internal val PrivateKey.curvePrivate: Result<PublicKey>
    get() {
        val localBytes = bytes

        val result = runCatching { Signature.ed25519SkToCurve25519(localBytes.map { it.toUByte() }.toUByteArray()) }

        if (result.isFailure) {
            return Result.failure(SodiumError.ConversionToCurveFailed(result.exceptionOrNull()))
        }
        return Result.success(result.getOrNull()!!.toPublicKey())
    }

internal val PublicKey.curvePublic: Result<PublicKey>
    get() {
        val localBytes = bytes

        val result = runCatching { Signature.ed25519PkToCurve25519(localBytes.map { it.toUByte() }.toUByteArray()) }
        if (result.isFailure) {
            return Result.failure(SodiumError.ConversionToCurveFailed(result.exceptionOrNull()))
        }
        return Result.success(result.getOrNull()!!.toPublicKey())
    }

internal fun PublicKey.Companion.shared(publicKey: PublicKey, privateKey: PublicKey): Result<PublicKey> {
    val pubKeyBytes = publicKey.bytes.map { it.toUByte() }.toUByteArray()
    val secretKeyBytes = privateKey.bytes.map { it.toUByte() }.toUByteArray()
    val result = runCatching { Box.beforeNM(pubKeyBytes, secretKeyBytes) }

    if (result.isFailure) {
        return Result.failure(SodiumError.SharedKeyFailed(result.exceptionOrNull()))
    }
    return Result.success(result.getOrNull()!!.toPublicKey())
}

internal fun PublicKey.Companion.fromUbytes(bytes: List<UByte>): PublicKey {
    return PublicKey(bytes.map { it.toByte() })
}

internal fun String.boxSeal(privateKey: PrivateKey, publicKey: PublicKey, nonce: List<Byte>): Result<List<Byte>> {
    val publicCurve = publicKey.curvePublic.getOrThrow()
    val privateCurve = privateKey.curvePrivate.getOrThrow()

    val sharedKey = PublicKey.shared(publicCurve, privateCurve).getOrThrow()
    val nonceU = nonce.map { it.toUByte() }.toUByteArray()
    val message = toByteArray().map { it.toUByte() }.toUByteArray()

    val encrypted = runCatching {
        SecretBox.easy(
            key = sharedKey.bytes.map { it.toUByte() }.toUByteArray(),
            message = message,
            nonce = nonceU
        )
    }

    if (encrypted.isFailure) {
        return Result.failure(SodiumError.EncryptionFailed(encrypted.exceptionOrNull()))
    }

    return Result.success(encrypted.getOrNull()!!.map { it.toByte() })
}

internal fun List<Byte>.boxOpen(privateKey: PrivateKey, publicKey: PublicKey, nonce: List<Byte>): Result<List<Byte>> {
    val publicCurve = publicKey.curvePublic.getOrThrow()
    val privateCurve = privateKey.curvePrivate.getOrThrow()

    val sharedKey = PublicKey.shared(publicCurve, privateCurve).getOrThrow()
        .bytes.map { it.toUByte() }.toUByteArray()

    val nonceU = nonce.map { it.toUByte() }.toUByteArray()
    val cipher = map { it.toUByte() }.toUByteArray()

    val decrypted = runCatching { SecretBox.openEasy(cipher, nonceU, sharedKey) }

    if (decrypted.isFailure) {
        return Result.failure(SodiumError.DecryptionFailed(decrypted.exceptionOrNull()))
    }

    return Result.success(decrypted.getOrNull()!!.map { it.toByte() })
}

internal fun PublicKey.Companion.generate(): PublicKey = Ed25519.createSeed32().toPublicKey()