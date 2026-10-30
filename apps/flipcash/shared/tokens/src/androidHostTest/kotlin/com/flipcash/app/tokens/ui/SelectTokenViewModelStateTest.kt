package com.flipcash.app.tokens.ui

import com.flipcash.app.core.tokens.TokenPurpose
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.TokenWithLocalizedBalance
import com.getcode.opencode.model.financial.usdf
import com.getcode.solana.keys.Mint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SelectTokenViewModelStateTest {

    private fun tokenWorth(nativeUsd: Double): TokenWithLocalizedBalance {
        val amount = Fiat(nativeUsd, CurrencyCode.USD)
        return TokenWithLocalizedBalance(
            token = Token.usdf,
            balance = LocalFiat(
                underlyingTokenAmount = amount,
                nativeAmount = amount,
                rate = Rate.oneToOne,
                mint = Mint.usdf,
            ),
        )
    }

    private fun namedTokenWorth(name: String, nativeUsd: Double): TokenWithLocalizedBalance =
        tokenWorth(nativeUsd).let { it.copy(token = it.token.copy(name = name)) }

    /**
     * Both cards read $1.00, so nothing the user can see decides which sits above the other — but
     * `Fiat` carries six decimal places, and a launchpad currency's value moves in the digits below
     * the cent on every price refresh. Comparing raw values let that noise reorder the deck: Dollars
     * and Dad Cash traded places while the wallet was on screen. Order by the displayed figure and
     * the name breaks the tie instead, the same way on every refresh.
     */
    @Test
    fun `cards showing the same figure keep a stable order as sub-cent values move`() {
        val dollars = namedTokenWorth("Dollars", 1.0)
        val dadCashLow = namedTokenWorth("Dad Cash", 0.999_6)
        val dadCashHigh = namedTokenWorth("Dad Cash", 1.000_4)

        // Every card here displays $1.00; only the invisible digits differ between the two refreshes.
        val before = listOf(dollars, dadCashLow).sortedWith(SelectTokenViewModel.BalanceOrder)
        val after = listOf(dollars, dadCashHigh).sortedWith(SelectTokenViewModel.BalanceOrder)

        assertEquals(listOf("Dad Cash", "Dollars"), before.map { it.token.name })
        assertEquals(listOf("Dad Cash", "Dollars"), after.map { it.token.name })
    }

    @Test
    fun `a difference the user can see still orders the deck`() {
        val sorted = listOf(namedTokenWorth("Dollars", 1.0), namedTokenWorth("Dad Cash", 1.01))
            .sortedWith(SelectTokenViewModel.BalanceOrder)

        assertEquals(listOf("Dad Cash", "Dollars"), sorted.map { it.token.name })
    }

    /**
     * The wallet total must be round(sum(x)), not sum(round(x)): rounding each token to cents first
     * drifts the total by up to a penny and diverged from iOS (ExchangedFiat.total sums unrounded).
     *
     * Two tokens each worth $0.014 display as $0.01 apiece — so sum(round) is $0.02 — but their true
     * sum is $0.028, which rounds to $0.03.
     */
    @Test
    fun `total sums unrounded values, then rounds for display`() {
        val state = SelectTokenViewModel.State(
            purpose = TokenPurpose.Balance,
            rate = Rate.oneToOne,
            tokens = listOf(tokenWorth(0.014), tokenWorth(0.014)),
        )

        val total = state.totalBalance!!.nativeAmount

        // Unrounded sum is retained; display formatting rounds it to $0.03.
        assertEquals(0.028, total.decimalValue, 1e-3)
        assertEquals(0.03, total.rounded(2).decimalValue, 1e-6)

        // Regression guard: rounding per token first (the old behaviour) would have shown $0.02.
        val sumOfRounded = state.tokens!!.sumOf { it.balance.nativeAmount.rounded(2).decimalValue }
        assertEquals(0.02, sumOfRounded, 1e-6)
        assertNotEquals(sumOfRounded, total.rounded(2).decimalValue, 1e-6)
    }
}
