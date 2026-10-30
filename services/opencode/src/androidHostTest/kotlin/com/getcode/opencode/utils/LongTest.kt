package com.getcode.opencode.utils

import kotlin.test.Test
import kotlin.test.assertEquals

class LongTest {

    @Test
    fun flooredZero() {
        assertEquals(0L, 0L.floored)
    }

    @Test
    fun flooredExactSecond() {
        assertEquals(1000L, 1000L.floored)
    }

    @Test
    fun flooredRoundsDown() {
        assertEquals(1000L, 1500L.floored)
    }

    @Test
    fun flooredSubSecond() {
        assertEquals(0L, 500L.floored)
    }

    @Test
    fun flooredExactMultipleSeconds() {
        assertEquals(5000L, 5000L.floored)
    }

    @Test
    fun flooredLargeValue() {
        assertEquals(1710504000000L, 1710504000999L.floored)
    }

    @Test
    fun flooredJustBelowSecond() {
        assertEquals(0L, 999L.floored)
    }
}
