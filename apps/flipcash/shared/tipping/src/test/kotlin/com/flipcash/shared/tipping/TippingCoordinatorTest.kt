package com.flipcash.shared.tipping

import com.flipcash.app.analytics.FlipcashAnalyticsService
import com.flipcash.app.funding.PurchaseMethodController
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.services.controllers.ProfileController
import com.flipcash.services.models.UserProfile
import com.flipcash.services.user.UserManager
import com.flipcash.shared.payments.TipPaymentDelegate
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.exchange.VerifiedFiatCalculator
import com.getcode.util.resources.ResourceHelper
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
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
}
