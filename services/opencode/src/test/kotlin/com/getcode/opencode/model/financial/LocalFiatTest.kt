package com.getcode.opencode.model.financial

import com.getcode.solana.keys.Mint
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LocalFiatTest {

    // region constructors

    @Test
    fun `Zero is identity for addition`() {
        val zero = LocalFiat.Zero
        assertEquals(0L, zero.underlyingTokenAmount.quarks)
        assertEquals(0L, zero.nativeAmount.quarks)
        assertEquals(Mint.usdf, zero.mint)
    }

    @Test
    fun `constructor from usdf and rate`() {
        val usdf = Fiat(quarks = 1000L, currencyCode = CurrencyCode.USD)
        val rate = Rate(fx = 1.5, currency = CurrencyCode.CAD)

        val localFiat = LocalFiat.fromUsd(usdf, rate)

        assertEquals(1000L, localFiat.underlyingTokenAmount.quarks)
        assertEquals(CurrencyCode.CAD, localFiat.nativeAmount.currencyCode)
        assertEquals(rate, localFiat.rate)
    }

    @Test
    fun `constructor from usdf and nativeAmount derives rate`() {
        val usdf = Fiat(fiat = 10.0, currencyCode = CurrencyCode.USD)
        val native = Fiat(fiat = 15.0, currencyCode = CurrencyCode.CAD)

        val localFiat = LocalFiat(usdf = usdf, nativeAmount = native)

        assertEquals(CurrencyCode.CAD, localFiat.rate.currency)
        assertEquals(1.5, localFiat.rate.fx, 0.001)
    }

    @Test
    fun `fromNativeAmount computes usd from rate`() {
        val native = Fiat(fiat = 15.0, currencyCode = CurrencyCode.CAD)
        val rate = Rate(fx = 1.5, currency = CurrencyCode.CAD)

        val localFiat = LocalFiat.fromNativeAmount(native, rate, Mint.usdf)

        assertEquals(CurrencyCode.USD, localFiat.underlyingTokenAmount.currencyCode)
        // 15 / 1.5 = 10
        assertEquals(10.0, localFiat.underlyingTokenAmount.decimalValue, 0.01)
    }

    // endregion

    // region operators

    @Test
    fun `plus adds underlying and native amounts`() {
        val a = LocalFiat(
            underlyingTokenAmount = Fiat(quarks = 100L),
            nativeAmount = Fiat(fiat = 1.5, currencyCode = CurrencyCode.CAD),
            rate = Rate(fx = 1.5, currency = CurrencyCode.CAD),
            mint = Mint.usdf,
        )
        val b = LocalFiat(
            underlyingTokenAmount = Fiat(quarks = 200L),
            nativeAmount = Fiat(fiat = 3.0, currencyCode = CurrencyCode.CAD),
            rate = Rate(fx = 1.5, currency = CurrencyCode.CAD),
            mint = Mint.usdf,
        )

        val result = a + b

        assertEquals(300L, result.underlyingTokenAmount.quarks)
    }

    @Test
    fun `minus subtracts underlying and native amounts`() {
        val a = LocalFiat(
            underlyingTokenAmount = Fiat(quarks = 300L),
            nativeAmount = Fiat(fiat = 4.5, currencyCode = CurrencyCode.CAD),
            rate = Rate(fx = 1.5, currency = CurrencyCode.CAD),
            mint = Mint.usdf,
        )
        val b = LocalFiat(
            underlyingTokenAmount = Fiat(quarks = 100L),
            nativeAmount = Fiat(fiat = 1.5, currencyCode = CurrencyCode.CAD),
            rate = Rate(fx = 1.5, currency = CurrencyCode.CAD),
            mint = Mint.usdf,
        )

        val result = a - b

        assertEquals(200L, result.underlyingTokenAmount.quarks)
    }

    @Test
    fun `plus throws on currency mismatch`() {
        val a = LocalFiat(
            underlyingTokenAmount = Fiat(quarks = 100L),
            nativeAmount = Fiat(fiat = 1.0, currencyCode = CurrencyCode.CAD),
            rate = Rate(fx = 1.0, currency = CurrencyCode.CAD),
            mint = Mint.usdf,
        )
        val b = LocalFiat(
            underlyingTokenAmount = Fiat(quarks = 100L),
            nativeAmount = Fiat(fiat = 1.0, currencyCode = CurrencyCode.EUR),
            rate = Rate(fx = 1.0, currency = CurrencyCode.EUR),
            mint = Mint.usdf,
        )

        assertFailsWith<IllegalArgumentException> {
            a + b
        }
    }

    @Test
    fun `plus with zero does not throw on currency mismatch`() {
        val a = LocalFiat(
            underlyingTokenAmount = Fiat(quarks = 100L),
            nativeAmount = Fiat(fiat = 1.5, currencyCode = CurrencyCode.CAD),
            rate = Rate(fx = 1.5, currency = CurrencyCode.CAD),
            mint = Mint.usdf,
        )

        val result = a + LocalFiat.Zero
        assertEquals(a, result)
    }

    @Test
    fun `plus zero on left returns right`() {
        val b = LocalFiat(
            underlyingTokenAmount = Fiat(quarks = 100L),
            nativeAmount = Fiat(fiat = 1.5, currencyCode = CurrencyCode.CAD),
            rate = Rate(fx = 1.5, currency = CurrencyCode.CAD),
            mint = Mint.usdf,
        )

        val result = LocalFiat.Zero + b
        assertEquals(b, result)
    }

    @Test
    fun `minus zero does not throw on currency mismatch`() {
        val a = LocalFiat(
            underlyingTokenAmount = Fiat(quarks = 100L),
            nativeAmount = Fiat(fiat = 1.5, currencyCode = CurrencyCode.CAD),
            rate = Rate(fx = 1.5, currency = CurrencyCode.CAD),
            mint = Mint.usdf,
        )

        val result = a - LocalFiat.Zero
        assertEquals(a, result)
    }

    @Test
    fun `minus throws on currency mismatch`() {
        val a = LocalFiat(
            underlyingTokenAmount = Fiat(quarks = 100L),
            nativeAmount = Fiat(fiat = 1.0, currencyCode = CurrencyCode.USD),
            rate = Rate(fx = 1.0, currency = CurrencyCode.USD),
            mint = Mint.usdf,
        )
        val b = LocalFiat(
            underlyingTokenAmount = Fiat(quarks = 100L),
            nativeAmount = Fiat(fiat = 1.0, currencyCode = CurrencyCode.GBP),
            rate = Rate(fx = 1.0, currency = CurrencyCode.GBP),
            mint = Mint.usdf,
        )

        assertFailsWith<IllegalArgumentException> {
            a - b
        }
    }

    // endregion

    // region zero-fee regressions (commits 73d2ea2, eaf014b, 8542fe4)

    @Test
    fun `buy net amount - CAD minus null fee uses Zero without crash`() {
        val cadRate = Rate(fx = 1.35, currency = CurrencyCode.CAD)
        val amount = LocalFiat(
            underlyingTokenAmount = Fiat(fiat = 10.0, currencyCode = CurrencyCode.USD),
            nativeAmount = Fiat(fiat = 13.5, currencyCode = CurrencyCode.CAD),
            rate = cadRate,
            mint = Mint.usdf,
        )
        val feeAmount: LocalFiat? = null

        val netAmount = amount - (feeAmount ?: LocalFiat.Zero)

        assertEquals(amount, netAmount)
    }

    @Test
    fun `swap total transfer - CAD plus null fee uses Zero without crash`() {
        val cadRate = Rate(fx = 1.35, currency = CurrencyCode.CAD)
        val swapAmount = LocalFiat(
            underlyingTokenAmount = Fiat(fiat = 10.0, currencyCode = CurrencyCode.USD),
            nativeAmount = Fiat(fiat = 13.5, currencyCode = CurrencyCode.CAD),
            rate = cadRate,
            mint = Mint.usdf,
        )
        val feeAmount: LocalFiat? = null

        val total = swapAmount + (feeAmount ?: LocalFiat.Zero)

        assertEquals(swapAmount, total)
    }

    @Test
    fun `withdraw fee - Fiat Zero subtracted from non-USD amount does not crash`() {
        val underlyingUsd = Fiat(fiat = 10.0, currencyCode = CurrencyCode.USD)
        val feeAmount = Fiat.Zero

        val transferAmount = underlyingUsd - feeAmount

        assertEquals(underlyingUsd, transferAmount)
    }

    // endregion

    // region sum

    @Test
    fun `sum of empty list returns Zero`() {
        val result = emptyList<LocalFiat>().sum()

        assertEquals(LocalFiat.Zero, result)
    }

    @Test
    fun `sum accumulates multiple items`() {
        val items = listOf(
            LocalFiat(
                underlyingTokenAmount = Fiat(quarks = 100L),
                nativeAmount = Fiat(fiat = 1.0, currencyCode = CurrencyCode.USD),
                rate = Rate.oneToOne,
                mint = Mint.usdf,
            ),
            LocalFiat(
                underlyingTokenAmount = Fiat(quarks = 200L),
                nativeAmount = Fiat(fiat = 2.0, currencyCode = CurrencyCode.USD),
                rate = Rate.oneToOne,
                mint = Mint.usdf,
            ),
        )

        val result = items.sum()

        assertEquals(300L, result.underlyingTokenAmount.quarks)
    }

    // endregion
}
