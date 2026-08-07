package com.getcode.ed25519kmp

import com.getcode.ed25519.Ed25519 as JniEd25519
import java.util.Base64

/**
 * Android actual: delegates to the existing JNI [com.getcode.ed25519.Ed25519].
 *
 * The JNI layer encodes/decodes through Android's Base64 internally (Base64.DEFAULT
 * which adds newlines). We replicate that encoding here using [java.util.Base64]
 * (available Java 8+, works in both Android runtime and JVM host tests) with the
 * MIME codec which also handles newlines on decode.
 */
actual object Ed25519Kmp {

    actual fun createKeyPair(seed: ByteArray): KeyPair {
        // Ed25519.java calls Base64.encodeToString(seed, Base64.DEFAULT) internally
        // for createKeyPair(byte[]). We must replicate Base64.DEFAULT encoding
        // (which wraps at 76 chars) so the JNI receives the expected format.
        val seedB64 = Base64.getMimeEncoder().encodeToString(seed)
        val jniPair = JniEd25519.createKeyPair(seedB64)
        // JniPair.publicKey / privateKey are base64 strings (Base64.DEFAULT = MIME).
        val publicKey = Base64.getMimeDecoder().decode(jniPair.publicKey)
        val privateKey = Base64.getMimeDecoder().decode(jniPair.privateKey)
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
