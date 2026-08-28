package com.flipcash.app.balance.internal

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.flipcash.app.analytics.StubFlipcashAnalytics
import com.flipcash.app.core.ui.onboarding.TutorialItem
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.core.dispatchers.TestDispatchers
import com.flipcash.app.funding.PurchaseMethodController
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.app.tokens.WalletRevealCoordinator
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.transactionhistory.ActivityFeedCoordinator
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The tip milestone is read off the chat cache, which reports every account as never having tipped
 * until its history has been reconciled — a wait that scales with how many conversations the
 * account has. The wallet must not draw that answer, but it must not wait on it either: the
 * milestones publish immediately off local state, carrying
 * [WalletViewModel.State.isTipMilestoneResolved] to say whether the tip half can be believed, and
 * only the tutorial reads that flag.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WalletMilestoneGatingTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val hasEverTipped = MutableStateFlow<Boolean?>(null)

    private val chatCoordinator: ChatCoordinator = mockk(relaxed = true) {
        every { hasEverTipped() } returns hasEverTipped
    }
    private val feedCoordinator: ActivityFeedCoordinator = mockk(relaxed = true) {
        every { hasEverReceivedMoney() } returns flowOf(true)
    }
    private val tokenCoordinator: TokenCoordinator = mockk(relaxed = true) {
        every { hasAnyBalance } returns flowOf(true)
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
        analytics = StubFlipcashAnalytics(),
        chatCoordinator = chatCoordinator,
        feedCoordinator = feedCoordinator,
        tokenCoordinator = tokenCoordinator,
        walletReveal = walletReveal,
    )

    @Test
    fun `milestones publish without waiting on the tip milestone`() =
        runTest(mainCoroutineRule.dispatcher) {
            dispatchers = TestDispatchers(testScheduler)

            val vm = createViewModel()
            advanceUntilIdle()

            val state = vm.stateFlow.value
            assertNotNull(
                state.onboardingItems,
                "the add-money milestone is answerable from local state and must not wait",
            )
            assertFalse(
                state.isTipMilestoneResolved,
                "an un-reconciled chat cache cannot answer whether the account has tipped",
            )
            assertTrue(
                state.isNewUserTutorialComplete,
                "so the tutorial withholds itself rather than drawing an answer it does not have",
            )
            assertTrue(
                state.hasReceivedMoney,
                "the milestone the chat cache says nothing about is drawn immediately",
            )
        }

    @Test
    fun `the tip milestone is believed once the chat cache reconciles`() =
        runTest(mainCoroutineRule.dispatcher) {
            dispatchers = TestDispatchers(testScheduler)

            val vm = createViewModel()
            advanceUntilIdle()
            hasEverTipped.value = true
            advanceUntilIdle()

            val state = vm.stateFlow.value
            val items = state.onboardingItems
            assertNotNull(items)
            assertTrue(state.isTipMilestoneResolved)
            assertEquals(
                listOf(true, true),
                items.map { it.isCompleted },
                "both milestones read complete: the account holds a balance and has tipped",
            )
            assertTrue(items.any { it is TutorialItem.ScanTipCard })
        }

    @Test
    fun `a reconciled cache that has never seen a tip draws the tutorial`() =
        runTest(mainCoroutineRule.dispatcher) {
            dispatchers = TestDispatchers(testScheduler)

            val vm = createViewModel()
            advanceUntilIdle()
            hasEverTipped.value = false
            advanceUntilIdle()

            val state = vm.stateFlow.value
            assertTrue(state.isTipMilestoneResolved)
            assertFalse(
                state.isNewUserTutorialComplete,
                "an answered 'never tipped' is an outstanding milestone, not an unknown one",
            )
        }
}
