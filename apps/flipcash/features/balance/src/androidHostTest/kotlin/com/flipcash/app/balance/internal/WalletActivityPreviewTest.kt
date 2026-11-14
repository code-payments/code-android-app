package com.flipcash.app.balance.internal

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.flipcash.app.analytics.FlipcashAnalytics
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.core.dispatchers.TestDispatchers
import com.flipcash.app.funding.PurchaseMethodController
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.app.tokens.WalletRevealCoordinator
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.services.user.UserManager
import com.flipcash.shared.transactionhistory.ActivityFeedCoordinator
import com.flipcash.shared.transactionhistory.TransactionListItem
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

/**
 * The wallet previews three rows of activity and the Money tab enough to fill what is left of the
 * screen. The screen says which it is, and the preview follows when it changes (the flag flipped in
 * Beta Features) without the view model being rebuilt.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WalletActivityPreviewTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private fun rows(count: Int) = List(count) { mockk<TransactionListItem>(relaxed = true) }

    private val feedCoordinator: ActivityFeedCoordinator = mockk(relaxed = true) {
        every { recentTransactions(limit = WalletViewModel.RECENT_PREVIEW_COUNT) } returns
            flowOf(rows(WalletViewModel.RECENT_PREVIEW_COUNT))
        every { recentTransactions(limit = WalletViewModel.MONEY_PREVIEW_COUNT) } returns
            flowOf(rows(WalletViewModel.MONEY_PREVIEW_COUNT))
    }

    private val walletReveal: WalletRevealCoordinator = mockk(relaxed = true) {
        every { pending } returns MutableStateFlow(null)
    }

    private lateinit var dispatchers: TestDispatchers

    private fun createViewModel() = WalletViewModel(
        userManager = mockk<UserManager>(relaxed = true),
        userFlags = mockk<UserFlagsCoordinator>(relaxed = true),
        dispatchers = dispatchers,
        purchaseMethodController = mockk<PurchaseMethodController>(relaxed = true),
        analytics = FlipcashAnalytics.None,
        feedCoordinator = feedCoordinator,
        tokenCoordinator = mockk<TokenCoordinator>(relaxed = true) {
            every { hasAnyBalance } returns flowOf(false)
        },
        walletReveal = walletReveal,
    )

    @Test
    fun `the preview follows the size the screen asks for`() =
        runTest(mainCoroutineRule.dispatcher) {
            dispatchers = TestDispatchers(testScheduler)
            val vm = createViewModel()
            advanceUntilIdle()
            assertEquals(WalletViewModel.RECENT_PREVIEW_COUNT, vm.stateFlow.value.transactions.size)

            vm.dispatchEvent(WalletViewModel.Event.OnActivityPreviewSizeChanged(WalletViewModel.MONEY_PREVIEW_COUNT))
            advanceUntilIdle()
            assertEquals(WalletViewModel.MONEY_PREVIEW_COUNT, vm.stateFlow.value.transactions.size)

            vm.dispatchEvent(WalletViewModel.Event.OnActivityPreviewSizeChanged(WalletViewModel.RECENT_PREVIEW_COUNT))
            advanceUntilIdle()
            assertEquals(WalletViewModel.RECENT_PREVIEW_COUNT, vm.stateFlow.value.transactions.size)
        }
}
