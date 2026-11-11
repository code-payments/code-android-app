package com.getcode.opencode.internal.solana

import kotlin.test.Test
import kotlin.test.assertEquals

class ShortVecTest {

    // --- encodeLen / decodeLen roundtrip ---

    @Test
    fun encodeLenZero() {
        val encoded = ShortVec.encodeLen(0)
        assertEquals(listOf<Byte>(0), encoded)
    }

    @Test
    fun encodeLenSmallValue() {
        val encoded = ShortVec.encodeLen(5)
        assertEquals(listOf<Byte>(5), encoded)
    }

    @Test
    fun encodeLenMaxSingleByte() {
        val encoded = ShortVec.encodeLen(0x7f)
        assertEquals(1, encoded.size)
        assertEquals(0x7f.toByte(), encoded[0])
    }

    @Test
    fun encodeLenTwoBytes() {
        val encoded = ShortVec.encodeLen(0x80)
        assertEquals(2, encoded.size)
    }

    @Test
    fun encodeLenLargerValue() {
        val encoded = ShortVec.encodeLen(0x3FFF)
        assertEquals(2, encoded.size)
    }

    @Test
    fun encodeLenThreeBytes() {
        val encoded = ShortVec.encodeLen(0x4000)
        assertEquals(3, encoded.size)
    }

    @Test
    fun roundtripZero() {
        val encoded = ShortVec.encodeLen(0)
        val (decoded, _) = ShortVec.decodeLen(encoded)
        assertEquals(0, decoded)
    }

    @Test
    fun roundtripSmall() {
        val encoded = ShortVec.encodeLen(42)
        val (decoded, _) = ShortVec.decodeLen(encoded)
        assertEquals(42, decoded)
    }

    @Test
    fun roundtripBoundary127() {
        val encoded = ShortVec.encodeLen(127)
        val (decoded, _) = ShortVec.decodeLen(encoded)
        assertEquals(127, decoded)
    }

    @Test
    fun roundtripBoundary128() {
        val encoded = ShortVec.encodeLen(128)
        val (decoded, _) = ShortVec.decodeLen(encoded)
        assertEquals(128, decoded)
    }

    @Test
    fun roundtripBoundary16383() {
        val encoded = ShortVec.encodeLen(16383)
        val (decoded, _) = ShortVec.decodeLen(encoded)
        assertEquals(16383, decoded)
    }

    @Test
    fun roundtripBoundary16384() {
        val encoded = ShortVec.encodeLen(16384)
        val (decoded, _) = ShortVec.decodeLen(encoded)
        assertEquals(16384, decoded)
    }

    @Test
    fun decodeLenReturnsTailBytes() {
        val encoded = ShortVec.encodeLen(5)
        val extra = listOf<Byte>(0xA, 0xB, 0xC)
        val input = encoded + extra
        val (value, remaining) = ShortVec.decodeLen(input)
        assertEquals(5, value)
        assertEquals(extra, remaining)
    }

    // --- encode / encodeList ---

    @Test
    fun encodePrependsLength() {
        val data = listOf<Byte>(1, 2, 3)
        val encoded = ShortVec.encode(data)
        assertEquals(listOf<Byte>(3, 1, 2, 3), encoded)
    }

    @Test
    fun encodeEmptyList() {
        val encoded = ShortVec.encode(emptyList())
        assertEquals(listOf<Byte>(0), encoded)
    }

    @Test
    fun encodeListMultipleItems() {
        val items = listOf(
            listOf<Byte>(1, 2),
            listOf<Byte>(3, 4, 5)
        )
        val encoded = ShortVec.encodeList(items)
        // 2 items, then [1,2], then [3,4,5]
        assertEquals(listOf<Byte>(2, 1, 2, 3, 4, 5), encoded)
    }

    @Test
    fun encodeListEmpty() {
        val encoded = ShortVec.encodeList(emptyList())
        assertEquals(listOf<Byte>(0), encoded)
    }

    @Test
    fun encodeListSingleItem() {
        val items = listOf(listOf<Byte>(0xA, 0xB))
        val encoded = ShortVec.encodeList(items)
        assertEquals(listOf<Byte>(1, 0xA, 0xB), encoded)
    }
}
