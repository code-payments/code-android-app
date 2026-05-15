package com.getcode.utils

import kotlin.test.Test
import kotlin.test.assertEquals

class ExtensionsTest {

    // --- Int.intToByteArray / Int.bytes ---

    @Test
    fun intToByteArrayZero() {
        val bytes = 0.intToByteArray()
        assertEquals(4, bytes.size)
        bytes.forEach { assertEquals(0, it.toInt()) }
    }

    @Test
    fun intToByteArrayOne() {
        // Little-endian: 1 in first byte
        val bytes = 1.intToByteArray()
        assertEquals(1, bytes[0])
        assertEquals(0, bytes[1])
        assertEquals(0, bytes[2])
        assertEquals(0, bytes[3])
    }

    @Test
    fun intToByteArray256() {
        // 256 = 0x00000100 little-endian → [0, 1, 0, 0]
        val bytes = 256.intToByteArray()
        assertEquals(0, bytes[0])
        assertEquals(1, bytes[1])
        assertEquals(0, bytes[2])
        assertEquals(0, bytes[3])
    }

    @Test
    fun intBytesProperty() {
        assertEquals(42.intToByteArray().toList(), 42.bytes)
    }

    // --- Long.toByteArray / Long.bytes ---

    @Test
    fun longToByteArrayZero() {
        val bytes = 0L.toByteArray()
        assertEquals(8, bytes.size)
        bytes.forEach { assertEquals(0, it.toInt()) }
    }

    @Test
    fun longToByteArrayOne() {
        val bytes = 1L.toByteArray()
        assertEquals(1, bytes[0])
        for (i in 1..7) assertEquals(0, bytes[i])
    }

    @Test
    fun longBytesProperty() {
        assertEquals(123456L.toByteArray().toList(), 123456L.bytes)
    }

    // --- byteArrayToLong roundtrip ---

    @Test
    fun longRoundtrip() {
        val value = 987654321L
        assertEquals(value, value.toByteArray().byteArrayToLong())
    }

    // --- byteArrayToInt ---

    @Test
    fun byteArrayToIntZero() {
        assertEquals(0, byteArrayOf(0, 0, 0, 0).byteArrayToInt())
    }

    @Test
    fun byteArrayToIntOne() {
        assertEquals(1, byteArrayOf(1, 0, 0, 0).byteArrayToInt())
    }

    @Test
    fun byteArrayToInt256() {
        assertEquals(256, byteArrayOf(0, 1, 0, 0).byteArrayToInt())
    }

    // --- Int/Long roundtrip via byteArrayToInt ---

    @Test
    fun intRoundtrip() {
        val value = 12345
        assertEquals(value, value.intToByteArray().byteArrayToInt())
    }

    // --- toByteList ---

    @Test
    fun toByteListConvertsCorrectly() {
        assertEquals(listOf<Byte>(1, 2, -1), listOf(1, 2, 255).toByteList())
    }

    // --- subByteArray ---

    @Test
    fun subByteArrayExtractsRange() {
        val arr = byteArrayOf(10, 20, 30, 40, 50)
        val sub = arr.subByteArray(1, 3)
        assertEquals(3, sub.size)
        assertEquals(20, sub[0])
        assertEquals(30, sub[1])
        assertEquals(40, sub[2])
    }

    // --- Byte shl ---

    @Test
    fun byteShl() {
        val b: Byte = 1
        assertEquals(4.toByte(), b shl 2)
    }

    // --- String.urlEncode / urlDecode ---

    @Test
    fun urlEncodeDecodeRoundtrip() {
        val original = "hello world & foo=bar"
        assertEquals(original, original.urlEncode().urlDecode())
    }

    @Test
    fun urlEncodeSpaces() {
        assertEquals("hello+world", "hello world".urlEncode())
    }

    // --- String.replaceParam ---

    @Test
    fun replaceParamSingle() {
        assertEquals("Hello Alice", "Hello %1\$s".replaceParam("Alice"))
    }

    @Test
    fun replaceParamMultiple() {
        assertEquals("Hello Alice and Bob", "Hello %1\$s and %2\$s".replaceParam("Alice", "Bob"))
    }

    @Test
    fun replaceParamNullUsesEmpty() {
        assertEquals("Hello ", "Hello %1\$s".replaceParam(null))
    }

    @Test
    fun replaceParamWithIndex() {
        assertEquals("Hello World", "Hello %1\$s".replaceParam(0, "World"))
    }

    // --- hexEncodedString ---

    @Test
    fun hexEncodedStringLowercase() {
        assertEquals("0a1bff", listOf<Byte>(0x0a, 0x1b, -1).hexEncodedString())
    }

    @Test
    fun hexEncodedStringUppercase() {
        assertEquals("0A1BFF", listOf<Byte>(0x0a, 0x1b, -1).hexEncodedString(setOf(HexEncodingOptions.Uppercase)))
    }

    @Test
    fun hexEncodedStringEmpty() {
        assertEquals("", emptyList<Byte>().hexEncodedString())
    }

    // --- toUTF8Bytes ---

    @Test
    fun toUTF8Bytes() {
        val bytes = "abc".toUTF8Bytes()
        assertEquals(3, bytes.size)
        assertEquals(97, bytes[0].toInt()) // 'a'
    }

    // --- longToByteArray alias ---

    @Test
    fun longToByteArrayAlias() {
        assertEquals(42L.toByteArray().toList(), 42L.longToByteArray().toList())
    }
}
