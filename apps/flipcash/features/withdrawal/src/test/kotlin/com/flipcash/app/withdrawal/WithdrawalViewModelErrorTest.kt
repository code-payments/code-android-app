package com.flipcash.app.withdrawal

import android.content.ClipboardManager
import com.flipcash.app.activityfeed.ActivityFeedCoordinator
import com.flipcash.app.analytics.FlipcashAnalyticsService
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.services.user.UserManager
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.controllers.TransactionOperations
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.exchange.VerifiedFiatCalculator
import com.getcode.util.resources.FakeResourceHelper
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.core.dispatchers.TestDispatchers
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WithdrawalViewModelErrorTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val resources = FakeResourceHelper()
    private val exchange = mockk<Exchange>(relaxed = true)
    private val verifiedFiatCalculator = mockk<VerifiedFiatCalculator>(relaxed = true)
    private val userManager = mockk<UserManager>(relaxed = true)
    private val transactionController = mockk<TransactionOperations>(relaxed = true)
    private val clipboardManager = mockk<ClipboardManager>(relaxed = true)
    private val activityFeedCoordinator = mockk<ActivityFeedCoordinator>(relaxed = true)
    private val analytics = mockk<FlipcashAnalyticsService>(relaxed = true)
    private val tokenCoordinator = mockk<TokenCoordinator>(relaxed = true)
    private val userFlags = mockk<UserFlagsCoordinator>(relaxed = true)

    private lateinit var dispatchers: TestDispatchers

    @Before
    fun setUp() {
        BottomBarManager.clear()
    }

    @After
    fun tearDown() {
        BottomBarManager.clear()
    }

    private fun createViewModel(): WithdrawalViewModel {
        return WithdrawalViewModel(
            resources = resources,
            exchange = exchange,
            verifiedFiatCalculator = verifiedFiatCalculator,
            userManager = userManager,
            userFlags = userFlags,
            transactionController = transactionController,
            clipboardManager = clipboardManager,
            activityFeedCoordinator = activityFeedCoordinator,
            analytics = analytics,
            tokenCoordinator = tokenCoordinator,
            dispatchers = dispatchers,
        )
    }

    @Test
    fun `withdrawal failure with missing data shows failedWithdrawal error`() = runTest(mainCoroutineRule.dispatcher) {
        dispatchers = TestDispatchers(testScheduler)
        // Let the VM initialize with a valid (relaxed) accountCluster, then null it out
        // so OnWithdrawalConfirmed's mapNotNull guard triggers the error
        val vm = createViewModel()

        every { userManager.accountCluster } returns null
        vm.dispatchEvent(WithdrawalViewModel.Event.OnWithdrawalConfirmed)
        advanceUntilIdle()

        assertTrue(BottomBarManager.messages.value.any { it.title == "error_title_failedWithdrawal" })
    }
}
