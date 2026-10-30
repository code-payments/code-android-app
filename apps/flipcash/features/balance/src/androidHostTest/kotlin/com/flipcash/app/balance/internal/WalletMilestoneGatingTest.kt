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
import com.flipcash.shared.transactionhistory.FeedSyncState
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Add Money row hides until the activity feed has synced, shows while no money has been added
 * (tipping does not matter), and hides once money is added or a balance is held.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WalletMilestoneGatingTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val hasReceivedMoney = MutableStateFlow<Boolean?>(false)
    private val hasBalance = MutableStateFlow(false)

    private val feedCoordinator: ActivityFeedCoordinator = mockk(relaxed = true) {
        every { hasEverReceivedMoney() } returns hasReceivedMoney
    }
    private val tokenCoordinator: TokenCoordinator = mockk(relaxed = true) {
        every { hasAnyBalance } returns hasBalance
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
        tokenCoordinator = tokenCoordinator,
        walletReveal = walletReveal,
    )

    @Test
    fun `row is hidden until history has synced`() =
        runTest(mainCoroutineRule.dispatcher) {
            dispatchers = TestDispatchers(testScheduler)
            every { feedCoordinator.syncState } returns MutableStateFlow(FeedSyncState.Unknown)

            val vm = createViewModel()
            advanceUntilIdle()

            assertFalse(vm.stateFlow.value.showAddMoney)
        }

    @Test
    fun `row shows for a synced account that never added money, tipping or not`() =
        runTest(mainCoroutineRule.dispatcher) {
            dispatchers = TestDispatchers(testScheduler)
            every { feedCoordinator.syncState } returns MutableStateFlow(FeedSyncState.Synced)

            val vm = createViewModel()
            advanceUntilIdle()

            assertTrue(vm.stateFlow.value.showAddMoney)
            assertFalse(vm.stateFlow.value.hasReceivedMoney)
        }

    @Test
    fun `row hides and tiles show once money is added`() =
        runTest(mainCoroutineRule.dispatcher) {
            dispatchers = TestDispatchers(testScheduler)
            every { feedCoordinator.syncState } returns MutableStateFlow(FeedSyncState.Synced)

            val vm = createViewModel()
            advanceUntilIdle()
            hasReceivedMoney.value = true
            advanceUntilIdle()

            assertFalse(vm.stateFlow.value.showAddMoney)
            assertTrue(vm.stateFlow.value.hasReceivedMoney)
        }

    @Test
    fun `a held balance hides the row without a feed row`() =
        runTest(mainCoroutineRule.dispatcher) {
            dispatchers = TestDispatchers(testScheduler)
            every { feedCoordinator.syncState } returns MutableStateFlow(FeedSyncState.Synced)

            val vm = createViewModel()
            advanceUntilIdle()
            hasBalance.value = true
            advanceUntilIdle()

            assertFalse(vm.stateFlow.value.showAddMoney)
        }

    /** The feed answers `null` until it has synced: a stale `false` must not draw the row. */
    @Test
    fun `row stays hidden while the feed has no believable answer even if the sync state is Synced`() =
        runTest(mainCoroutineRule.dispatcher) {
            dispatchers = TestDispatchers(testScheduler)
            every { feedCoordinator.syncState } returns MutableStateFlow(FeedSyncState.Synced)
            hasReceivedMoney.value = null

            val vm = createViewModel()
            advanceUntilIdle()

            assertFalse(vm.stateFlow.value.showAddMoney)
        }

    @Test
    fun `a held balance proves money was added but an unknown feed answer stays unknown`() {
        assertEquals(true, WalletViewModel.receivedMoney(hasReceivedMoney = null, holdsBalance = true))
        assertEquals(true, WalletViewModel.receivedMoney(hasReceivedMoney = true, holdsBalance = false))
        assertEquals(null, WalletViewModel.receivedMoney(hasReceivedMoney = null, holdsBalance = false))
        assertEquals(false, WalletViewModel.receivedMoney(hasReceivedMoney = false, holdsBalance = false))
    }
}
