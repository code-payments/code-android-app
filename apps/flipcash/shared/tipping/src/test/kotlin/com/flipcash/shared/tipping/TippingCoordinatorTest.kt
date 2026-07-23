package com.flipcash.shared.tipping

import com.flipcash.app.currency.PreferredCurrencyController
import com.flipcash.app.funding.PurchaseMethodController
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.services.controllers.ProfileController
import com.flipcash.services.models.UserProfile
import com.flipcash.services.user.UserManager
import com.flipcash.shared.payments.TipPaymentDelegate
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.exchange.VerifiedFiatCalculator
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Limits
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.SendLimit
import com.getcode.solana.keys.Mint
import com.getcode.util.resources.ResourceHelper
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
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TippingCoordinatorTest {

    private val profileController = mockk<ProfileController>()
    private val userManager = mockk<UserManager>()
    private val tipPaymentDelegate = mockk<TipPaymentDelegate>(relaxed = true)
    private val userFlags = mockk<UserFlagsCoordinator>(relaxed = true)
    private val preferredCurrency = mockk<PreferredCurrencyController>(relaxed = true)
    private val exchange = mockk<Exchange>(relaxed = true)
    private val tokenCoordinator = mockk<TokenCoordinator>(relaxed = true)
    private val transactionController = mockk<TransactionController>(relaxed = true)
    private val verifiedFiatCalculator = mockk<VerifiedFiatCalculator>(relaxed = true)
    private val resources = mockk<ResourceHelper>(relaxed = true)
    private val purchaseMethodController = mockk<PurchaseMethodController>(relaxed = true)

    // Per-transaction send limit of $100 for USD; no limit for any other currency.
    private val limits = mockk<Limits> {
        every { sendLimitFor(any()) } returns null
        every { sendLimitFor(CurrencyCode.USD) } returns
            SendLimit(nextTransaction = 100.0, maxPerTransaction = 500.0, maxPerDay = 1000.0)
    }

    private fun buildCoordinator() = TippingCoordinator(
        userFlags,
        profileController,
        userManager,
        tipPaymentDelegate,
        exchange,
        tokenCoordinator,
        transactionController,
        verifiedFiatCalculator,
        resources,
        purchaseMethodController,
    )

    private val coordinator = buildCoordinator()

    private fun profile(name: String) = UserProfile(
        displayName = name,
        socialAccounts = emptyList(),
        phoneNumber = null,
        email = null,
    )

    @Test
    fun `resolveProfile delegates to ProfileController`() = runTest {
        val userId = listOf<Byte>(1, 2, 3)
        val expected = profile("Alice")
        coEvery { profileController.getProfileForUser(userId) } returns Result.success(expected)

        val result = coordinator.resolveProfile(userId)

        assertSame(expected, result.getOrNull())
    }

    @Test
    fun `currentUserProfile returns the cached profile when present`() = runTest {
        val cached = profile("Me")
        every { userManager.profile } returns cached

        val result = coordinator.currentUserProfile()

        assertSame(cached, result.getOrNull())
    }

    @Test
    fun `currentUserProfile refreshes from the server when nothing is cached`() = runTest {
        val fetched = profile("Fresh")
        every { userManager.profile } returns null
        coEvery { profileController.updateUserProfile() } returns Result.success(fetched)

        val result = coordinator.currentUserProfile()

        assertSame(fetched, result.getOrNull())
    }

    @Test
    fun `currentUserId reflects UserManager accountId`() {
        val id = listOf<Byte>(9, 9)
        every { userManager.accountId } returns id

        assertEquals(id, coordinator.currentUserId)
    }

    @Test
    fun `exceedsSendLimit is true when the amount is over the per-transaction limit`() {
        every { transactionController.limits } returns MutableStateFlow(limits)

        assertTrue(coordinator.exceedsSendLimit(Fiat(150.0, CurrencyCode.USD)))
    }

    @Test
    fun `exceedsSendLimit is false when the amount is within the limit`() {
        every { transactionController.limits } returns MutableStateFlow(limits)

        assertFalse(coordinator.exceedsSendLimit(Fiat(100.0, CurrencyCode.USD)))
    }

    @Test
    fun `exceedsSendLimit is false when no limit is known for the currency`() {
        every { transactionController.limits } returns MutableStateFlow(limits)

        // No send limit configured for CAD — nothing to enforce, so it's allowed.
        assertFalse(coordinator.exceedsSendLimit(Fiat(999.0, CurrencyCode.CAD)))
    }

    @Test
    fun `maxTipAmount is the smaller of the send limit and the selected token balance`() = runTest {
        every { transactionController.limits } returns MutableStateFlow(limits)
        every { tokenCoordinator.observeSelectedTokenMint() } returns flowOf(Mint(listOf<Byte>(1)))
        every { tokenCoordinator.balanceForToken(any<Mint>()) } returns flowOf(Fiat(40.0, CurrencyCode.USD))
        every { exchange.observePreferredRate() } returns flowOf(Rate.oneToOne)

        val max = buildCoordinator().maxTipAmount.first { it != null }

        // Send limit is $100, balance is $40 — the smaller wins.
        assertEquals(40.0, max!!.toDouble())
    }
}
