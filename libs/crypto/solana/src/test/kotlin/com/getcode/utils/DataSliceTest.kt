package com.getcode.utils

import com.getcode.utils.DataSlice.byteToUnsignedInt
import com.getcode.utils.DataSlice.canConsume
import com.getcode.utils.DataSlice.chunk
import com.getcode.utils.DataSlice.consume
import com.getcode.utils.DataSlice.prefix
import com.getcode.utils.DataSlice.suffix
import com.getcode.utils.DataSlice.tail
import com.getcode.utils.DataSlice.toLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DataSliceTest {

    // --- canConsume ---

    @Test
    fun canConsumeTrue() {
        assertTrue(listOf<Byte>(1, 2, 3).canConsume(3))
    }

    @Test
    fun canConsumeFalse() {
        assertFalse(listOf<Byte>(1, 2).canConsume(3))
    }

    @Test
    fun canConsumeZero() {
        assertTrue(emptyList<Byte>().canConsume(0))
    }

    // --- consume ---

    @Test
    fun consumeSplitsCorrectly() {
        val result = listOf<Byte>(1, 2, 3, 4, 5).consume(3)
        assertEquals(listOf<Byte>(1, 2, 3), result.consumed)
        assertEquals(listOf<Byte>(4, 5), result.remaining)
    }

    @Test
    fun consumeZeroReturnsEmptyConsumed() {
        val result = listOf<Byte>(1, 2).consume(0)
        assertEquals(emptyList(), result.consumed)
        assertEquals(listOf<Byte>(1, 2), result.remaining)
    }

    @Test
    fun consumeAll() {
        val result = listOf<Byte>(1, 2, 3).consume(3)
        assertEquals(listOf<Byte>(1, 2, 3), result.consumed)
        assertEquals(emptyList(), result.remaining)
    }

    // --- prefix ---

    @Test
    fun prefixReturnsFirstN() {
        assertEquals(listOf<Byte>(1, 2), listOf<Byte>(1, 2, 3, 4).prefix(2))
    }

    @Test
    fun prefixOutOfBoundsReturnsEmpty() {
        assertEquals(emptyList(), listOf<Byte>(1, 2).prefix(5))
    }

    @Test
    fun prefixNegativeReturnsEmpty() {
        assertEquals(emptyList(), listOf<Byte>(1, 2).prefix(-1))
    }

    // --- suffix ---

    @Test
    fun suffixReturnsFromIndex() {
        assertEquals(listOf<Byte>(3, 4), listOf<Byte>(1, 2, 3, 4).suffix(2))
    }

    @Test
    fun suffixOutOfBoundsReturnsEmpty() {
        assertEquals(emptyList(), listOf<Byte>(1, 2).suffix(5))
    }

    // --- tail ---

    @Test
    fun tailIsSameAsSuffix() {
        val data = listOf<Byte>(1, 2, 3, 4, 5)
        assertEquals(data.suffix(2), data.tail(2))
    }

    // --- chunk ---

    @Test
    fun chunkSplitsIntoEqualParts() {
        val data = listOf<Byte>(1, 2, 3, 4, 5, 6)
        val chunks = data.chunk(2, 3) { it.toList() }
        assertEquals(3, chunks?.size)
        assertEquals(listOf<Byte>(1, 2), chunks?.get(0))
        assertEquals(listOf<Byte>(3, 4), chunks?.get(1))
        assertEquals(listOf<Byte>(5, 6), chunks?.get(2))
    }

    @Test
    fun chunkTooLargeReturnsNull() {
        assertNull(listOf<Byte>(1, 2).chunk(2, 3) { it })
    }

    // --- byteToUnsignedInt ---

    @Test
    fun positiveByteUnchanged() {
        assertEquals(127, (127).toByte().byteToUnsignedInt())
    }

    @Test
    fun negativeByteConverted() {
        assertEquals(128, (-128).toByte().byteToUnsignedInt())
        assertEquals(255, (-1).toByte().byteToUnsignedInt())
    }

    @Test
    fun zeroByteUnchanged() {
        assertEquals(0, (0).toByte().byteToUnsignedInt())
    }

    // --- ByteArray.toLong ---

    @Test
    fun byteArrayToLongSingleByte() {
        assertEquals(42L, byteArrayOf(42).toLong())
    }

    @Test
    fun byteArrayToLongLittleEndian() {
        // 0x0100 in little-endian = 256
        assertEquals(256L, byteArrayOf(0, 1).toLong())
    }

    @Test
    fun byteArrayToLongAllZeros() {
        assertEquals(0L, byteArrayOf(0, 0, 0, 0).toLong())
    }

    @Test
    fun byteArrayToLongMaxUnsignedByte() {
        assertEquals(255L, byteArrayOf(-1).toLong())
    }
}
