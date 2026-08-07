package com.getcode.vendor

import java.math.BigInteger
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

/**
 * JVM-only helpers for Base58. These use [MessageDigest] and [BigInteger] which are not
 * available on Kotlin/Native, so they live in `androidMain` rather than `commonMain`.
 *
 * All are extension functions / top-level functions on `Base58` so existing callers that
 * call `Base58.encodeChecked(...)` etc. keep compiling unchanged.
 */

/** Encodes version + payload with a 4-byte SHA256d checksum appended. */
fun Base58.encodeChecked(version: Int, payload: ByteArray): String {
    require(version in 0..255) { "Version not in range." }
    val addressBytes = ByteArray(1 + payload.size + 4)
    addressBytes[0] = version.toByte()
    payload.copyInto(addressBytes, 1)
    val checksum = hashTwice(addressBytes, 0, payload.size + 1)
    checksum.copyInto(addressBytes, payload.size + 1, 0, 4)
    return encode(addressBytes)
}

/** Decodes a base58-encoded string and verifies the 4-byte checksum. */
@Throws(Base58.AddressFormatException::class)
fun Base58.decodeChecked(input: String): ByteArray {
    val decoded = decode(input)
    if (decoded.size < 4) throw Base58.AddressFormatException.InvalidDataLength("Input too short: ${decoded.size}")
    val data = decoded.copyOfRange(0, decoded.size - 4)
    val checksum = decoded.copyOfRange(decoded.size - 4, decoded.size)
    val actualChecksum = hashTwice(data).copyOfRange(0, 4)
    if (!checksum.contentEquals(actualChecksum)) throw Base58.AddressFormatException.InvalidChecksum()
    return data
}

/** Decodes a base58 string to a [BigInteger]. */
@Throws(Base58.AddressFormatException::class)
fun Base58.decodeToBigInteger(input: String): BigInteger = BigInteger(1, decode(input))

/**
 * Computes SHA-256(SHA-256(input[offset..<offset+length])).
 */
@JvmOverloads
fun Base58.hashTwice(input: ByteArray, offset: Int = 0, length: Int = input.size): ByteArray {
    val digest = try {
        MessageDigest.getInstance("SHA-256")
    } catch (e: NoSuchAlgorithmException) {
        throw RuntimeException(e)
    }
    digest.update(input, offset, length)
    return digest.digest(digest.digest())
}
