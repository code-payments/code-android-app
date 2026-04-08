package com.flipcash.app.transactions.internal

import com.flipcash.app.activityfeed.ActivityFeedCoordinator
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.features.transactions.R
import com.flipcash.services.user.UserManager
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.controllers.TransactionOperations
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.solana.keys.PublicKey
import com.getcode.util.resources.ResourceHelper
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
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionHistoryViewModelErrorTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val tokenCoordinator = mockk<TokenCoordinator>(relaxed = true)
    private val feedCoordinator = mockk<ActivityFeedCoordinator>(relaxed = true)
    // Mockito for Result-returning methods (MockK double-boxes Result inline class)
    private val transactionController: TransactionOperations = mock()
    private val featureFlags = mockk<FeatureFlagController>(relaxed = true)
    private val userManager = mockk<UserManager>(relaxed = true)
    private val resources = mockk<ResourceHelper>(relaxed = true)

    private val accountCluster = mockk<AccountCluster>(relaxed = true)

    private lateinit var dispatchers: TestDispatchers

    @Before
    fun setUp() {
        BottomBarManager.clear()

        every { userManager.accountCluster } returns accountCluster
        every { resources.getString(R.string.error_title_failedToCancelTransfer) } returns "error_title_failedToCancelTransfer"
        every { resources.getString(R.string.error_description_failedToCancelTransfer) } returns "error_description_failedToCancelTransfer"
    }

    @After
    fun tearDown() {
        BottomBarManager.clear()
    }

    private fun createViewModel(): TransactionHistoryViewModel {
        return TransactionHistoryViewModel(
            tokenCoordinator = tokenCoordinator,
            feedCoordinator = feedCoordinator,
            transactionController = transactionController,
            featureFlags = featureFlags,
            userManager = userManager,
            resources = resources,
            dispatchers = dispatchers,
        )
    }

    @Test
    fun `cancel transfer failure shows failedToCancelTransfer error`() = runTest(mainCoroutineRule.dispatcher) {
        dispatchers = TestDispatchers(testScheduler)
        val vault = mockk<PublicKey>(relaxed = true)
        whenever(transactionController.cancelRemoteSend(owner = any(), vault = any()))
            .thenReturn(Result.failure(RuntimeException("cancel failed")))

        val vm = createViewModel()
        vm.dispatchEvent(TransactionHistoryViewModel.Event.CancelTransfer(vault))
        advanceUntilIdle()

        assertTrue(BottomBarManager.messages.value.any { it.title == "error_title_failedToCancelTransfer" })
    }
}
