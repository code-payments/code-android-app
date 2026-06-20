package com.flipcash.app.tokens

import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.solana.keys.Mint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests the conversion math used by [TokenCoordinator.add] and [TokenCoordinator.subtract]
 * mint-based overloads.
 *
 * The key invariant: `rateFor(currency)` returns the rate FROM USD TO native
 * (e.g. 1 USD = 1.37 CAD → fx=1.37, currency=CAD). [LocalFiat.fromNativeAmount]
 * divides by this fx to recover the USD equivalent.
 *
 * Using `rateToUsd` (which inverts the fx) would produce the wrong USD amount.
 */
class TokenCoordinatorMintOverloadTest {

    private val mint = Mint("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAaaaaaaaaaaa")

    // region fromNativeAmount rate convention

    @Test
    fun `fromNativeAmount with rateFor convention produces correct USD amount`() {
        // 1 USD = 1.37 CAD (rateFor returns this)
        val cadRate = Rate(fx = 1.37, currency = CurrencyCode.CAD)
        val nativeAmount = Fiat(fiat = 5.0, currencyCode = CurrencyCode.CAD)

        val localFiat = LocalFiat.fromNativeAmount(nativeAmount, cadRate, mint)

        // 5 CAD / 1.37 = ~3.65 USD
        val expectedUsd = 5.0 / 1.37
        assertEquals(expectedUsd, localFiat.underlyingTokenAmount.decimalValue, 0.001)
        assertEquals(CurrencyCode.USD, localFiat.underlyingTokenAmount.currencyCode)
        assertEquals(5.0, localFiat.nativeAmount.decimalValue, 0.001)
        assertEquals(CurrencyCode.CAD, localFiat.nativeAmount.currencyCode)
        assertEquals(CurrencyCode.CAD, localFiat.rate.currency)
    }

    @Test
    fun `fromNativeAmount with inverted rate (rateToUsd) produces wrong USD amount`() {
        // rateToUsd returns: Rate(fx = 1/1.37 = 0.73, currency = USD)
        val invertedRate = Rate(fx = 1.0 / 1.37, currency = CurrencyCode.USD)
        val nativeAmount = Fiat(fiat = 5.0, currencyCode = CurrencyCode.CAD)

        val localFiat = LocalFiat.fromNativeAmount(nativeAmount, invertedRate, mint)

        // 5.0 / 0.73 = ~6.85 USD — WRONG (should be ~3.65)
        val wrongUsd = 5.0 / (1.0 / 1.37)
        assertEquals(wrongUsd, localFiat.underlyingTokenAmount.decimalValue, 0.001)
        assertTrue(localFiat.underlyingTokenAmount.decimalValue > 5.0,
            "Inverted rate produces inflated USD (${localFiat.underlyingTokenAmount.decimalValue}), proving rateToUsd is wrong for this use case")
    }

    // endregion

    // region add pipeline end-to-end math

    @Test
    fun `LocalFiat from rateFor flows correctly through add conversion`() {
        // Simulate what add(Token, LocalFiat) does internally:
        // 1. Gets rateToUsd(currency) → Rate(fx = 1/1.37 = 0.73, currency = USD)
        // 2. Calls nativeAmount.convertingTo(rate)
        val cadRate = Rate(fx = 1.37, currency = CurrencyCode.CAD)
        val nativeAmount = Fiat(fiat = 5.0, currencyCode = CurrencyCode.CAD)

        val localFiat = LocalFiat.fromNativeAmount(nativeAmount, cadRate, mint)

        // add() internally does: exchange.rateToUsd(fiat.rate.currency) → Rate(1/1.37, USD)
        val rateToUsd = Rate(fx = 1.0 / cadRate.fx, currency = CurrencyCode.USD)
        val usdAmount = localFiat.nativeAmount.convertingTo(rateToUsd)

        // 5 CAD * (1/1.37) = ~3.65 USD
        val expectedUsd = 5.0 / 1.37
        assertEquals(expectedUsd, usdAmount.decimalValue, 0.001)
        assertEquals(CurrencyCode.USD, usdAmount.currencyCode)
    }

    @Test
    fun `USD amount uses oneToOne rate and passes through unchanged`() {
        val nativeAmount = Fiat(fiat = 10.0, currencyCode = CurrencyCode.USD)

        val localFiat = LocalFiat.fromNativeAmount(nativeAmount, Rate.oneToOne, Mint.usdf)

        assertEquals(10.0, localFiat.underlyingTokenAmount.decimalValue, 0.001)
        assertEquals(10.0, localFiat.nativeAmount.decimalValue, 0.001)

        // add() pipeline: rateToUsd(USD) = Rate(1.0, USD)
        val usdAmount = localFiat.nativeAmount.convertingTo(Rate.oneToOne)
        assertEquals(10.0, usdAmount.decimalValue, 0.001)
    }

    // endregion
}
