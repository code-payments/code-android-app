package com.getcode.opencode.utils

import java.math.RoundingMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DoubleExtensionsTest {

    // --- roundTo ---

    @Test
    fun roundToDefaultHalfUp() {
        assertEquals(1.56, 1.555.roundTo(2))
    }

    @Test
    fun roundToZeroDecimals() {
        assertEquals(2.0, 1.5.roundTo(0))
    }

    @Test
    fun roundToHalfDown() {
        assertEquals(1.55, 1.555.roundTo(2, RoundingMode.HALF_DOWN))
    }

    @Test
    fun roundToFloor() {
        assertEquals(1.55, 1.559.roundTo(2, RoundingMode.FLOOR))
    }

    @Test
    fun roundToCeiling() {
        assertEquals(1.56, 1.551.roundTo(2, RoundingMode.CEILING))
    }

    @Test
    fun roundToThreeDecimals() {
        assertEquals(3.142, 3.14159.roundTo(3))
    }

    @Test
    fun roundToNegativeValue() {
        assertEquals(-1.56, (-1.555).roundTo(2))
    }

    // --- toByteArray ---

    @Test
    fun toByteArrayLength() {
        assertEquals(8, 1.0.toByteArray().size)
    }

    @Test
    fun toByteArrayRoundtrip() {
        val value = 3.14159
        val bytes = value.toByteArray()
        val longBits = java.lang.Double.doubleToLongBits(value)

        // Reconstruct from big-endian byte array
        var reconstructed = 0L
        for (i in 0 until 8) {
            reconstructed = (reconstructed shl 8) or (bytes[i].toLong() and 0xFF)
        }

        assertEquals(longBits, reconstructed)
    }

    @Test
    fun toByteArrayZero() {
        val bytes = 0.0.toByteArray()
        assertTrue(bytes.all { it == 0.toByte() })
    }
}
