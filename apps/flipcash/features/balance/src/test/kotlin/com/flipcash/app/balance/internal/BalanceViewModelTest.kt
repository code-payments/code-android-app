package com.flipcash.app.balance.internal

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.flipcash.app.analytics.StubFlipcashAnalytics
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.core.dispatchers.TestDispatchers
import com.flipcash.app.activityfeed.ActivityFeedCoordinator
import com.flipcash.app.funding.PurchaseMethodController
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.services.internal.model.thirdparty.OnRampProvider
import com.flipcash.services.user.UserManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Regression coverage for the AddMoneyUX flag removal. That flag was `launched = true`
 * (always on), so the "deposit-first" add-money path is now unconditional: tapping
 * "Add Money" must always route through [PurchaseMethodController.presentDepositOptions]
 * and never fall back to the old currency-discovery route.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BalanceViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val userManager: UserManager = mockk(relaxed = true)
    private val userFlags: UserFlagsCoordinator = mockk(relaxed = true)
    private val purchaseMethodController: PurchaseMethodController = mockk(relaxed = true)
    private val chatCoordinator: ChatCoordinator = mockk(relaxed = true)
    private val feedCoordinator: ActivityFeedCoordinator = mockk(relaxed = true)

    private lateinit var dispatchers: TestDispatchers

    private fun createViewModel() = BalanceViewModel(
        userManager = userManager,
        userFlags = userFlags,
        dispatchers = dispatchers,
        purchaseMethodController = purchaseMethodController,
        analytics = StubFlipcashAnalytics(),
        chatCoordinator = chatCoordinator,
        feedCoordinator = feedCoordinator,
    )

    @Test
    fun `PresentDepositOptions always routes through add-money deposit options`() =
        runTest(mainCoroutineRule.dispatcher) {
            dispatchers = TestDispatchers(testScheduler)
            val route = mockk<AppRoute>(relaxed = true)
            coEvery { purchaseMethodController.presentDepositOptions(popToRoot = true) } returns route

            val vm = createViewModel()
            vm.dispatchEvent(BalanceViewModel.Event.PresentDepositOptions)
            advanceUntilIdle()

            // The removed AddMoneyUX flag used to short-circuit to a plain Deposit route;
            // the add-money sheet must now always be presented.
            coVerify(exactly = 1) { purchaseMethodController.presentDepositOptions(popToRoot = true) }
        }

    @Test
    fun `OnPreferredOnRampProviderChanged updates state`() {
        val provider = mockk<OnRampProvider.Defined>()
        val updated = BalanceViewModel.updateStateForEvent(
            BalanceViewModel.Event.OnPreferredOnRampProviderChanged(provider)
        )(BalanceViewModel.State())
        assertEquals(provider, updated.preferredOnRampProvider)
    }
}
