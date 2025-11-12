package com.getcode.opencode.model.financial

import org.junit.Test
import kotlin.test.assertEquals

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
}