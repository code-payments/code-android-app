package com.getcode.ed25519kmp

import android.util.Base64
import com.getcode.ed25519.Ed25519 as JniEd25519

/**
 * Android actual: delegates to the existing JNI [com.getcode.ed25519.Ed25519].
 *
 * The JNI layer encodes/decodes through base64 internally; we handle that
 * translation here so [commonMain] consumers always work with raw [ByteArray].
 */
actual object Ed25519Kmp {

    actual fun createKeyPair(seed: ByteArray): KeyPair {
        // Ed25519.createKeyPair expects a base64-encoded seed (Base64.DEFAULT).
        val seedB64 = Base64.encodeToString(seed, Base64.DEFAULT)
        val jniPair = JniEd25519.createKeyPair(seedB64)
        // JniPair.publicKey / privateKey are base64 strings (Base64.DEFAULT).
        val publicKey = Base64.decode(jniPair.publicKey, Base64.DEFAULT)
        val privateKey = Base64.decode(jniPair.privateKey, Base64.DEFAULT)
        return KeyPair(publicKey = publicKey, privateKey = privateKey)
    }

    actual fun sign(
        message: ByteArray,
        publicKey: ByteArray,
        privateKey: ByteArray,
    ): ByteArray = JniEd25519.Signature(message, privateKey, publicKey)
        ?: error("Ed25519.Signature returned null")

    actual fun verify(
        signature: ByteArray,
        message: ByteArray,
        publicKey: ByteArray,
    ): Boolean = JniEd25519.Verify(signature, message, publicKey)

    actual fun onCurve(publicKey: ByteArray): Boolean =
        JniEd25519.OnCurve(publicKey)
}
