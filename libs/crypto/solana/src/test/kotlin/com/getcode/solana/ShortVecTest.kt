package com.getcode.solana

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShortVecTest {

    @Test
    fun encodeLenThenDecodeLenRoundtrip() {
        val values = listOf(0, 1, 127, 128, 255, 256, 16383, 16384)
        for (value in values) {
            val encoded = ShortVec.encodeLen(value)
            val (decoded, remaining) = ShortVec.decodeLen(encoded)
            assertEquals(value, decoded, "Roundtrip failed for value $value")
            assertTrue(remaining.isEmpty(), "No remaining bytes expected for value $value")
        }
    }

    @Test
    fun encodeLenZeroReturnsSingleZeroByte() {
        val encoded = ShortVec.encodeLen(0)
        assertEquals(listOf<Byte>(0), encoded)
    }

    @Test
    fun encodeLenSingleByteNoContinuation() {
        val encoded = ShortVec.encodeLen(127)
        assertEquals(1, encoded.size)
        assertEquals(127.toByte(), encoded[0])
    }

    @Test
    fun encodeLenContinuationBitForValue128() {
        val encoded = ShortVec.encodeLen(128)
        assertEquals(2, encoded.size)
        // First byte should have continuation bit set (0x80)
        assertTrue((encoded[0].toInt() and 0x80) != 0, "Continuation bit should be set")
        // Low 7 bits of first byte = 0, second byte = 1
        assertEquals(0x80.toByte(), encoded[0])
        assertEquals(1.toByte(), encoded[1])
    }

    @Test
    fun decodeLenPreservesRemainingBytes() {
        val encoded = ShortVec.encodeLen(42)
        val extra = listOf<Byte>(0xAA.toByte(), 0xBB.toByte())
        val input = encoded + extra
        val (decoded, remaining) = ShortVec.decodeLen(input)
        assertEquals(42, decoded)
        assertEquals(extra, remaining)
    }

    @Test
    fun encodePrependsLengthThenData() {
        val data = listOf<Byte>(10, 20, 30)
        val encoded = ShortVec.encode(data)
        // First byte(s) should be the length encoding, then the data
        val expectedLength = ShortVec.encodeLen(3)
        assertEquals(expectedLength + data, encoded)
    }

    @Test
    fun encodeListEmptyList() {
        val encoded = ShortVec.encodeList(emptyList())
        // Should just be the encoded length of 0
        assertEquals(ShortVec.encodeLen(0), encoded)
    }

    @Test
    fun encodeListSingleItem() {
        val item = listOf<Byte>(1, 2, 3)
        val encoded = ShortVec.encodeList(listOf(item))
        val expected = ShortVec.encodeLen(1) + item
        assertEquals(expected, encoded)
    }

    @Test
    fun encodeListMultipleItems() {
        val item1 = listOf<Byte>(1, 2)
        val item2 = listOf<Byte>(3, 4, 5)
        val encoded = ShortVec.encodeList(listOf(item1, item2))
        val expected = ShortVec.encodeLen(2) + item1 + item2
        assertEquals(expected, encoded)
    }
}
