package com.getcode.crypt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class Sha256HashTest {

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    // --- Known SHA-256 vectors (NIST FIPS 180-4) ---

    @Test
    fun hashEmptyInput() {
        val result = Sha256Hash.hash(ByteArray(0))
        assertEquals(32, result.size)
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            result.toHex(),
        )
    }

    @Test
    fun hashAbc() {
        val result = Sha256Hash.hash("abc".toByteArray(Charsets.UTF_8))
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            result.toHex(),
        )
    }

    @Test
    fun hashWithOffsetAndLength() {
        val input = "XXabcYY".toByteArray(Charsets.UTF_8)
        val full = Sha256Hash.hash("abc".toByteArray(Charsets.UTF_8))
        val partial = Sha256Hash.hash(input, 2, 3)
        assertTrue(full.contentEquals(partial))
    }

    // --- hashTwice ---

    @Test
    fun hashTwiceDiffersFromSingleHash() {
        val input = "abc".toByteArray(Charsets.UTF_8)
        val once = Sha256Hash.hash(input)
        val twice = Sha256Hash.hashTwice(input)
        assertNotEquals(once.toHex(), twice.toHex())
    }

    @Test
    fun hashTwiceEqualsHashOfHash() {
        val input = "abc".toByteArray(Charsets.UTF_8)
        val twice = Sha256Hash.hashTwice(input)
        val manual = Sha256Hash.hash(Sha256Hash.hash(input))
        assertTrue(twice.contentEquals(manual))
    }

    @Test
    fun hashTwiceTwoInputs() {
        val a = "abc".toByteArray(Charsets.UTF_8)
        val b = "def".toByteArray(Charsets.UTF_8)
        val combined = Sha256Hash.hashTwice(a + b)
        val split = Sha256Hash.hashTwice(a, b)
        assertTrue(combined.contentEquals(split))
    }

    // --- wrap / getBytes roundtrip ---

    @Test
    fun wrapAndGetBytesRoundtrip() {
        val raw = ByteArray(32) { it.toByte() }
        val hash = Sha256Hash.wrap(raw)
        assertTrue(raw.contentEquals(hash.bytes))
    }

    @Test
    fun wrapFromHexString() {
        val hex = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        val hash = Sha256Hash.wrap(hex)
        assertEquals(hex, hash.toString())
    }

    @Test
    fun wrapInvalidLengthThrows() {
        assertFailsWith<IllegalArgumentException> {
            Sha256Hash.wrap(ByteArray(31))
        }
    }

    // --- of ---

    @Test
    fun ofWrapsHash() {
        val input = "test".toByteArray(Charsets.UTF_8)
        val hash = Sha256Hash.of(input)
        assertTrue(Sha256Hash.hash(input).contentEquals(hash.bytes))
    }

    // --- twiceOf ---

    @Test
    fun twiceOfWrapsHashTwice() {
        val input = "test".toByteArray(Charsets.UTF_8)
        val hash = Sha256Hash.twiceOf(input)
        assertTrue(Sha256Hash.hashTwice(input).contentEquals(hash.bytes))
    }

    // --- equals / hashCode ---

    @Test
    fun equalsAndHashCode() {
        val raw = ByteArray(32) { 1 }
        val a = Sha256Hash.wrap(raw)
        val b = Sha256Hash.wrap(raw.copyOf())
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun notEqualsDifferentBytes() {
        val a = Sha256Hash.wrap(ByteArray(32) { 0 })
        val b = Sha256Hash.wrap(ByteArray(32) { 1 })
        assertNotEquals(a, b)
    }

    // --- compareTo ---

    @Test
    fun compareToOrdering() {
        val small = Sha256Hash.wrap(ByteArray(32) { 0 })
        val large = Sha256Hash.wrap(ByteArray(32) { 1 })
        assertTrue(small < large)
        assertTrue(large > small)
        assertEquals(0, small.compareTo(small))
    }

    // --- ZERO_HASH ---

    @Test
    fun zeroHashIsAllZeros() {
        assertEquals(32, Sha256Hash.ZERO_HASH.bytes.size)
        Sha256Hash.ZERO_HASH.bytes.forEach { assertEquals(0, it.toInt()) }
    }

    // --- toString ---

    @Test
    fun toStringIsLowercaseHex() {
        val hash = Sha256Hash.of("abc".toByteArray(Charsets.UTF_8))
        val str = hash.toString()
        assertEquals(64, str.length)
        assertTrue(str.all { it in '0'..'9' || it in 'a'..'f' })
    }

    // --- wrapReversed ---

    @Test
    fun wrapReversedReversesBytesBeforeWrapping() {
        val raw = ByteArray(32) { it.toByte() }
        val reversed = Sha256Hash.wrapReversed(raw)
        assertEquals(raw[0], reversed.bytes[31])
        assertEquals(raw[31], reversed.bytes[0])
    }

    // --- toBigInteger ---

    @Test
    fun toBigIntegerPositive() {
        val hash = Sha256Hash.wrap(ByteArray(32) { 0xff.toByte() })
        assertTrue(hash.toBigInteger().signum() > 0)
    }
}
