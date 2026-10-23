package com.flipcash.app.core.util

import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import kotlin.test.Test
import kotlin.test.assertEquals

class FiatTest {

    @Test
    fun `whole amounts below the first scale keep their normal formatting`() {
        assertEquals("$5", Fiat(5.0).abbreviated())
        assertEquals("$999", Fiat(999.0).abbreviated())
    }

    @Test
    fun `a fractional amount below the first scale keeps its cents`() {
        assertEquals("$12.50", Fiat(12.5).abbreviated())
    }

    @Test
    fun `thousands scale to K`() {
        assertEquals("$1K", Fiat(1_000.0).abbreviated())
        assertEquals("$1.5K", Fiat(1_500.0).abbreviated())
    }

    @Test
    fun `the digit cap decides how many decimals a scaled amount keeps`() {
        assertEquals("$1.23K", Fiat(1_234.0).abbreviated())
        assertEquals("$12.3K", Fiat(12_345.0).abbreviated())
        assertEquals("$123K", Fiat(123_456.0).abbreviated())
    }

    @Test
    fun `millions and billions scale to their own suffix`() {
        assertEquals("$1M", Fiat(1_000_000.0).abbreviated())
        assertEquals("$2.5M", Fiat(2_500_000.0).abbreviated())
        assertEquals("$1B", Fiat(1_000_000_000.0).abbreviated())
    }

    @Test
    fun `an amount that rounds into the next scale is printed in that scale`() {
        assertEquals("$1M", Fiat(999_999.0).abbreviated())
    }

    @Test
    fun `a lower cap allows fewer digits`() {
        assertEquals("$1.2K", Fiat(1_234.0).abbreviated(maxDigits = 2))
    }

    @Test
    fun `zero is left alone`() {
        assertEquals("$0", Fiat.Zero.abbreviated())
    }

    @Test
    fun `a high-denomination currency scales instead of overflowing`() {
        // IDR carries no fraction digits and no single-character symbol, so the digits are all
        // that's left to keep in check.
        assertEquals("332K", Fiat(332_000.0, CurrencyCode.IDR).abbreviated())
        assertEquals("500", Fiat(500.0, CurrencyCode.IDR).abbreviated())
    }

    @Test
    fun `a low-value currency's tip tiers stay within the cap`() {
        // The $5 / $10 / $20 tiers in pesos, dong and yen — the amounts that overflow the button
        // when written out in full (7,500 / 15,000 / 30,000 pesos).
        assertEquals("7.5K", Fiat(7_500.0, CurrencyCode.ARS).abbreviated())
        assertEquals("15K", Fiat(15_000.0, CurrencyCode.ARS).abbreviated())
        assertEquals("30K", Fiat(30_000.0, CurrencyCode.ARS).abbreviated())

        assertEquals("\u20ab126K", Fiat(126_000.0, CurrencyCode.VND).abbreviated())
        assertEquals("\u20ab1.32M", Fiat(1_315_000.0, CurrencyCode.VND).abbreviated())

        assertEquals("\u00a5750", Fiat(750.0, CurrencyCode.JPY).abbreviated())
        assertEquals("\u00a53K", Fiat(3_000.0, CurrencyCode.JPY).abbreviated())
    }

    @Test
    fun `an odd peso amount keeps a decimal only while it fits`() {
        assertEquals("25.5K", Fiat(25_500.0, CurrencyCode.ARS).abbreviated())
        assertEquals("123K", Fiat(123_456.0, CurrencyCode.ARS).abbreviated())
    }
}
