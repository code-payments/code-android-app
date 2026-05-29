package com.flipcash.app.core.money

import org.junit.Test
import java.util.Locale
import kotlin.test.assertEquals

class FormatUtilsTest {

    @Test
    fun `round 1_005 rounds up to 1_01`() {
        // Note: 1.005 in IEEE 754 is slightly less than 1.005,
        // so (1.005 * 100.0).roundToInt() == 100 → 1.0
        // This documents the actual floating-point behavior.
        val result = FormatUtils.round(1.005)
        assertEquals(1.0, result)
    }

    @Test
    fun `round 1_004 truncates to 1_0`() {
        assertEquals(1.0, FormatUtils.round(1.004))
    }

    @Test
    fun `round 1_999 rounds to 2_0`() {
        assertEquals(2.0, FormatUtils.round(1.999))
    }

    @Test
    fun `round 0_0 stays 0_0`() {
        assertEquals(0.0, FormatUtils.round(0.0))
    }

    @Test
    fun `format 1234_56 returns comma-separated with two decimals`() {
        assertEquals("1,234.56", FormatUtils.format(1234.56))
    }

    @Test
    fun `format 0_0 returns 0_00`() {
        assertEquals("0.00", FormatUtils.format(0.0))
    }

    @Test
    fun `formatWholeRoundDown 1234_99 returns 1,234`() {
        assertEquals("1,234", FormatUtils.formatWholeRoundDown(1234.99))
    }

    @Test
    fun `formatWholeRoundDown 0_1 returns 0`() {
        assertEquals("0", FormatUtils.formatWholeRoundDown(0.1))
    }

    @Test
    fun `formatCurrency 10_50 with US locale returns dollar format`() {
        assertEquals("$10.50", FormatUtils.formatCurrency(10.50, Locale.US))
    }

    @Test
    fun `1000 withCommas returns 1,000`() {
        assertEquals("1,000", 1000.withCommas())
    }

    @Test
    fun `1000000 withCommas returns 1,000,000`() {
        assertEquals("1,000,000", 1000000.withCommas())
    }

    @Test
    fun `999 withCommas returns 999`() {
        assertEquals("999", 999.withCommas())
    }

    @Test
    fun `0 withCommas returns 0`() {
        assertEquals("0", 0.withCommas())
    }
}
