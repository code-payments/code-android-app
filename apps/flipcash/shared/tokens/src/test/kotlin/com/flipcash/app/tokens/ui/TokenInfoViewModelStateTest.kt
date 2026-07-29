package com.flipcash.app.tokens.ui

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
}
