package com.getcode.opencode.internal.solana.extensions

import kotlin.test.Test
import kotlin.test.assertEquals

class UInt16Test {

    @Test
    fun zeroEncodesCorrectly() {
        assertEquals(listOf<Byte>(0, 0), 0.toU16Bytes())
    }

    @Test
    fun oneEncodesCorrectly() {
        assertEquals(listOf<Byte>(1, 0), 1.toU16Bytes())
    }

    @Test
    fun maxSingleByteValue() {
        assertEquals(listOf<Byte>(0xFF.toByte(), 0), 255.toU16Bytes())
    }

    @Test
    fun value256EncodesLittleEndian() {
        assertEquals(listOf<Byte>(0, 1), 256.toU16Bytes())
    }

    @Test
    fun value257() {
        assertEquals(listOf<Byte>(1, 1), 257.toU16Bytes())
    }

    @Test
    fun maxUInt16() {
        assertEquals(listOf<Byte>(0xFF.toByte(), 0xFF.toByte()), 65535.toU16Bytes())
    }

    @Test
    fun arbitraryValue() {
        // 0x1234 = 4660 → little-endian: [0x34, 0x12]
        assertEquals(listOf<Byte>(0x34, 0x12), 0x1234.toU16Bytes())
    }
}
