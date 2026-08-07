package com.getcode.crypt

import org.kotlincrypto.macs.hmac.sha2.HmacSHA256
import org.kotlincrypto.macs.hmac.sha2.HmacSHA512

/** Multiplatform HMAC utilities backed by kotlincrypto. */
object Hmac {

    /**
     * Computes an HMAC over [message] using [key] with the given [algorithm].
     *
     * Supported algorithm strings: `"HmacSHA512"`, `"HmacSHA256"`.
     */
    fun hmac(algorithm: String, key: ByteArray, message: ByteArray): ByteArray {
        return when (algorithm) {
            "HmacSHA512" -> {
                val mac = HmacSHA512(key)
                mac.update(message)
                mac.doFinal()
            }
            "HmacSHA256" -> {
                val mac = HmacSHA256(key)
                mac.update(message)
                mac.doFinal()
            }
            else -> throw IllegalArgumentException("Unsupported HMAC algorithm: $algorithm")
        }
    }

    /** Encodes [bytes] as a lowercase hex string. */
    fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        for (j in bytes.indices) {
            val v = bytes[j].toInt() and 0xFF
            hexChars[j * 2] = "0123456789abcdef"[v ushr 4]
            hexChars[j * 2 + 1] = "0123456789abcdef"[v and 0x0F]
        }
        return hexChars.concatToString()
    }
}
