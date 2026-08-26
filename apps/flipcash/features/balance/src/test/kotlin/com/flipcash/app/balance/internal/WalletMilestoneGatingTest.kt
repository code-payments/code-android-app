package com.flipcash.app.balance.internal

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.flipcash.app.analytics.StubFlipcashAnalytics
import com.flipcash.app.balance.internal.components.TutorialItem
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.core.dispatchers.TestDispatchers
import com.flipcash.app.funding.PurchaseMethodController
import com.flipcash.app.tokens.TokenCoordinator
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The tip milestone is read off the chat cache, which reports every account as never having tipped
 * until its history has been reconciled. The wallet must not draw that: [WalletViewModel.State.
 * onboardingItems] stays null — holding the tab on its loading state — until the chat coordinator
 * commits to an answer.
 *
 * A held balance deliberately does not release the wait here, unlike for the activity feed: it is
 * evidence about money, not about tipping.
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
    )

    @Test
    fun `milestones are withheld while the tip milestone is unknown`() =
        runTest(mainCoroutineRule.dispatcher) {
            dispatchers = TestDispatchers(testScheduler)

            val vm = createViewModel()
            advanceUntilIdle()

            assertNull(
                vm.stateFlow.value.onboardingItems,
                "an un-reconciled chat cache reports every account as never having tipped",
            )
            assertTrue(
                vm.stateFlow.value.isAwaitingActivity,
                "the tab must keep loading rather than draw a milestone it cannot yet answer",
            )
        }

    @Test
    fun `milestones are published once the tip milestone resolves`() =
        runTest(mainCoroutineRule.dispatcher) {
            dispatchers = TestDispatchers(testScheduler)

            val vm = createViewModel()
            advanceUntilIdle()
            hasEverTipped.value = true
            advanceUntilIdle()

            val items = vm.stateFlow.value.onboardingItems
            assertNotNull(items)
            assertEquals(
                listOf(true, true),
                items.map { it.isCompleted },
                "both milestones read complete: the account holds a balance and has tipped",
            )
            assertTrue(items.any { it is TutorialItem.ScanTipCard })
        }
}
