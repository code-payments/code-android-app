package com.getcode.ed25519kmp

/**
 * Cross-platform Ed25519 signatures (RFC 8032 / orlp implementation).
 *
 * All byte arrays are raw (not base64). Sizes:
 *   seed       = 32 bytes
 *   publicKey  = 32 bytes
 *   privateKey = 64 bytes  (the clamped SHA-512 expansion of the seed, orlp convention —
 *                            not `seed || publicKey`; the seed is not recoverable from it)
 *   signature  = 64 bytes
 */
expect object Ed25519Kmp {

    /** Derives a (publicKey, privateKey) pair from a 32-byte seed. */
    fun createKeyPair(seed: ByteArray): KeyPair

    /**
     * Signs [message] with [privateKey] (64-byte extended key) + [publicKey].
     * Returns a 64-byte signature.
     */
    fun sign(message: ByteArray, publicKey: ByteArray, privateKey: ByteArray): ByteArray

    /**
     * Verifies [signature] over [message] using [publicKey].
     * Returns true iff the signature is valid.
     */
    fun verify(signature: ByteArray, message: ByteArray, publicKey: ByteArray): Boolean

    /** Returns true iff [publicKey] is a valid point on the Ed25519 curve. */
    fun onCurve(publicKey: ByteArray): Boolean
}

/** Ed25519 key pair. */
data class KeyPair(val publicKey: ByteArray, val privateKey: ByteArray) {
    override fun equals(other: Any?): Boolean =
        other is KeyPair &&
            publicKey.contentEquals(other.publicKey) &&
            privateKey.contentEquals(other.privateKey)
    override fun hashCode(): Int = 31 * publicKey.contentHashCode() + privateKey.contentHashCode()
}
