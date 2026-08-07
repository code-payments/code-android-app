package com.getcode.crypt

import kotlin.jvm.JvmStatic
import org.kotlincrypto.macs.hmac.sha2.HmacSHA512

/*
 * Copyright (c) 2012 Cole Barnes [cryptofreek{at}gmail{dot}com]
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

/**
 * Pure-Kotlin PBKDF2-HMAC-SHA512 implementation following RFC 2898 §5.2.
 * Passes all RFC 6070 test vectors (adapted for SHA-512).
 */
object PBKDF2SHA512 {

    private const val H_LEN = 64 // HmacSHA512 output size in bytes

    /**
     * Derives a key of [dkLen] bytes from password [P] and salt [S] using [c]
     * PBKDF2 iterations with HMAC-SHA-512 as the pseudorandom function.
     */
    @JvmStatic
    fun derive(P: String, S: String, c: Int, dkLen: Int): ByteArray {
        require(dkLen > 0) { "dkLen must be positive" }
        require(dkLen.toLong() <= (0xFFFFFFFFL) * H_LEN) { "derived key too long" }

        val password = P.encodeToByteArray()
        val salt = S.encodeToByteArray()
        val l = (dkLen + H_LEN - 1) / H_LEN
        val dk = ByteArray(dkLen)
        var offset = 0

        for (i in 1..l) {
            val t = f(password, salt, c, i)
            val copyLen = minOf(H_LEN, dkLen - offset)
            t.copyInto(dk, offset, 0, copyLen)
            offset += copyLen
        }
        return dk
    }

    /** Computes the T_i block as defined in RFC 2898 §5.2. */
    private fun f(password: ByteArray, salt: ByteArray, c: Int, i: Int): ByteArray {
        // INT(i) — 4-byte big-endian representation of i
        val iBytes = byteArrayOf(
            (i shr 24).toByte(),
            (i shr 16).toByte(),
            (i shr 8).toByte(),
            i.toByte(),
        )

        // U_1 = HMAC(Password, Salt || INT(i))
        val hmac1 = HmacSHA512(password)
        hmac1.update(salt)
        hmac1.update(iBytes)
        var u = hmac1.doFinal()
        val xor = u.copyOf()

        // U_j = HMAC(Password, U_{j-1}), for j = 2..c
        for (j in 2..c) {
            val hmacJ = HmacSHA512(password)
            hmacJ.update(u)
            u = hmacJ.doFinal()
            for (k in xor.indices) xor[k] = (xor[k].toInt() xor u[k].toInt()).toByte()
        }

        return xor
    }
}
