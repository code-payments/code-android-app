package com.flipcash.app.tokens.ui

import com.flipcash.app.activityfeed.ActivityFeedCoordinator
import com.flipcash.app.analytics.FlipcashAnalyticsService
import com.flipcash.app.core.tokens.TokenSwapPurpose
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.services.user.UserManager
import com.flipcash.shared.tokens.R
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.controllers.TransactionOperations
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.TokenWithBalance
import com.getcode.opencode.utils.generate
import com.getcode.solana.keys.PublicKey
import com.getcode.util.resources.ResourceHelper
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.core.dispatchers.TestDispatchers
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.whenever
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BuySellSwapTokenViewModelErrorTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val userManager = mockk<UserManager>(relaxed = true)
    private val exchange = mockk<Exchange>(relaxed = true)
    // Mockito for Result-returning methods (MockK double-boxes Result inline class)
    private val transactionController: TransactionOperations = mock()
    private val resources = mockk<ResourceHelper>(relaxed = true)
    private val tokenCoordinator = mockk<TokenCoordinator>(relaxed = true)
    private val feedCoordinator = mockk<ActivityFeedCoordinator>(relaxed = true)
    private val analytics = mockk<FlipcashAnalyticsService>(relaxed = true)

    private val accountCluster = mockk<AccountCluster>(relaxed = true)

    private lateinit var dispatchers: TestDispatchers

    @Before
    fun setUp() {
        BottomBarManager.clear()
        mockkStatic("com.getcode.utils.LoggingKt")
        every { com.getcode.utils.trace(any(), any(), any(), any(), any()) } returns Unit

        // Mock PublicKey.generate() to prevent Ed25519 native lib load
        // (TransactionController.buy default param evaluates SwapFundingSource.SubmitIntent which calls Ed25519)
        mockkStatic("com.getcode.opencode.utils.PublicKeyKt")
        every { PublicKey.generate() } returns mockk<PublicKey>(relaxed = true)

        every { userManager.accountCluster } returns accountCluster
        every { resources.getString(R.string.error_title_buySellFailed) } returns "error_title_buySellFailed"
        every { resources.getString(R.string.error_description_buySellFailed) } returns "error_description_buySellFailed"

        // Stub limits StateFlow so init block doesn't NPE on null flow
        whenever(transactionController.limits).thenReturn(MutableStateFlow(null))
    }

    @After
    fun tearDown() {
        BottomBarManager.clear()
        unmockkStatic("com.getcode.utils.LoggingKt")
        unmockkStatic("com.getcode.opencode.utils.PublicKeyKt")
    }

    private fun createViewModel(): BuySellSwapTokenViewModel {
        return BuySellSwapTokenViewModel(
            userManager = userManager,
            exchange = exchange,
            transactionController = transactionController,
            resources = resources,
            tokenCoordinator = tokenCoordinator,
            feedCoordinator = feedCoordinator,
            analytics = analytics,
            dispatchers = dispatchers,
        )
    }

    @Test
    fun `buy failure shows buySellFailed error`() = runTest(mainCoroutineRule.dispatcher) {
        dispatchers = TestDispatchers(testScheduler)
        transactionController.stub {
            onBlocking { buy(any(), any(), anyOrNull(), any(), any(), anyOrNull()) } doReturn
                Result.failure(RuntimeException("buy failed"))
        }

        val token = mockk<Token>(relaxed = true)
        val tokenWithBalance = mockk<TokenWithBalance>(relaxed = true) {
            every { this@mockk.token } returns token
        }
        val amount = mockk<LocalFiat>(relaxed = true)

        val vm = createViewModel()
        vm.dispatchEvent(BuySellSwapTokenViewModel.Event.OnPurposeChanged(TokenSwapPurpose.Buy(mockk(relaxed = true))))
        vm.dispatchEvent(BuySellSwapTokenViewModel.Event.OnSelectedTokenChanged(tokenWithBalance))
        vm.dispatchEvent(BuySellSwapTokenViewModel.Event.ProceedWithPurchase(amount))
        advanceUntilIdle()

        assertTrue(BottomBarManager.messages.value.any { it.title == "error_title_buySellFailed" })
    }

    @Test
    fun `sell failure shows buySellFailed error`() = runTest(mainCoroutineRule.dispatcher) {
        dispatchers = TestDispatchers(testScheduler)
        whenever(transactionController.sell(any(), any(), any()))
            .thenReturn(Result.failure(RuntimeException("sell failed")))

        val token = mockk<Token>(relaxed = true)
        val tokenWithBalance = mockk<TokenWithBalance>(relaxed = true) {
            every { this@mockk.token } returns token
        }
        val amount = mockk<LocalFiat>(relaxed = true)

        val vm = createViewModel()
        vm.dispatchEvent(BuySellSwapTokenViewModel.Event.OnPurposeChanged(TokenSwapPurpose.Sell(mockk(relaxed = true))))
        vm.dispatchEvent(BuySellSwapTokenViewModel.Event.OnSelectedTokenChanged(tokenWithBalance))
        vm.dispatchEvent(BuySellSwapTokenViewModel.Event.ProceedWithSale(amount))
        advanceUntilIdle()

        assertTrue(BottomBarManager.messages.value.any { it.title == "error_title_buySellFailed" })
    }
}
