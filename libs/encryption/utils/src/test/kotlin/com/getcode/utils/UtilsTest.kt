package com.getcode.utils

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class UtilsTest {

    // --- bigIntegerToBytes / bytesToBigInteger roundtrip ---

    @Test
    fun bigIntegerToBytesRoundtrip() {
        val value = BigInteger("123456789")
        val bytes = Utils.bigIntegerToBytes(value, 8)
        assertEquals(8, bytes.size)
        assertEquals(value, Utils.bytesToBigInteger(bytes))
    }

    @Test
    fun bigIntegerToBytesZero() {
        val bytes = Utils.bigIntegerToBytes(BigInteger.ZERO, 4)
        assertEquals(4, bytes.size)
        bytes.forEach { assertEquals(0, it.toInt()) }
    }

    @Test
    fun bigIntegerToBytesNegativeThrows() {
        assertFailsWith<IllegalArgumentException> {
            Utils.bigIntegerToBytes(BigInteger.valueOf(-1), 4)
        }
    }

    @Test
    fun bigIntegerToBytesTooSmallThrows() {
        assertFailsWith<IllegalArgumentException> {
            Utils.bigIntegerToBytes(BigInteger.valueOf(256), 1)
        }
    }

    // --- uint16 LE ---

    @Test
    fun uint16LEWriteRead() {
        val out = ByteArray(2)
        Utils.uint16ToByteArrayLE(0x1234, out, 0)
        assertEquals(0x1234, Utils.readUint16(out, 0))
    }

    @Test
    fun uint16LEZero() {
        val out = ByteArray(2)
        Utils.uint16ToByteArrayLE(0, out, 0)
        assertEquals(0, Utils.readUint16(out, 0))
    }

    @Test
    fun uint16LEMaxValue() {
        val out = ByteArray(2)
        Utils.uint16ToByteArrayLE(0xFFFF, out, 0)
        assertEquals(0xFFFF, Utils.readUint16(out, 0))
    }

    // --- uint32 LE ---

    @Test
    fun uint32LEWriteRead() {
        val out = ByteArray(4)
        Utils.uint32ToByteArrayLE(0x12345678L, out, 0)
        assertEquals(0x12345678L, Utils.readUint32(out, 0))
    }

    @Test
    fun uint32LEZero() {
        val out = ByteArray(4)
        Utils.uint32ToByteArrayLE(0L, out, 0)
        assertEquals(0L, Utils.readUint32(out, 0))
    }

    // --- uint32 BE ---

    @Test
    fun uint32BEWriteRead() {
        val out = ByteArray(4)
        Utils.uint32ToByteArrayBE(0x12345678L, out, 0)
        assertEquals(0x12345678L, Utils.readUint32BE(out, 0))
    }

    // --- int64 LE ---

    @Test
    fun int64LEWriteRead() {
        val out = ByteArray(8)
        Utils.int64ToByteArrayLE(0x123456789ABCDEF0L, out, 0)
        assertEquals(0x123456789ABCDEF0L, Utils.readInt64(out, 0))
    }

    @Test
    fun int64LEZero() {
        val out = ByteArray(8)
        Utils.int64ToByteArrayLE(0L, out, 0)
        assertEquals(0L, Utils.readInt64(out, 0))
    }

    @Test
    fun int64LENegative() {
        val out = ByteArray(8)
        Utils.int64ToByteArrayLE(-1L, out, 0)
        assertEquals(-1L, Utils.readInt64(out, 0))
    }

    // --- Stream variants ---

    @Test
    fun uint16StreamLERoundtrip() {
        val baos = ByteArrayOutputStream()
        Utils.uint16ToByteStreamLE(0xABCD, baos)
        val bytes = baos.toByteArray()
        assertEquals(0xABCD, Utils.readUint16(bytes, 0))
    }

    @Test
    fun uint32StreamLERoundtrip() {
        val baos = ByteArrayOutputStream()
        Utils.uint32ToByteStreamLE(0xDEADBEEFL, baos)
        val bytes = baos.toByteArray()
        assertEquals(0xDEADBEEFL, Utils.readUint32(bytes, 0))
    }

    @Test
    fun int64StreamLERoundtrip() {
        val baos = ByteArrayOutputStream()
        Utils.int64ToByteStreamLE(Long.MAX_VALUE, baos)
        val bytes = baos.toByteArray()
        assertEquals(Long.MAX_VALUE, Utils.readInt64(bytes, 0))
    }

    // --- reverseBytes ---

    @Test
    fun reverseBytesCorrect() {
        val input = byteArrayOf(1, 2, 3, 4)
        assertContentEquals(byteArrayOf(4, 3, 2, 1), Utils.reverseBytes(input))
    }

    @Test
    fun reverseBytesIdempotent() {
        val input = byteArrayOf(1, 2, 3, 4)
        assertContentEquals(input, Utils.reverseBytes(Utils.reverseBytes(input)))
    }

    @Test
    fun reverseBytesEmpty() {
        assertContentEquals(byteArrayOf(), Utils.reverseBytes(byteArrayOf()))
    }

    // --- encodeMPI / decodeMPI roundtrip ---

    @Test
    fun encodeMPIDecodeRoundtripWithoutLength() {
        val value = BigInteger.valueOf(12345)
        val encoded = Utils.encodeMPI(value, false)
        val decoded = Utils.decodeMPI(encoded, false)
        assertEquals(value, decoded)
    }

    @Test
    fun encodeMPIZeroWithLength() {
        val encoded = Utils.encodeMPI(BigInteger.ZERO, true)
        assertEquals(4, encoded.size)
        encoded.forEach { assertEquals(0, it.toInt()) }
    }

    @Test
    fun encodeMPIZeroWithoutLength() {
        val encoded = Utils.encodeMPI(BigInteger.ZERO, false)
        assertEquals(0, encoded.size)
    }

    @Test
    fun decodeMPIZeroWithLength() {
        val encoded = Utils.encodeMPI(BigInteger.ZERO, true)
        assertEquals(BigInteger.ZERO, Utils.decodeMPI(encoded, true))
    }

    @Test
    fun decodeMPIZeroWithoutLength() {
        assertEquals(BigInteger.ZERO, Utils.decodeMPI(ByteArray(0), false))
    }

    // --- encodeCompactBits / decodeCompactBits ---

    @Test
    fun compactBitsRoundtrip() {
        val value = BigInteger.valueOf(0x123456)
        val compact = Utils.encodeCompactBits(value)
        val decoded = Utils.decodeCompactBits(compact)
        assertEquals(value, decoded)
    }

    // --- Mock clock ---

    @Test
    fun mockClockSetAndRead() {
        try {
            Utils.setMockClock(1000L) // 1000 seconds
            assertEquals(1000L, Utils.currentTimeSeconds())
            assertEquals(1_000_000L, Utils.currentTimeMillis())
        } finally {
            Utils.resetMocking()
        }
    }

    @Test
    fun rollMockClock() {
        try {
            Utils.setMockClock(100L)
            Utils.rollMockClock(10)
            assertEquals(110L, Utils.currentTimeSeconds())
        } finally {
            Utils.resetMocking()
        }
    }

    @Test
    fun rollMockClockWithoutSetThrows() {
        Utils.resetMocking()
        assertFailsWith<IllegalStateException> {
            Utils.rollMockClock(1)
        }
    }

    @Test
    fun resetMockingRestoresRealTime() {
        Utils.setMockClock(1L)
        Utils.resetMocking()
        assertTrue(Utils.currentTimeSeconds() > 1L)
    }

    // --- HexEncoder ---

    @Test
    fun hexEncoderEmpty() {
        assertEquals("", Utils.HEX.encode(byteArrayOf()))
    }

    @Test
    fun hexEncoderMixed() {
        assertEquals("0a1bff", Utils.HEX.encode(byteArrayOf(0x0a, 0x1b, 0xff.toByte())))
    }
}
