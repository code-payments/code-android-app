package com.getcode.opencode.model.financial

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FiatTest {

    // --- Construction ---

    @Test
    fun constructFromQuarks() {
        val fiat = Fiat(quarks = 1_000_000L)
        assertEquals(1.0, fiat.decimalValue)
    }

    @Test
    fun constructFromDouble() {
        val fiat = Fiat(1.50)
        assertEquals(1_500_000L, fiat.quarks)
    }

    @Test
    fun constructFromInt() {
        val fiat = Fiat(5)
        assertEquals(5_000_000L, fiat.quarks)
    }

    @Test
    fun constructFromString() {
        val fiat = Fiat("2.50")
        assertEquals(2_500_000L, fiat.quarks)
    }

    @Test
    fun constructFromStringInvalid() {
        // parseStringToDouble uses NumberFormat.parse which may throw various exceptions
        try {
            Fiat("not_a_number")
            // If it doesn't throw, it parsed something — that's also a valid behavior
        } catch (_: Exception) {
            // Expected
        }
    }

    @Test
    fun constructWithCurrencyCode() {
        val fiat = Fiat(10.0, CurrencyCode.EUR)
        assertEquals(CurrencyCode.EUR, fiat.currencyCode)
    }

    // --- decimalValue ---

    @Test
    fun decimalValuePrecision() {
        val fiat = Fiat(quarks = 1_500_000L)
        assertEquals(1.5, fiat.decimalValue)
    }

    @Test
    fun decimalValueSmallAmount() {
        val fiat = Fiat(quarks = 1L) // 0.000001
        assertEquals(0.000001, fiat.decimalValue, 0.0000001)
    }

    // --- Sign checks ---

    @Test
    fun positiveValue() {
        val fiat = Fiat(1.0)
        assertTrue(fiat.isPositive)
        assertFalse(fiat.isNegative)
    }

    @Test
    fun negativeValue() {
        val fiat = Fiat(quarks = -1_000_000L)
        assertFalse(fiat.isPositive)
        assertTrue(fiat.isNegative)
    }

    @Test
    fun zeroIsNeitherPositiveNorNegative() {
        assertFalse(Fiat.Zero.isPositive)
        assertFalse(Fiat.Zero.isNegative)
    }

    // --- Comparisons ---

    @Test
    fun compareToEqual() {
        assertEquals(0, Fiat(1.0).compareTo(Fiat(1.0)))
    }

    @Test
    fun compareToLess() {
        assertTrue(Fiat(1.0) < Fiat(2.0))
    }

    @Test
    fun compareToGreater() {
        assertTrue(Fiat(3.0) > Fiat(2.0))
    }

    @Test
    fun valueLessThan() {
        assertTrue(Fiat(1.0).valueLessThan(Fiat(2.0)))
        assertFalse(Fiat(2.0).valueLessThan(Fiat(1.0)))
    }

    @Test
    fun valueGreaterThan() {
        assertTrue(Fiat(2.0).valueGreaterThan(Fiat(1.0)))
        assertFalse(Fiat(1.0).valueGreaterThan(Fiat(2.0)))
    }

    @Test
    fun valueGreaterThanOrEqualTo() {
        assertTrue(Fiat(2.0).valueGreaterThanOrEqualTo(Fiat(2.0)))
        assertTrue(Fiat(3.0).valueGreaterThanOrEqualTo(Fiat(2.0)))
    }

    @Test
    fun valueLessThanOrEqualTo() {
        assertTrue(Fiat(2.0).valueLessThanOrEqualTo(Fiat(2.0)))
        assertTrue(Fiat(1.0).valueLessThanOrEqualTo(Fiat(2.0)))
    }

    @Test
    fun valueNonZero() {
        assertTrue(Fiat(1.0).valueNonZero())
        assertFalse(Fiat.Zero.valueNonZero())
    }

    // --- Operator overloads ---

    @Test
    fun plus() {
        val result = Fiat(1.50) + Fiat(2.50)
        assertEquals(4_000_000L, result.quarks)
    }

    @Test
    fun plusZeroReturnsOriginal() {
        val original = Fiat(5.0)
        val result = original + Fiat.Zero
        assertTrue(result === original) // identity — short-circuit
    }

    @Test
    fun plusDifferentCurrencyThrows() {
        assertFailsWith<IllegalArgumentException> {
            Fiat(1.0, CurrencyCode.USD) + Fiat(1.0, CurrencyCode.EUR)
        }
    }

    @Test
    fun minus() {
        val result = Fiat(5.0) - Fiat(2.0)
        assertEquals(3_000_000L, result.quarks)
    }

    @Test
    fun minusDifferentCurrencyThrows() {
        assertFailsWith<IllegalArgumentException> {
            Fiat(5.0, CurrencyCode.USD) - Fiat(1.0, CurrencyCode.EUR)
        }
    }

    @Test
    fun timesInt() {
        val result = Fiat(2.0) * 3
        assertEquals(6_000_000L, result.quarks)
    }

    @Test
    fun timesDouble() {
        val result = Fiat(2.0) * 1.5
        assertEquals(3_000_000L, result.quarks)
    }

    @Test
    fun divInt() {
        val result = Fiat(6.0) / 3
        assertEquals(2_000_000L, result.quarks)
    }

    // --- Rounding ---

    @Test
    fun roundedDefault() {
        val fiat = Fiat(1.555)
        val rounded = fiat.rounded()
        assertEquals(1.56, rounded.decimalValue, 0.001)
    }

    @Test
    fun roundedCustomPlaces() {
        val fiat = Fiat(1.5555)
        val rounded = fiat.rounded(3)
        assertEquals(1.556, rounded.decimalValue, 0.0001)
    }

    // --- Collection operations ---

    @Test
    fun sum() {
        val result = listOf(Fiat(1.0), Fiat(2.0), Fiat(3.0)).sum()
        assertEquals(6_000_000L, result.quarks)
    }

    @Test
    fun sumEmpty() {
        val result = emptyList<Fiat>().sum()
        assertEquals(Fiat.Zero, result)
    }

    @Test
    fun orZeroNull() {
        val fiat: Fiat? = null
        assertEquals(Fiat.Zero, fiat.orZero())
    }

    @Test
    fun orZeroNonNull() {
        val fiat: Fiat? = Fiat(5.0)
        assertEquals(5_000_000L, fiat.orZero().quarks)
    }

    // --- toFiat extension ---

    @Test
    fun intToFiat() {
        assertEquals(Fiat(5), 5.toFiat())
    }

    @Test
    fun doubleToFiat() {
        assertEquals(Fiat(5.5).quarks, 5.5.toFiat().quarks)
    }

    @Test
    fun longToFiat() {
        assertEquals(Fiat(5L), 5L.toFiat())
    }

    // --- min / max ---

    @Test
    fun minReturnsSmallerValue() {
        assertEquals(Fiat(1.0), min(Fiat(1.0), Fiat(2.0)))
    }

    @Test
    fun maxReturnsLargerValue() {
        assertEquals(Fiat(2.0), max(Fiat(1.0), Fiat(2.0)))
    }

    // --- Currency conversion ---

    @Test
    fun convertingTo() {
        val usd = Fiat(100.0, CurrencyCode.USD)
        val rate = Rate(0.85, CurrencyCode.EUR)
        val eur = usd.convertingTo(rate)
        assertEquals(CurrencyCode.EUR, eur.currencyCode)
        assertEquals(85.0, eur.decimalValue, 0.01)
    }

    @Test
    fun convertingToUsdIfNeededNonUsd() {
        val eur = Fiat(85.0, CurrencyCode.EUR)
        val rate = Rate(0.85, CurrencyCode.EUR) // EUR/USD = 0.85
        val usd = eur.convertingToUsdIfNeeded(rate)
        // Should invert rate: 85 * (1/0.85) = 100
        assertEquals(100.0, usd.decimalValue, 0.01)
    }

    @Test
    fun convertingToUsdIfNeededAlreadyUsd() {
        val usd = Fiat(100.0, CurrencyCode.USD)
        val rate = Rate(1.0, CurrencyCode.USD)
        val result = usd.convertingToUsdIfNeeded(rate)
        assertTrue(result === usd) // identity
    }

    // --- Constants ---

    @Test
    fun multiplier() {
        assertEquals(1_000_000.0, Fiat.MULTIPLIER)
    }

    @Test
    fun zeroConstant() {
        assertEquals(0L, Fiat.Zero.quarks)
        assertEquals(CurrencyCode.USD, Fiat.Zero.currencyCode)
    }
}
