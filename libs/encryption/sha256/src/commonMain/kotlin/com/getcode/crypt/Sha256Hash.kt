package com.getcode.crypt

import kotlin.jvm.JvmStatic
import org.kotlincrypto.hash.sha2.SHA256

/*
 * Copyright 2011 Google Inc.
 * Copyright 2014 Andreas Schildbach
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Wraps a 32-byte SHA-256 hash so that [equals] and [hashCode] work correctly,
 * making it safe to use as a map key. Provides factory methods for computing
 * single and double SHA-256 hashes.
 */
class Sha256Hash private constructor(val bytes: ByteArray) : Comparable<Sha256Hash> {

    companion object {
        /** The byte length of a SHA-256 hash output. */
        const val LENGTH: Int = 32

        /** A hash whose bytes are all zeros. */
        val ZERO_HASH: Sha256Hash = wrap(ByteArray(LENGTH))

        /** Creates a new instance that wraps the given raw hash bytes (must be exactly 32 bytes). */
        @JvmStatic
        fun wrap(rawHashBytes: ByteArray): Sha256Hash {
            require(rawHashBytes.size == LENGTH) {
                "Expected $LENGTH bytes but got ${rawHashBytes.size}"
            }
            return Sha256Hash(rawHashBytes)
        }

        /**
         * Creates a new instance that wraps the given hex-encoded hash value
         * (must represent exactly 32 bytes, i.e., 64 hex characters).
         */
        @JvmStatic
        fun wrap(hexString: String): Sha256Hash = wrap(decodeHex(hexString))

        /** Creates a new instance wrapping the given bytes with their order reversed. */
        @JvmStatic
        fun wrapReversed(rawHashBytes: ByteArray): Sha256Hash = wrap(reverseBytes(rawHashBytes))

        /** Computes a single SHA-256 hash of [contents] and wraps the result. */
        @JvmStatic
        fun of(contents: ByteArray): Sha256Hash = wrap(hash(contents))

        /**
         * Computes a double SHA-256 hash (SHA-256(SHA-256(contents))) of [contents]
         * and wraps the result.
         */
        @JvmStatic
        fun twiceOf(contents: ByteArray): Sha256Hash = wrap(hashTwice(contents))

        /**
         * Computes a double SHA-256 hash over the concatenation of [content1] and [content2]
         * and wraps the result.
         */
        @JvmStatic
        fun twiceOf(content1: ByteArray, content2: ByteArray): Sha256Hash =
            wrap(hashTwice(content1, content2))

        /** Calculates the SHA-256 hash of [input]. */
        @JvmStatic
        fun hash(input: ByteArray): ByteArray = hash(input, 0, input.size)

        /** Calculates the SHA-256 hash of [length] bytes from [input] starting at [offset]. */
        @JvmStatic
        fun hash(input: ByteArray, offset: Int, length: Int): ByteArray {
            val digest = SHA256()
            digest.update(input, offset, length)
            return digest.digest()
        }

        /** Calculates SHA-256(SHA-256([input])). */
        @JvmStatic
        fun hashTwice(input: ByteArray): ByteArray = hashTwice(input, 0, input.size)

        /**
         * Calculates SHA-256(SHA-256([input1] || [input2])), equivalent to concatenating
         * the two arrays and calling [hashTwice].
         */
        @JvmStatic
        fun hashTwice(input1: ByteArray, input2: ByteArray): ByteArray {
            val first = SHA256().run {
                update(input1)
                update(input2)
                digest()
            }
            return SHA256().also { it.update(first) }.digest()
        }

        /** Calculates SHA-256(SHA-256([length] bytes of [input] starting at [offset])). */
        @JvmStatic
        fun hashTwice(input: ByteArray, offset: Int, length: Int): ByteArray {
            val first = SHA256().also { it.update(input, offset, length) }.digest()
            return SHA256().also { it.update(first) }.digest()
        }

        /**
         * Calculates SHA-256(SHA-256([input1][offset1]..[length1] || [input2][offset2]..[length2])).
         */
        @JvmStatic
        fun hashTwice(
            input1: ByteArray, offset1: Int, length1: Int,
            input2: ByteArray, offset2: Int, length2: Int,
        ): ByteArray {
            val first = SHA256().run {
                update(input1, offset1, length1)
                update(input2, offset2, length2)
                digest()
            }
            return SHA256().also { it.update(first) }.digest()
        }

        // ── helpers ──────────────────────────────────────────────────────────

        private fun reverseBytes(src: ByteArray): ByteArray {
            val buf = ByteArray(src.size)
            for (i in src.indices) buf[i] = src[src.size - 1 - i]
            return buf
        }

        private fun decodeHex(hex: String): ByteArray {
            require(hex.length % 2 == 0) { "Hex string must have even length" }
            val result = ByteArray(hex.length / 2)
            for (i in result.indices) {
                val hi = hex[i * 2].digitToInt(16)
                val lo = hex[i * 2 + 1].digitToInt(16)
                result[i] = ((hi shl 4) or lo).toByte()
            }
            return result
        }
    }

    /** Returns a reversed copy of the internal byte array. */
    fun getReversedBytes(): ByteArray {
        val buf = ByteArray(bytes.size)
        for (i in bytes.indices) buf[i] = bytes[bytes.size - 1 - i]
        return buf
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Sha256Hash) return false
        return bytes.contentEquals(other.bytes)
    }

    /**
     * Returns a hash code derived from the last four bytes of the wrapped hash.
     * Those bytes are typically non-zero even for proof-of-work hashes.
     */
    override fun hashCode(): Int =
        ((bytes[LENGTH - 4].toInt() and 0xFF) shl 24) or
        ((bytes[LENGTH - 3].toInt() and 0xFF) shl 16) or
        ((bytes[LENGTH - 2].toInt() and 0xFF) shl 8) or
        (bytes[LENGTH - 1].toInt() and 0xFF)

    /** Returns the lowercase hex representation of the hash bytes. */
    override fun toString(): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            sb.append("0123456789abcdef"[v ushr 4])
            sb.append("0123456789abcdef"[v and 0x0F])
        }
        return sb.toString()
    }

    /**
     * Compares hashes in reverse-byte order (little-endian numeric comparison),
     * consistent with Bitcoin's natural ordering.
     */
    override fun compareTo(other: Sha256Hash): Int {
        for (i in LENGTH - 1 downTo 0) {
            val a = bytes[i].toInt() and 0xFF
            val b = other.bytes[i].toInt() and 0xFF
            if (a > b) return 1
            if (a < b) return -1
        }
        return 0
    }
}
