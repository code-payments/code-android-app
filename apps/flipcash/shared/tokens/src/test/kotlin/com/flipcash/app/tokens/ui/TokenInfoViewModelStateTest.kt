package com.flipcash.app.tokens.ui

import com.flipcash.app.core.data.Loadable
import com.flipcash.app.tokens.data.MarketCapPoint
import com.flipcash.app.tokens.data.Period
import com.getcode.solana.keys.Mint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers the buy-gate predicate [TokenInfoViewModel.State.hasFundableBalance].
 *
 * Regression: buying a currency was gated on USDF reserves only, so a user
 * holding another Flipcash currency (but no USDF) was wrongly sent to the
 * Add Money flow instead of the swap screen, where that currency can fund
 * the purchase.
 */
class TokenInfoViewModelStateTest {

    private val reduce = TokenInfoViewModel.Companion.updateStateForEvent

    private val target = Mint(ByteArray(32) { 1 }.toList())
    private val other = Mint(ByteArray(32) { 2 }.toList())

    @Test
    fun `hasFundableBalance is false when no balances`() {
        val state = TokenInfoViewModel.State(mint = target)
        assertFalse(state.hasFundableBalance)
    }

    @Test
    fun `hasFundableBalance is false when only the target token has a balance`() {
        val state = TokenInfoViewModel.State(mint = target, fundableBalanceMints = setOf(target))
        assertFalse(state.hasFundableBalance)
    }

    @Test
    fun `hasFundableBalance is true when another currency has a balance`() {
        // The regression: no USDF, but a non-target currency can fund the buy.
        val state = TokenInfoViewModel.State(mint = target, fundableBalanceMints = setOf(other))
        assertTrue(state.hasFundableBalance)
    }

    @Test
    fun `hasFundableBalance is true when USDF reserves are present`() {
        val state = TokenInfoViewModel.State(mint = target, fundableBalanceMints = setOf(Mint.usdf))
        assertTrue(state.hasFundableBalance)
    }

    @Test
    fun `OnFundableBalancesUpdated stores the fundable mints`() {
        val state = reduce(
            TokenInfoViewModel.Event.OnFundableBalancesUpdated(setOf(other))
        )(TokenInfoViewModel.State())
        assertEquals(setOf(other), state.fundableBalanceMints)
    }

    // The card-expand overlay reuses one keyless VM across opens, so each open re-points it with
    // OnMintProvided. The chart period must not carry over from the previous open.
    @Test
    fun `OnMintProvided resets the chart period to all time when asked`() {
        val state = reduce(
            TokenInfoViewModel.Event.OnMintProvided(target, resetChartPeriod = true)
        )(TokenInfoViewModel.State(mint = target, selectedPeriod = Period.Week))
        assertEquals(Period.All, state.selectedPeriod)
    }

    // The pushed screen re-dispatches OnMintProvided when a screen pushed over it pops.
    @Test
    fun `OnMintProvided keeps the chart period by default`() {
        val state = reduce(
            TokenInfoViewModel.Event.OnMintProvided(target)
        )(TokenInfoViewModel.State(mint = target, selectedPeriod = Period.Week))
        assertEquals(Period.Week, state.selectedPeriod)
    }

    @Test
    fun `OnMintProvided keeps the chart data when reopening the same mint`() {
        val data = mapOf(Period.All to Loadable.Loaded(listOf(MarketCapPoint(1L, 2.0))))
        val state = reduce(
            TokenInfoViewModel.Event.OnMintProvided(target)
        )(TokenInfoViewModel.State(mint = target, historicalMarketCapData = data))
        assertEquals(data, state.historicalMarketCapData)
    }

    @Test
    fun `OnMintProvided drops the previous mint's chart data`() {
        val data = mapOf(Period.All to Loadable.Loaded(listOf(MarketCapPoint(1L, 2.0))))
        val state = reduce(
            TokenInfoViewModel.Event.OnMintProvided(target)
        )(TokenInfoViewModel.State(mint = other, historicalMarketCapData = data))
        assertTrue(state.historicalMarketCapData.isEmpty())
    }
}
