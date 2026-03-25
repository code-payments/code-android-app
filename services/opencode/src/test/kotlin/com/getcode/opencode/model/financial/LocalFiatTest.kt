package com.getcode.opencode.model.financial

import com.getcode.solana.keys.Mint
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LocalFiatTest {

    @Before
    fun setUp() {
        mockkStatic("com.getcode.utils.LoggingKt")
        every { com.getcode.utils.trace(any(), any(), any(), any(), any()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkStatic("com.getcode.utils.LoggingKt")
    }

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

        val localFiat = LocalFiat(usdf = usdf, rate = rate)

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
