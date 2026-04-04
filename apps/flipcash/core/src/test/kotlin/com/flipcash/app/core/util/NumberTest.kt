package com.flipcash.app.core.util

import kotlin.test.Test
import kotlin.test.assertEquals

class NumberTest {

    @Test
    fun belowThousand() {
        assertEquals("999", 999.abbreviated())
    }

    @Test
    fun exactlyThousand() {
        assertEquals("1K", 1000.abbreviated())
    }

    @Test
    fun thousandsWithDecimals() {
        assertEquals("1.5K", 1500.abbreviated())
    }

    @Test
    fun thousandsTrimsTrailingZeros() {
        assertEquals("2K", 2000.abbreviated())
    }

    @Test
    fun exactlyMillion() {
        assertEquals("1M", 1_000_000.abbreviated())
    }

    @Test
    fun millionsWithDecimals() {
        assertEquals("2.5M", 2_500_000.abbreviated())
    }

    @Test
    fun exactlyBillion() {
        assertEquals("1B", 1_000_000_000.abbreviated())
    }

    @Test
    fun billionsWithDecimals() {
        assertEquals("1.23B", 1_230_000_000L.abbreviated())
    }

    @Test
    fun exactlyTrillion() {
        assertEquals("1T", 1_000_000_000_000L.abbreviated())
    }

    @Test
    fun zero() {
        assertEquals("0", 0.abbreviated())
    }

    @Test
    fun smallNumber() {
        assertEquals("42", 42.abbreviated())
    }

    @Test
    fun doubleInput() {
        assertEquals("1.5K", 1500.0.abbreviated())
    }
}
