package com.flipcash.shared.tipping

import com.flipcash.app.analytics.FlipcashAnalyticsService
import com.flipcash.app.core.tipping.TipAmount
import com.flipcash.app.funding.PurchaseMethodController
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.services.controllers.ProfileController
import com.flipcash.services.models.TipAction
import com.flipcash.services.models.TipOrigin
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.user.UserManager
import com.flipcash.shared.payments.TipPaymentDelegate
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.exchange.VerifiedFiat
import com.getcode.opencode.exchange.VerifiedFiatCalculator
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.Token
import com.getcode.solana.keys.Mint
import com.getcode.util.resources.ResourceHelper
import com.getcode.util.vibration.Vibrator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class TippingCoordinatorTest {

    private val profileController = mockk<ProfileController>()
    private val userManager = mockk<UserManager>()
    private val tipPaymentDelegate = mockk<TipPaymentDelegate>(relaxed = true)
    private val exchange = mockk<Exchange>(relaxed = true)
    private val tokenCoordinator = mockk<TokenCoordinator>(relaxed = true)
    private val verifiedFiatCalculator = mockk<VerifiedFiatCalculator>(relaxed = true)
    private val resources = mockk<ResourceHelper>(relaxed = true)
    private val purchaseMethodController = mockk<PurchaseMethodController>(relaxed = true)
    private val analytics = mockk<FlipcashAnalyticsService>(relaxed = true)
    private val vibrator = mockk<Vibrator>(relaxed = true)

    private fun buildCoordinator() = TippingCoordinator(
        profileController,
        userManager,
        tipPaymentDelegate,
        exchange,
        tokenCoordinator,
        verifiedFiatCalculator,
        resources,
        purchaseMethodController,
        analytics,
        vibrator,
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

    /**
     * Reaches into [TippingCoordinator]'s private `_userId`/`_recipient`/`_canTip` state to select
     * a tip-card recipient without going through [TippingCoordinator.resolveTipCard] — that method
     * builds a real scannable [com.getcode.opencode.model.core.OpenCodePayload], which loads the
     * native `kikCodes`/`codeScanner` libraries a plain JVM unit test has no access to
     * (`UnsatisfiedLinkError`). Only [TippingCoordinator.confirmTip]'s wiring is under test here.
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T> TippingCoordinator.privateStateFlow(name: String): MutableStateFlow<T> {
        val field = TippingCoordinator::class.java.getDeclaredField(name)
        field.isAccessible = true
        return field.get(this) as MutableStateFlow<T>
    }

    @Test
    fun `confirmTip sends the tip card as a TIP action`() = runTest {
        // A tip card has no "send cash" path — every payment through it is a genuine tip, so
        // confirmTip must always report TipAction.TIP on the wire, never SEND.
        val userId = listOf<Byte>(4, 5, 6)

        val mint = Mint.usdf
        val token = mockk<Token>(relaxed = true) {
            every { address } returns mint
        }
        val rate = Rate.oneToOne
        val amount = Fiat(5.0, CurrencyCode.USD)
        val owner = mockk<AccountCluster>(relaxed = true) {
            every { withTimelockForToken(token) } returns this
        }
        val verifiedFiat = VerifiedFiat(LocalFiat.Zero, null)

        every { exchange.preferredRate } returns rate
        // selectedToken is a plain `val` built once at construction from these two calls, not a
        // lazily-recomputed property — stubbing them has to happen before TippingCoordinator is
        // built, or the coordinator captures whatever the mock's default (unstubbed) answer was.
        every { tokenCoordinator.observeSelectedTokenMint() } returns flowOf(mint)
        every { tokenCoordinator.tokens } returns flowOf(listOf(token))
        every { tokenCoordinator.balanceForToken(token) } returns amount
        every { userManager.accountCluster } returns owner
        every { tipPaymentDelegate.minimumTipFor(userId, any()) } returns flowOf(null)
        coEvery {
            verifiedFiatCalculator.compute(any(), any(), any(), any(), any())
        } returns Result.success(verifiedFiat)
        coEvery {
            tipPaymentDelegate.send(any(), any(), any(), any(), any(), any())
        } returns Result.success<ChatId?>(null)

        val coordinator = buildCoordinator()

        // Selects the tip card the same way onCardResolved would, minus the native-dependent
        // OpenCodePayload construction resolveTipCard performs.
        coordinator.privateStateFlow<ID?>("_userId").value = userId
        coordinator.privateStateFlow<UserProfile?>("_recipient").value = profile("Bob")
        coordinator.privateStateFlow<Boolean>("_canTip").value = true

        coordinator.selectAmount(TipAmount.Custom(amount))
        coordinator.confirmTip()

        // confirmTip fires into the coordinator's own background scope rather than this test's,
        // so the send lands asynchronously — coVerify's timeout polls for it instead of asserting
        // immediately after the (non-suspend) call above returns.
        coVerify(timeout = 5_000) {
            tipPaymentDelegate.send(
                userId = userId,
                verifiedFiat = verifiedFiat,
                token = token,
                source = owner,
                origin = TipOrigin.TIPCARD,
                action = TipAction.TIP,
            )
        }
    }
}
