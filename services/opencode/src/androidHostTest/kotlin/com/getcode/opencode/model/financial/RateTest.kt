package com.getcode.opencode.model.financial

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RateTest {

    @Test
    fun `oneToOne has fx of 1 and USD currency`() {
        assertEquals(1.0, Rate.oneToOne.fx)
        assertEquals(CurrencyCode.USD, Rate.oneToOne.currency)
    }

    @Test
    fun `ignore uses MIN_VALUE sentinel`() {
        assertEquals(Double.MIN_VALUE, Rate.ignore.fx)
    }

    @Test
    fun `isUsable returns true for normal rate`() {
        assertTrue(Rate(fx = 1.35, currency = CurrencyCode.CAD).isUsable())
    }

    @Test
    fun `isUsable returns true for oneToOne`() {
        assertTrue(Rate.oneToOne.isUsable())
    }

    @Test
    fun `isUsable returns false for ignore sentinel`() {
        assertFalse(Rate.ignore.isUsable())
    }

    @Test
    fun `orOneToOne returns self when non-null`() {
        val rate = Rate(fx = 0.85, currency = CurrencyCode.EUR)
        assertEquals(rate, rate.orOneToOne())
    }

    @Test
    fun `orOneToOne returns oneToOne when null`() {
        val rate: Rate? = null
        assertEquals(Rate.oneToOne, rate.orOneToOne())
    }

    @Test
    fun `two rates with same values are equal`() {
        assertEquals(
            Rate(fx = 1.5, currency = CurrencyCode.CAD),
            Rate(fx = 1.5, currency = CurrencyCode.CAD),
        )
    }

    @Test
    fun `rates with different fx are not equal`() {
        assertNotEquals(
            Rate(fx = 1.5, currency = CurrencyCode.CAD),
            Rate(fx = 2.0, currency = CurrencyCode.CAD),
        )
    }

    @Test
    fun `rates with different currency are not equal`() {
        assertNotEquals(
            Rate(fx = 1.5, currency = CurrencyCode.CAD),
            Rate(fx = 1.5, currency = CurrencyCode.EUR),
        )
    }
}
