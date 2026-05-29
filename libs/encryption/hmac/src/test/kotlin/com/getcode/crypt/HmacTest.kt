package com.getcode.crypt

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class HmacTest {

    // --- RFC 4231 Test Vectors for HMAC-SHA-512 ---

    @Test
    fun hmacSha512Rfc4231TestCase1() {
        val key = ByteArray(20) { 0x0b.toByte() }
        val message = "Hi There".toByteArray(Charsets.UTF_8)
        val result = Hmac.hmac("HmacSHA512", key, message)
        assertEquals(64, result.size)
        assertEquals(
            "87aa7cdea5ef619d4ff0b4241a1d6cb0" +
                "2379f4e2ce4ec2787ad0b30545e17cde" +
                "daa833b7d6b8a702038b274eaea3f4e4" +
                "be9d914eeb61f1702e696c203a126854",
            Hmac.bytesToHex(result),
        )
    }

    @Test
    fun hmacSha512Rfc4231TestCase2() {
        val key = "Jefe".toByteArray(Charsets.UTF_8)
        val message = "what do ya want for nothing?".toByteArray(Charsets.UTF_8)
        val result = Hmac.hmac("HmacSHA512", key, message)
        assertEquals(
            "164b7a7bfcf819e2e395fbe73b56e0a3" +
                "87bd64222e831fd610270cd7ea250554" +
                "9758bf75c05a994a6d034f65f8f0e6fd" +
                "caeab1a34d4a6b4b636e070a38bce737",
            Hmac.bytesToHex(result),
        )
    }

    @Test
    fun hmacSha256KnownVector() {
        val key = "key".toByteArray(Charsets.UTF_8)
        val message = "The quick brown fox jumps over the lazy dog".toByteArray(Charsets.UTF_8)
        val result = Hmac.hmac("HmacSHA256", key, message)
        assertEquals(
            "f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8",
            Hmac.bytesToHex(result),
        )
    }

    @Test
    fun deterministicOutput() {
        val key = "k".toByteArray()
        val msg = "m".toByteArray()
        val a = Hmac.hmac("HmacSHA512", key, msg)
        val b = Hmac.hmac("HmacSHA512", key, msg)
        assertContentEquals(a, b)
    }

    // --- bytesToHex ---

    @Test
    fun bytesToHexAllZero() {
        assertEquals("0000000000000000", Hmac.bytesToHex(ByteArray(8) { 0 }))
    }

    @Test
    fun bytesToHexAllFF() {
        assertEquals("ffffffffffffffff", Hmac.bytesToHex(ByteArray(8) { 0xff.toByte() }))
    }

    @Test
    fun bytesToHexEmpty() {
        assertEquals("", Hmac.bytesToHex(ByteArray(0)))
    }

    @Test
    fun bytesToHexMixed() {
        assertEquals("0a1bff", Hmac.bytesToHex(byteArrayOf(0x0a, 0x1b, 0xff.toByte())))
    }
}
