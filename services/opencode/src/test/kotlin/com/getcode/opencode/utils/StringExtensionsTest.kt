package com.getcode.opencode.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StringExtensionsTest {

    // --- addLeadingZero ---

    @Test
    fun addLeadingZeroAlreadyLongEnough() {
        assertEquals("abc", "abc".addLeadingZero(2))
    }

    @Test
    fun addLeadingZeroExactLength() {
        assertEquals("abc", "abc".addLeadingZero(3))
    }

    // --- padded ---

    @Test
    fun paddedShorterThanMin() {
        val result = "abc".padded(6)
        assertEquals(6, result.length)
        assertEquals("abc   ", result)
    }

    @Test
    fun paddedAlreadyLongEnough() {
        assertEquals("abcdef", "abcdef".padded(3))
    }

    @Test
    fun paddedExactLength() {
        assertEquals("abc", "abc".padded(3))
    }

    // --- toLocaleAwareDoubleOrNull ---

    @Test
    fun parseValidNumber() {
        val result = "123.456".toLocaleAwareDoubleOrNull()
        assertEquals(123.456, result!!, 0.001)
    }

    @Test
    fun parseIntegerString() {
        val result = "42".toLocaleAwareDoubleOrNull()
        assertEquals(42.0, result!!, 0.001)
    }

    @Test
    fun parseInvalidStringReturnsNull() {
        assertNull("not_a_number".toLocaleAwareDoubleOrNull())
    }

    @Test
    fun parseEmptyStringReturnsNull() {
        assertNull("".toLocaleAwareDoubleOrNull())
    }

    // --- base58 ---

    @Test
    fun base58EncodesString() {
        val result = "hello".base58
        assert(result.isNotEmpty())
    }

    // --- base64EncodedData ---

    @Test
    fun base64EncodedDataNoPaddingNeeded() {
        // "abcd" = 4 bytes, 4 % 4 == 0 → no padding
        val result = "abcd".base64EncodedData()
        assertEquals(4, result.size)
    }

    @Test
    fun base64EncodedDataPaddingNeeded() {
        // "abc" = 3 bytes, 3 % 4 == 3 → needs (4-3)=1 padding "=" chars → 3+4=7
        val result = "abc".base64EncodedData()
        assertEquals(7, result.size)
    }
}
