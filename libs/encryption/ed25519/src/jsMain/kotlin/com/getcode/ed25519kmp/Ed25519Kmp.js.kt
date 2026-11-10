package com.getcode.ed25519kmp

/**
 * JS actual: the pure-Kotlin [Ed25519Reference], since the orlp C sources the other targets
 * compile don't reach Kotlin/JS.
 */
actual object Ed25519Kmp {

    actual fun createKeyPair(seed: ByteArray): KeyPair = Ed25519Reference.createKeyPair(seed)

    actual fun sign(message: ByteArray, publicKey: ByteArray, privateKey: ByteArray): ByteArray =
        Ed25519Reference.sign(message, publicKey, privateKey)

    actual fun verify(signature: ByteArray, message: ByteArray, publicKey: ByteArray): Boolean =
        Ed25519Reference.verify(signature, message, publicKey)

    actual fun onCurve(publicKey: ByteArray): Boolean = Ed25519Reference.onCurve(publicKey)
}
