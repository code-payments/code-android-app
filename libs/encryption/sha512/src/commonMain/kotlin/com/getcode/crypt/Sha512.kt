package com.getcode.crypt

import org.kotlincrypto.hash.sha2.SHA512

/**
 * One-shot SHA-512.
 *
 * Android already had SHA-512 as a `MessageDigest` extension in `androidMain`, which cannot cross
 * to iOS; this is the multiplatform equivalent, matching how [Sha256Hash] wraps kotlincrypto.
 */
object Sha512 {

    /** Length of a SHA-512 digest, in bytes. */
    const val LENGTH: Int = 64

    /** Calculates the SHA-512 hash of [input]. */
    fun hash(input: ByteArray): ByteArray = hash(input, 0, input.size)

    /** Calculates the SHA-512 hash of [length] bytes from [input] starting at [offset]. */
    fun hash(input: ByteArray, offset: Int, length: Int): ByteArray {
        val digest = SHA512()
        digest.update(input, offset, length)
        return digest.digest()
    }
}
