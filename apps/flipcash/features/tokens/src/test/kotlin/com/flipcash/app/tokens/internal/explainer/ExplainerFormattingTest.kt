package com.flipcash.app.tokens.internal.explainer

import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Rate
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExplainerFormattingTest {

    @Test
    fun `today vector reads in usd`() {
        assertEquals("$0.02994", ExplainerCurrency(Rate.oneToOne).price(BigDecimal("0.02994")))
        assertEquals("$22.7K", ExplainerCurrency(Rate.oneToOne).reserve(BigDecimal(22_700)))
        assertEquals("0.99%", ExplainerFormat.percent(BigDecimal("0.992")))
    }

    @Test
    fun `display converts to the preferred currency while the curve stays in usd`() {
        val euro = ExplainerCurrency(Rate(fx = 0.5, currency = CurrencyCode.EUR))
        // Reserve labels: 5,000 USD is 2,500 EUR; 10M USD is 5M EUR.
        assertEquals("\u20ac2.5K", euro.reserve(BigDecimal(5_000)))
        assertEquals("\u20ac50K", euro.reserve(BigDecimal(100_000)))
        assertEquals("\u20ac500K", euro.reserve(BigDecimal(1_000_000)))
        assertEquals("\u20ac5M", euro.reserve(BigDecimal(10_000_000)))
        // Per-token price: 0.02994 USD is 0.01497 EUR, kept to four significant figures.
        assertEquals("\u20ac0.01497", euro.price(BigDecimal("0.02994")))
        // Worth: 369.18 USD is 184.59 EUR.
        assertEquals(CurrencyCode.EUR, euro.convert(Fiat(369.18)).currencyCode)
        assertEquals("\u20ac184.59", euro.convert(Fiat(369.18)).formatted())

    }

    @Test
    fun `a zero-decimal currency keeps whole units and rolls over compact suffixes`() {
        val yen = ExplainerCurrency(Rate(fx = 150.0, currency = CurrencyCode.JPY))
        assertEquals("\u00a5750K", yen.reserve(BigDecimal(5_000)))
        assertEquals("\u00a51.5B", yen.reserve(BigDecimal(10_000_000)))
        // 999,960 rounds to 1000K, which must read 1M.
        assertEquals("\u00a51M", ExplainerCurrency(Rate.oneToOne.copy(currency = CurrencyCode.JPY)).reserve(BigDecimal(999_960)))
        assertEquals("\u00a54.491", yen.price(BigDecimal("0.02994")))
    }

    @Test
    fun `a tiny non-zero share reads as less than a hundredth of a percent`() {
        val share = BigDecimal(597).multiply(BigDecimal(100)).divide(BigDecimal(21_000_000), java.math.MathContext(20))
        assertEquals("<0.01%", ExplainerFormat.percent(share))
        assertEquals("<0.01%", ExplainerFormat.percent(BigDecimal("0.0099")))
        assertEquals("0.01%", ExplainerFormat.percent(BigDecimal("0.01")))
        assertEquals("0%", ExplainerFormat.percent(BigDecimal.ZERO))
    }

    @Test
    fun `label layout always draws today and drops labels that come within the gap`() {
        val labels = listOf(
            ExplainerLabelBox(centre = 20f, width = 40f, isToday = false),
            ExplainerLabelBox(centre = 100f, width = 40f, isToday = true),
            ExplainerLabelBox(centre = 130f, width = 40f, isToday = false),
            ExplainerLabelBox(centre = 300f, width = 40f, isToday = false),
            ExplainerLabelBox(centre = 395f, width = 40f, isToday = false),
        )
        val lefts = layoutExplainerLabels(labels, totalWidth = 400f, minGap = 12f)
        assertEquals(0f, lefts[0]) // clamped inside, 0..40 vs today 80..120: gap 40 >= 12
        assertEquals(80f, lefts[1])
        assertNull(lefts[2]) // overlaps Today
        assertEquals(280f, lefts[3])
        assertEquals(360f, lefts[4]) // clamped to the right edge
    }
}
