package com.getcode.solana.keys

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KeyTest {

    @Test
    fun `Key32 zero has 32 zero bytes`() {
        val zero = Key32.zero

        assertEquals(32, zero.bytes.size)
        assertTrue(zero.bytes.all { it == 0.toByte() })
    }

    @Test
    fun `Key32 zero size is 32`() {
        assertEquals(32, Key32.zero.size)
    }

    @Test
    fun `Key32 from base58 string roundtrips correctly`() {
        val base58 = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"
        val key = Key32(base58)

        assertEquals(base58, key.base58())
        assertEquals(32, key.bytes.size)
    }

    @Test
    fun `Key32 compareTo returns zero for equal keys`() {
        val bytes = ByteArray(32) { it.toByte() }.toList()
        val key1 = Key32(bytes)
        val key2 = Key32(bytes)

        assertEquals(0, key1.compareTo(key2))
    }

    @Test
    fun `Key32 compareTo orders by unsigned byte value`() {
        // key with 0xFF in first byte should be greater than key with 0x00
        val smallBytes = ByteArray(32) { 0x00.toByte() }.toList()
        val largeBytes = ByteArray(32).also { it[0] = 0xFF.toByte() }.toList()

        val smallKey = Key32(smallBytes)
        val largeKey = Key32(largeBytes)

        assertTrue(smallKey < largeKey)
        assertTrue(largeKey > smallKey)
    }

    @Test
    fun `Key32 compareTo compares subsequent bytes when first bytes are equal`() {
        val bytes1 = ByteArray(32) { 0x01.toByte() }.toList()
        val bytes2 = ByteArray(32) { 0x01.toByte() }.toMutableList().also {
            it[1] = 0x02.toByte()
        }

        val key1 = Key32(bytes1)
        val key2 = Key32(bytes2)

        assertTrue(key1 < key2)
    }

    @Test
    fun `Key32 equality for same bytes`() {
        val bytes = ByteArray(32) { 42.toByte() }.toList()
        val key1 = Key32(bytes)
        val key2 = Key32(bytes)

        assertEquals(key1, key2)
    }

    @Test
    fun `Key32 zero base58 encodes consistently`() {
        val zero1 = Key32.zero
        val zero2 = Key32(ByteArray(32).toList())

        assertEquals(zero1.base58(), zero2.base58())
    }
}
