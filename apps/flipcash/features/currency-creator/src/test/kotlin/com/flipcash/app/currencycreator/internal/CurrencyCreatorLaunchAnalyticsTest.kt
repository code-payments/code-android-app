package com.flipcash.app.currencycreator.internal

import com.flipcash.analytics.LaunchMethod
import com.flipcash.analytics.State
import com.flipcash.analytics.events.CurrencyLaunchEvents
import com.flipcash.app.analytics.RecordingAnalytics
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.core.dispatchers.TestDispatchers
import com.flipcash.app.currencycreator.CurrencyCreatorCoordinator
import com.flipcash.app.funding.PurchaseMethodController
import com.flipcash.app.tokens.BalancePoller
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.services.controllers.ModerationController
import com.flipcash.services.user.UserManager
import com.getcode.opencode.controllers.CurrencyController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.exchange.VerifiedFiatCalculator
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.base58
import com.getcode.util.resources.ContentReader
import com.getcode.util.resources.FakeResourceHelper
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class CurrencyCreatorLaunchAnalyticsTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val userManager = mockk<UserManager>(relaxed = true)
    private val tokenCoordinator = mockk<TokenCoordinator>(relaxed = true)
    private val balancePoller = mockk<BalancePoller>(relaxed = true)
    private val exchange = mockk<Exchange>(relaxed = true)
    private val analytics = RecordingAnalytics()

    private fun mint(seed: Int) = Mint(List(32) { seed.toByte() })

    private fun TestScope.launchFailing(fundedWith: Mint, launched: Mint, cause: Throwable) {
        every { exchange.preferredRate } returns Rate.oneToOne
        every { tokenCoordinator.balanceForToken(any<Mint>()) } returns flowOf(Fiat.Zero)
        coEvery { balancePoller.awaitBalanceChange(launched, any(), any(), any(), any()) } returns Result.failure(cause)
        // No account cluster ends the LaunchToken pipeline early, which still records the funding mint.
        every { userManager.accountCluster } returns null

        val vm = CurrencyCreatorViewModel(
            dispatchers = TestDispatchers(testScheduler),
            userManager = userManager,
            userFlags = mockk<UserFlagsCoordinator>(relaxed = true),
            moderationController = mockk<ModerationController>(relaxed = true),
            currencyController = mockk<CurrencyController>(relaxed = true),
            verifiedFiatCalculator = mockk<VerifiedFiatCalculator>(relaxed = true),
            tokenCoordinator = tokenCoordinator,
            balancePoller = balancePoller,
            resources = FakeResourceHelper(),
            contentReader = mockk<ContentReader>(relaxed = true),
            purchaseMethodController = mockk<PurchaseMethodController>(relaxed = true),
            currencyCreatorCoordinator = mockk<CurrencyCreatorCoordinator>(relaxed = true),
            exchange = exchange,
            analytics = analytics,
        )
        vm.dispatchEvent(CurrencyCreatorViewModel.Event.LaunchToken(fundedWith))
        vm.dispatchEvent(CurrencyCreatorViewModel.Event.PurchaseSubmitted(mockk(relaxed = true), launched))
        advanceUntilIdle()
    }

    @Test
    fun `a launch funded with USDF tracks Currency Launch With Reserves on failure`() =
        runTest(mainCoroutineRule.dispatcher) {
            val launched = mint(1)
            launchFailing(Mint.usdf, launched, RuntimeException("boom"))

            assertEquals(
                listOf(
                    CurrencyLaunchEvents.launched(
                        LaunchMethod.RESERVES, State.FAILURE, launched.base58(), Mint.usdf.base58(),
                        5.0, CurrencyCode.USD.name, "boom",
                    )
                ),
                analytics.events,
            )
        }

    @Test
    fun `a launch funded with another currency tracks Currency Launch With Currency on failure`() =
        runTest(mainCoroutineRule.dispatcher) {
            val launched = mint(1)
            val funding = mint(2)
            launchFailing(funding, launched, RuntimeException("boom"))

            assertEquals(
                listOf(
                    CurrencyLaunchEvents.launched(
                        LaunchMethod.CURRENCY, State.FAILURE, launched.base58(), funding.base58(),
                        5.0, CurrencyCode.USD.name, "boom",
                    )
                ),
                analytics.events,
            )
        }
}
