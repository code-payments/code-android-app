package com.flipcash.app.core.ui

import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.solana.keys.Mint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * An activity row leads with the viewer's own currency, so a tip denominated in someone else's has
 * to be restated — and the restated figure has to hold still, which is why a USDF payment converts
 * from the USD value it settled at rather than from today's peso.
 */
class ViewerAmountTest {

    private val jeffy = Mint("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v")

    private fun usd(amount: Double) = Fiat(amount, CurrencyCode.USD)
    private fun ars(amount: Double) = Fiat(amount, CurrencyCode.ARS)
    private fun eur(amount: Double) = Fiat(amount, CurrencyCode.EUR)

    private val usdRate = Rate(fx = 1.0, currency = CurrencyCode.USD)
    private val eurRate = Rate(fx = 0.9, currency = CurrencyCode.EUR)

    @Test
    fun `an amount already in the viewer's currency shows one line`() {
        val amount = LocalFiat(usdf = usd(5.0), nativeAmount = usd(5.0))

        val shown = amount.forViewer(usdRate, rates = emptyMap())

        assertEquals(usd(5.0), shown.viewer)
        assertNull(shown.transferred)
    }

    @Test
    fun `a USDF tip in pesos leads with the dollars it settled at`() {
        val amount = LocalFiat(usdf = usd(5.0), nativeAmount = ars(7_500.0))

        val shown = amount.forViewer(usdRate, rates = mapOf(CurrencyCode.ARS to Rate(3_000.0, CurrencyCode.ARS)))

        // The peso has halved against the dollar since — the row still reads $5, not $2.50.
        assertEquals(usd(5.0), shown.viewer)
        assertEquals(ars(7_500.0), shown.transferred)
    }

    @Test
    fun `a USDF tip crosses its settled dollars into whatever currency the viewer reads`() {
        val amount = LocalFiat(usdf = usd(5.0), nativeAmount = ars(7_500.0))

        val shown = amount.forViewer(eurRate, rates = emptyMap())

        assertEquals(eur(4.5), shown.viewer)
        assertEquals(ars(7_500.0), shown.transferred)
    }

    @Test
    fun `a non-USDF tip has no settled dollars, so it crosses through today's rates`() {
        val amount = LocalFiat(usdf = Fiat(1_234_567L), nativeAmount = ars(7_500.0), mint = jeffy)

        val shown = amount.forViewer(usdRate, rates = mapOf(CurrencyCode.ARS to Rate(1_500.0, CurrencyCode.ARS)))

        // `underlyingTokenAmount` holds the mint's own quarks here, not dollars, so it is ignored.
        assertEquals(usd(5.0), shown.viewer)
        assertEquals(ars(7_500.0), shown.transferred)
    }

    @Test
    fun `a non-USDF tip with no rate to cross falls back to what was transferred`() {
        val amount = LocalFiat(usdf = Fiat(1_234_567L), nativeAmount = ars(7_500.0), mint = jeffy)

        val shown = amount.forViewer(usdRate, rates = emptyMap())

        assertEquals(ars(7_500.0), shown.viewer)
        assertNull(shown.transferred)
    }

    @Test
    fun `an amount that signs itself is not signed twice`() {
        // The row supplies the sign because feed amounts are magnitudes; one that isn't would
        // otherwise format as "--$5.00".
        assertEquals("-$5.00", usd(-5.0).formatted(extraPrefix = usd(-5.0).sign("-")))
        assertEquals("-$5.00", usd(5.0).formatted(extraPrefix = usd(5.0).sign("-")))
    }

    @Test
    fun `an unusable rate is treated as no rate at all`() {
        val amount = LocalFiat(usdf = Fiat(1_234_567L), nativeAmount = ars(7_500.0), mint = jeffy)

        val shown = amount.forViewer(usdRate, rates = mapOf(CurrencyCode.ARS to Rate(0.0, CurrencyCode.ARS)))

        assertEquals(ars(7_500.0), shown.viewer)
        assertNull(shown.transferred)
    }
}
