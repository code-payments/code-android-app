package com.getcode.opencode.internal.extensions

import com.codeinc.opencode.gen.currency.v1.coreMintFiatExchangeRate
import com.codeinc.opencode.gen.currency.v1.verifiedCoreMintFiatExchangeRate
import com.getcode.opencode.internal.manager.VerifiedState
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.transactions.ExchangeData
import com.getcode.solana.keys.Mint
import com.google.protobuf.Timestamp
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class VerifiedStateExtTest {

    private fun verifiedStateAt(epochSeconds: Long): VerifiedState {
        return VerifiedState(
            rateProto = verifiedCoreMintFiatExchangeRate {
                exchangeRate = coreMintFiatExchangeRate {
                    currencyCode = "usd"
                    exchangeRate = 1.0
                    timestamp = Timestamp.newBuilder().setSeconds(epochSeconds).build()
                }
            },
            reserveProto = null,
        )
    }

    private fun freshVerifiedState(): VerifiedState {
        return verifiedStateAt(Clock.System.now().epochSeconds)
    }

    private fun staleVerifiedState(age: Duration): VerifiedState {
        val staleEpoch = Clock.System.now().epochSeconds - age.inWholeSeconds
        return verifiedStateAt(staleEpoch)
    }

    private val amount = LocalFiat(
        underlyingTokenAmount = Fiat(quarks = 500_000L, currencyCode = CurrencyCode.USD),
        nativeAmount = Fiat(fiat = 0.75, currencyCode = CurrencyCode.CAD),
        rate = Rate(fx = 1.5, currency = CurrencyCode.CAD),
        mint = Mint.usdf,
    )

    @Test
    fun `returns Verified with correct fields when timeout is provided and rate is fresh`() {
        val state = freshVerifiedState()
        val result = state.exchangeDataFor(
            amount = amount,
            mint = Mint.usdf,
            billExchangeDataTimeout = 30.seconds,
        )

        assertIs<ExchangeData.Verified>(result)
        assertEquals(Mint.usdf, result.mint)
        assertEquals(amount.nativeAmount.decimalValue, result.nativeAmount)
        assertEquals(amount.underlyingTokenAmount.quarks, result.quarks)
        assertEquals(state, result.verifiedState)
    }

    @Test
    fun `passes through the verifiedState reference`() {
        val state = freshVerifiedState()
        val result = state.exchangeDataFor(
            amount = amount,
            mint = Mint.usdf,
            billExchangeDataTimeout = 30.seconds,
        )

        assertIs<ExchangeData.Verified>(result)
        assert(result.verifiedState === state)
    }

    @Test
    fun `returns null when timeout is zero`() {
        val state = freshVerifiedState()
        val result = state.exchangeDataFor(
            amount = amount,
            mint = Mint.usdf,
            billExchangeDataTimeout = Duration.ZERO,
        )

        assertNull(result)
    }

    @Test
    fun `returns null when rate exceeds timeout`() {
        val state = staleVerifiedState(age = 60.seconds)
        val result = state.exchangeDataFor(
            amount = amount,
            mint = Mint.usdf,
            billExchangeDataTimeout = 30.seconds,
        )

        assertNull(result)
    }

    @Test
    fun `uses default timeout when null and rate is fresh`() {
        val state = freshVerifiedState()
        val result = state.exchangeDataFor(
            amount = amount,
            mint = Mint.usdf,
            billExchangeDataTimeout = null,
        )

        assertNotNull(result)
        assertIs<ExchangeData.Verified>(result)
    }

    @Test
    fun `returns null when null timeout and rate exceeds default 15 minutes`() {
        val state = staleVerifiedState(age = 16.minutes)
        val result = state.exchangeDataFor(
            amount = amount,
            mint = Mint.usdf,
            billExchangeDataTimeout = null,
        )

        assertNull(result)
    }
}
