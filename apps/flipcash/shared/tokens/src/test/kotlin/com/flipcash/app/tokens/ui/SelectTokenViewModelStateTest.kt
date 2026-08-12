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
