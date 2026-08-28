package com.flipcash.shared.payments

import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.app.userflags.FieldOverride
import com.flipcash.app.userflags.ResolvedFlag
import com.flipcash.app.userflags.ResolvedUserFlags
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.services.controllers.ResolverController
import com.flipcash.services.models.TipPresets
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.UserProfile
import com.flipcash.shared.chat.ChatCoordinator
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Limits
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.SendLimit
import com.getcode.solana.keys.Mint
import io.mockk.coEvery
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

    private fun recipient(fee: Fiat?) = UserProfile(
        displayName = "Alice",
        socialAccounts = emptyList(),
        phoneNumber = null,
        email = null,
        minDmChatInitFee = fee,
    )

    /** Presets whose USD region carries a $1 minimum — the floor when a recipient sets none. */
    private fun usdPresets() = MutableStateFlow(
        resolvedFlagsWith(
            listOf(TipPresets(region = "usd", minimum = 1.0, low = 2.0, medium = 5.0, high = 10.0)),
        ),
    )

    private fun resolvedFlagsWith(presets: List<TipPresets>): ResolvedUserFlags =
        mockk {
            every { tipPresets } returns ResolvedFlag(presets, FieldOverride.None)
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

    @Test
    fun `minTipAmount is the dedicated minimum preset, not the lowest tier`() = runTest {
        every { userFlags.resolvedFlags } returns MutableStateFlow(
            resolvedFlagsWith(
                listOf(TipPresets(region = "usd", minimum = 1.0, low = 2.0, medium = 5.0, high = 10.0)),
            ),
        )
        every { exchange.observePreferredRate() } returns flowOf(Rate.oneToOne)

        val min = buildDelegate().minTipAmount.first { it != null }

        // The dedicated `minimum` (1.0) — not the lowest tier `low` (2.0).
        assertEquals(1.0, min!!.toDouble())
        assertEquals(CurrencyCode.USD, min.currencyCode)
    }

    @Test
    fun `tipPresets are the low medium high tiers, excluding the minimum`() = runTest {
        every { userFlags.resolvedFlags } returns MutableStateFlow(
            resolvedFlagsWith(
                listOf(TipPresets(region = "usd", minimum = 1.0, low = 2.0, medium = 5.0, high = 10.0)),
            ),
        )
        every { exchange.observePreferredRate() } returns flowOf(Rate.oneToOne)

        val presets = buildDelegate().tipPresets.first { it.isNotEmpty() }

        assertEquals(listOf(2.0, 5.0, 10.0), presets.map { it.toDouble() })
    }

    @Test
    fun `falls back to the default minimum when the server provides no presets`() = runTest {
        every { userFlags.resolvedFlags } returns MutableStateFlow(resolvedFlagsWith(emptyList()))
        every { exchange.observePreferredRate() } returns flowOf(Rate.oneToOne)

        val delegate = buildDelegate()
        val min = delegate.minTipAmount.first { it != null }
        val presets = delegate.tipPresets.first { it.isNotEmpty() }

        // Default fallback: $1 minimum, $5/$10/$20 tiers.
        assertEquals(1.0, min!!.toDouble())
        assertEquals(listOf(5.0, 10.0, 20.0), presets.map { it.toDouble() })
    }

    @Test
    fun `minimumToOpenDmWith is the recipient's own fee, not the regional preset`() = runTest {
        every { userFlags.resolvedFlags } returns usdPresets()
        every { exchange.observePreferredRate() } returns flowOf(Rate(fx = 1.0, currency = CurrencyCode.USD))

        val recipient = recipient(Fiat(5.0, CurrencyCode.USD))
        val min = buildDelegate().minimumToOpenDmWith(recipient).first { it != null }

        assertEquals(5.0, min!!.toDouble())
        assertEquals(CurrencyCode.USD, min.currencyCode)
    }

    @Test
    fun `minimumToOpenDmWith converts the recipient's fee into the currency being entered`() = runTest {
        every { userFlags.resolvedFlags } returns usdPresets()
        every { exchange.observePreferredRate() } returns flowOf(Rate(fx = 2.0, currency = CurrencyCode.EUR))
        // The fee was set in CAD at half a dollar to the dollar; the sender enters in EUR at two.
        every { exchange.rateToUsd(CurrencyCode.CAD) } returns Rate(fx = 0.5, currency = CurrencyCode.USD)
        every { exchange.rateFor(CurrencyCode.EUR) } returns Rate(fx = 2.0, currency = CurrencyCode.EUR)

        val recipient = recipient(Fiat(10.0, CurrencyCode.CAD))
        val min = buildDelegate().minimumToOpenDmWith(recipient).first { it != null }

        // CAD 10 → USD 5 → EUR 10.
        assertEquals(10.0, min!!.toDouble())
        assertEquals(CurrencyCode.EUR, min.currencyCode)
    }

    @Test
    fun `minimumToOpenDmWith falls back to the preset when the recipient charges nothing`() = runTest {
        every { userFlags.resolvedFlags } returns usdPresets()
        every { exchange.observePreferredRate() } returns flowOf(Rate(fx = 1.0, currency = CurrencyCode.USD))

        val delegate = buildDelegate()

        assertEquals(1.0, delegate.minimumToOpenDmWith(recipient(fee = null)).first { it != null }!!.toDouble())
        assertEquals(1.0, delegate.minimumToOpenDmWith(null).first { it != null }!!.toDouble())
    }

    @Test
    fun `minimumToOpenDmWith falls back to the preset rather than state a floor in another currency`() = runTest {
        every { userFlags.resolvedFlags } returns usdPresets()
        every { exchange.observePreferredRate() } returns flowOf(Rate(fx = 1.0, currency = CurrencyCode.USD))
        // No rate for the currency the recipient set their fee in.
        every { exchange.rateToUsd(CurrencyCode.CAD) } returns null

        val recipient = recipient(Fiat(10.0, CurrencyCode.CAD))
        val min = buildDelegate().minimumToOpenDmWith(recipient).first { it != null }

        assertEquals(1.0, min!!.toDouble())
        assertEquals(CurrencyCode.USD, min.currencyCode)
    }

    @Test
    fun `minimumTipFor charges the recipient's fee while no chat with them exists`() = runTest {
        every { userFlags.resolvedFlags } returns usdPresets()
        every { exchange.observePreferredRate() } returns flowOf(Rate(fx = 1.0, currency = CurrencyCode.USD))
        val userId = listOf<Byte>(1, 2, 3)
        coEvery { chatCoordinator.getChatId(userId) } returns
            Result.failure(IllegalStateException("no DM yet"))

        val min = buildDelegate()
            .minimumTipFor(userId, recipient(Fiat(5.0, CurrencyCode.USD)))
            .first { it != null }

        assertEquals(5.0, min!!.toDouble())
    }

    @Test
    fun `minimumTipFor drops to the system minimum once a chat exists`() = runTest {
        every { userFlags.resolvedFlags } returns usdPresets()
        every { exchange.observePreferredRate() } returns flowOf(Rate(fx = 1.0, currency = CurrencyCode.USD))
        val userId = listOf<Byte>(1, 2, 3)
        coEvery { chatCoordinator.getChatId(userId) } returns Result.success(ChatId(byteArrayOf(9)))

        // The fee opened that chat; it isn't charged again.
        val min = buildDelegate()
            .minimumTipFor(userId, recipient(Fiat(5.0, CurrencyCode.USD)))
            .first { it != null }

        assertEquals(1.0, min!!.toDouble())
    }
}
