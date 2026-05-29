package com.getcode.crypt

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PBKDF2SHA512Test {

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    // Known PBKDF2-HMAC-SHA512 vector:
    // Python: hashlib.pbkdf2_hmac('sha512', b'password', b'salt', 1, 64)
    @Test
    fun knownVectorC1Len64() {
        val result = PBKDF2SHA512.derive("password", "salt", 1, 64)
        assertEquals(64, result.size)
        assertEquals(
            "867f70cf1ade02cff3752599a3a53dc4" +
                "af34c7a669815ae5d513554e1c8cf252" +
                "c02d470a285a0501bad999bfe943c08f" +
                "050235d7d68b1da55e63f73b60a57fce",
            result.toHex(),
        )
    }

    // Python: hashlib.pbkdf2_hmac('sha512', b'password', b'salt', 4096, 64)
    @Test
    fun knownVectorC4096Len64() {
        val result = PBKDF2SHA512.derive("password", "salt", 4096, 64)
        assertEquals(64, result.size)
        assertEquals(
            "d197b1b33db0143e018b12f3d1d1479e" +
                "6cdebdcc97c5c0f87f6902e072f457b5" +
                "143f30602641b3d55cd335988cb36b84" +
                "376060ecd532e039b742a239434af2d5",
            result.toHex(),
        )
    }

    @Test
    fun outputLengthRespected() {
        assertEquals(20, PBKDF2SHA512.derive("pass", "salt", 1, 20).size)
        assertEquals(32, PBKDF2SHA512.derive("pass", "salt", 1, 32).size)
        assertEquals(64, PBKDF2SHA512.derive("pass", "salt", 1, 64).size)
    }

    @Test
    fun deterministicOutput() {
        val a = PBKDF2SHA512.derive("key", "saltsalt", 10, 64)
        val b = PBKDF2SHA512.derive("key", "saltsalt", 10, 64)
        assertContentEquals(a, b)
    }

    @Test
    fun differentIterationsProduceDifferentOutput() {
        val c1 = PBKDF2SHA512.derive("password", "salt", 1, 64)
        val c2 = PBKDF2SHA512.derive("password", "salt", 2, 64)
        assertFalse(c1.contentEquals(c2))
    }

    @Test
    fun emptySalt() {
        val result = PBKDF2SHA512.derive("password", "", 1, 32)
        assertEquals(32, result.size)
    }

    @Test
    fun differentPasswordsProduceDifferentOutput() {
        val a = PBKDF2SHA512.derive("password1", "salt", 1, 32)
        val b = PBKDF2SHA512.derive("password2", "salt", 1, 32)
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun differentSaltsProduceDifferentOutput() {
        val a = PBKDF2SHA512.derive("password", "salt1", 1, 32)
        val b = PBKDF2SHA512.derive("password", "salt2", 1, 32)
        assertFalse(a.contentEquals(b))
    }
}
