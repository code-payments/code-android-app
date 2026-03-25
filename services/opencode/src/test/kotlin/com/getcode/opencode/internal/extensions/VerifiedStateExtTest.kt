package com.getcode.opencode.internal.extensions

import com.getcode.opencode.internal.manager.VerifiedState
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.transactions.ExchangeData
import com.getcode.solana.keys.Mint
import io.mockk.mockk
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

class VerifiedStateExtTest {

    private val verifiedState = VerifiedState(
        rateProto = mockk(relaxed = true),
        reserveProto = null,
    )

    private val amount = LocalFiat(
        underlyingTokenAmount = Fiat(quarks = 500_000L, currencyCode = CurrencyCode.USD),
        nativeAmount = Fiat(fiat = 0.75, currencyCode = CurrencyCode.CAD),
        rate = Rate(fx = 1.5, currency = CurrencyCode.CAD),
        mint = Mint.usdf,
    )

    @Test
    fun `returns null when timeout is null`() {
        val result = verifiedState.exchangeDataFor(
            amount = amount,
            mint = Mint.usdf,
            billExchangeDataTimeout = null,
        )

        assertNull(result)
    }

    @Test
    fun `returns Verified with correct fields when timeout is provided`() {
        val result = verifiedState.exchangeDataFor(
            amount = amount,
            mint = Mint.usdf,
            billExchangeDataTimeout = 30.seconds,
        )

        assertIs<ExchangeData.Verified>(result)
        assertEquals(Mint.usdf, result.mint)
        assertEquals(amount.nativeAmount.decimalValue, result.nativeAmount)
        assertEquals(amount.underlyingTokenAmount.quarks, result.quarks)
        assertEquals(verifiedState, result.verifiedState)
    }

    @Test
    fun `passes through the verifiedState reference`() {
        val result = verifiedState.exchangeDataFor(
            amount = amount,
            mint = Mint.usdf,
            billExchangeDataTimeout = 1.seconds,
        )

        assertIs<ExchangeData.Verified>(result)
        assert(result.verifiedState === verifiedState)
    }
}
