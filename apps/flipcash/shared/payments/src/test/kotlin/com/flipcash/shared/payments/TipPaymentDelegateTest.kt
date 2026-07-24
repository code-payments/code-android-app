package com.flipcash.shared.payments

import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.services.controllers.ResolverController
import com.flipcash.shared.chat.ChatCoordinator
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Limits
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.SendLimit
import com.getcode.solana.keys.Mint
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TipPaymentDelegateTest {

    private val resolverController = mockk<ResolverController>(relaxed = true)
    private val transactionController = mockk<TransactionController>(relaxed = true)
    private val tokenCoordinator = mockk<TokenCoordinator>(relaxed = true)
    private val chatCoordinator = mockk<ChatCoordinator>(relaxed = true)
    private val exchange = mockk<Exchange>(relaxed = true)
    private val userFlags = mockk<UserFlagsCoordinator>(relaxed = true)

    // Per-transaction send limit of $100 for USD; no limit for any other currency.
    private val limits = mockk<Limits> {
        every { sendLimitFor(any()) } returns null
        every { sendLimitFor(CurrencyCode.USD) } returns
            SendLimit(nextTransaction = 100.0, maxPerTransaction = 500.0, maxPerDay = 1000.0)
    }

    private fun buildDelegate() = TipPaymentDelegate(
        resolverController,
        transactionController,
        tokenCoordinator,
        chatCoordinator,
        exchange,
        userFlags,
    )

    @Test
    fun `exceedsSendLimit is true when the amount is over the per-transaction limit`() {
        every { transactionController.limits } returns MutableStateFlow(limits)

        assertTrue(buildDelegate().exceedsSendLimit(Fiat(150.0, CurrencyCode.USD)))
    }

    @Test
    fun `exceedsSendLimit is false when the amount is within the limit`() {
        every { transactionController.limits } returns MutableStateFlow(limits)

        assertFalse(buildDelegate().exceedsSendLimit(Fiat(100.0, CurrencyCode.USD)))
    }

    @Test
    fun `exceedsSendLimit is false when no limit is known for the currency`() {
        every { transactionController.limits } returns MutableStateFlow(limits)

        // No send limit configured for CAD — nothing to enforce, so it's allowed.
        assertFalse(buildDelegate().exceedsSendLimit(Fiat(999.0, CurrencyCode.CAD)))
    }

    @Test
    fun `maxTipAmount is the smaller of the send limit and the selected token balance`() = runTest {
        every { transactionController.limits } returns MutableStateFlow(limits)
        every { tokenCoordinator.observeSelectedTokenMint() } returns flowOf(Mint(listOf<Byte>(1)))
        every { tokenCoordinator.balanceForToken(any<Mint>()) } returns flowOf(Fiat(40.0, CurrencyCode.USD))
        every { exchange.observePreferredRate() } returns flowOf(Rate.oneToOne)

        val max = buildDelegate().maxTipAmount.first { it != null }

        // Send limit is $100, balance is $40 — the smaller wins.
        assertEquals(40.0, max!!.toDouble())
    }
}
