package com.getcode.ed25519kmp

import net.i2p.crypto.eddsa.EdDSAEngine
import net.i2p.crypto.eddsa.EdDSAPrivateKey
import net.i2p.crypto.eddsa.EdDSAPublicKey
import net.i2p.crypto.eddsa.math.GroupElement
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec
import java.security.MessageDigest

/**
 * JVM (desktop) actual: pure Java, on `net.i2p.crypto:eddsa`, so there is no native library to
 * ship per OS.
 *
 * The 64-byte private key keeps the orlp layout the Android and Apple actuals produce: the clamped
 * SHA-512 expansion of the seed (`a || prefix`). eddsa calls that same value `h`, and accepts it
 * directly through [EdDSAPrivateKeySpec]'s `(spec, h)` constructor, so a key created on any
 * platform signs identically here. The JDK's own Ed25519 provider can't do this: its private key
 * spec only takes the 32-byte seed, which the orlp key doesn't contain.
 */
actual object Ed25519Kmp {

    private val spec = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519)

    actual fun createKeyPair(seed: ByteArray): KeyPair {
        val privateKey = EdDSAPrivateKey(EdDSAPrivateKeySpec(seed, spec))
        return KeyPair(publicKey = privateKey.abyte, privateKey = privateKey.h)
    }

    actual fun sign(
        message: ByteArray,
        publicKey: ByteArray,
        privateKey: ByteArray,
    ): ByteArray {
        val key = EdDSAPrivateKey(EdDSAPrivateKeySpec(spec, privateKey))
        return EdDSAEngine(MessageDigest.getInstance(spec.hashAlgorithm)).run {
            initSign(key)
            update(message)
            sign()
        }
    }

    actual fun verify(
        signature: ByteArray,
        message: ByteArray,
        publicKey: ByteArray,
    ): Boolean = runCatching {
        val key = EdDSAPublicKey(EdDSAPublicKeySpec(publicKey, spec))
        EdDSAEngine(MessageDigest.getInstance(spec.hashAlgorithm)).run {
            initVerify(key)
            update(message)
            verify(signature)
        }
    }.getOrDefault(false)

    actual fun onCurve(publicKey: ByteArray): Boolean = runCatching {
        GroupElement(spec.curve, publicKey)
        true
    }.getOrDefault(false)
}
