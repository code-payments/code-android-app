package com.getcode.opencode.model.financial

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FiatTests {

    @Test
    fun `Default formatting parameters`() {
        // Verify the output with default parameters (formatting=None, showPrefix=true, includeCommas=true) for a standard positive value.
        val fiat = 1234.56.toFiat()
        val formatted = fiat.formatted()
        assertEquals("$1,234.56", formatted)
    }

    @Test
    fun `Formatting None with non zero value`() {
        // Test Formatting.None with a positive value. It should use the currency's default fraction digits.
        val fiat = 1234.56.toFiat()
        val formatted = fiat.formatted(rule = Fiat.FormattingRule.None)
        assertEquals("$1,234.56", formatted)
    }

    @Test
    fun `Formatting None with zero value`() {
        // Test Formatting.None with a value of zero.
        val fiat = 0.0.toFiat()
        val formatted = fiat.formatted(rule = Fiat.FormattingRule.None)
        assertEquals("$0.00", formatted)
    }

    @Test
    fun `Formatting Truncated with whole number`() {
        // Test Formatting.Truncated with a whole number (e.g., 123.0). The output should have no decimal part (0 fraction digits).
        val fiat = 123.0.toFiat()
        val formatted = fiat.formatted(rule = Fiat.FormattingRule.Truncated)
        assertEquals("$123", formatted)
    }

    @Test
    fun `Formatting Truncated with fractional number`() {
        // Test Formatting.Truncated with a number that has a fractional part. The output should retain the default number of decimal digits for the currency.
        val fiat = 123.45.toFiat()
        val formatted = fiat.formatted(rule = Fiat.FormattingRule.Truncated)
        // The fractional part is not truncated if it's non-zero.
        assertEquals("$123.45", formatted)
    }

    @Test
    fun `Formatting Length with specific decimal places`() {
        // Test Formatting.Length with a specific number of decimal places (e.g., 4) to ensure the output is formatted and rounded correctly.
        val fiat = 1234.56789.toFiat()
        val formatted = fiat.formatted(rule = Fiat.FormattingRule.Length(4))
        assertEquals("$1,234.5679", formatted) // Rounded up
    }

    @Test
    fun `Formatting Length with zero decimal places`() {
        // Test Formatting.Length with decimalPlaces set to 0. The output should be rounded to the nearest whole number.
        val fiat = 1234.56.toFiat()
        val formatted = fiat.formatted(rule = Fiat.FormattingRule.Length(0))
        assertEquals("$1,235", formatted) // Rounded up
    }

    @Test
    fun `Formatting Length with more decimal places than value`() {
        // Test Formatting.Length where decimalPlaces is greater than the number of available decimal digits in the value. 
        // The output should be padded with zeros.
        val fiat = 123.45.toFiat()
        val formatted = fiat.formatted(rule = Fiat.FormattingRule.Length(5))
        assertEquals("$123.45000", formatted)
    }

    @Test
    fun `showPrefix disabled`() {
        // Test with showPrefix=false to verify that the currency symbol prefix is not included in the output for a positive value.
        val fiat = 1234.56.toFiat()
        val formatted = fiat.formatted(showPrefix = false)
        assertEquals("1,234.56", formatted)
    }

    @Test
    fun `includeCommas disabled`() {
        // Test with includeCommas=false for a large number to verify that thousands separators are not used.
        val fiat = 1234567.89.toFiat()
        val formatted = fiat.formatted(includeCommas = false)
        assertEquals("$1234567.89", formatted)
    }

    @Test
    fun `includeCommas enabled for large number`() {
        // Test with includeCommas=true for a large number (e.g., > 1,000) to ensure thousands separators are correctly applied.
        val fiat = 1234567.89.toFiat()
        val formatted = fiat.formatted(includeCommas = true)
        assertEquals("$1,234,567.89", formatted)
    }

    @Test
    fun `Combination of all parameters disabled`() {
        // Test with showPrefix=false and includeCommas=false simultaneously, using a specific Formatting.Length, to verify all parameters work together.
        val fiat = 1234567.89123.toFiat()
        val formatted = fiat.formatted(
            rule = Fiat.FormattingRule.Length(4),
            showPrefix = false,
            includeCommas = false
        )
        assertEquals("1234567.8912", formatted)
    }

    @Test
    fun `Large number formatting`() {
        // Test with a very large number to check for correct comma grouping and handling of large values.
        val fiat = 9876543210.98.toFiat()
        val formatted = fiat.formatted()
        assertEquals("$9,876,543,210.98", formatted)
    }

    @Test
    fun `Small number  less than 1  formatting`() {
        // Test with a value between 0 and 1 (e.g., 0.12345) to ensure correct formatting of fractional numbers.
        val fiat = 0.12345.toFiat()
        val formatted = fiat.formatted(rule = Fiat.FormattingRule.Length(5))
        assertEquals("$0.12345", formatted)
    }

    @Test
    fun `Rounding up behavior`() {
        // Test rounding behavior with Formatting.Length. A value like 1.235 with 2 decimal places should round up to 1.24 due to RoundingMode.HALF_UP.
        val fiat = 1.235.toFiat()
        val formatted = fiat.formatted(rule = Fiat.FormattingRule.Length(2))
        assertEquals("$1.24", formatted)
    }

    @Test
    fun `Rounding down behavior`() {
        // Test rounding behavior with Formatting.Length. A value like 1.234 with 2 decimal places should round down to 1.23.
        val fiat = 1.234.toFiat()
        val formatted = fiat.formatted(rule = Fiat.FormattingRule.Length(2))
        assertEquals("$1.23", formatted)
    }

    @Test
    fun `Non USD currency formatting  e g   EUR `() {
        // Test formatting with a different CurrencyCode (e.g., EUR) to ensure the correct currency symbol and default fraction digits are used.
        val fiat = 1234.56.toFiat(CurrencyCode.EUR)
        val formatted = fiat.formatted()
        assertEquals("€1,234.56", formatted)
    }

    @Test
    fun `Currency with zero default fraction digits  e g   JPY `() {
        // Test with a currency like JPY that has 0 default fraction digits to check default behavior and overrides with Formatting.Length.
        val jpyFiat = 12345.0.toFiat(CurrencyCode.JPY)

        // Default formatting for JPY should have 0 fraction digits.
        val defaultFormatted = jpyFiat.formatted()
        assertEquals("¥12,345", defaultFormatted)

        // Override with Formatting.Length to show fraction digits.
        val lengthFormatted = jpyFiat.formatted(rule = Fiat.FormattingRule.Length(2))
        assertEquals("¥12,345.00", lengthFormatted)

        // JPY with fractional value should round to nearest whole number.
        val jpyFiatFractional = 1.534.toFiat(CurrencyCode.JPY)
        val defaultFormattedFractional = jpyFiatFractional.formatted()
        assertEquals("¥2", defaultFormattedFractional)
    }

    @Test
    fun `Maximum Long value for quarks`() {
        // Test with a Fiat object initialized with Long.MAX_VALUE for quarks to check for potential overflows or formatting issues with very large numbers.
        val fiat = Fiat(
            quarks = Long.MAX_VALUE,
            currencyCode = CurrencyCode.USD
        )
        assertEquals("$9,223,372,036,854.78", fiat.formatted())
    }

    // region Arithmetic operators

    @Test
    fun `plus adds two Fiat values with same currency`() {
        val a = Fiat(fiat = 10.50)
        val b = Fiat(fiat = 5.25)
        val result = a + b
        assertEquals(15.75, result.decimalValue, 0.001)
        assertEquals(CurrencyCode.USD, result.currencyCode)
    }

    @Test
    fun `plus with zero returns original`() {
        val a = Fiat(fiat = 10.50)
        val result = a + Fiat.Zero
        // Adding zero should return the original instance
        assertTrue(result === a)
    }

    @Test
    fun `plus throws on currency mismatch`() {
        val usd = Fiat(fiat = 10.0, currencyCode = CurrencyCode.USD)
        val eur = Fiat(fiat = 5.0, currencyCode = CurrencyCode.EUR)
        assertFailsWith<IllegalArgumentException> {
            usd + eur
        }
    }

    @Test
    fun `minus subtracts two Fiat values`() {
        val a = Fiat(fiat = 10.50)
        val b = Fiat(fiat = 3.25)
        val result = a - b
        assertEquals(7.25, result.decimalValue, 0.001)
    }

    @Test
    fun `minus with zero returns original`() {
        val a = Fiat(fiat = 10.50)
        val result = a - Fiat.Zero
        assertTrue(result === a)
    }

    @Test
    fun `minus can produce negative value`() {
        val a = Fiat(fiat = 3.0)
        val b = Fiat(fiat = 10.0)
        val result = a - b
        assertTrue(result.isNegative)
        assertEquals(-7.0, result.decimalValue, 0.001)
    }

    @Test
    fun `minus throws on currency mismatch`() {
        val usd = Fiat(fiat = 10.0, currencyCode = CurrencyCode.USD)
        val cad = Fiat(fiat = 5.0, currencyCode = CurrencyCode.CAD)
        assertFailsWith<IllegalArgumentException> {
            usd - cad
        }
    }

    @Test
    fun `times Int multiplies quarks`() {
        val fiat = Fiat(fiat = 5.0)
        val result = fiat * 3
        assertEquals(15.0, result.decimalValue, 0.001)
        assertEquals(CurrencyCode.USD, result.currencyCode)
    }

    @Test
    fun `times Int by zero produces zero quarks`() {
        val fiat = Fiat(fiat = 5.0)
        val result = fiat * 0
        assertEquals(0L, result.quarks)
    }

    @Test
    fun `times Double multiplies quarks with rounding`() {
        val fiat = Fiat(fiat = 10.0)
        val result = fiat * 1.5
        assertEquals(15.0, result.decimalValue, 0.001)
    }

    @Test
    fun `times Double fractional multiplier`() {
        val fiat = Fiat(fiat = 7.0)
        val result = fiat * 0.1
        assertEquals(0.7, result.decimalValue, 0.001)
    }

    @Test
    fun `div divides quarks`() {
        val fiat = Fiat(fiat = 10.0)
        val result = fiat / 4
        assertEquals(2.5, result.decimalValue, 0.001)
    }

    @Test
    fun `div integer division truncates`() {
        // 10 quarks / 3 = 3 quarks (integer division)
        val fiat = Fiat(quarks = 10L)
        val result = fiat / 3
        assertEquals(3L, result.quarks)
    }

    @Test
    fun `sum of Fiat list accumulates values`() {
        val items = listOf(
            Fiat(fiat = 1.0),
            Fiat(fiat = 2.0),
            Fiat(fiat = 3.0),
        )
        val result = items.sum()
        assertEquals(6.0, result.decimalValue, 0.001)
    }

    @Test
    fun `sum of empty list returns Zero`() {
        val result = emptyList<Fiat>().sum()
        assertEquals(Fiat.Zero, result)
    }

    // endregion

    // region Currency conversion

    @Test
    fun `convertingTo applies rate and changes currency`() {
        val usd = Fiat(fiat = 100.0, currencyCode = CurrencyCode.USD)
        val cadRate = Rate(fx = 1.35, currency = CurrencyCode.CAD)

        val result = usd.convertingTo(cadRate)

        assertEquals(CurrencyCode.CAD, result.currencyCode)
        assertEquals(135.0, result.decimalValue, 0.01)
    }

    @Test
    fun `convertingTo with rate of 1 preserves value`() {
        val usd = Fiat(fiat = 50.0, currencyCode = CurrencyCode.USD)
        val result = usd.convertingTo(Rate.oneToOne)
        assertEquals(50.0, result.decimalValue, 0.001)
    }

    @Test
    fun `convertingTo with fractional rate`() {
        val usd = Fiat(fiat = 100.0, currencyCode = CurrencyCode.USD)
        val gbpRate = Rate(fx = 0.79, currency = CurrencyCode.GBP)

        val result = usd.convertingTo(gbpRate)

        assertEquals(CurrencyCode.GBP, result.currencyCode)
        assertEquals(79.0, result.decimalValue, 0.01)
    }

    @Test
    fun `convertingToUsdIfNeeded returns self when already USD`() {
        val usd = Fiat(fiat = 50.0, currencyCode = CurrencyCode.USD)
        val usdRate = Rate(fx = 1.0, currency = CurrencyCode.USD)

        val result = usd.convertingToUsdIfNeeded(usdRate)

        assertTrue(result === usd)
    }

    @Test
    fun `convertingToUsdIfNeeded converts non-USD using inverse rate`() {
        // If we have CAD 135 and rate is 1.35 CAD/USD,
        // then converting to USD should give us 100
        val cad = Fiat(fiat = 135.0, currencyCode = CurrencyCode.CAD)
        val cadRate = Rate(fx = 1.35, currency = CurrencyCode.CAD)

        val result = cad.convertingToUsdIfNeeded(cadRate)

        assertEquals(CurrencyCode.CAD, result.currencyCode)
        assertEquals(100.0, result.decimalValue, 0.01)
    }

    // endregion

    // region Comparison operators

    @Test
    fun `compareTo returns negative when less`() {
        val small = Fiat(fiat = 5.0)
        val large = Fiat(fiat = 10.0)
        assertTrue(small < large)
    }

    @Test
    fun `compareTo returns positive when greater`() {
        val small = Fiat(fiat = 5.0)
        val large = Fiat(fiat = 10.0)
        assertTrue(large > small)
    }

    @Test
    fun `compareTo returns zero for equal values`() {
        val a = Fiat(fiat = 5.0)
        val b = Fiat(fiat = 5.0)
        assertEquals(0, a.compareTo(b))
    }

    @Test
    fun `less than or equal for equal values`() {
        val a = Fiat(fiat = 5.0)
        val b = Fiat(fiat = 5.0)
        assertTrue(a <= b)
        assertTrue(b <= a)
    }

    @Test
    fun `greater than or equal for equal values`() {
        val a = Fiat(fiat = 5.0)
        val b = Fiat(fiat = 5.0)
        assertTrue(a >= b)
    }

    @Test
    fun `valueLessThan compares formatted double values`() {
        val small = Fiat(fiat = 1.0)
        val large = Fiat(fiat = 2.0)
        assertTrue(small.valueLessThan(large))
        assertFalse(large.valueLessThan(small))
    }

    @Test
    fun `valueGreaterThan compares formatted double values`() {
        val small = Fiat(fiat = 1.0)
        val large = Fiat(fiat = 2.0)
        assertTrue(large.valueGreaterThan(small))
        assertFalse(small.valueGreaterThan(large))
    }

    @Test
    fun `valueGreaterThanOrEqualTo for equal values`() {
        val a = Fiat(fiat = 5.0)
        val b = Fiat(fiat = 5.0)
        assertTrue(a.valueGreaterThanOrEqualTo(b))
    }

    @Test
    fun `valueLessThanOrEqualTo for equal values`() {
        val a = Fiat(fiat = 5.0)
        val b = Fiat(fiat = 5.0)
        assertTrue(a.valueLessThanOrEqualTo(b))
    }

    @Test
    fun `min returns smaller Fiat`() {
        val small = Fiat(fiat = 3.0)
        val large = Fiat(fiat = 7.0)
        assertEquals(small, min(small, large))
        assertEquals(small, min(large, small))
    }

    @Test
    fun `max returns larger Fiat`() {
        val small = Fiat(fiat = 3.0)
        val large = Fiat(fiat = 7.0)
        assertEquals(large, max(small, large))
        assertEquals(large, max(large, small))
    }

    // endregion

    // region Properties and helpers

    @Test
    fun `isPositive returns true for positive quarks`() {
        assertTrue(Fiat(fiat = 1.0).isPositive)
    }

    @Test
    fun `isPositive returns false for zero`() {
        assertFalse(Fiat.Zero.isPositive)
    }

    @Test
    fun `isNegative returns true for negative quarks`() {
        val negative = Fiat(fiat = 0.0) - Fiat(fiat = 5.0)
        assertTrue(negative.isNegative)
    }

    @Test
    fun `isNegative returns false for positive value`() {
        assertFalse(Fiat(fiat = 1.0).isNegative)
    }

    @Test
    fun `valueNonZero returns true for non-zero`() {
        assertTrue(Fiat(fiat = 0.01).valueNonZero())
    }

    @Test
    fun `valueNonZero returns false for zero`() {
        assertFalse(Fiat.Zero.valueNonZero())
    }

    @Test
    fun `orZero returns self when non-null`() {
        val fiat = Fiat(fiat = 5.0)
        assertEquals(fiat, fiat.orZero())
    }

    @Test
    fun `orZero returns Zero when null`() {
        val fiat: Fiat? = null
        assertEquals(Fiat.Zero, fiat.orZero())
    }

    @Test
    fun `toFiat Int extension`() {
        val fiat = 5.toFiat()
        assertEquals(5.0, fiat.decimalValue, 0.001)
        assertEquals(CurrencyCode.USD, fiat.currencyCode)
    }

    @Test
    fun `toFiat Long extension creates from quarks`() {
        // Long maps to the primary constructor (quarks), so 5_000_000L quarks = 5.0
        val fiat = 5_000_000L.toFiat(CurrencyCode.EUR)
        assertEquals(5.0, fiat.decimalValue, 0.001)
        assertEquals(CurrencyCode.EUR, fiat.currencyCode)
    }

    @Test
    fun `toFiat Double extension`() {
        val fiat = 5.5.toFiat()
        assertEquals(5.5, fiat.decimalValue, 0.001)
    }

    @Test
    fun `toFiat throws for unsupported Number type`() {
        assertFailsWith<IllegalArgumentException> {
            5.0f.toFiat()
        }
    }

    @Test
    fun `rounded applies rounding to specified decimal places`() {
        val fiat = Fiat(fiat = 1.2345)
        val rounded = fiat.rounded(2)
        assertEquals(1.23, rounded.decimalValue, 0.001)
    }

    @Test
    fun `rounded half-up rounding`() {
        val fiat = Fiat(fiat = 1.235)
        val rounded = fiat.rounded(2)
        assertEquals(1.24, rounded.decimalValue, 0.001)
    }

    @Test
    fun `string constructor parses amount`() {
        val fiat = Fiat(stringAmount = "123.45")
        assertEquals(123.45, fiat.decimalValue, 0.001)
    }

    @Test
    fun `Fiat constructor from Int`() {
        val fiat = Fiat(fiat = 42)
        assertEquals(42.0, fiat.decimalValue, 0.001)
    }

    @Test
    fun `decimalValue reflects quarks divided by multiplier`() {
        val fiat = Fiat(quarks = 5_000_000L)
        assertEquals(5.0, fiat.decimalValue, 0.001)
    }

    // tokenBalance and estimatedTokenAmountIn tests skipped — MintMetadata.usdf
    // requires Ed25519 JNI which is unavailable in JVM unit tests

    @Test
    fun `estimatedTokenAmountIn for null token uses fallback supply of 0`() {
        val fiat = Fiat(fiat = 10.0)
        // null token should not crash; uses 0 supply and null decimals
        val result = fiat.estimatedTokenAmountIn(null)
        // With null token and 0 supply, should produce some result string
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `formatted with extraPrefix and suffix`() {
        val fiat = Fiat(fiat = 10.0)
        val result = fiat.formatted(extraPrefix = "~", suffix = "USD")
        assertEquals("~ $10.00 USD", result)
    }

    // endregion
}